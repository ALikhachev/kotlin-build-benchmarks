/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.benchmarks.evaluation.gradle

import org.jetbrains.kotlin.build.benchmarks.dsl.Scenario
import org.jetbrains.kotlin.build.benchmarks.dsl.Step
import org.jetbrains.kotlin.build.benchmarks.dsl.Suite
import org.jetbrains.kotlin.build.benchmarks.evaluation.AbstractBenchmarkEvaluator
import org.jetbrains.kotlin.build.benchmarks.evaluation.BuildResult
import org.jetbrains.kotlin.build.benchmarks.evaluation.results.MutableMetricsContainer
import org.jetbrains.kotlin.build.benchmarks.evaluation.results.StepResult
import org.jetbrains.kotlin.build.benchmarks.evaluation.results.ValueMetric
import org.jetbrains.kotlin.build.benchmarks.utils.Either
import org.jetbrains.kotlin.build.benchmarks.utils.TimeInterval
import org.jetbrains.kotlin.build.benchmarks.utils.mapSuccess
import org.jetbrains.kotlin.build.benchmarks.utils.stackTraceString
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.jetbrains.kotlin.gradle.internal.build.metrics.GradleBuildMetricsData
import java.io.File
import java.io.ObjectInputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.LinkedHashMap

class GradleBenchmarkEvaluator(private val projectPath: File) : AbstractBenchmarkEvaluator(projectPath) {
    private lateinit var gradleConnector: GradleConnector
    private lateinit var c: ProjectConnection
    private val heapDumpPath = System.getenv("HEAP_DUMP_PATH")

    override fun runBenchmarks(benchmarks: Suite) {
        initDaemonConnection()

        try {
            super.runBenchmarks(benchmarks)
        } finally {
            cleanupDaemonConnections()
        }
    }

    private fun initDaemonConnection() {
        gradleConnector = GradleConnector.newConnector()
        val root = projectPath.absoluteFile
        c = gradleConnector.forProjectDirectory(root).connect()
    }

    private fun cleanupDaemonConnections() {
        println("Stopping Gradle daemons...")
        gradleConnector.disconnect()
        c.close()
    }

    override fun stopDaemons() {
        cleanupDaemonConnections()
        initDaemonConnection()
    }

    override fun runBuild(suite: Suite, scenario: Scenario, step: Step, buildLogsOutputStream: OutputStream?): Either<StepResult> {
        val tasksToExecute = step.tasks ?: suite.defaultTasks
        val jdk = scenario.jdk ?: suite.defaultJdk
        val arguments = scenario.arguments ?: suite.defaultArguments
        return runBuild(jdk, tasksToExecute, buildLogsOutputStream, step.isExpectedToFail, arguments)
            .mapSuccess { metrics -> StepResult(step, metrics) }
    }

    override fun runBuild(jdk: File?, tasksToExecute: Array<String>, buildLogsOutputStream: OutputStream?, isExpectedToFail: Boolean, arguments: Array<String>): Either<BuildResult> {
        val gradleBuildListener = BuildRecordingProgressListener()
        val metricsFile = File.createTempFile("kt-benchmarks-", "-metrics").apply { deleteOnExit() }
        val jvmArguments = mutableListOf<String>()
        if (!heapDumpPath.isNullOrEmpty()) {
            jvmArguments += "-XX:HeapDumpPath=${heapDumpPath}"
            jvmArguments += "-XX:+HeapDumpOnOutOfMemoryError"
        }

        try {
            progress.taskExecutionStarted(tasksToExecute)
            c.newBuild()
                .forTasks(*tasksToExecute)
                .withArguments("-Pkotlin.internal.single.build.metrics.file=${metricsFile.absolutePath}", *arguments)
                .setJvmArguments(jvmArguments)
                .setJavaHome(jdk)
                .setStandardOutput(buildLogsOutputStream)
                .setStandardError(buildLogsOutputStream)
                .addProgressListener(gradleBuildListener)
                .run()
        } catch (e: Exception) {
            if (!isExpectedToFail) {
                return Either.Failure(e)
            }
        }

        val timeMetrics = MutableMetricsContainer<TimeInterval>()
        timeMetrics[GradlePhasesMetrics.GRADLE_BUILD] = gradleBuildListener.allBuildTime
        timeMetrics[GradlePhasesMetrics.CONFIGURATION] = gradleBuildListener.configTime
        timeMetrics[GradlePhasesMetrics.EXECUTION] = gradleBuildListener.taskExecutionTime
        // todo: split inputs and outputs checks time
        timeMetrics[GradlePhasesMetrics.UP_TO_DATE_CHECKS] =
            gradleBuildListener.snapshotBeforeTaskTime + gradleBuildListener.snapshotAfterTaskTime
        timeMetrics[GradlePhasesMetrics.UP_TO_DATE_CHECKS_BEFORE_TASK] = gradleBuildListener.snapshotBeforeTaskTime
        timeMetrics[GradlePhasesMetrics.UP_TO_DATE_CHECKS_AFTER_TASK] = gradleBuildListener.snapshotAfterTaskTime
        gradleBuildListener.timeToRunFirstTest?.let {timeMetrics[GradlePhasesMetrics.FIRST_TEST_EXECUTION_WAITING] = it }

        if (metricsFile.exists() && metricsFile.length() > 0) {
            try {
                val buildData = ObjectInputStream(metricsFile.inputStream().buffered()).use { input ->
                    input.readObject() as GradleBuildMetricsData
                }
                buildData.parentMetric["GRADLE_TASK_ACTION"] = "GRADLE_TASK"
                addTaskExecutionData(timeMetrics, buildData, gradleBuildListener.taskTimes, gradleBuildListener.javaInstrumentationTimeMs)
            } catch (e: Exception) {
                System.err.println("Could not read metrics: ${e.stackTraceString()}")
                return Either.Failure(e)
            } finally {
                metricsFile.delete()
            }
        }

        return Either.Success(BuildResult(timeMetrics))
    }

