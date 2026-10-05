package com.acute.presentation.dto

import kotlinx.serialization.Serializable

@Serializable data class LoginRequest(val login: String, val password: String)
@Serializable data class TokenDto(val token: String)
@Serializable data class ErrorDto(val error: String)
@Serializable data class CreateBookingRequest(val slotId: Long, val serviceId: Long)
@Serializable data class SpecialistRefDto(val id: Long, val fullName: String, val specialty: String)
@Serializable data class ServiceRefDto(val id: Long, val name: String)
@Serializable data class ServiceDto(val id: Long, val name: String, val specialty: String, val durationMin: Int)
@Serializable data class SlotDto(val id: Long, val startsAt: String, val endsAt: String)
@Serializable data class ClientDto(val id: Long, val fullName: String, val login: String)
@Serializable data class BookingRowDto(
    val id: Long,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val specialist: SpecialistRefDto,
    val service: ServiceRefDto,
    val clientName: String,
)
@Serializable data class BookingCardDto(
    val id: Long,
    val status: String,
    val createdAt: String,
    val cancelledAt: String?,
    val slot: SlotDto,
    val specialist: SpecialistRefDto,
    val service: ServiceDto,
    val client: ClientDto,
)
@Serializable data class FreeSlotDto(
    val id: Long,
    val startsAt: String,
    val endsAt: String,
    val specialist: SpecialistRefDto,
)
@Serializable data class PageDto<T>(val items: List<T>, val total: Long)
@Serializable data class SpecialistLoadDto(
    val id: Long,
    val fullName: String,
    val specialty: String,
    val slotMinutes: Long,
    val bookedMinutes: Long,
    val load: Double,
)
@Serializable data class SummaryDto(
    val from: String,
    val to: String,
    val totalBookings: Long,
    val cancelledShare: Double,
    val specialists: List<SpecialistLoadDto>,
)
