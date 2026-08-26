package com.neko7ina.wallet.assistant.core.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

fun TravelDocument.stableId(): String {
    val source = buildString {
        append(provider.code)
        append('\u0000')
        append(reservation.reference)
        journeyKey?.let {
            append('\u0000')
            append(it)
        }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
