/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("NOTHING_TO_INLINE", "EXPERIMENTAL_FEATURE_WARNING")

package org.jetbrains.kotlin.build.benchmarks.utils

@JvmInline
value class TimeInterval constructor(val asNs: Long) {
    companion object {
        inline fun ms(ms: Long) = TimeInterval(ms * 1_000_000)

        inline fun ns(ns: Long) = TimeInterval(ns)
    }

    inline val asMs: Long
        get() = asNs / 1_000_000

    inline operator fun plus(other: TimeInterval) = TimeInterval(asNs + other.asNs)
}

fun min(i1: TimeInterval, i2: TimeInterval): TimeInterval = if (i1.asNs <= i2.asNs) i1 else i2
fun max(i1: TimeInterval, i2: TimeInterval): TimeInterval = if (i1.asNs >= i2.asNs) i1 else i2

