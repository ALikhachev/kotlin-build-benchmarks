/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.benchmarks.dsl

import java.io.File

class Suite(
    val scenarios: Array<Scenario>,
    val defaultTasks: Array<String>,
    val changeableFiles: Array<ChangeableFile>,
    val defaultJdk: File?,
    val defaultArguments: Array<String>
) {
    fun copy(scenarios: Array<Scenario> = this.scenarios,
             defaultTasks: Array<String> = this.defaultTasks,
             changeableFiles: Array<ChangeableFile> = this.changeableFiles,
             defaultJdk: File? = this.defaultJdk,
             defaultArguments: Array<String> = this.defaultArguments) =
        Suite(scenarios, defaultTasks, changeableFiles, defaultJdk, defaultArguments)
}

class Scenario(
    val expectedSlowBuildReason: String? = null,
    val name: String,
    val steps: Array<Step>,
    val repeat: UByte,
    val jdk: File?,
    val arguments: Array<String>?,
    val trackedMetrics: Set<String>?
)

sealed class Step {
    abstract val isMeasured: Boolean
    abstract val isExpectedToFail: Boolean
    abstract val tasks: Array<String>?

    class SimpleStep(
        override val isMeasured: Boolean,
        override val isExpectedToFail: Boolean,
        override val tasks: Array<String>?,
        val fileChanges: Array<FileChange>
    ) : Step()

    class RevertLastStep(
        override val isMeasured: Boolean,
        override val isExpectedToFail: Boolean,
        override val tasks: Array<String>?
    ) : Step()
}

data class FileChange(val changeableFile: ChangeableFile, val typeOfChange: TypeOfChange) {
    val changedFile: File
        get() = changeableFile.changedFile(typeOfChange)
}

private const val modFilesRootPath = "src/main/resources/change-files"

class ChangeableFile(changeFilesDirName: String) {
    private val changeFilesDir = File(modFilesRootPath, changeFilesDirName)

    val targetFile by lazy {
        changeFilesDir.resolve("_target-file.txt").readText().trim()
    }

    /*
        When source files in the Kotlin project are changed, unwanted changes might be introduced to the benchmarks
        (because change-files contain a copy of a target file at the moment of the last commit).

        _initial.benchmark is the last copy of a target file.
        In other words it is a base for all change-files.

        When a target file is updated, a corresponding _initial.benchmark should be updated too.
    */
    val expectedInitialFile: File =
        changeFilesDir.resolve("_initial.benchmark")

    fun changedFile(change: TypeOfChange): File =
        changeFilesDir.resolve("${change.name.constantCaseToCamelCase()}.benchmark")
}

@Suppress("unused")
enum class TypeOfChange {
    ADD_PRIVATE_FUNCTION,
    ADD_PUBLIC_FUNCTION,
    ADD_PRIVATE_CLASS,
    ADD_PUBLIC_CLASS,
    CHANGE_INLINE_FUNCTION,
    INTRODUCE_COMPILE_ERROR,
    FIX_COMPILE_ERROR,
    CHANGE_ANDROID_RESOURCE,
}

@Suppress("unused")
enum class Tasks(private val customTask: String? = null) {
    ASSEMBLE,
    KOTLIN_NPM_INSTALL(":kotlinNpmInstall"),
    CLEAN,
    BUILD,
    CORE_UTIL_CLASSES(":core:util.runtime:classes"),
    DIST,
    COMPILER_TEST_CLASSES(":compiler:testClasses"),
    IDEA_TEST_CLASSES(":idea:testClasses"),
    KOTLIN_GRADLE_PLUGIN_COMPILE_JAVA(":kotlin-gradle-plugin:compileJava"),
    KOTLIN_GRADLE_PLUGIN_TEST(":kotlin-gradle-plugin:test"),
    KOTLIN_GRADLE_PLUGIN_TEST_CLEAN(":kotlin-gradle-plugin:cleanTest"),
    PIPELINES_TEST(":plugins:pipelines:pipelines-config:pipelines-config-api:test"),
    PIPELINES_TEST_CLEAN(":plugins:pipelines:pipelines-config:pipelines-config-api:cleanTest"),
    SANTA_TRACKER_TEST(":santa-tracker:test"),
    SANTA_TRACKER_TEST_CLEAN(":santa-tracker:cleanTest"),
    IDEA_PLUGIN,
    INSTALL,
    CLASSES,
    TEST_CLASSES;

    val path: String
        get() = customTask ?: name.constantCaseToCamelCase()
}