package com.acute.presentation

import com.acute.domain.Conflict
import com.acute.domain.DomainError
import com.acute.domain.Invalid
import com.acute.domain.NotFound
import com.acute.presentation.dto.ErrorDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<DomainError> { call, cause ->
            val status = when (cause) {
                is NotFound -> HttpStatusCode.NotFound
                is Conflict -> HttpStatusCode.Conflict
                is Invalid -> HttpStatusCode.UnprocessableEntity
            }
            call.respond(status, ErrorDto(checkNotNull(cause.message)))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorDto("Некорректное тело запроса"))
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            call.application.log.error("Ошибка обработки запроса", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("Внутренняя ошибка сервера"))
        }
    }
}
