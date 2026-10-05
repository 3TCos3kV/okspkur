package com.acute.application

import com.acute.domain.NotFound
import com.acute.domain.Service

class ScheduleService(private val schedule: ScheduleRepository) {
    fun services(): List<Service> = schedule.services()

    fun freeSlots(serviceId: Long, period: Period, page: PageRequest): Page<FreeSlot> {
        val service = schedule.service(serviceId) ?: throw NotFound("Услуга не найдена")
        return schedule.freeSlots(service, period, page)
    }
}
