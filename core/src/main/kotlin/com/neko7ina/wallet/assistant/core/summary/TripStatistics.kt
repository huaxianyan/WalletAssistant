package com.neko7ina.wallet.assistant.core.summary

import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelDocumentStatus
import com.neko7ina.wallet.assistant.core.model.railStationName
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** 一条线路的累计出行次数。 */
data class RouteStat(
    val origin: String,
    val destination: String,
    val count: Int,
)

/**
 * 已完成出行的汇总结果。
 *
 * 「已完成」的口径是状态为 [TravelDocumentStatus.CONFIRMED]，且出发时间已经过去。
 * 改签后保留的原行程（`RESCHEDULED`）和退票行程（`REFUNDED`）都不计入，
 * 否则同一张订单会被算成两次出行。
 *
 * 统计只覆盖本地已保存的行程。用户首次邮箱同步时如果选择「仅同步未出发」，
 * 这里会是空的，需要引导用户导入历史行程。
 *
 * 站名统一走 [railStationName]。12306 老邮件不写「站」、新邮件写，不归一的话
 * 同一个车站会被拆成两个，[visitedStationCount] 会偏大、[topRoutes] 也会拆开。
 */
data class TravelSummary(
    val totalTrips: Int,
    val tripsThisYear: Int,
    val tripsThisMonth: Int,
    val visitedStationCount: Int,
    val topRoutes: List<RouteStat>,
) {
    val hasTrips: Boolean get() = totalTrips > 0

    companion object {
        val Empty = TravelSummary(
            totalTrips = 0,
            tripsThisYear = 0,
            tripsThisMonth = 0,
            visitedStationCount = 0,
            topRoutes = emptyList(),
        )
    }
}

/**
 * 从本地行程推导 Dashboard 需要的统计数字。
 *
 * 这里是纯函数，不依赖 Android，可以直接在 `:core:test` 里验证。
 */
object TripStatistics {
    /**
     * 中国铁路时间统一使用 `Asia/Shanghai`。年月的边界也按这个时区划分，
     * 不跟随设备时区，避免用户跨时区后历史统计整体漂移。
     */
    val CHINA_TIME_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    const val DEFAULT_TOP_ROUTE_COUNT = 3

    fun summarize(
        documents: List<TravelDocument>,
        now: Instant = Instant.now(),
        zoneId: ZoneId = CHINA_TIME_ZONE,
        topRouteCount: Int = DEFAULT_TOP_ROUTE_COUNT,
    ): TravelSummary {
        val nowLocal = now.atZone(zoneId)
        val completedTrips = documents.mapNotNull { it.toCompletedTrip(now, zoneId) }

        val topRoutes = completedTrips
            .groupingBy { trip -> trip.origin to trip.destination }
            .eachCount()
            .map { (route, count) -> RouteStat(route.first, route.second, count) }
            .sortedWith(
                compareByDescending<RouteStat> { it.count }
                    .thenBy { it.origin }
                    .thenBy { it.destination },
            )
            .take(topRouteCount.coerceAtLeast(0))

        return TravelSummary(
            totalTrips = completedTrips.size,
            tripsThisYear = completedTrips.count { it.departure.year == nowLocal.year },
            tripsThisMonth = completedTrips.count { trip ->
                trip.departure.year == nowLocal.year &&
                    trip.departure.monthValue == nowLocal.monthValue
            },
            visitedStationCount = completedTrips
                .flatMap { trip -> listOf(trip.origin, trip.destination) }
                .toSet()
                .size,
            topRoutes = topRoutes,
        )
    }

    private fun TravelDocument.toCompletedTrip(now: Instant, zoneId: ZoneId): CompletedTrip? {
        if (status != TravelDocumentStatus.CONFIRMED) return null
        val first = segments.firstOrNull() ?: return null
        val departure = segments.minOf { it.departureTime }.withZoneSameInstant(zoneId)
        if (departure.toInstant() > now) return null
        return CompletedTrip(
            departure = departure,
            origin = first.origin.railStationName,
            destination = segments.last().destination.railStationName,
        )
    }

    private data class CompletedTrip(
        val departure: ZonedDateTime,
        val origin: String,
        val destination: String,
    )
}
