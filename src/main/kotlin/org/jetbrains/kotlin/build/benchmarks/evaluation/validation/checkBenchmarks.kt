/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.benchmarks.evaluation.validation

import org.jetbrains.kotlin.build.benchmarks.dsl.FileChange
import org.jetbrains.kotlin.build.benchmarks.dsl.Step
import org.jetbrains.kotlin.build.benchmarks.dsl.Suite
import java.io.File
import java.lang.IllegalStateException

fun checkBenchmarks(projectPath: File, benchmarks: Suite) {
    val errors = arrayListOf<String>()

    val changeableFiles = benchmarks.changeableFiles
    changeableFiles.flatMapTo(LinkedHashSet<File>()) {
        listOf(File(projectPath, it.targetFile), it.expectedInitialFile)
    }.forEach {
        if (!it.isFile) {
            errors.add("File does not exist: $it")
        }
    }

    for (changeableFile in changeableFiles) {
        val targetFile = File(projectPath, changeableFile.targetFile)
        if (!changeableFile.expectedInitialFile.isFile || !targetFile.isFile) {
            continue
        }

        val expectedInitialContent = changeableFile.expectedInitialFile.readText().trim().normalizeLineSeparators()
        val actualInitialContent = targetFile.readText().trim().normalizeLineSeparators()

        if (expectedInitialContent != actualInitialContent) {
            targetFile.copyTo(changeableFile.expectedInitialFile, overwrite = true)
            errors.add(
                "Content of ${changeableFile.targetFile} does not match ${changeableFile.expectedInitialFile}." +
                        "\n${changeableFile.expectedInitialFile} was updated. Please review the changes and commit."
            )
        }
    }

    val seenChanges = HashSet<FileChange>()
    for (scenario in benchmarks.scenarios) {
        step@ for (step in scenario.steps) {
            when (step) {
                is Step.SimpleStep -> {
                    for (fileChange in step.fileChanges) {
                        if (!seenChanges.add(fileChange)) continue

                        if (!fileChange.changedFile.exists()) {
                            errors.add("Change file for $fileChange in ${scenario.name} does not exist: ${fileChange.changedFile}")
                        }
                    }
                }
                is Step.RevertLastStep -> continue@step
                is Step.StopDaemon -> continue@step
            }
        }
    }

    if (errors.any()) {
        throw IllegalStateException("Benchmark scenarios are not valid:\n" + errors.joinToString("\n") { "  * $it" })
    }
}

private fun String.normalizeLineSeparators() =
    replace("\r\n", "\n")

