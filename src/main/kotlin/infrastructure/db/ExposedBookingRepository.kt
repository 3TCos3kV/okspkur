package com.acute.infrastructure.db

import com.acute.application.BookingDetails
import com.acute.application.BookingFilter
import com.acute.application.BookingRepository
import com.acute.application.Page
import com.acute.application.PageRequest
import com.acute.domain.Booking
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneId

class ExposedBookingRepository(private val database: Database, private val zone: ZoneId) : BookingRepository {
    private val joined = Bookings.innerJoin(Slots).innerJoin(Specialists).innerJoin(Services).innerJoin(Users)

    override fun list(filter: BookingFilter, page: PageRequest): Page<BookingDetails> = dbTransaction(database) {
        val condition = conditions(filter)
        val items = joined.selectAll().where { condition }
            .orderBy(Slots.startsAt to SortOrder.DESC, Bookings.id to SortOrder.DESC)
            .limit(page.size).offset(page.offset)
            .map { BookingDetails(it.booking(), it.slot(), it.specialist(), it.service(), it.client()) }
        val total = joined.selectAll().where { condition }.count()
        Page(items, total)
    }

    private fun conditions(filter: BookingFilter): Op<Boolean> {
        var result: Op<Boolean> = Op.TRUE
        if (filter.status != null) result = result and (Bookings.status eq filter.status.name.lowercase())
        if (filter.specialistId != null) result = result and (Slots.specialistId eq filter.specialistId)
        if (filter.from != null) {
            result = result and (Slots.startsAt greaterEq filter.from.atStartOfDay(zone).toOffsetDateTime())
        }
        if (filter.to != null) {
            result = result and (Slots.startsAt less filter.to.plusDays(1).atStartOfDay(zone).toOffsetDateTime())
        }
        return result
    }

    override fun details(id: Long): BookingDetails? = dbTransaction(database) {
        val row = joined.selectAll().where { Bookings.id eq id }.singleOrNull()
        if (row == null) null else BookingDetails(
            row.booking(), row.slot(), row.specialist(), row.service(), row.client(),
        )
    }

    override fun forUpdate(id: Long): Booking? = dbTransaction(database) {
        Bookings.selectAll().where { Bookings.id eq id }.forUpdate().singleOrNull()?.booking()
    }

    override fun hasActiveBooking(slotId: Long): Boolean = dbTransaction(database) {
        !Bookings.selectAll().where { (Bookings.slotId eq slotId) and (Bookings.status eq "active") }.limit(1).empty()
    }

    override fun create(slotId: Long, serviceId: Long, clientId: Long, createdAt: OffsetDateTime): Long =
        dbTransaction(database) {
            Bookings.insert {
                it[Bookings.slotId] = slotId
                it[Bookings.serviceId] = serviceId
                it[Bookings.clientId] = clientId
                it[status] = "active"
                it[Bookings.createdAt] = createdAt
                it[cancelledAt] = null
            }[Bookings.id]
        }

    override fun cancel(id: Long, cancelledAt: OffsetDateTime) {
        dbTransaction(database) {
            Bookings.update({ Bookings.id eq id }) {
                it[status] = "cancelled"
                it[Bookings.cancelledAt] = cancelledAt
            }
        }
    }
}
