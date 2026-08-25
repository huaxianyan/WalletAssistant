package com.neko7ina.wallet.assistant.core.parser

import com.neko7ina.wallet.assistant.core.model.Location
import com.neko7ina.wallet.assistant.core.model.Money
import com.neko7ina.wallet.assistant.core.model.ProviderInfo
import com.neko7ina.wallet.assistant.core.model.Reservation
import com.neko7ina.wallet.assistant.core.model.SeatAssignment
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelDocumentType
import com.neko7ina.wallet.assistant.core.model.TravelSegment
import com.neko7ina.wallet.assistant.core.model.Traveler
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ChinaRailwayEmailParser : TravelDocumentParser {
    override val parserId = "china-railway-email"
    override val version = 1

    override fun detect(document: RawDocument): DetectionResult {
        val body = normalize(document.body)
        val signals = listOf(
            "12306.cn" in body,
            "成功购买了" in body,
            "所购车票信息如下" in body,
            "次列车" in body,
        ).count { it }

        return DetectionResult(
            supported = signals >= 3,
            confidence = signals / 4f,
        )
    }

    override fun parse(document: RawDocument): ParseResult {
        val body = normalize(document.body)
        if (!detect(document).supported) {
            return ParseResult.Failure("这封邮件不是可识别的铁路购票通知，请选择购票成功邮件。")
        }

        val purchaseMatch = PURCHASE_REGEX.find(body)
            ?: return ParseResult.Failure("邮件中缺少购票日期或车票数量，请检查邮件内容。")
        val totalPriceMatch = TOTAL_PRICE_REGEX.find(body)
            ?: return ParseResult.Failure("邮件中缺少订单总价，请检查邮件内容。")
        val orderMatch = ORDER_REGEX.find(body)
            ?: return ParseResult.Failure("邮件中缺少订单号码，请检查邮件内容。")
        val ticketMatches = TICKET_REGEX.findAll(body).toList()
        if (ticketMatches.isEmpty()) {
            return ParseResult.Failure("邮件中没有找到完整的乘车信息，请检查邮件内容。")
        }

        return try {
            val purchasedOn = LocalDate.of(
                purchaseMatch.groupValues[1].toInt(),
                purchaseMatch.groupValues[2].toInt(),
                purchaseMatch.groupValues[3].toInt(),
            )
            val expectedTicketCount = purchaseMatch.groupValues[4].toInt()
            val travelers = ticketMatches.mapIndexed { index, match ->
                Traveler(id = "traveler-${index + 1}", name = match.groupValues[2].trim())
            }
            val segments = ticketMatches.mapIndexed { index, match ->
                match.toSegment(travelers[index])
            }
            val warnings = if (segments.size == expectedTicketCount) {
                emptyList()
            } else {
                listOf("邮件显示有 $expectedTicketCount 张车票，当前识别出 ${segments.size} 张，请确认行程信息。")
            }

            ParseResult.Success(
                document = TravelDocument(
                    type = TravelDocumentType.RAIL,
                    provider = CHINA_RAILWAY,
                    reservation = Reservation(
                        reference = orderMatch.groupValues[1],
                        purchasedOn = purchasedOn,
                        totalPrice = Money(
                            amount = BigDecimal(totalPriceMatch.groupValues[1]),
                            currencyCode = "CNY",
                        ),
                    ),
                    travelers = travelers,
                    segments = segments,
                ),
                warnings = warnings,
            )
        } catch (_: DateTimeException) {
            ParseResult.Failure("邮件中的乘车日期或时间无效，请核对后手动填写。")
        } catch (_: NumberFormatException) {
            ParseResult.Failure("邮件中的车票数量或金额无法识别，请核对后手动填写。")
        }
    }

    private fun MatchResult.toSegment(traveler: Traveler): TravelSegment {
        val departure = LocalDate.of(
            groupValues[3].toInt(),
            groupValues[4].toInt(),
            groupValues[5].toInt(),
        ).atTime(
            LocalTime.of(groupValues[6].toInt(), groupValues[7].toInt()),
        ).atZone(CHINA_TIME_ZONE)

        return TravelSegment(
            origin = Location(groupValues[8].trim()),
            destination = Location(groupValues[9].trim()),
            departureTime = departure,
            arrivalTime = null,
            serviceNumber = groupValues[10].trim(),
            seatAssignments = listOf(
                SeatAssignment(
                    travelerId = traveler.id,
                    section = groupValues[11].trim(),
                    seat = groupValues[12].trim(),
                    category = groupValues[13].trim(),
                ),
            ),
            attributes = mapOf(
                "ticketType" to groupValues[14].trim(),
                "price" to groupValues[15],
                "credentialType" to groupValues[16].trim(),
            ),
        )
    }

    private fun normalize(text: String): String = text
        .replace('\u00A0', ' ')
        .replace(WHITESPACE_REGEX, " ")
        .trim()

    private companion object {
        val CHINA_TIME_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val CHINA_RAILWAY = ProviderInfo(code = "china-railway", name = "中国铁路 12306")
        val WHITESPACE_REGEX = Regex("\\s+")
        val PURCHASE_REGEX = Regex("于(\\d{4})年(\\d{1,2})月(\\d{1,2})日.*?成功购买了(\\d+)张车票")
        val TOTAL_PRICE_REGEX = Regex("票款共计([\\d.]+)元")
        val ORDER_REGEX = Regex("订单号码\\s*([A-Za-z0-9]+)")
        val TICKET_REGEX = Regex(
            """(\d+)[.．]\s*([^，,]+)[，,]\s*(\d{4})年(\d{1,2})月(\d{1,2})日(\d{1,2}):(\d{2})开[，,]\s*([^－—–,，-]+)\s*[-－—–]\s*([^，,]+)[，,]\s*([A-Za-z0-9]+)次列车[，,]\s*(\d+)车([^，,]+)号[，,]\s*([^，,]+)[，,]\s*([^，,]+)[，,]\s*票价([\d.]+)元[，,]\s*([^。.;；]+)""",
        )
    }
}
