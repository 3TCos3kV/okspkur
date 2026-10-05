package com.acute.presentation

import at.favre.lib.crypto.bcrypt.BCrypt
import com.acute.application.BookingCounts
import com.acute.application.BookingDetails
import com.acute.application.BookingFilter
import com.acute.application.BookingRepository
import com.acute.application.BookingService
import com.acute.application.FreeSlot
import com.acute.application.Page
import com.acute.application.PageRequest
import com.acute.application.Period
import com.acute.application.ScheduleRepository
import com.acute.application.ScheduleService
import com.acute.application.SpecialistMinutes
import com.acute.application.SummaryRepository
import com.acute.application.SummaryService
import com.acute.application.TransactionRunner
import com.acute.domain.Booking
import com.acute.domain.BookingStatus
import com.acute.domain.Client
import com.acute.domain.Service
import com.acute.domain.Slot
import com.acute.domain.Specialist
import com.acute.infrastructure.auth.Authenticator
import com.acute.infrastructure.auth.JwtConfig
import com.acute.infrastructure.auth.UserCredentials
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class FakeScheduleRepository : ScheduleRepository {
    val zone: ZoneId = ZoneId.of("Europe/Moscow")
    val specialists = listOf(Specialist(1, "Иванов", "Кардиолог"), Specialist(2, "Петров", "Терапевт"))
    val services = listOf(
        Service(1, "Приём", "Кардиолог", 30), Service(2, "Расширенный приём", "Кардиолог", 60),
        Service(3, "Консультация", "Терапевт", 30),
    )
    private val start = OffsetDateTime.parse("2026-10-01T10:00:00+03:00")
    val slots = listOf(
        Slot(1, 1, start, start.plusMinutes(30)),
        Slot(2, 1, start.plusDays(1), start.plusDays(1).plusMinutes(60)),
        Slot(3, 1, start.plusDays(2), start.plusDays(2).plusMinutes(30)),
        Slot(4, 2, start, start.plusMinutes(90)),
        Slot(5, 1, start.plusMonths(1), start.plusMonths(1).plusMinutes(30)),
    )
    var activeSlotIds: () -> Set<Long> = { emptySet() }

    override fun services(): List<Service> = services
    override fun service(id: Long): Service? = services.find { it.id == id }
    override fun specialist(id: Long): Specialist? = specialists.find { it.id == id }
    override fun slotForUpdate(id: Long): Slot? = slots.find { it.id == id }
    override fun freeSlots(service: Service, period: Period, page: PageRequest): Page<FreeSlot> {
        val taken = activeSlotIds()
        val matching = slots.filter {
            specialist(it.specialistId)?.specialty == service.specialty && it.durationMin >= service.durationMin &&
                it.id !in taken && contains(period, it)
        }.sortedWith(compareBy<Slot> { it.startsAt }.thenBy { it.id })
            .map { FreeSlot(it, checkNotNull(specialist(it.specialistId))) }
        val offset = page.offset.coerceAtMost(matching.size.toLong()).toInt()
        return Page(matching.drop(offset).take(page.size), matching.size.toLong())
    }
    fun contains(period: Period, slot: Slot): Boolean {
        val date = slot.startsAt.atZoneSameInstant(zone).toLocalDate()
        return date >= period.from && date <= period.to
    }
}

class FakeBookingRepository(private val schedule: FakeScheduleRepository) : BookingRepository {
    private val createdAt = OffsetDateTime.parse("2026-09-01T00:00:00Z")
    val bookings = mutableListOf(
        Booking(1, 1, 1, 1, BookingStatus.ACTIVE, createdAt, null),
        Booking(2, 2, 2, 1, BookingStatus.CANCELLED, createdAt, createdAt.plusDays(1)),
    )
    private val client = Client(1, "Клиент", "admin")

    init {
        schedule.activeSlotIds = { bookings.filter { it.status == BookingStatus.ACTIVE }.map { it.slotId }.toSet() }
    }
    override fun list(filter: BookingFilter, page: PageRequest): Page<BookingDetails> {
        val matching = bookings.map { details(it.id) ?: error("Запись исчезла") }.filter {
            val date = it.slot.startsAt.atZoneSameInstant(schedule.zone).toLocalDate()
            (filter.status == null || it.booking.status == filter.status) &&
                (filter.specialistId == null || it.specialist.id == filter.specialistId) &&
                (filter.from == null || date >= filter.from) && (filter.to == null || date <= filter.to)
        }.sortedWith(compareByDescending<BookingDetails> { it.slot.startsAt }.thenByDescending { it.booking.id })
        val offset = page.offset.coerceAtMost(matching.size.toLong()).toInt()
        return Page(matching.drop(offset).take(page.size), matching.size.toLong())
    }
    override fun details(id: Long): BookingDetails? {
        val booking = bookings.find { it.id == id } ?: return null
        val slot = checkNotNull(schedule.slotForUpdate(booking.slotId))
        return BookingDetails(
            booking, slot, checkNotNull(schedule.specialist(slot.specialistId)),
            checkNotNull(schedule.service(booking.serviceId)), client.copy(id = booking.clientId),
        )
    }
    override fun forUpdate(id: Long): Booking? = bookings.find { it.id == id }
    override fun hasActiveBooking(slotId: Long) =
        bookings.any { it.slotId == slotId && it.status == BookingStatus.ACTIVE }
    override fun create(slotId: Long, serviceId: Long, clientId: Long, createdAt: OffsetDateTime): Long {
        val id = (bookings.maxOfOrNull { it.id } ?: 0) + 1
        bookings.add(Booking(id, slotId, serviceId, clientId, BookingStatus.ACTIVE, createdAt, null))
        return id
    }
    override fun cancel(id: Long, cancelledAt: OffsetDateTime) {
        val index = bookings.indexOfFirst { it.id == id }
        bookings[index] = bookings[index].copy(status = BookingStatus.CANCELLED, cancelledAt = cancelledAt)
    }
}

class FakeSummaryRepository(
    private val schedule: FakeScheduleRepository,
    private val bookings: FakeBookingRepository,
) : SummaryRepository {
    override fun specialistMinutes(period: Period): List<SpecialistMinutes> = schedule.specialists.map { specialist ->
        val slots = schedule.slots.filter { it.specialistId == specialist.id && schedule.contains(period, it) }
        SpecialistMinutes(specialist, slots.sumOf { it.durationMin },
            slots.filter { bookings.hasActiveBooking(it.id) }.sumOf { it.durationMin })
    }
    override fun bookingCounts(period: Period): BookingCounts {
        val matching = bookings.bookings.filter {
            schedule.contains(period, checkNotNull(schedule.slotForUpdate(it.slotId)))
        }
        return BookingCounts(matching.size.toLong(), matching.count { it.status == BookingStatus.CANCELLED }.toLong())
    }
}

class FakeTransactionRunner : TransactionRunner {
    override fun <T> inTransaction(block: () -> T): T = block()
}

fun fakeComponents(): Components {
    val schedule = FakeScheduleRepository()
    val bookings = FakeBookingRepository(schedule)
    val jwt = JwtConfig("test-secret", "okspkur", "okspkur", Duration.ofHours(1))
    val hash = BCrypt.withDefaults().hashToString(4, "demo".toCharArray())
    return Components(
        BookingService(bookings, schedule, FakeTransactionRunner(),
            Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), ZoneOffset.UTC)),
        ScheduleService(schedule), SummaryService(FakeSummaryRepository(schedule, bookings)),
        Authenticator({ if (it == "admin") UserCredentials(1, hash) else null }, jwt, Clock.systemUTC()),
        jwt, schedule.zone,
    )
}
