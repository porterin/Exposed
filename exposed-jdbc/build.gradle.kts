import tanvd.kosogor.proxy.publishJar

plugins {
    kotlin("jvm") apply true
}

repositories {
    jcenter()
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
    runtime(Libs.spek_runner_junit5)

    testImplementation(Libs.testContainers)
    testImplementation(Libs.testContainersPostgresql)
    testImplementation(Libs.testContainersJuniper)

    testImplementation(Libs.hikariCP)
}

publishJar {
    publication {
        artifactId = "exposed-jdbc"
    }

    bintray {
        username = project.properties["bintrayUser"]?.toString() ?: System.getenv("BINTRAY_USER")
        secretKey = project.properties["bintrayApiKey"]?.toString() ?: System.getenv("BINTRAY_API_KEY")
        repository = "exposed"
        info {
            publish = false
            githubRepo = "https://github.com/JetBrains/Exposed.git"
            vcsUrl = "https://github.com/JetBrains/Exposed.git"
            userOrg = "kotlin"
            license = "Apache-2.0"
        }
    }
}
