package com.acute.infrastructure.db

import com.acute.application.BookingCounts
import com.acute.application.Period
import com.acute.application.SpecialistMinutes
import com.acute.application.SummaryRepository
import com.acute.domain.Specialist
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.ZoneId

class ExposedSummaryRepository(private val database: Database, private val zone: ZoneId) : SummaryRepository {
    override fun specialistMinutes(period: Period): List<SpecialistMinutes> = dbTransaction(database) {
        // Минуты не дублируются: бронирование допускает лишь одну активную запись на слот.
        checkNotNull(exec(
            """
            SELECT sp.id, sp.full_name, sp.specialty,
                   coalesce(sum(extract(epoch FROM s.ends_at - s.starts_at) / 60), 0)::bigint AS slot_minutes,
                   coalesce(sum(extract(epoch FROM s.ends_at - s.starts_at) / 60)
                       FILTER (WHERE b.id IS NOT NULL), 0)::bigint AS booked_minutes
            FROM specialists sp
            LEFT JOIN slots s ON s.specialist_id = sp.id AND s.starts_at >= ? AND s.starts_at < ?
            LEFT JOIN bookings b ON b.slot_id = s.id AND b.status = 'active'
            GROUP BY sp.id, sp.full_name, sp.specialty
            ORDER BY sp.id
            """.trimIndent(),
            listOf(
                Slots.startsAt.columnType to period.from.atStartOfDay(zone).toOffsetDateTime(),
                Slots.startsAt.columnType to period.to.plusDays(1).atStartOfDay(zone).toOffsetDateTime(),
            ),
        ) { result ->
            buildList {
                while (result.next()) {
                    add(SpecialistMinutes(
                        Specialist(result.getLong("id"), result.getString("full_name"), result.getString("specialty")),
                        result.getLong("slot_minutes"), result.getLong("booked_minutes"),
                    ))
                }
            }
        })
    }

    override fun bookingCounts(period: Period): BookingCounts = dbTransaction(database) {
        checkNotNull(exec(
            """
            SELECT count(*), count(*) FILTER (WHERE b.status = 'cancelled')
            FROM bookings b JOIN slots s ON s.id = b.slot_id
            WHERE s.starts_at >= ? AND s.starts_at < ?
            """.trimIndent(),
            listOf(
                Slots.startsAt.columnType to period.from.atStartOfDay(zone).toOffsetDateTime(),
                Slots.startsAt.columnType to period.to.plusDays(1).atStartOfDay(zone).toOffsetDateTime(),
            ),
        ) { result ->
            result.next()
            BookingCounts(result.getLong(1), result.getLong(2))
        })
    }
}
