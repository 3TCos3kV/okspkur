package com.acute.presentation.routes

import com.acute.application.BookingService
import com.acute.presentation.bookingFilter
import com.acute.presentation.dto.CreateBookingRequest
import com.acute.presentation.dto.PageDto
import com.acute.presentation.dto.toDto
import com.acute.presentation.dto.toRowDto
import com.acute.presentation.entityId
import com.acute.presentation.io
import com.acute.presentation.pageRequest
import com.acute.presentation.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.ZoneId

fun Route.bookingRoutes(bookings: BookingService, zone: ZoneId) {
    authenticate("jwt") {
        get("/api/bookings") {
            val filter = call.bookingFilter()
            val page = call.pageRequest()
            val result = call.io { bookings.list(filter, page) }
            call.respond(PageDto(result.items.map { it.toRowDto(zone) }, result.total))
        }
        get("/api/bookings/{id}") {
            val id = call.entityId("Запись не найдена")
            call.respond(call.io { bookings.card(id) }.toDto(zone))
        }
        post("/api/bookings") {
            val request = call.receive<CreateBookingRequest>()
            val userId = call.userId()
            val result = call.io { bookings.book(request.slotId, request.serviceId, userId) }
            call.respond(HttpStatusCode.Created, result.toDto(zone))
        }
        post("/api/bookings/{id}/cancel") {
            val id = call.entityId("Запись не найдена")
            call.respond(call.io { bookings.cancel(id) }.toDto(zone))
        }
    }
}
