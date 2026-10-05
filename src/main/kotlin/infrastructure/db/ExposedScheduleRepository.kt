package com.acute.infrastructure.db

import com.acute.application.FreeSlot
import com.acute.application.Page
import com.acute.application.PageRequest
import com.acute.application.Period
import com.acute.application.ScheduleRepository
import com.acute.domain.Service
import com.acute.domain.Slot
import com.acute.domain.Specialist
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.OffsetDateTime
import java.time.ZoneId

class ExposedScheduleRepository(private val database: Database, private val zone: ZoneId) : ScheduleRepository {
    override fun services(): List<Service> = dbTransaction(database) {
        Services.selectAll().orderBy(Services.id to SortOrder.ASC).map { it.service() }
    }

    override fun service(id: Long): Service? = dbTransaction(database) {
        Services.selectAll().where { Services.id eq id }.singleOrNull()?.service()
    }

    override fun specialist(id: Long): Specialist? = dbTransaction(database) {
        Specialists.selectAll().where { Specialists.id eq id }.singleOrNull()?.specialist()
    }

    override fun slotForUpdate(id: Long): Slot? = dbTransaction(database) {
        Slots.selectAll().where { Slots.id eq id }.forUpdate().singleOrNull()?.slot()
    }

    override fun freeSlots(
        service: Service,
        period: Period,
        page: PageRequest,
    ): Page<FreeSlot> = dbTransaction(database) {
        val conditions = """
            FROM slots s
            JOIN specialists sp ON sp.id = s.specialist_id
            WHERE sp.specialty = ?
              AND s.ends_at - s.starts_at >= make_interval(mins => ?)
              AND s.starts_at >= ? AND s.starts_at < ?
              AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.slot_id = s.id AND b.status = 'active')
        """.trimIndent()
        val args = listOf(
            TextColumnType() to service.specialty,
            IntegerColumnType() to service.durationMin,
            Slots.startsAt.columnType to period.from.atStartOfDay(zone).toOffsetDateTime(),
            Slots.startsAt.columnType to period.to.plusDays(1).atStartOfDay(zone).toOffsetDateTime(),
        )
        val items = checkNotNull(exec<List<FreeSlot>>(
            """
            SELECT s.id, s.specialist_id, s.starts_at, s.ends_at, sp.full_name, sp.specialty
            $conditions
            ORDER BY s.starts_at, s.id LIMIT ? OFFSET ?
            """.trimIndent(),
            args + listOf(IntegerColumnType() to page.size, LongColumnType() to page.offset),
        ) { result ->
            buildList {
                while (result.next()) {
                    val slot = Slot(
                        result.getLong("id"), result.getLong("specialist_id"),
                        result.getObject("starts_at", OffsetDateTime::class.java),
                        result.getObject("ends_at", OffsetDateTime::class.java),
                    )
                    add(FreeSlot(slot, Specialist(
                        slot.specialistId, result.getString("full_name"), result.getString("specialty"),
                    )))
                }
            }
        })
        val total = checkNotNull(exec<Long>("SELECT count(*) $conditions", args) { result ->
            result.next()
            result.getLong(1)
        })
        Page(items, total)
    }
}
