group = "org.jetbrains"
version = "1.0-SNAPSHOT"

plugins {
    java
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "5.1.0"
}

buildscript {
    val kotlinVersion = System.getenv("KOTLIN_VERSION") ?: "1.7.10"
    extra["kotlinVersion"] = kotlinVersion
    val kotlinRepo = "https://buildserver.labs.intellij.net/guestAuth/app/rest/builds/buildType:(id:Kotlin_KotlinDev_CompilerDistAndMavenArtifacts),number:$kotlinVersion,branch:default:any/artifacts/content/maven"
    extra["kotlinRepo"] = kotlinRepo

    repositories {
        mavenCentral()
        mavenLocal()
        maven {
            url = uri(kotlinRepo)
        }
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

apply {
    plugin("kotlin")
}

val toolingApiVersion = "6.5"
val kotlinVersion: String by extra
val kotlinRepo: String by extra

repositories {
    mavenCentral()
    maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    mavenLocal()
    maven {
        url = uri(kotlinRepo)
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("gradle-build-metrics", kotlinVersion))
    implementation("org.gradle:gradle-tooling-api:$toolingApiVersion")
    // The tooling API need an SLF4J implementation available at runtime, replace this with any other implementation
    runtimeOnly("org.slf4j:slf4j-simple:1.7.10")
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
