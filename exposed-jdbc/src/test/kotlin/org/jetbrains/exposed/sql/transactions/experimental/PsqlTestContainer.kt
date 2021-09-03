package org.jetbrains.exposed.sql.transactions.experimental

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

object PsqlTestContainer {

  init {
    System.setProperty("com.zaxxer.hikari.housekeeping.periodMs", "1000")
  }

  private const val databaseDockerImageName = "postgres:9.5"
//  private const val databaseDockerImageName = "mdillon/postgis:9.5-alpine"

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
//      leakDetectionThreshold = 5
    }

  val dataSource = HikariDataSource(hikariConfig).apply {}

  val db: Database =
    Database.connect(dataSource)

}