    private fun addTaskExecutionData(
        timeMetrics: MutableMetricsContainer<TimeInterval>,
        buildData: GradleBuildMetricsData,
        taskTimes: Map<String, TimeInterval>,
        javaInstrumentationTime: TimeInterval
    ) {
        var compilationTime = TimeInterval(0)
        var nonCompilationTime = TimeInterval(0)

        val taskDataByType = buildData.buildOperationData.values.groupByTo(TreeMap()) { shortTaskTypeName(it.typeFqName) }
        for ((typeFqName, tasksData) in taskDataByType) {
            val aggregatedTimeMs = LinkedHashMap<String, Long>()
            var timeForTaskType = TimeInterval(0)
            fun replaceRootName(name: String) = if (buildData.parentMetric[name] == null) typeFqName else name

            for (taskData in tasksData) {
                if (!taskData.didWork) continue

                timeForTaskType += taskTimes.getOrElse(taskData.path) { TimeInterval(0) }

                for ((metricName, timeMs) in taskData.buildTimesMs) {
                    if (timeMs <= 0) continue

                    // replace root metric name with task type fq name
                    val name = replaceRootName(metricName)
                    aggregatedTimeMs[name] = aggregatedTimeMs.getOrDefault(name, 0L) + timeMs
                }
            }
            val taskTypeContainer = MutableMetricsContainer<TimeInterval>()
            for ((metricName, timeMs) in aggregatedTimeMs) {
                val parentName = buildData.parentMetric[metricName]?.let { replaceRootName(it) }
                val value = ValueMetric(TimeInterval.ms(timeMs))
                taskTypeContainer.set(metricName, value, parentName)
            }
            if (typeFqName == "JavaCompile") {
                taskTypeContainer.set("Not null instrumentation", ValueMetric(javaInstrumentationTime), "JavaCompile")
            }

            val parentMetric = if (typeFqName in compileTasksTypes) {
                compilationTime += timeForTaskType
                GradlePhasesMetrics.COMPILATION_TASKS.name
            } else {
                nonCompilationTime += timeForTaskType
                GradlePhasesMetrics.NON_COMPILATION_TASKS.name
            }
            timeMetrics.set(typeFqName, taskTypeContainer, parentMetric = parentMetric)
        }

        val buildSrcCompile = taskTimes.getOrElse(":buildSrc:compileKotlin") { TimeInterval(0) }
        compilationTime += buildSrcCompile
        timeMetrics[GradlePhasesMetrics.KOTLIN_COMPILE_BUILD_SRC] = buildSrcCompile
        timeMetrics[GradlePhasesMetrics.COMPILATION_TASKS] = compilationTime
        timeMetrics[GradlePhasesMetrics.NON_COMPILATION_TASKS] = nonCompilationTime
    }

    private val compileTasksTypes = setOf("JavaCompile", "KotlinCompile", "KotlinCompileCommon", "Kotlin2JsCompile")

    private fun shortTaskTypeName(fqName: String) =
        fqName.substringAfterLast(".").removeSuffix("_Decorated")
}
