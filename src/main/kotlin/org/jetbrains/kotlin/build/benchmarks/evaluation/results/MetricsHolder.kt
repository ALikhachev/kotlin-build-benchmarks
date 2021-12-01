/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.benchmarks.evaluation.results

import java.util.ArrayDeque

sealed class MetricsHolder<T>

abstract class MetricsContainer<T> : MetricsHolder<T>() {
    internal abstract val metricsValues: Map<String, MetricsHolder<T>>
    internal abstract val metricsChildren: Map<String, Iterable<String>>

    fun getMetric(metric: String): MetricsHolder<T>? = metricsValues[metric]
    fun findValue(metric: String): T? {
        var result: T? = null
        walkTimeMetrics(fn = { name, value ->
            if (name == metric) {
                result = value
            }
        })
        return result
    }

    abstract fun <R> map(fn: (T) -> R): MetricsContainer<R>
    abstract fun plus(other: MetricsContainer<T>, plus: (T, T) -> T): MetricsContainer<T>

    fun walkTimeMetrics(
        fn: (String, T) -> Unit,
        onEnter: (String) -> Unit = {},
        onExit: (String) -> Unit = {}
    ) {
        val metricsToVisit = ArrayDeque<String>()
        val entered = HashSet<String>()
        val exited = HashSet<String>()

        val allChildren = metricsChildren.values.flatMapTo(HashSet()) { it }
        metricsToVisit.addAll(metricsValues.keys.filter { it !in allChildren })

        while (metricsToVisit.isNotEmpty()) {
            val currentName = metricsToVisit.pollFirst()
            val currentMetric = metricsValues[currentName] ?: continue

            when (currentMetric) {
                is ValueMetric -> {
                    if (entered.add(currentName)) {
                        fn(currentName, currentMetric.value)

                        val childrenMetrics = metricsChildren[currentName]
                        if (childrenMetrics != null) {
                            onEnter(currentName)
                            metricsToVisit.addFirst(currentName)
                            childrenMetrics.reversed().forEach {
                                metricsToVisit.addFirst(it)
                            }
                        }
                    } else if (exited.add(currentName)) {
                        onExit(currentName)
                    }
                }
                is MetricsContainer -> {
                    currentMetric.walkTimeMetrics(fn, onEnter, onExit)
                }
            }
        }
    }
}

class MutableMetricsContainer<T> : MetricsContainer<T>() {
    private val myMetrics = LinkedHashMap<String, MetricsHolder<T>>()
    private val myChildren = LinkedHashMap<String, MutableSet<String>>()

    override val metricsValues: Map<String, MetricsHolder<T>>
        get() = myMetrics

    override val metricsChildren: Map<String, Iterable<String>>
        get() = myChildren

    fun set(name: String, value: MetricsHolder<T>, parentMetric: String? = null) {
        myMetrics.getOrPut(name) {
            if (parentMetric != null) {
                myChildren.getOrPut(parentMetric) { LinkedHashSet() }.add(name)
            }
            value
        }
    }

    override fun <R> map(fn: (T) -> R): MetricsContainer<R> {
        val newMetrics = MutableMetricsContainer<R>()
        for ((k, v) in myChildren) {
            newMetrics.myChildren[k] = LinkedHashSet(v)
        }
        for ((k, v) in myMetrics) {
            newMetrics.myMetrics[k] = when (v) {
                is ValueMetric -> {
                    ValueMetric(fn(v.value))
                }
                is MetricsContainer -> {
                    v.map(fn)
                }
            }
        }
        return newMetrics
    }

    override fun plus(other: MetricsContainer<T>, plus: (T, T) -> T): MetricsContainer<T> {
        val newMetrics = MutableMetricsContainer<T>()

        for (key in myChildren.keys + other.metricsChildren.keys) {
            val myValues = myChildren[key] ?: emptySet()
            val otherValues = other.metricsChildren[key] ?: emptySet()
            newMetrics.myChildren[key] = LinkedHashSet(myValues + otherValues)
        }
        for (key in myMetrics.keys + other.metricsValues.keys) {
            val myValue = myMetrics[key]
            val otherValue = other.metricsValues[key]
            val newValue: MetricsHolder<T> = when {
                myValue == null -> otherValue!!
                otherValue == null -> myValue
                else -> {
                    if (myValue is ValueMetric && otherValue is ValueMetric) {
                        ValueMetric(plus(myValue.value, otherValue.value))
                    } else if (myValue is MetricsContainer && otherValue is MetricsContainer) {
                        myValue.plus(otherValue, plus)
                    } else {
                        error("Container cannot be summed with value")
                    }
                }
            }
            newMetrics.myMetrics[key] = newValue
        }
        return newMetrics
    }
}

class ValueMetric<T>(val value: T) : MetricsHolder<T>()