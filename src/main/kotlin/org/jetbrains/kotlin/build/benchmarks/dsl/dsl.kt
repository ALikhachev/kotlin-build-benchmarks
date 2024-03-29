/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.benchmarks.dsl

import java.io.File

fun suite(fn: SuiteBuilder.() -> Unit): Suite =
    SuiteBuilderImpl().apply(fn).build()

@DslMarker
annotation class BenchmarksDslMarker

@BenchmarksDslMarker
interface SuiteBuilder {
    fun scenario(name: String, fn: ScenarioBuilder.() -> Unit)
    fun defaultTasks(vararg tasks: Tasks)
    fun defaultTasks(vararg tasks: String)
    var defaultJdk: String?
    fun changeableFile(name: String): ChangeableFile
    fun defaultArguments(vararg arguments: String)
}

@BenchmarksDslMarker
interface ScenarioBuilder {
    fun step(fn: StepWithFileChangesBuilder.() -> Unit)
    fun revertLastStep(fn: StepBuilder.() -> Unit)
    fun expectSlowBuild(reason: String)
    fun arguments(vararg arguments: String)
    fun trackedMetrics(trackedMetrics: Set<String>?)
    fun stopDaemon()
    fun cleanupTasks(vararg taskNames: String)
    var repeat: UByte
    var jdk: String?
    var k2CompatibleTasks: Set<String>
}

@BenchmarksDslMarker
interface StepBuilder {
    var isMeasured: Boolean
    fun doNotMeasure() {
        isMeasured = false
    }

    var isExpectedToFail: Boolean
    fun expectBuildToFail() {
        isExpectedToFail = true
    }

    fun runTasks(vararg tasksToRun: Tasks)

    fun runTasks(vararg tasksToRun: String)
}

interface StepWithFileChangesBuilder : StepBuilder {
    fun changeFile(changeableFile: ChangeableFile, typeOfChange: TypeOfChange)
}

class SuiteBuilderImpl : SuiteBuilder {
    private var defaultTasks = arrayOf<String>()
    override var defaultJdk: String? = null
    private val scenarios = arrayListOf<Scenario>()
    private val changeableFiles = arrayListOf<ChangeableFile>()
    private val defaultArguments = arrayListOf<String>()

    override fun scenario(name: String, fn: ScenarioBuilder.() -> Unit) {
        scenarios.add(ScenarioBuilderImpl(name = name).apply(fn).build())
    }

    override fun defaultTasks(vararg tasks: Tasks) {
        defaultTasks = tasks.map { it.path }.toTypedArray()
    }

    override fun defaultTasks(vararg tasks: String) {
        defaultTasks = arrayOf(*tasks)
    }

    override fun changeableFile(name: String): ChangeableFile {
        val changeableFile = ChangeableFile(name)
        changeableFiles.add(changeableFile)
        return changeableFile
    }

    override fun defaultArguments(vararg arguments: String) {
        defaultArguments.addAll(arguments)
    }

    fun build() =
        Suite(scenarios = scenarios.toTypedArray(), defaultTasks = defaultTasks, changeableFiles = changeableFiles.toTypedArray(), defaultJdk = defaultJdk?.let { File(it) }, defaultArguments = defaultArguments.toTypedArray())
}

class ScenarioBuilderImpl(private val name: String) : ScenarioBuilder {
    override var repeat: UByte = 1U
    override var jdk: String? = null
    override var k2CompatibleTasks: Set<String> = emptySet()
    private var trackedMetrics: Set<String>? = null
    private var arguments: MutableList<String>? = null
    private var cleanupTasks: MutableList<String>? = null

    private var expectedSlowBuildReason: String? = null
    override fun expectSlowBuild(reason: String) {
        expectedSlowBuildReason = reason
    }

    override fun arguments(vararg arguments: String) {
        if (this.arguments == null) {
            this.arguments = arrayListOf()
        }
        this.arguments!!.addAll(arguments)
    }

    override fun trackedMetrics(trackedMetrics: Set<String>?) {
        this.trackedMetrics = trackedMetrics?.toMutableSet()?.apply {
            add("GRADLE_BUILD")
            add("GRADLE_BUILD.CONFIGURATION")
        }
    }

    private val steps = arrayListOf<Step>()

    override fun step(fn: StepWithFileChangesBuilder.() -> Unit) {
        steps.add(SimpleStepBuilder().apply(fn).build())
    }

    override fun revertLastStep(fn: StepBuilder.() -> Unit) {
        steps.add(RevertStepBuilder().apply(fn).build())
    }

    override fun stopDaemon() {
        steps.add(Step.StopDaemon())
    }

    override fun cleanupTasks(vararg taskNames: String) {
        if (cleanupTasks == null) {
            cleanupTasks = mutableListOf()
        }
        val cleanupTasks = this.cleanupTasks ?: error("Cleanup tasks are set to null")
        cleanupTasks += taskNames
    }

    fun build() =
        Scenario(
            name = name,
            steps = steps.toTypedArray(),
            expectedSlowBuildReason = expectedSlowBuildReason,
            repeat = repeat,
            k2CompatibleTasks = k2CompatibleTasks,
            jdk = jdk?.let { File(it) },
            arguments = arguments?.toTypedArray(),
            trackedMetrics = trackedMetrics,
            explicitCleanupTasks = cleanupTasks?.toTypedArray(),
        )
}

abstract class AbstractStepBuilder : StepBuilder {
    override var isMeasured = true
    override var isExpectedToFail = false
    protected var tasks: Array<String>? = null

    override fun runTasks(vararg tasksToRun: Tasks) {
        this.tasks = tasksToRun.map { it.path }.toTypedArray()
    }

    override fun runTasks(vararg tasksToRun: String) {
        this.tasks = arrayOf(*tasksToRun)
    }
}

class SimpleStepBuilder : AbstractStepBuilder(), StepWithFileChangesBuilder {
    private val fileChanges = arrayListOf<FileChange>()

    override fun changeFile(changeableFile: ChangeableFile, typeOfChange: TypeOfChange) {
        fileChanges.add(FileChange(changeableFile, typeOfChange))
    }

    fun build() =
        Step.SimpleStep(
            isMeasured = this.isMeasured,
            isExpectedToFail = this.isExpectedToFail,
            tasks = this.tasks,
            fileChanges = this.fileChanges.toTypedArray()
        )
}

class RevertStepBuilder : AbstractStepBuilder() {
    fun build() =
        Step.RevertLastStep(
            isMeasured = this.isMeasured,
            isExpectedToFail = this.isExpectedToFail,
            tasks = this.tasks
        )
}