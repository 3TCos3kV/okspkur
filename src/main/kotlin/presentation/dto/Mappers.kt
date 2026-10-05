package com.acute.presentation.dto

import com.acute.application.BookingDetails
import com.acute.application.FreeSlot
import com.acute.domain.Client
import com.acute.domain.Service
import com.acute.domain.Slot
import com.acute.domain.Specialist
import com.acute.domain.SpecialistLoad
import com.acute.domain.Summary
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private fun OffsetDateTime.format(zone: ZoneId): String =
    atZoneSameInstant(zone).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

fun Specialist.toDto() = SpecialistRefDto(id, fullName, specialty)
fun Service.toDto() = ServiceDto(id, name, specialty, durationMin)
fun Slot.toDto(zone: ZoneId) = SlotDto(id, startsAt.format(zone), endsAt.format(zone))
fun Client.toDto() = ClientDto(id, fullName, login)
fun BookingDetails.toRowDto(zone: ZoneId) = BookingRowDto(
    booking.id, slot.startsAt.format(zone), slot.endsAt.format(zone), booking.status.name.lowercase(),
    specialist.toDto(), ServiceRefDto(service.id, service.name), client.fullName,
)
fun BookingDetails.toDto(zone: ZoneId) = BookingCardDto(
    booking.id, booking.status.name.lowercase(), booking.createdAt.format(zone), booking.cancelledAt?.format(zone),
    slot.toDto(zone), specialist.toDto(), service.toDto(), client.toDto(),
)
fun FreeSlot.toDto(zone: ZoneId) = FreeSlotDto(
    slot.id, slot.startsAt.format(zone), slot.endsAt.format(zone), specialist.toDto(),
)
fun SpecialistLoad.toDto() = SpecialistLoadDto(
    specialist.id, specialist.fullName, specialist.specialty, slotMinutes, bookedMinutes, load,
)
fun Summary.toDto() = SummaryDto(
    from.toString(), to.toString(), totalBookings, cancelledShare, specialists.map { it.toDto() },
)
