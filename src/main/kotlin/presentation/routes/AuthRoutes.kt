package com.acute.presentation.routes

import com.acute.infrastructure.auth.Authenticator
import com.acute.presentation.dto.ErrorDto
import com.acute.presentation.dto.LoginRequest
import com.acute.presentation.dto.TokenDto
import com.acute.presentation.io
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.authRoutes(authenticator: Authenticator) {
    post("/api/auth/login") {
        val request = call.receive<LoginRequest>()
        val token = call.io { authenticator.login(request.login, request.password) }
        if (token == null) call.respond(HttpStatusCode.Unauthorized, ErrorDto("Неверные логин или пароль"))
        else call.respond(TokenDto(token))
    }
}
