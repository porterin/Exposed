plugins {
    kotlin("jvm") apply true
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-core"))
    api(Libs.kotlin_stdlib_jdk8)

    api(Libs.kotlinx_coroutines_jdk8)
    testImplementation(Libs.kotlinx_coroutines_test)

    testImplementation(Libs.junit_jupiter_api)
    testImplementation(Libs.junit_jupiter_engine)

    testImplementation(Libs.mockito_core)
    testImplementation(Libs.mockito_inline)
    testImplementation(Libs.mockito_kotlin)
    testImplementation(Libs.mockk)

    testImplementation(Libs.kluent)

    testImplementation(Libs.spek_dsl_jvm)
    runtimeOnly(Libs.spek_runner_junit5)

    testImplementation(Libs.testContainers)
    testImplementation(Libs.testContainersPostgresql)
    testImplementation(Libs.testContainersJuniper)

    testImplementation(Libs.hikariCP)
}
