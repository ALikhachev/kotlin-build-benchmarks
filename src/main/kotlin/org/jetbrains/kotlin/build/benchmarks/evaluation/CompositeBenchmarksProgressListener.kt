/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.benchmarks.evaluation

import org.jetbrains.kotlin.build.benchmarks.dsl.Scenario
import org.jetbrains.kotlin.build.benchmarks.dsl.Step
import org.jetbrains.kotlin.build.benchmarks.dsl.Tasks
import org.jetbrains.kotlin.build.benchmarks.evaluation.results.ScenarioResult
import org.jetbrains.kotlin.build.benchmarks.evaluation.results.StepResult
import org.jetbrains.kotlin.build.benchmarks.utils.Either

class CompositeBenchmarksProgressListener : BenchmarksProgressListener {
    private val delegates = arrayListOf<BenchmarksProgressListener>()

    fun add(progressListener: BenchmarksProgressListener) {
        delegates.add(progressListener)
    }

    private inline fun forEachListener(fn: (BenchmarksProgressListener) -> Unit) {
        delegates.forEach(fn)
    }

    override fun scenarioStarted(scenario: Scenario) {
        forEachListener { it.scenarioStarted(scenario) }
    }

    override fun scenarioFinished(scenario: Scenario, result: Either<ScenarioResult>) {
        forEachListener { it.scenarioFinished(scenario, result) }
    }

    override fun stepStarted(step: Step) {
        forEachListener { it.stepStarted(step) }
    }

    override fun stepFinished(step: Step, result: Either<StepResult>) {
        forEachListener { it.stepFinished(step, result) }
    }

    override fun taskExecutionStarted(tasks: Array<String>) {
        forEachListener { it.taskExecutionStarted(tasks) }
    }

    override fun cleanupStarted() {
        forEachListener { it.cleanupStarted() }
    }

    override fun cleanupFinished() {
        forEachListener { it.cleanupFinished() }
    }

    override fun startBenchmarks() {
        forEachListener { it.startBenchmarks() }
    }

    override fun allFinished() {
        forEachListener { it.allFinished() }
    }
}