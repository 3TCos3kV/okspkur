package com.acute.presentation.routes

import com.acute.application.ScheduleService
import com.acute.presentation.dto.PageDto
import com.acute.presentation.dto.toDto
import com.acute.presentation.entityId
import com.acute.presentation.io
import com.acute.presentation.pageRequest
import com.acute.presentation.period
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.ZoneId

fun Route.scheduleRoutes(schedule: ScheduleService, zone: ZoneId) {
    authenticate("jwt") {
        get("/api/services") {
            call.respond(call.io { schedule.services() }.map { it.toDto() })
        }
        get("/api/services/{id}/free-slots") {
            val id = call.entityId("Услуга не найдена")
            val period = call.period()
            val page = call.pageRequest()
            val result = call.io { schedule.freeSlots(id, period, page) }
            call.respond(PageDto(result.items.map { it.toDto(zone) }, result.total))
        }
    }
}
