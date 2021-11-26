/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.benchmarks.evaluation

import org.jetbrains.kotlin.build.benchmarks.dsl.FileChange
import org.jetbrains.kotlin.build.benchmarks.dsl.Step
import org.jetbrains.kotlin.build.benchmarks.utils.stackTraceString
import java.io.File
import java.util.ArrayDeque

class ChangesApplier(private val projectPath: File) {
    private val appliedChanges = ArrayDeque<ApplicableChanges>()

    fun applyStepChanges(step: Step): Boolean {
        return when (step) {
            is Step.SimpleStep -> {
                val applicable = ApplicableChanges(projectPath, changes = step.fileChanges)
                if (applicable.apply()) {
                    appliedChanges.addFirst(applicable)
                    true
                } else {
                    applicable.revert()
                    false
                }
            }
            is Step.RevertLastStep -> {
                appliedChanges.pollFirst().revert()
            }
            is Step.StopDaemon -> {
                // do nothing here
                true
            }
        }
    }

    fun revertAppliedChanges() {
        while (appliedChanges.isNotEmpty()) {
            try {
                appliedChanges.pollFirst().revert()
            } catch (e: Exception) {
                System.err.println("Failed to revert changes: ${e.stackTraceString()}")
            }
        }
    }

    val hasAppliedChanges: Boolean
        get() = appliedChanges.isNotEmpty()
}

private class ApplicableChanges(private val projectPath: File, private val changes: Array<FileChange>) {
    private val prevVersions = arrayListOf<Pair<File, ByteArray?>>()

    fun apply(): Boolean {
        for (change in changes) {
            val file = File(projectPath, change.changeableFile.targetFile)
            val prevContent = if (file.exists()) file.readBytes() else null

            try {
                val newContent = change.changedFile.readBytes()
                file.writeBytes(newContent)
            } catch (e: Exception) {
                System.err.println("Could not apply change ${change}: ${e.stackTraceString()}")
                return false
            }

            prevVersions.add(file to prevContent)
        }
        return true
    }

    fun revert(): Boolean {
        val failedToRevert = prevVersions.mapNotNull { (file, prevContent) ->
            try {
                if (prevContent == null) file.delete() else file.writeBytes(prevContent)
                null
            } catch (e: Exception) {
                System.err.println("Could not revert change to ${file}: ${e.stackTraceString()}")
                file to prevContent
            }
        }
        prevVersions.clear()
        prevVersions.addAll(failedToRevert)
        return failedToRevert.isEmpty()
    }
}