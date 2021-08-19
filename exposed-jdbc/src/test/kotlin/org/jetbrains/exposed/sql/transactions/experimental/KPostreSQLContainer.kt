package org.jetbrains.exposed.sql.transactions.experimental

import org.testcontainers.containers.PostgreSQLContainer

class KPostgreSQLContainer(imageName: String) : PostgreSQLContainer<KPostgreSQLContainer>(imageName)
