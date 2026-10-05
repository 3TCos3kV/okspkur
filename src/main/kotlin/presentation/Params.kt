package com.acute.presentation

import com.acute.application.BookingFilter
import com.acute.application.PageRequest
import com.acute.application.Period
import com.acute.domain.BookingStatus
import com.acute.domain.Invalid
import com.acute.domain.NotFound
import io.ktor.server.application.ApplicationCall
import java.time.LocalDate
import java.time.format.DateTimeParseException

private fun ApplicationCall.integer(name: String, default: Int): Int {
    val value = request.queryParameters[name] ?: return default
    return value.toIntOrNull() ?: throw Invalid("Параметр $name должен быть целым числом")
}

fun ApplicationCall.pageRequest() = PageRequest(integer("page", 1), integer("size", 20))

private fun ApplicationCall.date(name: String, required: Boolean): LocalDate? {
    val value = request.queryParameters[name]
    if (value == null) {
        if (required) throw Invalid("Не задан параметр $name")
        return null
    }
    try {
        if (!value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) {
            throw Invalid("Параметр $name должен быть датой в формате YYYY-MM-DD")
        }
        return LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw Invalid("Параметр $name должен быть датой в формате YYYY-MM-DD")
    }
}

fun ApplicationCall.period() = Period(checkNotNull(date("from", true)), checkNotNull(date("to", true)))

fun ApplicationCall.bookingFilter(): BookingFilter {
    val status = when (val value = request.queryParameters["status"]) {
        null -> null
        "active" -> BookingStatus.ACTIVE
        "cancelled" -> BookingStatus.CANCELLED
        else -> throw Invalid("Неизвестный статус: $value")
    }
    val value = request.queryParameters["specialistId"]
    val specialistId = if (value == null) null else value.toLongOrNull()?.takeIf { it > 0 }
        ?: throw Invalid("Параметр specialistId должен быть положительным целым числом")
    return BookingFilter(status, specialistId, date("from", false), date("to", false))
}

fun ApplicationCall.entityId(message: String): Long =
    parameters["id"]?.toLongOrNull()?.takeIf { it > 0 } ?: throw NotFound(message)
