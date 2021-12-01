/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.benchmarks.dsl

import java.util.*

fun String.constantCaseToCamelCase() =
    lowercase()
        .split("_")
        .mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
        .joinToString("")