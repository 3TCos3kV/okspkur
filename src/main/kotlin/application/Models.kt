package com.acute.application

import com.acute.domain.Booking
import com.acute.domain.BookingStatus
import com.acute.domain.Client
import com.acute.domain.Invalid
import com.acute.domain.Service
import com.acute.domain.Slot
import com.acute.domain.Specialist
import java.time.LocalDate

data class Page<T>(val items: List<T>, val total: Long)
data class PageRequest(val page: Int, val size: Int) {
    init {
        if (page < 1) throw Invalid("Параметр page должен быть не меньше 1")
        if (size !in 1..100) throw Invalid("Параметр size должен быть от 1 до 100")
    }
    val offset: Long get() = (page - 1).toLong() * size
}
data class Period(val from: LocalDate, val to: LocalDate) {
    init {
        if (to < from) throw Invalid("Параметр to раньше from")
    }
}
data class BookingFilter(
    val status: BookingStatus?,
    val specialistId: Long?,
    val from: LocalDate?,
    val to: LocalDate?,
) {
    init {
        if (from != null && to != null && to < from) throw Invalid("Параметр to раньше from")
    }
}
data class BookingDetails(
    val booking: Booking,
    val slot: Slot,
    val specialist: Specialist,
    val service: Service,
    val client: Client,
)
data class FreeSlot(val slot: Slot, val specialist: Specialist)
data class SpecialistMinutes(val specialist: Specialist, val slotMinutes: Long, val bookedMinutes: Long)
data class BookingCounts(val total: Long, val cancelled: Long)
