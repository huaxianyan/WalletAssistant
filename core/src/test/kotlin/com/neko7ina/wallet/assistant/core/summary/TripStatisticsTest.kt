package com.neko7ina.wallet.assistant.core.summary

import com.neko7ina.wallet.assistant.core.model.Location
import com.neko7ina.wallet.assistant.core.model.Money
import com.neko7ina.wallet.assistant.core.model.ProviderInfo
import com.neko7ina.wallet.assistant.core.model.Reservation
import com.neko7ina.wallet.assistant.core.model.SeatAssignment
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelDocumentStatus
import com.neko7ina.wallet.assistant.core.model.TravelDocumentType
import com.neko7ina.wallet.assistant.core.model.TravelSegment
import com.neko7ina.wallet.assistant.core.model.Traveler
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripStatisticsTest {
    @Test
    fun `没有行程时返回空汇总`() {
        val summary = TripStatistics.summarize(emptyList(), now = NOW)

        assertEquals(TravelSummary.Empty, summary)
        assertFalse(summary.hasTrips)
    }

    @Test
    fun `尚未出发的行程不计入出行次数`() {
        val summary = TripStatistics.summarize(
            listOf(
                trip("E1", "北京南", "上海虹桥", at(2026, 9, 20, 8, 0)),
                trip("E2", "北京南", "上海虹桥", at(2026, 9, 14, 18, 0)),
            ),
            now = NOW,
        )

        assertEquals(0, summary.totalTrips)
    }

    @Test
    fun `到达出发时刻的行程计入出行次数`() {
        val summary = TripStatistics.summarize(
            listOf(trip("E1", "北京南", "上海虹桥", at(2026, 9, 14, 16, 0))),
            now = NOW,
        )

        assertEquals(1, summary.totalTrips)
    }

    @Test
    fun `改签保留的原行程和退票行程都不计入`() {
        val summary = TripStatistics.summarize(
            listOf(
                trip("E1", "北京南", "上海虹桥", at(2026, 9, 10, 8, 0)),
                trip(
                    "E2",
                    "北京南",
                    "上海虹桥",
                    at(2026, 9, 10, 8, 0),
                    status = TravelDocumentStatus.RESCHEDULED,
                ),
                trip(
                    "E3",
                    "北京南",
                    "上海虹桥",
                    at(2026, 9, 10, 8, 0),
                    status = TravelDocumentStatus.REFUNDED,
                ),
            ),
            now = NOW,
        )

        assertEquals(1, summary.totalTrips)
    }

    @Test
    fun `分别统计今年的出行和本月的出行`() {
        val summary = TripStatistics.summarize(
            listOf(
                trip("E1", "北京南", "上海虹桥", at(2026, 9, 1, 8, 0)),
                trip("E2", "上海虹桥", "北京南", at(2026, 9, 12, 8, 0)),
                trip("E3", "北京南", "杭州东", at(2026, 3, 10, 8, 0)),
                trip("E4", "杭州东", "北京南", at(2025, 12, 31, 8, 0)),
            ),
            now = NOW,
        )

        assertEquals(4, summary.totalTrips)
        assertEquals(3, summary.tripsThisYear)
        assertEquals(2, summary.tripsThisMonth)
    }

    @Test
    fun `年月边界按中国时区划分而不是设备时区`() {
        val summary = TripStatistics.summarize(
            listOf(trip("E1", "北京南", "上海虹桥", at(2026, 9, 1, 7, 0))),
            now = NOW,
        )

        val utcSummary = TripStatistics.summarize(
            listOf(trip("E1", "北京南", "上海虹桥", at(2026, 9, 1, 7, 0))),
            now = NOW,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(1, summary.tripsThisMonth)
        assertEquals(0, utcSummary.tripsThisMonth)
        assertEquals(1, summary.totalTrips)
        assertEquals(1, utcSummary.totalTrips)
    }

    @Test
    fun `常坐线路按次数降序排序并截断到指定条数`() {
        val summary = TripStatistics.summarize(
            listOf(
                trip("E1", "北京南", "上海虹桥", at(2026, 1, 1, 8, 0)),
                trip("E2", "北京南", "上海虹桥", at(2026, 2, 1, 8, 0)),
                trip("E3", "北京南", "上海虹桥", at(2026, 3, 1, 8, 0)),
                trip("E4", "上海虹桥", "杭州东", at(2026, 4, 1, 8, 0)),
                trip("E5", "上海虹桥", "杭州东", at(2026, 5, 1, 8, 0)),
                trip("E6", "广州南", "深圳北", at(2026, 6, 1, 8, 0)),
            ),
            now = NOW,
            topRouteCount = 2,
        )

        assertEquals(
            listOf(
                RouteStat("北京南站", "上海虹桥站", 3),
                RouteStat("上海虹桥站", "杭州东站", 2),
            ),
            summary.topRoutes,
        )
    }

    @Test
    fun `次数相同的线路按站名稳定排序`() {
        val summary = TripStatistics.summarize(
            listOf(
                trip("E1", "杭州东", "北京南", at(2026, 1, 1, 8, 0)),
                trip("E2", "北京南", "上海虹桥", at(2026, 2, 1, 8, 0)),
            ),
            now = NOW,
            topRouteCount = 2,
        )

        assertEquals(
            listOf(
                RouteStat("北京南站", "上海虹桥站", 1),
                RouteStat("杭州东站", "北京南站", 1),
            ),
            summary.topRoutes,
        )
    }

    @Test
    fun `站名带不带「站」的同一线路合并统计`() {
        val summary = TripStatistics.summarize(
            listOf(
                trip("E1", "镇江", "上海", at(2019, 5, 1, 8, 0)),
                trip("E2", "镇江站", "上海站", at(2026, 5, 1, 8, 0)),
            ),
            now = NOW,
        )

        assertEquals(2, summary.totalTrips)
        assertEquals(listOf(RouteStat("镇江站", "上海站", 2)), summary.topRoutes)
        assertEquals(2, summary.visitedStationCount)
    }

    @Test
    fun `多段行程的首末站名都走归一`() {
        val document = trip(
            "E1",
            "北京南",
            "济南西",
            at(2026, 1, 1, 8, 0),
        ).let { base ->
            base.copy(
                segments = listOf(
                    base.segments.single(),
                    base.segments.single().copy(
                        origin = Location("济南西站"),
                        destination = Location("上海虹桥"),
                        departureTime = at(2026, 1, 1, 10, 30),
                    ),
                ),
            )
        }

        val summary = TripStatistics.summarize(listOf(document), now = NOW)

        assertEquals(1, summary.totalTrips)
        assertEquals(listOf(RouteStat("北京南站", "上海虹桥站", 1)), summary.topRoutes)
        // 只统计首段起点和末段终点，中间换乘站不计入走过车站数。
        assertEquals(2, summary.visitedStationCount)
    }

    @Test
    fun `走过车站数按去重后的站点数统计`() {
        val summary = TripStatistics.summarize(
            listOf(
                trip("E1", "北京南", "上海虹桥", at(2026, 1, 1, 8, 0)),
                trip("E2", "上海虹桥", "北京南", at(2026, 2, 1, 8, 0)),
            ),
            now = NOW,
        )

        assertEquals(2, summary.visitedStationCount)
        assertTrue(summary.hasTrips)
    }

    @Test
    fun `多段行程按首段起点和末段终点归并线路`() {
        val document = trip(
            "E1",
            "北京南",
            "济南西",
            at(2026, 1, 1, 8, 0),
        ).let { base ->
            base.copy(
                segments = listOf(
                    base.segments.single(),
                    base.segments.single().copy(
                        origin = Location("济南西"),
                        destination = Location("上海虹桥"),
                        departureTime = at(2026, 1, 1, 10, 30),
                    ),
                ),
            )
        }

        val summary = TripStatistics.summarize(listOf(document), now = NOW)

        assertEquals(1, summary.totalTrips)
        assertEquals(listOf(RouteStat("北京南站", "上海虹桥站", 1)), summary.topRoutes)
    }

    @Test
    fun `缺少区段的行程被跳过而不是抛出异常`() {
        val summary = TripStatistics.summarize(
            listOf(trip("E1", "北京南", "上海虹桥", at(2026, 1, 1, 8, 0)).copy(segments = emptyList())),
            now = NOW,
        )

        assertEquals(TravelSummary.Empty, summary)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, TripStatistics.CHINA_TIME_ZONE)

    private fun trip(
        reference: String,
        origin: String,
        destination: String,
        departure: ZonedDateTime,
        status: TravelDocumentStatus = TravelDocumentStatus.CONFIRMED,
    ): TravelDocument = TravelDocument(
        type = TravelDocumentType.RAIL,
        provider = ProviderInfo(code = "china-railway", name = "中国铁路 12306"),
        reservation = Reservation(
            reference = reference,
            purchasedOn = null,
            totalPrice = Money(BigDecimal("120.00"), "CNY"),
        ),
        travelers = listOf(Traveler(id = "traveler-1", name = "测试乘客")),
        segments = listOf(
            TravelSegment(
                origin = Location(origin),
                destination = Location(destination),
                departureTime = departure,
                arrivalTime = null,
                serviceNumber = "G8001",
                seatAssignments = listOf(
                    SeatAssignment(
                        travelerId = "traveler-1",
                        section = "2",
                        seat = "17C",
                        category = "二等座",
                    ),
                ),
                attributes = emptyMap(),
            ),
        ),
        status = status,
    )

    private companion object {
        val NOW: Instant = ZonedDateTime
            .of(2026, 9, 14, 16, 0, 0, 0, TripStatistics.CHINA_TIME_ZONE)
            .toInstant()
    }
}
