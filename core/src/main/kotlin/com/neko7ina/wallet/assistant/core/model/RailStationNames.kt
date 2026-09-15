package com.neko7ina.wallet.assistant.core.model

/**
 * 12306 对车站名的写法在 2020 年前后变了：老邮件不写「站」（如「镇江」），
 * 新邮件写（如「镇江站」），指的是同一个车站。
 *
 * 这里统一**去掉**末尾的「站」。卡片上的字段本来就写明「出发站 / 目的站」，
 * 名字里再带一个「站」字是重复的；去掉之后与历史数据的主流写法也一致。
 * 只去掉一个后缀，不猜站名，也不动名字中间已有的字。
 *
 * ## 只用于展示和统计，不要用于解析或读取，更不要写回 `Location.name`
 *
 * `TravelDocument.stableId()` 由 `journeyKey` 派生，而 `journeyKey` 里含起终点名。
 * 更要紧的是 `MutableOrder.fromDocuments()` 会拿**已保存行程的 `Location.name`**
 * 反过来重算 `journeyKey`（增量同步每次都走这条路），算完还会落库。
 *
 * 所以只要在解析层或反序列化时改动 `Location.name`，`journeyKey` 就会变，
 * `stableId()` 跟着变，设备上已有的行程主键会全部失配 —— 下次同步变成重复插入，
 * 发车提醒的 Alarm、自动归档任务和 Google Wallet pass 的 objectId 也会一起孤立。
 *
 * 结论：**存储保留邮件原文，归一化只发生在展示层和统计层。** 换言之，
 * [normalize] 的结果只能拿去渲染，不能回写进模型。
 */
object RailStationNames {
    private const val SUFFIX = "站"

    /**
     * 去掉车站名末尾的「站」。本来就不带「站」的原样返回，所以可以重复调用。
     *
     * 只去一个后缀，不猜站名；名字本身就是「站」时保留原文，不返回空串。
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val stripped = trimmed.removeSuffix(SUFFIX)
        return stripped.ifEmpty { trimmed }
    }
}

/** 铁路车站的展示名。仅适用于铁路行程，航空等其他来源不要直接复用。 */
val Location.railStationName: String
    get() = RailStationNames.normalize(name)

/** 「起点 → 终点」的展示文案，两端都过一遍站名归一。 */
val TravelSegment.railRoute: String
    get() = "${origin.railStationName} → ${destination.railStationName}"
