package com.neko7ina.wallet.assistant.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RailStationNamesTest {
    @Test
    fun `老格式的站名补上「站」`() {
        assertEquals("镇江站", RailStationNames.normalize("镇江"))
        assertEquals("上海站", RailStationNames.normalize("上海"))
        assertEquals("上海虹桥站", RailStationNames.normalize("上海虹桥"))
        assertEquals("北京南站", RailStationNames.normalize("北京南"))
    }

    @Test
    fun `已经是新格式的站名原样返回`() {
        assertEquals("镇江站", RailStationNames.normalize("镇江站"))
        assertEquals("上海虹桥站", RailStationNames.normalize("上海虹桥站"))
    }

    @Test
    fun `重复归一得到同一个结果`() {
        val once = RailStationNames.normalize("镇江")

        assertEquals(once, RailStationNames.normalize(once))
    }

    @Test
    fun `去掉首尾空白后再判断是否需要补「站」`() {
        assertEquals("镇江站", RailStationNames.normalize("  镇江 "))
        assertEquals("镇江站", RailStationNames.normalize("\t镇江\n"))
        assertEquals("镇江站", RailStationNames.normalize("镇江站 "))
    }

    @Test
    fun `空站名原样返回而不是变成单独一个「站」`() {
        assertEquals("", RailStationNames.normalize(""))
        assertEquals("", RailStationNames.normalize("   "))
    }

    @Test
    fun `只补末尾的后缀不动站名中间已有的字`() {
        assertEquals("上下站台站", RailStationNames.normalize("上下站台"))
    }

    @Test
    fun `站名扩展属性都走归一`() {
        val segment = TravelSegment(
            origin = Location("镇江"),
            destination = Location("上海站"),
            departureTime = java.time.ZonedDateTime.parse("2026-09-14T08:00:00+08:00"),
            arrivalTime = null,
            serviceNumber = "G8001",
            seatAssignments = emptyList(),
            attributes = emptyMap(),
        )

        assertEquals("镇江站", segment.origin.railStationName)
        assertEquals("上海站", segment.destination.railStationName)
        assertEquals("镇江站 → 上海站", segment.railRoute)
    }
}
