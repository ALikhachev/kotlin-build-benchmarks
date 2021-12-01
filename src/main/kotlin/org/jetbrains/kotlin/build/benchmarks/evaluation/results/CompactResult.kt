/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.benchmarks.evaluation.results

import org.jetbrains.kotlin.build.benchmarks.dsl.Scenario
import org.jetbrains.kotlin.build.benchmarks.evaluation.AbstractBenchmarksProgressListener
import org.jetbrains.kotlin.build.benchmarks.utils.Either
import org.jetbrains.kotlin.build.benchmarks.utils.TimeInterval
import org.jetbrains.kotlin.build.benchmarks.utils.mapSuccess
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class CompactResults : Serializable {
    val scenarioResults = LinkedHashMap<String, CompactScenarioResult>()

    companion object {
        const val serialVersionUID: Long = 0
    }
}

class CompactScenarioResult(val scenarioName: String) : Serializable {
    val categoryResultsNs = LinkedHashMap<String, Long>()

    companion object {
        const val serialVersionUID: Long = 0
    }
}

class CompactResultListener(private val targetFile: File) : AbstractBenchmarksProgressListener() {
    private val myResults = CompactResults()

    override fun scenarioFinished(scenario: Scenario, result: Either<ScenarioResult>) {
        result.mapSuccess { scenarioResult ->
            val compactResult = CompactScenarioResult(scenario.name)
            var allT = TimeInterval(0)
            var gradleT = TimeInterval(0)
            var javaT = TimeInterval(0)
            var kotlinT = TimeInterval(0)
            var otherT = TimeInterval(0)
            scenarioResult.stepResults.forEach { stepResult ->
                if (stepResult.step.isMeasured) {
                    val metrics = stepResult.buildResult.timeMetrics
                    allT += metrics.findValue("GRADLE_BUILD") ?: TimeInterval(0)
                    gradleT += metrics.findValue("CONFIGURATION") ?: TimeInterval(0)
                    gradleT += metrics.findValue("UP_TO_DATE_CHECKS") ?: TimeInterval(0)
                    javaT += metrics.findValue("JavaCompile") ?: TimeInterval(0)
                    kotlinT += metrics.findValue("KotlinCompile") ?: TimeInterval(0)
                    kotlinT += metrics.findValue("Kotlin2JsCompile") ?: TimeInterval(0)
                    kotlinT += metrics.findValue("KotlinCompileCommon") ?: TimeInterval(0)
                    otherT += metrics.findValue("NON_COMPILATION_TASKS") ?: TimeInterval(0)
                }
            }
            compactResult.categoryResultsNs["Whole build"] = allT.asNs
            compactResult.categoryResultsNs["Gradle"] = gradleT.asNs
            compactResult.categoryResultsNs["Java"] = javaT.asNs
            compactResult.categoryResultsNs["Kotlin"] = kotlinT.asNs
            compactResult.categoryResultsNs["Other"] = otherT.asNs
            myResults.scenarioResults[compactResult.scenarioName] = compactResult
        }
    }

    override fun allFinished() {
        targetFile.parentFile.mkdirs()
        targetFile.outputStream().buffered().use {
            ObjectOutputStream(it).writeObject(myResults)
        }
        println("Compact result written to: $targetFile")
        printTable(myResults)
    }
}

private fun File.readCompactResults() = inputStream().buffered().use {
    ObjectInputStream(it).readObject() as CompactResults
}

fun printTable(res: CompactResults) {
    val categories = LinkedHashSet<String>()
    res.scenarioResults.values.flatMapTo(categories) { it.categoryResultsNs.keys }
    val table = TextTable("Scenario", *categories.toTypedArray())
    res.scenarioResults.forEach { scenario, result ->
        table.addRow(scenario, *categories.map { TimeInterval(result.categoryResultsNs[it]!!).asMs.toString() + " ms" }.toTypedArray())
    }
    println()
    println(buildString { table.printTo(this) })
}

fun main() {
    /*printCompareTable(file1, file2)*/
}

@Suppress("unused")
fun printCompareTable(file1: File, file2: File) {
    val res1 = file1.readCompactResults()
    val res2 = file2.readCompactResults()

    val categories = LinkedHashSet<String>()
    (res1.scenarioResults.values + res2.scenarioResults.values).flatMapTo(categories) { it.categoryResultsNs.keys }
    val table = TextTable("Scenario", *categories.toTypedArray())

    val scenarios = res1.scenarioResults.keys.intersect(res2.scenarioResults.keys)
    for (scenario in scenarios) {
        val scenarioResult1 = res1.scenarioResults.get(scenario)!!
        val scenarioResult2 = res2.scenarioResults.get(scenario)!!

        val times = categories
            .map { TimeInterval(scenarioResult1.categoryResultsNs[it]!!) to TimeInterval(scenarioResult2.categoryResultsNs[it]!!) }
            .map { (time1, time2) ->
                "${time1.asMs}->${time2.asMs} ms (${(100.0 * (time2.asMs - time1.asMs) / time1.asMs).shortStr}%)"
            }
        table.addRow(scenario, *times.toTypedArray())
    }

    println("Change in benchmarks from ${file1.name} to ${file2.name}")
    println(buildString { table.printTo(this) })
}

private class TextTable(vararg columnNames: String) {
    private val rows = ArrayList<List<String>>()
    private val columnsCount = columnNames.size
    private val maxLengths = IntArray(columnsCount) { columnNames[it].length }

    init {
        rows.add(columnNames.toList())
    }

    fun addRow(vararg row: String) {
        check(row.size == columnsCount) { "Row size ${row.size} differs from columns count $columnsCount" }
        rows.add(row.toList())

        for ((i, col) in row.withIndex()) {
            maxLengths[i] = Math.max(maxLengths[i], col.length)
        }
    }

    fun printTo(sb: StringBuilder) {
        for (row in rows) {
            sb.appendln()
            for ((i, col) in row.withIndex()) {
                if (i > 0) sb.append("|")

                sb.append(col.padEnd(maxLengths[i], ' '))
            }
        }
    }
}

private val Double.shortStr
    get() = String.format("%.1f", this)