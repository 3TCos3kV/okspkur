package com.acute.application

import com.acute.domain.BookingPolicy
import com.acute.domain.NotFound
import java.time.Clock
import java.time.OffsetDateTime

class BookingService(
    private val bookings: BookingRepository,
    private val schedule: ScheduleRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun list(filter: BookingFilter, page: PageRequest): Page<BookingDetails> = bookings.list(filter, page)

    fun card(id: Long): BookingDetails = bookings.details(id) ?: throw NotFound("Запись не найдена")

    fun book(slotId: Long, serviceId: Long, clientId: Long): BookingDetails = transactions.inTransaction {
        // Блокировка слота защищает от двойного бронирования без уникального индекса.
        val slot = schedule.slotForUpdate(slotId) ?: throw NotFound("Слот не найден")
        val service = schedule.service(serviceId) ?: throw NotFound("Услуга не найдена")
        val specialist = checkNotNull(schedule.specialist(slot.specialistId))
        BookingPolicy.ensureCanBook(slot, specialist, service, bookings.hasActiveBooking(slot.id))
        val id = bookings.create(slot.id, service.id, clientId, OffsetDateTime.now(clock))
        checkNotNull(bookings.details(id))
    }

    fun cancel(id: Long): BookingDetails = transactions.inTransaction {
        val booking = bookings.forUpdate(id) ?: throw NotFound("Запись не найдена")
        BookingPolicy.ensureCanCancel(booking)
        bookings.cancel(id, OffsetDateTime.now(clock))
        checkNotNull(bookings.details(id))
    }
}
