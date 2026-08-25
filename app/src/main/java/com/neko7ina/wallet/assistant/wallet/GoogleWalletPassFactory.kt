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
        val objectId = "${BuildConfig.WALLET_ISSUER_ID}." +
            "${BuildConfig.WALLET_CLASS_SUFFIX}_${document.stableId()}"
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
                    addJsonObject {
                        put("id", classId)
                        putJsonObject("classTemplateInfo") {
                            putJsonObject("cardTemplateOverride") {
                                putJsonArray("cardRowTemplateInfos") {
                                    add(twoItemRow("origin", "destination"))
                                    add(twoItemRow("departure_date", "departure_time"))
                                    add(twoItemRow("seat", "traveler"))
                                }
                            }
                        }
                    }
                }
                putJsonArray("genericObjects") {
                    addJsonObject {
                        put("id", objectId)
                        put("classId", classId)
                        put("state", "ACTIVE")
                        put("genericType", "GENERIC_TYPE_UNSPECIFIED")
                        put("hexBackgroundColor", "#6F7378")
                        put("cardTitle", localized("中国铁路"))
                        put(
                            "header",
                            localized("${segment.origin.name} → ${segment.destination.name}"),
                        )
                        put("subheader", localized(segment.serviceNumber))
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
                            addTextModule("origin", "出发站", segment.origin.name)
                            addTextModule("destination", "目的站", segment.destination.name)
                            addTextModule(
                                "departure_date",
                                "出发日期",
                                segment.departureTime.format(DEPARTURE_DATE_FORMAT),
                            )
                            addTextModule(
                                "departure_time",
                                "出发时间",
                                segment.departureTime.format(DEPARTURE_TIME_FORMAT),
                            )
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

    private fun twoItemRow(startId: String, endId: String): JsonObject = buildJsonObject {
        putJsonObject("twoItems") {
            putJsonObject("startItem") {
                putJsonObject("firstValue") {
                    putJsonArray("fields") {
                        addJsonObject {
                            put("fieldPath", "object.textModulesData['$startId']")
                        }
                    }
                }
            }
            putJsonObject("endItem") {
                putJsonObject("firstValue") {
                    putJsonArray("fields") {
                        addJsonObject {
                            put("fieldPath", "object.textModulesData['$endId']")
                        }
                    }
                }
            }
        }
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
        val DEPARTURE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")
        val DEPARTURE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
