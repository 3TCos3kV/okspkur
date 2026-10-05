package com.acute.infrastructure.db

import com.acute.application.TransactionRunner
import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private val metricsRegistered = Key<Boolean>()

fun <T> dbTransaction(database: Database, block: JdbcTransaction.() -> T): T = transaction(database) {
    if (getUserData(metricsRegistered) != true) {
        registerInterceptor(QueryInterceptor())
        putUserData(metricsRegistered, true)
    }
    block()
}

class ExposedTransactionRunner(private val database: Database) : TransactionRunner {
    override fun <T> inTransaction(block: () -> T): T = dbTransaction(database) { block() }
}
