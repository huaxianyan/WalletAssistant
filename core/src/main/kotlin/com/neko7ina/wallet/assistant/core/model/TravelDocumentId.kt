package com.neko7ina.wallet.assistant.core.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

fun TravelDocument.stableId(): String {
    val source = "${provider.code}\u0000${reservation.reference}"
    return MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
