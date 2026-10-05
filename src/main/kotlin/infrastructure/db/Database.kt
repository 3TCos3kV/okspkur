package com.acute.infrastructure.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database

data class DbConfig(val url: String, val user: String, val password: String, val schema: String, val poolSize: Int)

fun connectDatabase(config: DbConfig): Database {
    val pool = HikariConfig().apply {
        jdbcUrl = config.url
        username = config.user
        password = config.password
        schema = config.schema
        maximumPoolSize = config.poolSize
    }
    return Database.connect(HikariDataSource(pool))
}
