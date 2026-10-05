package com.acute.domain

import java.time.Duration
import java.time.OffsetDateTime

data class Specialist(val id: Long, val fullName: String, val specialty: String)
data class Service(val id: Long, val name: String, val specialty: String, val durationMin: Int)
data class Slot(val id: Long, val specialistId: Long, val startsAt: OffsetDateTime, val endsAt: OffsetDateTime) {
    val durationMin: Long get() = Duration.between(startsAt, endsAt).toMinutes()
}
enum class BookingStatus { ACTIVE, CANCELLED }
data class Booking(
    val id: Long,
    val slotId: Long,
    val serviceId: Long,
    val clientId: Long,
    val status: BookingStatus,
    val createdAt: OffsetDateTime,
    val cancelledAt: OffsetDateTime?,
)
data class Client(val id: Long, val fullName: String, val login: String)
