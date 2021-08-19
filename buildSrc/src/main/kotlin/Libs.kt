object Libs {
  private fun buildDependencyTemplate(group: String, version: String) = { name: String ->
    "$group:$name:$version"
  }

  private const val kotlin_group = "org.jetbrains.kotlin"
  const val kotlin_stdlib_jdk8 = "$kotlin_group:kotlin-stdlib-jdk8"

  private const val kotlinx_group = "org.jetbrains.kotlinx"
  private const val kotlinx_version = "1.3.3"
  const val kotlinx_coroutines_core = "$kotlinx_group:kotlinx-coroutines-core:$kotlinx_version"
  const val kotlinx_coroutines_jdk8 = "$kotlinx_group:kotlinx-coroutines-jdk8:$kotlinx_version"
  const val kotlinx_coroutines_test = "$kotlinx_group:kotlinx-coroutines-test:$kotlinx_version"

  private const val junit_version = "5.4.0"
  private const val junit_group = "org.junit.jupiter"
  const val junit_jupiter_api = "$junit_group:junit-jupiter-api:$junit_version"
  const val junit_jupiter_engine = "$junit_group:junit-jupiter-engine:$junit_version"

  private const val mockito_version = "2.24.5"
  private const val mockito_group = "org.mockito"
  const val mockito_core = "$mockito_group:mockito-core:$mockito_version"
  const val mockito_inline = "$mockito_group:mockito-inline:$mockito_version"
  const val mockito_kotlin: String = "com.nhaarman.mockitokotlin2:mockito-kotlin:2.1.0"

  const val mockk = "io.mockk:mockk:1.9.3"
  const val kluent = "org.amshove.kluent:kluent:1.60"

  private const val spek2_group = "org.spekframework.spek2"
  private const val spek2_version = "2.0.5"
  const val spek_dsl_jvm = "$spek2_group:spek-dsl-jvm:$spek2_version"
  const val spek_runner_junit5 = "$spek2_group:spek-runner-junit5:$spek2_version"

  private const val testContainersGroup = "org.testcontainers"
  private const val testContainersVersion = "1.12.3"
  const val testContainers = "$testContainersGroup:testcontainers:$testContainersVersion"
  const val testContainersPostgresql = "$testContainersGroup:postgresql:$testContainersVersion"
  const val testContainersJuniper = "$testContainersGroup:junit-jupiter:$testContainersVersion"

  const val hikariCP = "com.zaxxer:HikariCP:3.4.2"

  object Micrometer {
    private val buildDependency = buildDependencyTemplate("io.micrometer", "1.5.5")

    val core = buildDependency("micrometer-core")
  }
}
