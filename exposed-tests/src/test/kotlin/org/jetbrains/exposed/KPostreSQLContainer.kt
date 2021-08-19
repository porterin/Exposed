package org.jetbrains.exposed

import org.testcontainers.containers.PostgreSQLContainer

class KPostgreSQLContainer(imageName: String) : PostgreSQLContainer<KPostgreSQLContainer>(imageName)
