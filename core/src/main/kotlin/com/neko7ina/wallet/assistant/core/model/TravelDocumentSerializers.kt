package com.neko7ina.wallet.assistant.core.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object BigDecimalAsStringSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}

object LocalDateAsStringSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object ZonedDateTimeAsStringSerializer : KSerializer<ZonedDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ZonedDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ZonedDateTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): ZonedDateTime = ZonedDateTime.parse(decoder.decodeString())
}

@OptIn(ExperimentalSerializationApi::class)
object NullableZonedDateTimeAsStringSerializer : KSerializer<ZonedDateTime?> {
    override val descriptor: SerialDescriptor = ZonedDateTimeAsStringSerializer.descriptor.nullable

    override fun serialize(encoder: Encoder, value: ZonedDateTime?) {
        encoder.encodeNullableSerializableValue(ZonedDateTimeAsStringSerializer, value)
    }

    override fun deserialize(decoder: Decoder): ZonedDateTime? =
        decoder.decodeNullableSerializableValue(ZonedDateTimeAsStringSerializer)
}
