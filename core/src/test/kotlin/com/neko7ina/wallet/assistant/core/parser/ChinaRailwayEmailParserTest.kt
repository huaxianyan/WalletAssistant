package com.neko7ina.wallet.assistant.core.parser

import java.math.BigDecimal
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChinaRailwayEmailParserTest {
    @Test
    fun `导入购票邮件后显示完整乘车信息`() {
        val email = RawDocument(
            subject = "网上购票成功通知",
            sender = "12306",
            body = """
                尊敬的 华贤阳先生：
                您好！
                您于2026年02月15日在中国铁路客户服务中心网站(12306.cn) 成功购买了1张车票，票款共计120.00元，订单号码 E851136111 。 所购车票信息如下：
                1.华贤阳，2026年02月21日13:10开，镇江站-上海站，G7229次列车，2车17C号，二等座，成人票，票价120.0元，电子客票。
            """.trimIndent(),
        )

        val result = ChinaRailwayEmailParser().parse(email)

        val success = assertIs<ParseResult.Success>(result)
        val document = success.document
        val segment = document.segments.single()
        val seat = segment.seatAssignments.single()
        assertEquals("E851136111", document.reservation.reference)
        assertEquals(BigDecimal("120.00"), document.reservation.totalPrice.amount)
        assertEquals("华贤阳", document.travelers.single().name)
        assertEquals("G7229", segment.serviceNumber)
        assertEquals("镇江站", segment.origin.name)
        assertEquals("上海站", segment.destination.name)
        assertEquals(ZoneId.of("Asia/Shanghai"), segment.departureTime.zone)
        assertEquals("2026-02-21T13:10+08:00[Asia/Shanghai]", segment.departureTime.toString())
        assertEquals("2", seat.section)
        assertEquals("17C", seat.seat)
        assertEquals("二等座", seat.category)
        assertEquals("成人票", segment.attributes["ticketType"])
        assertEquals("电子客票", segment.attributes["credentialType"])
    }

    @Test
    fun `读取邮件截图文字后识别被空格和换行拆开的字段`() {
        val ocrText = """
            您于2026年02月15日在中国铁路客户服务中心网站(12306.cn) 成功购买了1 张车票,票款共计120.00元,订单号码
            E851136111 。所购车票信息如下:
            1.华贤阳,2026年02月21日13：10开,镇江站-上海站,G7229次列车,2车17C号,二等座,成人票,票价
            120.0元,电子客票。
        """.trimIndent()

        val result = ChinaRailwayEmailParser().parse(
            RawDocument(body = normalizeOcrTextForStructuredParsing(ocrText)),
        )

        val document = assertIs<ParseResult.Success>(result).document
        assertEquals("E851136111", document.reservation.reference)
        assertEquals("G7229", document.segments.single().serviceNumber)
        assertEquals("17C", document.segments.single().seatAssignments.single().seat)
    }
}
