package com.neko7ina.wallet.assistant.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RailStationNamesTest {
    @Test
    fun `新格式的站名去掉末尾的「站」`() {
        assertEquals("镇江", RailStationNames.normalize("镇江站"))
        assertEquals("上海", RailStationNames.normalize("上海站"))
        assertEquals("上海虹桥", RailStationNames.normalize("上海虹桥站"))
        assertEquals("北京南", RailStationNames.normalize("北京南站"))
    }

    @Test
    fun `本来就不带「站」的站名原样返回`() {
        assertEquals("镇江", RailStationNames.normalize("镇江"))
        assertEquals("上海虹桥", RailStationNames.normalize("上海虹桥"))
    }

    @Test
    fun `重复归一得到同一个结果`() {
        val once = RailStationNames.normalize("镇江站")

        assertEquals("镇江", once)
        assertEquals(once, RailStationNames.normalize(once))
    }

    @Test
    fun `去掉首尾空白后再判断是否需要去「站」`() {
        assertEquals("镇江", RailStationNames.normalize("  镇江站 "))
        assertEquals("镇江", RailStationNames.normalize("\t镇江站\n"))
        assertEquals("镇江", RailStationNames.normalize("镇江站 "))
    }

    @Test
    fun `空站名原样返回`() {
        assertEquals("", RailStationNames.normalize(""))
        assertEquals("", RailStationNames.normalize("   "))
    }

    @Test
    fun `站名本身就写作「站」时保留原文而不是返回空串`() {
        assertEquals("站", RailStationNames.normalize("站"))
        assertEquals("站", RailStationNames.normalize(" 站 "))
    }

    @Test
    fun `只去末尾的后缀不动站名中间已有的字`() {
        assertEquals("上下站台", RailStationNames.normalize("上下站台站"))
        assertEquals("上下站台", RailStationNames.normalize("上下站台"))
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

        assertEquals("镇江", segment.origin.railStationName)
        assertEquals("上海", segment.destination.railStationName)
        assertEquals("镇江 → 上海", segment.railRoute)
    }
}
