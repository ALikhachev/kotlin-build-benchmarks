package org.jetbrains.kotlin.build.benchmarks.evaluation.results

import org.jetbrains.kotlin.build.benchmarks.dsl.Scenario
import org.jetbrains.kotlin.build.benchmarks.dsl.Step
import org.jetbrains.kotlin.build.benchmarks.evaluation.AbstractBenchmarksProgressListener
import org.jetbrains.kotlin.build.benchmarks.utils.Either
import org.jetbrains.kotlin.build.benchmarks.utils.TimeInterval
import org.jetbrains.kotlin.build.benchmarks.utils.mapSuccess

class TeamCityMetricReporter : AbstractBenchmarksProgressListener() {
    private var currentScenario: Scenario? = null
    private var currentScenarioRun: Int = 0

    override fun scenarioStarted(scenario: Scenario) {
        startTest(scenario.name)
        if (currentScenario == scenario) {
            currentScenarioRun++
        } else {
            currentScenarioRun = 0
            currentScenario = scenario
        }
    }

    override fun stepFinished(step: Step, result: Either<StepResult>) {
        when (result) {
            is Either.Success -> {
                reportMessage("Step finished")
            }
            is Either.Failure -> {
                reportMessage("Step finished with error: ${result.reason}", MessageStatus.FAILURE)
            }
        }
    }

    override fun scenarioFinished(scenario: Scenario, result: Either<ScenarioResult>) {
        when (result) {
            is Either.Success -> {
                result.mapSuccess {
                    setParameter("env.br.${specialCharactersToUnderscore(scenario.name)}.display_name", scenario.name)
                    for ((stepIndex, stepResult) in it.stepResults.withIndex()) {
                        if (!stepResult.step.isMeasured) continue
                        var prefix = ""

                        fun reportMetrics(metric: String, value: Any) {
                            if (value is Number && value.toLong() == 0L) return
                            fun formatValue(value: Any) = when (value) {
                                is TimeInterval -> value.asMs.toString()
                                else -> value.toString()
                            }
                            val fullMetricName = "$prefix$metric"
                            val statisticKey =
                                specialCharactersToUnderscore("${scenario.name}.iter-${currentScenarioRun + 1}.step-${stepIndex + 1}.$fullMetricName")
                            if (scenario.trackedMetrics?.contains(fullMetricName) != false) {
                                setParameter("env.br.$statisticKey", formatValue(value))
                            }
                            reportStatistics(statisticKey, formatValue(value))
                        }

                        stepResult.buildResult.timeMetrics.walkTimeMetrics(
                            fn = ::reportMetrics,
                            onEnter = {
                                prefix += "$it."
                            },
                            onExit = {
                                prefix = prefix.substring(0, prefix.length - (it.length + 1))
                            }
                        )
                        stepResult.buildResult.performanceMetrics.walkTimeMetrics(
                            fn = ::reportMetrics,
                            onEnter = {
                                prefix += "$it."
                            },
                            onExit = {
                                prefix = prefix.substring(0, prefix.length - (it.length + 1))
                            }
                        )
                    }
                }
            }
            is Either.Failure -> {
                failTest(scenario.name, result.reason)
            }
        }
        finishTest(scenario.name)
    }

    override fun cleanupStarted() {
        reportMessage("Cleanup after last scenario is started")
    }

    override fun cleanupFinished() {
        reportMessage("Cleanup after last scenario is finished")
    }
}

val nonAlphabeticalCharactersAndDot = Regex("[^\\w.]")
fun specialCharactersToUnderscore(key: String): String {
    return key.replace(nonAlphabeticalCharactersAndDot, "_")
}

fun escapeTcCharacters(message: String) = message
    .replace("|", "||")
    .replace("\n", "|n")
    .replace("\r", "|r")
    .replace("'", "|'")
    .replace("[", "|[")
    .replace("]", "|]")

fun setParameter(key: String, value: String) {
    println("##teamcity[setParameter name='${escapeTcCharacters(key)}' value='${escapeTcCharacters(value)}']")
}

fun reportStatistics(key: String, value: String) {
    println("##teamcity[buildStatisticValue key='${escapeTcCharacters(key)}' value='${escapeTcCharacters(value)}']")
}

enum class MessageStatus {
    NORMAL, WARNING, FAILURE, ERROR
}

fun reportMessage(msg: String, status: MessageStatus = MessageStatus.NORMAL) {
    println("##teamcity[message text='${escapeTcCharacters(msg)}' status='$status']")
}

fun startTest(name: String) {
    println("##teamcity[testStarted name='${escapeTcCharacters(name)}']")
}

fun failTest(name: String, msg: String) {
    println("##teamcity[testFailed name='${escapeTcCharacters(name)}' message='${escapeTcCharacters(msg)}']")
}

fun finishTest(name: String) {
    println("##teamcity[testFinished name='${escapeTcCharacters(name)}']")
}