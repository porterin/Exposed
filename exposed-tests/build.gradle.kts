import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.exposed.gradle.setupDialectTest
import org.jetbrains.exposed.gradle.Versions
import org.jetbrains.exposed.gradle.setupTestDriverDependencies


plugins {
    kotlin("jvm") apply true
}

repositories {
    mavenCentral()
}

val dialect: String by project

dependencies {
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Versions.kotlinCoroutines)
    implementation(project(":exposed-core"))
    implementation(project(":exposed-jdbc"))
    implementation(project(":exposed-dao"))
    implementation(kotlin("test-junit"))
    implementation("org.slf4j", "slf4j-api", Versions.slf4j)
    implementation("org.apache.logging.log4j", "log4j-slf4j-impl", Versions.log4j2)
    implementation("org.apache.logging.log4j", "log4j-api", Versions.log4j2)
    implementation("org.apache.logging.log4j", "log4j-core", Versions.log4j2)
    implementation("junit", "junit", "4.12")
    implementation("org.hamcrest", "hamcrest-library", "1.3")
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-debug", Versions.kotlinCoroutines)

    testRuntimeOnly("org.testcontainers", "testcontainers", Versions.testContainers)
    implementation("org.testcontainers", "mysql", Versions.testContainers)
    implementation("com.opentable.components", "otj-pg-embedded", Versions.otjPgEmbedded)

    implementation("com.h2database", "h2", Versions.h2)

    setupTestDriverDependencies(dialect) { group, artifactId, version ->
        testImplementation(group, artifactId, version)
    }

    implementation(Libs.junit_jupiter_api)
    implementation(Libs.junit_jupiter_engine)

    implementation(Libs.mockito_core)
    implementation(Libs.mockito_inline)
    implementation(Libs.mockito_kotlin)
    implementation(Libs.mockk)

    implementation(Libs.kluent)

    implementation(Libs.testContainers)
    implementation(Libs.testContainersPostgresql)
    implementation(Libs.testContainersJuniper)

    implementation(Libs.hikariCP)
    implementation(Libs.Micrometer.core)
}

tasks.withType(Test::class.java) {
    jvmArgs = listOf("-XX:MaxPermSize=256m")
    testLogging {
        events.addAll(listOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED))
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

setupDialectTest(dialect)
