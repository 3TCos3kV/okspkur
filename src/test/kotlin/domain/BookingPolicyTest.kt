package com.acute.domain

import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BookingPolicyTest {
    private val specialist = Specialist(1, "Иванов", "Кардиолог")
    private val service = Service(1, "Приём", "Кардиолог", 30)
    private val start = OffsetDateTime.parse("2026-10-01T10:00:00+03:00")
    private val slot = Slot(1, 1, start, start.plusMinutes(30))
    private val booking = Booking(1, 1, 1, 1, BookingStatus.ACTIVE, start, null)

    @Test fun `занятый слот нельзя забронировать`() {
        val error = assertFailsWith<Conflict> { BookingPolicy.ensureCanBook(slot, specialist, service, true) }
        assertEquals("Слот уже занят", error.message)
    }
    @Test fun `услугу другой специальности нельзя забронировать`() {
        val error = assertFailsWith<Invalid> {
            BookingPolicy.ensureCanBook(slot, specialist, service.copy(specialty = "Терапевт"), false)
        }
        assertEquals("Услуга не относится к специальности специалиста", error.message)
    }
    @Test fun `услугу длиннее слота нельзя забронировать`() {
        val error = assertFailsWith<Invalid> {
            BookingPolicy.ensureCanBook(slot, specialist, service.copy(durationMin = 60), false)
        }
        assertEquals("Услуга длиннее слота", error.message)
    }
    @Test fun `услуга равной слоту длительности помещается`() {
        BookingPolicy.ensureCanBook(slot, specialist, service, false)
    }
    @Test fun `свободный подходящий слот бронируется`() {
        BookingPolicy.ensureCanBook(slot.copy(endsAt = start.plusMinutes(60)), specialist, service, false)
    }
    @Test fun `активную запись можно отменить`() {
        BookingPolicy.ensureCanCancel(booking)
    }
    @Test fun `отменённую запись нельзя отменить повторно`() {
        val error = assertFailsWith<Conflict> {
            BookingPolicy.ensureCanCancel(booking.copy(status = BookingStatus.CANCELLED, cancelledAt = start))
        }
        assertEquals("Запись уже отменена", error.message)
    }
    @Test fun `специальность проверяется раньше длительности и занятости`() {
        val error = assertFailsWith<Invalid> {
            BookingPolicy.ensureCanBook(slot, specialist, service.copy(specialty = "Терапевт", durationMin = 90), true)
        }
        assertEquals("Услуга не относится к специальности специалиста", error.message)
    }
    @Test fun `длительность проверяется раньше занятости`() {
        val error = assertFailsWith<Invalid> {
            BookingPolicy.ensureCanBook(slot, specialist, service.copy(durationMin = 60), true)
        }
        assertEquals("Услуга длиннее слота", error.message)
    }
}
