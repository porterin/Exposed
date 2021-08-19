package org.jetbrains.exposed

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.logging.LoggingMeterRegistry
import io.micrometer.core.instrument.logging.LoggingRegistryConfig
import org.jetbrains.exposed.sql.Database
import java.time.Duration

object PsqlTestContainer {

  init {
    System.setProperty("com.zaxxer.hikari.housekeeping.periodMs", "1000")
  }

  //  private const val databaseDockerImageName = "postgres:9.5"
  private const val databaseDockerImageName = "mdillon/postgis:9.5-alpine"

  private const val databaseName = "test_db"
  private const val dbUsername = "test_user"
  private const val dbPassword = "test_password"

  private val container: KPostgreSQLContainer =
    KPostgreSQLContainer(databaseDockerImageName)
      .withDatabaseName(databaseName)
      .withUsername(dbUsername)
      .withPassword(dbPassword)
      .also {
        it.start()
      }

  private val hikariConfig =
    HikariConfig().apply {
      jdbcUrl = "jdbc:postgresql://${container.containerIpAddress}:${container.firstMappedPort}/$databaseName?prepareThreshold=0"
      username = dbUsername
      password = dbPassword
      isAutoCommit = false

      minimumIdle = 0
      maximumPoolSize = 1
      connectionTimeout = 20_000
//      leakDetectionThreshold = 5
    }

  private val meterRegistry = LoggingMeterRegistry(object : LoggingRegistryConfig {
    override fun get(key: String): String? {
      return null
    }

    override fun step(): Duration {
      return Duration.ofSeconds(5)
    }
  }, Clock.SYSTEM)

  val dataSource = HikariDataSource(hikariConfig).apply {
//    metricRegistry = meterRegistry
  }

  val db: Database =
    Database.connect(dataSource)

}
