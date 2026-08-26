package com.neko7ina.wallet.assistant.core.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.serialization.Serializable

@Serializable
data class TravelDocument(
    val type: TravelDocumentType,
    val provider: ProviderInfo,
    val reservation: Reservation,
    val travelers: List<Traveler>,
    val segments: List<TravelSegment>,
    val journeyKey: String? = null,
    val status: TravelDocumentStatus = TravelDocumentStatus.CONFIRMED,
)

@Serializable
enum class TravelDocumentStatus {
    CONFIRMED,
    RESCHEDULED,
    REFUNDED,
}

@Serializable
enum class TravelDocumentType {
    RAIL,
    FLIGHT,
    BUS,
    HOTEL,
    EVENT,
    GENERIC,
}

@Serializable
data class ProviderInfo(
    val code: String,
    val name: String,
)

@Serializable
data class Reservation(
    val reference: String,
    @Serializable(with = NullableLocalDateAsStringSerializer::class)
    val purchasedOn: LocalDate?,
    val totalPrice: Money,
)

@Serializable
data class Money(
    @Serializable(with = BigDecimalAsStringSerializer::class)
    val amount: BigDecimal,
    val currencyCode: String,
)

@Serializable
data class Traveler(
    val id: String,
    val name: String,
)

@Serializable
data class TravelSegment(
    val origin: Location,
    val destination: Location,
    @Serializable(with = ZonedDateTimeAsStringSerializer::class)
    val departureTime: ZonedDateTime,
    @Serializable(with = NullableZonedDateTimeAsStringSerializer::class)
    val arrivalTime: ZonedDateTime?,
    val serviceNumber: String,
    val seatAssignments: List<SeatAssignment>,
    val attributes: Map<String, String>,
)

@Serializable
data class Location(
    val name: String,
)

@Serializable
data class SeatAssignment(
    val travelerId: String,
    val section: String,
    val seat: String,
    val category: String,
    val status: TravelDocumentStatus = TravelDocumentStatus.CONFIRMED,
)
