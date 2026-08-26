package com.neko7ina.wallet.assistant.email

import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable
data class ImapAccountConfig(
    val emailAddress: String,
    val username: String,
    val host: String,
    val port: Int,
    val credential: String,
) {
    val fingerprint: String
        get() = MessageDigest.getInstance("SHA-256")
            .digest("$emailAddress|$username|$host|$port".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

data class ImapAccountSummary(
    val emailAddress: String,
    val host: String,
)

@Serializable
data class ImapSyncCheckpoint(
    val folderName: String,
    val uidValidity: Long,
    val lastScannedUid: Long,
)

enum class ImapProviderPreset(
    val displayName: String,
    val host: String?,
) {
    GMAIL("Gmail", "imap.gmail.com"),
    QQ_MAIL("QQ 邮箱", "imap.qq.com"),
    NETEASE_163("163 邮箱", "imap.163.com"),
    NETEASE_126("126 邮箱", "imap.126.com"),
    CUSTOM("自定义", null),
}
