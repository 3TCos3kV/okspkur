package com.acute.presentation

import com.acute.application.BookingService
import com.acute.application.ScheduleService
import com.acute.application.SummaryService
import com.acute.infrastructure.auth.Authenticator
import com.acute.infrastructure.auth.JwtConfig
import com.acute.infrastructure.auth.findUserByLogin
import com.acute.infrastructure.db.DbConfig
import com.acute.infrastructure.db.ExposedBookingRepository
import com.acute.infrastructure.db.ExposedScheduleRepository
import com.acute.infrastructure.db.ExposedSummaryRepository
import com.acute.infrastructure.db.ExposedTransactionRunner
import com.acute.infrastructure.db.connectDatabase
import com.acute.presentation.routes.authRoutes
import com.acute.presentation.routes.bookingRoutes
import com.acute.presentation.routes.scheduleRoutes
import com.acute.presentation.routes.summaryRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import java.time.Clock
import java.time.Duration
import java.time.ZoneId

data class Components(
    val bookings: BookingService,
    val schedule: ScheduleService,
    val summary: SummaryService,
    val authenticator: Authenticator,
    val jwt: JwtConfig,
    val zone: ZoneId,
)

fun Application.module() {
    val config = environment.config
    val database = connectDatabase(DbConfig(
        config.property("db.url").getString(), config.property("db.user").getString(),
        config.property("db.password").getString(), config.property("db.schema").getString(),
        config.property("db.poolSize").getString().toInt(),
    ))
    val jwt = JwtConfig(
        config.property("jwt.secret").getString(), config.property("jwt.issuer").getString(),
        config.property("jwt.audience").getString(),
        Duration.ofHours(config.property("jwt.ttlHours").getString().toLong()),
    )
    val zone = ZoneId.of(config.property("app.zone").getString())
    val clock = Clock.systemUTC()
    val schedule = ExposedScheduleRepository(database, zone)
    configure(Components(
        BookingService(ExposedBookingRepository(database, zone), schedule, ExposedTransactionRunner(database), clock),
        ScheduleService(schedule), SummaryService(ExposedSummaryRepository(database, zone)),
        Authenticator(findUserByLogin(database), jwt, clock), jwt, zone,
    ))
}

fun Application.configure(components: Components) {
    install(RequestMetrics)
    install(ContentNegotiation) { json() }
    install(CallLogging)
    configureSecurity(components)
    configureStatusPages()
    routing {
        authRoutes(components.authenticator)
        bookingRoutes(components.bookings, components.zone)
        scheduleRoutes(components.schedule, components.zone)
        summaryRoutes(components.summary)
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        staticResources("/", "static")
    }
}
