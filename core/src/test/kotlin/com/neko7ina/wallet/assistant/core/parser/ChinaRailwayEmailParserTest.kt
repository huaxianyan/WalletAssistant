package com.neko7ina.wallet.assistant.core.parser

import com.neko7ina.wallet.assistant.core.model.TravelDocumentStatus
import java.math.BigDecimal
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChinaRailwayEmailParserTest {
    @Test
    fun `导入购票邮件后显示完整乘车信息`() {
        val result = ChinaRailwayEmailParser().parse(
            RawDocument(body = purchaseEmail()),
        )

        val document = assertIs<ParseResult.Success>(result).documents.single()
        val segment = document.segments.single()
        val seat = segment.seatAssignments.single()
        assertEquals("E100000001", document.reservation.reference)
        assertEquals(BigDecimal("120.00"), document.reservation.totalPrice.amount)
        assertEquals("测试乘客", document.travelers.single().name)
        assertEquals("G8001", segment.serviceNumber)
        assertEquals("苹果站", segment.origin.name)
        assertEquals("香蕉站", segment.destination.name)
        assertEquals(ZoneId.of("Asia/Shanghai"), segment.departureTime.zone)
        assertEquals("2027-02-21T13:10+08:00[Asia/Shanghai]", segment.departureTime.toString())
        assertEquals("2", seat.section)
        assertEquals("17C", seat.seat)
        assertEquals("二等座", seat.category)
        assertEquals("成人票", segment.attributes["ticketType"])
    }

    @Test
    fun `读取邮件截图文字后识别被空格和换行拆开的字段`() {
        val ocrText = """
            您于2027年02月15日在中国铁路客户服务中心网站(12306.cn) 成功购买了1 张车票,票款共计120.00元,订单号码
            E100000001 。所购车票信息如下:
            1.测试乘客,2027年02月21日13：10开,苹果站-香蕉站,G8001次列车,2车17C号,二等座,成人票,票价
            120.0元,电子客票。
        """.trimIndent()

        val result = ChinaRailwayEmailParser().parse(
            RawDocument(body = normalizeOcrTextForStructuredParsing(ocrText)),
        )

        val document = assertIs<ParseResult.Success>(result).documents.single()
        assertEquals("G8001", document.segments.single().serviceNumber)
        assertEquals("17C", document.segments.single().seatAssignments.single().seat)
    }

    @Test
    fun `导入候补兑现邮件后创建有效行程`() {
        val email = """
            尊敬的 测试乘客先生：
            您好！
            您在中国铁路客户服务中心网站(12306.cn)成功办理了候补购票业务，成功兑现了1张车票，票款共计109.00元，订单号码 E100000003。车票信息如下：
            1.测试乘客，2027年10月05日13:10开，苹果站-香蕉站，G8003次列车,6车6A号，二等座，票价109.0元。
        """.trimIndent()

        val document = assertIs<ParseResult.Success>(
            ChinaRailwayEmailParser().parse(RawDocument(body = email)),
        ).documents.single()

        assertEquals(TravelDocumentStatus.CONFIRMED, document.status)
        assertEquals(null, document.reservation.purchasedOn)
        assertEquals("G8003", document.segments.single().serviceNumber)
    }

    @Test
    fun `导入改签邮件后创建改签后的有效行程`() {
        val email = """
            尊敬的 测试乘客先生：
            您好！
            您于2027年01月25日在中国铁路客户服务中心网站(12306.cn)成功改签车票1张，新车票票款共计111.50元，属等价改签,无支付和退款手续。订单号码 E100000002。改签后的车票信息如下:
            1.测试乘客，2027年01月25日13:10开，苹果站-香蕉站，G8002次列车,13车8F号，二等座，成人票，票价111.5元，电子客票。
        """.trimIndent()

        val result = assertIs<ParseResult.Success>(
            ChinaRailwayEmailParser().parse(RawDocument(body = email)),
        )

        assertEquals(TravelDocumentStatus.CONFIRMED, result.documents.single().status)
        assertEquals("G8002", result.documents.single().segments.single().serviceNumber)
    }

    @Test
    fun `按邮件顺序归并往返票部分退票和改签`() {
        val purchaseRoundTrip = RawDocument(
            body = """
                尊敬的 测试乘客先生：
                您好！
                您于2027年03月01日在中国铁路客户服务中心网站(12306.cn)成功购买了2张车票，票款共计160.00元，订单号码 E100000004。所购车票信息如下：
                1.测试乘客，2027年03月03日18:22开，苹果站-香蕉站，C801次列车，4车11F号，二等座，成人票，票价80.0元，电子客票。
                2.测试乘客，2027年03月04日18:10开，香蕉站-苹果站，C802次列车，13车7F号，二等座，成人票，票价80.0元，检票口检票口1，电子客票。
            """.trimIndent(),
            receivedAtEpochMillis = 1,
        )
        val partialRefund = RawDocument(
            body = """
                尊敬的 测试乘客先生：
                您好！
                您于2027年03月02日在中国铁路客户服务中心网站(12306.cn)成功办理了退票业务，订单号码 E100000004，应退票款64.00元，所退车票信息如下：
                测试乘客，2027年03月03日18:22开，苹果站-香蕉站，C801次列车，4车11F号，二等座，票价80.0元，退票费16.0元，应退票款64.0元。
            """.trimIndent(),
            receivedAtEpochMillis = 2,
        )
        val purchaseBeforeChange = RawDocument(body = purchaseEmail("E100000005"), receivedAtEpochMillis = 3)
        val reschedule = RawDocument(
            body = """
                尊敬的 测试乘客先生：
                您好！
                您于2027年02月16日在中国铁路客户服务中心网站(12306.cn)成功改签车票1张，新车票票款共计120.00元，属等价改签,无支付和退款手续。订单号码 E100000005。改签后的车票信息如下：
                1.测试乘客，2027年02月21日15:10开，苹果站-香蕉站，G8006次列车，5车9A号，二等座，成人票，票价120.0元，电子客票。
            """.trimIndent(),
            receivedAtEpochMillis = 4,
        )

        val result = assertIs<ParseResult.Success>(
            ChinaRailwayEmailParser().parseAll(
                listOf(reschedule, partialRefund, purchaseBeforeChange, purchaseRoundTrip),
            ),
        )

        val roundTrip = result.documents.filter { it.reservation.reference == "E100000004" }
        assertEquals(2, roundTrip.size)
        assertEquals(TravelDocumentStatus.REFUNDED, roundTrip.single { it.segments.single().serviceNumber == "C801" }.status)
        assertEquals(TravelDocumentStatus.CONFIRMED, roundTrip.single { it.segments.single().serviceNumber == "C802" }.status)

        val baseline = assertIs<ParseResult.Success>(
            ChinaRailwayEmailParser().parseAll(listOf(purchaseRoundTrip)),
        ).documents
        val incrementalRefund = assertIs<ParseResult.Success>(
            ChinaRailwayEmailParser().parseAll(
                documents = listOf(partialRefund),
                baselineDocuments = baseline,
            ),
        ).documents
        assertEquals(
            TravelDocumentStatus.REFUNDED,
            incrementalRefund.single { it.segments.single().serviceNumber == "C801" }.status,
        )
        assertEquals(
            TravelDocumentStatus.CONFIRMED,
            incrementalRefund.single { it.segments.single().serviceNumber == "C802" }.status,
        )

        val changed = result.documents.filter { it.reservation.reference == "E100000005" }
        assertEquals(2, changed.size)
        assertEquals(TravelDocumentStatus.RESCHEDULED, changed.single { it.segments.single().serviceNumber == "G8001" }.status)
        assertEquals(TravelDocumentStatus.CONFIRMED, changed.single { it.segments.single().serviceNumber == "G8006" }.status)
        assertTrue(result.warnings.none { "未找到唯一" in it })
    }

    private fun purchaseEmail(order: String = "E100000001"): String = """
        尊敬的 测试乘客先生：
        您好！
        您于2027年02月15日在中国铁路客户服务中心网站(12306.cn)成功购买了1张车票，票款共计120.00元，订单号码 $order。所购车票信息如下：
        1.测试乘客，2027年02月21日13:10开，苹果站-香蕉站，G8001次列车，2车17C号，二等座，成人票，票价120.0元，电子客票。
    """.trimIndent()
}
