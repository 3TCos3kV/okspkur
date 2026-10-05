package com.acute.domain

import java.time.LocalDate

data class SpecialistLoad(val specialist: Specialist, val slotMinutes: Long, val bookedMinutes: Long) {
    val load: Double get() = if (slotMinutes == 0L) 0.0 else bookedMinutes.toDouble() / slotMinutes
}
data class Summary(
    val from: LocalDate,
    val to: LocalDate,
    val totalBookings: Long,
    val cancelledBookings: Long,
    val specialists: List<SpecialistLoad>,
) {
    val cancelledShare: Double get() = if (totalBookings == 0L) 0.0 else cancelledBookings.toDouble() / totalBookings
}
