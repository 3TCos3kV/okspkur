package com.acute.presentation

import com.acute.presentation.dto.ErrorDto
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

fun Application.configureSecurity(components: Components) {
    val config = components.jwt
    install(Authentication) {
        jwt("jwt") {
            verifier(JWT.require(Algorithm.HMAC256(config.secret))
                .withIssuer(config.issuer).withAudience(config.audience).build())
            validate { credential ->
                if (credential.payload.subject?.toLongOrNull() != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, ErrorDto("Требуется авторизация")) }
        }
    }
}

fun ApplicationCall.userId(): Long = checkNotNull(principal<JWTPrincipal>()?.payload?.subject?.toLongOrNull())
