package com.acute.infrastructure.db

import kotlinx.coroutines.asContextElement
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.StatementInterceptor
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi

class QueryStats {
    var queries: Int = 0
        private set
    var nanos: Long = 0
        private set

    fun record(elapsed: Long) {
        queries++
        nanos += elapsed
    }
}

object SqlMetrics {
    private val current = ThreadLocal<QueryStats?>()

    fun context(stats: QueryStats) = current.asContextElement(stats)

    fun record(nanos: Long) {
        current.get()?.record(nanos)
    }
}

class QueryInterceptor : StatementInterceptor {
    private var startedAt: Long = 0

    override fun beforeExecution(transaction: Transaction, context: StatementContext) {
        startedAt = System.nanoTime()
    }

    override fun afterExecution(
        transaction: Transaction,
        contexts: List<StatementContext>,
        executedStatement: PreparedStatementApi,
    ) {
        SqlMetrics.record(System.nanoTime() - startedAt)
    }
}
