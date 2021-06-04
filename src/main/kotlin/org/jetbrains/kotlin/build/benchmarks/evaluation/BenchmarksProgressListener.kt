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

interface BenchmarksProgressListener {
    fun scenarioStarted(scenario: Scenario)
    fun scenarioFinished(scenario: Scenario, result: Either<ScenarioResult>)
    fun stepStarted(step: Step)
    fun stepFinished(step: Step, result: Either<StepResult>)
    fun taskExecutionStarted(tasks: Array<String>)
    fun cleanupStarted()
    fun cleanupFinished()
    fun allFinished()
}