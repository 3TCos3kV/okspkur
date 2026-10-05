package com.acute.presentation.routes

import com.acute.application.SummaryService
import com.acute.presentation.dto.toDto
import com.acute.presentation.io
import com.acute.presentation.period
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.summaryRoutes(summary: SummaryService) {
    authenticate("jwt") {
        get("/api/summary") {
            val period = call.period()
            call.respond(call.io { summary.summary(period) }.toDto())
        }
    }
}
