package com.acute.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SummaryTest {
    private val specialist = Specialist(1, "Иванов", "Кардиолог")
    private val date = LocalDate.of(2026, 10, 1)

    @Test fun `загрузка — доля занятых минут от минут слотов`() {
        assertEquals(0.25, SpecialistLoad(specialist, 120, 30).load)
    }
    @Test fun `загрузка специалиста без слотов равна нулю`() {
        assertEquals(0.0, SpecialistLoad(specialist, 0, 0).load)
    }
    @Test fun `доля отмен — отменённые от всех записей периода`() {
        assertEquals(0.2, Summary(date, date, 10, 2, emptyList()).cancelledShare)
    }
    @Test fun `доля отмен без записей равна нулю`() {
        assertEquals(0.0, Summary(date, date, 0, 0, emptyList()).cancelledShare)
    }
}
