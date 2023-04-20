/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.benchmarks.evaluation

import org.jetbrains.kotlin.build.benchmarks.dsl.Scenario
import org.jetbrains.kotlin.build.benchmarks.dsl.Step
import org.jetbrains.kotlin.build.benchmarks.dsl.Suite
import org.jetbrains.kotlin.build.benchmarks.dsl.Tasks
import org.jetbrains.kotlin.build.benchmarks.evaluation.results.ScenarioResult
import org.jetbrains.kotlin.build.benchmarks.evaluation.results.StepResult
import org.jetbrains.kotlin.build.benchmarks.evaluation.validation.checkBenchmarks
import org.jetbrains.kotlin.build.benchmarks.utils.Either
import java.io.File
import java.io.OutputStream

abstract class AbstractBenchmarkEvaluator(private val projectPath: File) {
    private val changesApplier = ChangesApplier(projectPath)
    protected val progress = CompositeBenchmarksProgressListener()
    var buildLogsOutputStreamProvider: ((scenarioName: String, stepName: String, scenarioIteration: UInt) -> OutputStream)? = null

    fun addListener(progressListener: BenchmarksProgressListener) {
        progress.add(progressListener)
    }

    open fun runBenchmarks(benchmarks: Suite) {
        checkBenchmarks(projectPath, benchmarks)

        try {
            progress.startBenchmarks()
            var prevScenario: Scenario? = null
            var prevIteration: UInt = 1U
            scenario@ for (scenario in benchmarks.scenarios) {
                for (scenarioIteration in (1U..scenario.repeat.toUInt())) {
                    if (prevScenario != null) {
                        cleanup(benchmarks, scenario, prevScenario, prevIteration)
                    }
                    progress.scenarioStarted(scenario)

                    val stepsResults = arrayListOf<StepResult>()
                    for ((stepIndex, step) in scenario.steps.withIndex()) {
                        if (step is Step.StopDaemon) {
                            stopDaemons()
                            continue
                        }

                        progress.stepStarted(step)

                        if (!changesApplier.applyStepChanges(step)) {
                            System.err.println("Aborting scenario: could not apply step changes")
                            continue@scenario
                        }
                        val stepResult = try {
                            val buildLogsOutputStream = getBuildLogsOutputStream(scenario.name, (stepIndex + 1).toString(), scenarioIteration)
                            runBuild(benchmarks, scenario, step, buildLogsOutputStream)
                        } catch (e: Exception) {
                            Either.Failure(e)
                        }

                        when (stepResult) {
                            is Either.Failure -> {
                                System.err.println("Aborting scenario: step failed")
                                progress.stepFinished(step, stepResult)
                                progress.scenarioFinished(scenario, Either.Failure("Step ${stepIndex + 1} failed. See logs in artifacts for more information."))
                                continue@scenario
                            }
                            is Either.Success<StepResult> -> {
                                stepsResults.add(stepResult.value)
                                progress.stepFinished(step, stepResult)
                            }
                        }
                    }

                    prevScenario = scenario
                    prevIteration = scenarioIteration
                    progress.scenarioFinished(scenario, Either.Success(ScenarioResult(stepsResults)))
                }
            }
            progress.allFinished()
        } finally {
            changesApplier.revertAppliedChanges()
        }
    }

    private fun cleanup(benchmarks: Suite, scenario: Scenario, prevScenario: Scenario, prevIteration: UInt) {
        if (!changesApplier.hasAppliedChanges) return

        progress.cleanupStarted()
        // ensure tasks in scenario are not affected by previous scenarios
        changesApplier.revertAppliedChanges()
        val tasksToBeRun = prevScenario.explicitCleanupTasks?.toSet()
            ?: scenario.steps.flatMapTo(LinkedHashSet()) { (it.tasks ?: benchmarks.defaultTasks).toList() }
                .also { it.remove(Tasks.CLEAN.path) }

        val buildLogsOutputStream = getBuildLogsOutputStream(prevScenario.name, "cleanup", prevIteration)

        val jdk = scenario.jdk ?: benchmarks.defaultJdk
        val arguments = scenario.arguments ?: benchmarks.defaultArguments

        if (tasksToBeRun.isNotEmpty()) {
            runBuild(jdk, tasksToBeRun.toTypedArray(), buildLogsOutputStream, arguments = arguments)
        } else {
            println("No cleanup tasks")
        }
        progress.cleanupFinished()
    }

    private fun getBuildLogsOutputStream(scenarioName: String, step: String, iteration: UInt): OutputStream? {
        return buildLogsOutputStreamProvider?.let { it(scenarioName, step, iteration) }
    }

    protected abstract fun stopDaemons()

    protected abstract fun runBuild(
        suite: Suite,
        scenario: Scenario,
        step: Step,
        buildLogsOutputStream: OutputStream?
    ): Either<StepResult>

    protected abstract fun runBuild(
        jdk: File?,
        tasksToExecute: Array<String>,
        buildLogsOutputStream: OutputStream?,
        isExpectedToFail: Boolean = false,
        arguments: Array<String> = emptyArray(),
        k2CompatibleTasks: Set<String> = emptySet()
    ): Either<BuildResult>
}

