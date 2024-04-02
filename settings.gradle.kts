pluginManagement {
    val kotlinVersion = System.getenv("KOTLIN_VERSION") ?: "1.8.10"
    repositories {
        val kotlinTeamCityRepo = "https://buildserver.labs.intellij.net/guestAuth/app/rest/builds/buildType:(id:Kotlin_KotlinDev_CompilerDistAndMavenArtifacts),number:$kotlinVersion,branch:default:any/artifacts/content/maven.zip!/"
        mavenLocal()
        gradlePluginPortal()
        maven {
            url = uri(kotlinTeamCityRepo)
        }
    }
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
    }
}
rootProject.name = "kotlin-build-benchmarks"

