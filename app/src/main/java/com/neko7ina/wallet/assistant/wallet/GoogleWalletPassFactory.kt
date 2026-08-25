package com.neko7ina.wallet.assistant.wallet

import com.neko7ina.wallet.assistant.BuildConfig
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.stableId
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class GoogleWalletPassFactory {
    private val json = Json { encodeDefaults = true }

    fun createUnsignedPass(document: TravelDocument): String {
        val classId = "${BuildConfig.WALLET_ISSUER_ID}.${BuildConfig.WALLET_CLASS_SUFFIX}"
        val objectId = "${BuildConfig.WALLET_ISSUER_ID}.${document.stableId()}"
        val segment = document.segments.first()
        val seat = segment.seatAssignments.first()

        val claims = buildJsonObject {
            put("iss", BuildConfig.WALLET_ISSUER_OWNER_EMAIL)
            put("aud", "google")
            put("typ", "savetowallet")
            put("iat", Instant.now().epochSecond)
            putJsonArray("origins") {}
            putJsonObject("payload") {
                putJsonArray("genericClasses") {
                    addJsonObject { put("id", classId) }
                }
                putJsonArray("genericObjects") {
                    addJsonObject {
                        put("id", objectId)
                        put("classId", classId)
                        put("state", "ACTIVE")
                        put("genericType", "GENERIC_TYPE_UNSPECIFIED")
                        put("hexBackgroundColor", "#1A73E8")
                        put("cardTitle", localized("铁路行程"))
                        put(
                            "header",
                            localized(
                                "${segment.serviceNumber} ${segment.origin.name} → ${segment.destination.name}",
                            ),
                        )
                        put(
                            "subheader",
                            localized(segment.departureTime.format(DEPARTURE_FORMAT)),
                        )
                        putJsonObject("validTimeInterval") {
                            putJsonObject("start") {
                                put(
                                    "date",
                                    segment.departureTime.toOffsetDateTime()
                                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                )
                            }
                        }
                        putJsonObject("notifications") {
                            putJsonObject("upcomingNotification") {
                                put("enabledNotification", true)
                            }
                        }
                        putJsonArray("textModulesData") {
                            addTextModule("departure", "出发", segment.departureTime.format(DEPARTURE_DETAIL_FORMAT))
                            addTextModule("seat", "座位", "${seat.section} 车 ${seat.seat}")
                            addTextModule("seat_class", "席别", seat.category)
                            addTextModule("traveler", "乘车人", document.travelers.first().name)
                        }
                    }
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), claims)
    }

    private fun localized(value: String): JsonObject = buildJsonObject {
        putJsonObject("defaultValue") {
            put("language", "zh-CN")
            put("value", value)
        }
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addTextModule(
        id: String,
        header: String,
        body: String,
    ) {
        addJsonObject {
            put("id", id)
            put("header", header)
            put("body", body)
        }
    }

    private companion object {
        val DEPARTURE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")
        val DEPARTURE_DETAIL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern(
            "yyyy 年 M 月 d 日 HH:mm · VV",
        )
    }
}
