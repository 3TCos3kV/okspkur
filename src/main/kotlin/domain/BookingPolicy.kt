package com.acute.domain

object BookingPolicy {
    fun ensureCanBook(slot: Slot, specialist: Specialist, service: Service, slotTaken: Boolean) {
        if (specialist.specialty != service.specialty) {
            throw Invalid("Услуга не относится к специальности специалиста")
        }
        if (service.durationMin > slot.durationMin) throw Invalid("Услуга длиннее слота")
        if (slotTaken) throw Conflict("Слот уже занят")
    }

    fun ensureCanCancel(booking: Booking) {
        if (booking.status == BookingStatus.CANCELLED) throw Conflict("Запись уже отменена")
    }
}
