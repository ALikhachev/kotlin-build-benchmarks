/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.benchmarks.evaluation

import org.jetbrains.kotlin.build.benchmarks.evaluation.results.MetricsContainer
import org.jetbrains.kotlin.build.benchmarks.utils.TimeInterval

class BuildResult(
    val timeMetrics: MetricsContainer<TimeInterval>,
    val performanceMetrics: MetricsContainer<Long>,
)