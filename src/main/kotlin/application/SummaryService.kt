package com.acute.application

import com.acute.domain.SpecialistLoad
import com.acute.domain.Summary

class SummaryService(private val repository: SummaryRepository) {
    fun summary(period: Period): Summary {
        val specialists = repository.specialistMinutes(period).map {
            SpecialistLoad(it.specialist, it.slotMinutes, it.bookedMinutes)
        }
        val counts = repository.bookingCounts(period)
        return Summary(period.from, period.to, counts.total, counts.cancelled, specialists)
    }
}
