package com.acute.presentation

import com.acute.infrastructure.db.QueryStats
import com.acute.infrastructure.db.SqlMetrics
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val startedAtKey = AttributeKey<Long>("requestStartedAt")
private val queryStatsKey = AttributeKey<QueryStats>("queryStats")

val RequestMetrics = createApplicationPlugin("RequestMetrics") {
    onCall { call ->
        call.attributes.put(startedAtKey, System.nanoTime())
        call.attributes.put(queryStatsKey, QueryStats())
    }
    onCallRespond { call, _ ->
        val stats = call.attributes[queryStatsKey]
        val elapsed = System.nanoTime() - call.attributes[startedAtKey]
        call.response.headers.append("X-Response-Time-Ms", "%.2f".format(Locale.ROOT, elapsed / 1_000_000.0))
        call.response.headers.append("X-Db-Queries", stats.queries.toString())
        call.response.headers.append("X-Db-Time-Ms", "%.2f".format(Locale.ROOT, stats.nanos / 1_000_000.0))
    }
}

// JDBC блокирует поток, поэтому запросы к базе выполняются вне потоков обработки HTTP.
suspend fun <T> ApplicationCall.io(block: () -> T): T =
    withContext(Dispatchers.IO + SqlMetrics.context(attributes[queryStatsKey])) { block() }
