package com.acute.application

import com.acute.domain.Booking
import com.acute.domain.Service
import com.acute.domain.Slot
import com.acute.domain.Specialist
import java.time.OffsetDateTime

interface ScheduleRepository {
    fun services(): List<Service>
    fun service(id: Long): Service?
    fun specialist(id: Long): Specialist?
    fun slotForUpdate(id: Long): Slot?
    fun freeSlots(service: Service, period: Period, page: PageRequest): Page<FreeSlot>
}
interface BookingRepository {
    fun list(filter: BookingFilter, page: PageRequest): Page<BookingDetails>
    fun details(id: Long): BookingDetails?
    fun forUpdate(id: Long): Booking?
    fun hasActiveBooking(slotId: Long): Boolean
    fun create(slotId: Long, serviceId: Long, clientId: Long, createdAt: OffsetDateTime): Long
    fun cancel(id: Long, cancelledAt: OffsetDateTime)
}
interface SummaryRepository {
    fun specialistMinutes(period: Period): List<SpecialistMinutes>
    fun bookingCounts(period: Period): BookingCounts
}
interface TransactionRunner {
    fun <T> inTransaction(block: () -> T): T
}
