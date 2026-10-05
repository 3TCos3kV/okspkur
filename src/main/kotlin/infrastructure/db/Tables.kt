package com.acute.infrastructure.db

import com.acute.domain.Booking
import com.acute.domain.BookingStatus
import com.acute.domain.Client
import com.acute.domain.Service
import com.acute.domain.Slot
import com.acute.domain.Specialist
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object Users : Table("users") {
    val id = long("id")
    val login = text("login")
    val passwordHash = text("password_hash")
    val fullName = text("full_name")
    override val primaryKey = PrimaryKey(id)
}
object Specialists : Table("specialists") {
    val id = long("id")
    val fullName = text("full_name")
    val specialty = text("specialty")
    override val primaryKey = PrimaryKey(id)
}
object Services : Table("services") {
    val id = long("id")
    val name = text("name")
    val specialty = text("specialty")
    val durationMin = integer("duration_min")
    override val primaryKey = PrimaryKey(id)
}
object Slots : Table("slots") {
    val id = long("id")
    val specialistId = long("specialist_id").references(Specialists.id)
    val startsAt = timestampWithTimeZone("starts_at")
    val endsAt = timestampWithTimeZone("ends_at")
    override val primaryKey = PrimaryKey(id)
}
object Bookings : Table("bookings") {
    val id = long("id").autoIncrement("bookings_id_seq")
    val slotId = long("slot_id").references(Slots.id)
    val serviceId = long("service_id").references(Services.id)
    val clientId = long("client_id").references(Users.id)
    val status = text("status")
    val createdAt = timestampWithTimeZone("created_at")
    val cancelledAt = timestampWithTimeZone("cancelled_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.specialist() = Specialist(this[Specialists.id], this[Specialists.fullName], this[Specialists.specialty])
fun ResultRow.service() = Service(
    this[Services.id], this[Services.name], this[Services.specialty], this[Services.durationMin],
)
fun ResultRow.slot() = Slot(this[Slots.id], this[Slots.specialistId], this[Slots.startsAt], this[Slots.endsAt])
fun ResultRow.booking() = Booking(
    this[Bookings.id], this[Bookings.slotId], this[Bookings.serviceId], this[Bookings.clientId],
    BookingStatus.valueOf(this[Bookings.status].uppercase()), this[Bookings.createdAt], this[Bookings.cancelledAt],
)
fun ResultRow.client() = Client(this[Users.id], this[Users.fullName], this[Users.login])
