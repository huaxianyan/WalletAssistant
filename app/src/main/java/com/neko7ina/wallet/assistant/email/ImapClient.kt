package com.neko7ina.wallet.assistant.email

import android.text.Html
import com.neko7ina.wallet.assistant.core.parser.RawDocument
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.BodyPart
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import com.sun.mail.imap.IMAPFolder
import javax.mail.search.FromStringTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImapClient {
    suspend fun listSelectableFolders(
        config: ImapAccountConfig,
    ): List<ImapFolderOption> = withContext(Dispatchers.IO) {
        connect(config).use { connection ->
            connection.store.defaultFolder.list("*")
                .filter { folder ->
                    runCatching { folder.exists() && folder.type and Folder.HOLDS_MESSAGES != 0 }
                        .getOrDefault(false)
                }
                .map { ImapFolderOption(it.fullName) }
                .distinctBy(ImapFolderOption::fullName)
                .sortedWith(
                    compareBy<ImapFolderOption> {
                        !it.fullName.equals(INBOX_FOLDER, ignoreCase = true)
                    }.thenBy { it.fullName.lowercase() },
                )
        }
    }

    suspend fun searchRailwayMessages(
        config: ImapAccountConfig,
        checkpoint: ImapSyncCheckpoint?,
        onProgress: (ImapSyncProgress) -> Unit = {},
    ): ImapSearchResult = withContext(Dispatchers.IO) {
        onProgress(ImapSyncProgress.Connecting)
        connect(config).use { connection ->
            onProgress(ImapSyncProgress.CheckingMessages)
            val folder = railwaySearchFolder(
                store = connection.store,
                configuredFolderName = config.folderName,
                savedAutomaticFolderName = checkpoint?.folderName,
            )
            folder.open(Folder.READ_ONLY)
            try {
                val uidFolder = folder as? UIDFolder
                    ?: throw ImapAccessException("邮箱服务器不支持可靠的增量同步。")
                val uidValidity = uidFolder.uidValidity
                val uidNext = uidFolder.uidNext
                val highestUid = if (uidNext > 0) {
                    uidNext - 1
                } else if (folder.messageCount > 0) {
                    uidFolder.getUID(folder.getMessage(folder.messageCount)).coerceAtLeast(0)
                } else {
                    0
                }
                val fullScan = checkpoint == null ||
                    checkpoint.uidValidity != uidValidity ||
                    checkpoint.folderName != folder.fullName
                val candidates = if (fullScan) {
                    folder.search(FromStringTerm(CHINA_RAILWAY_SENDER)).also { messages ->
                        folder.fetch(messages, envelopeAndUidProfile())
                    }.filter { uidFolder.getUID(it) <= highestUid }.toTypedArray()
                } else if (highestUid <= checkpoint.lastScannedUid) {
                    emptyArray<Message>()
                } else {
                    uidFolder.getMessagesByUID(
                        checkpoint.lastScannedUid + 1,
                        highestUid,
                    ).filterNotNull().toTypedArray().also { messages ->
                        folder.fetch(messages, envelopeAndUidProfile())
                    }.filter { it.isFromChinaRailway() }.toTypedArray()
                }
                ImapSearchResult(
                    messages = candidates.mapIndexedNotNull { index, message ->
                        onProgress(
                            ImapSyncProgress.ReadingRailwayMessage(
                                completed = index,
                                total = candidates.size,
                            ),
                        )
                        val uid = uidFolder.getUID(message)
                        message.toRawDocument("$uidValidity:$uid")
                    }.also {
                        if (candidates.isNotEmpty()) {
                            onProgress(
                                ImapSyncProgress.ReadingRailwayMessage(
                                    completed = candidates.size,
                                    total = candidates.size,
                                ),
                            )
                        }
                    },
                    nextCheckpoint = ImapSyncCheckpoint(
                        folderName = folder.fullName,
                        uidValidity = uidValidity,
                        lastScannedUid = highestUid,
                    ),
                    fullScan = fullScan,
                )
            } finally {
                folder.close(false)
            }
        }
    }

    private fun envelopeAndUidProfile(): FetchProfile = FetchProfile().apply {
        add(FetchProfile.Item.ENVELOPE)
        add(UIDFolder.FetchProfileItem.UID)
    }

    private fun Message.isFromChinaRailway(): Boolean = from
        ?.filterIsInstance<InternetAddress>()
        ?.any { it.address.equals(CHINA_RAILWAY_SENDER, ignoreCase = true) }
        ?: false

    private fun railwaySearchFolder(
        store: javax.mail.Store,
        configuredFolderName: String?,
        savedAutomaticFolderName: String?,
    ): Folder {
        configuredFolderName?.let { name ->
            val folder = store.getFolder(name)
            if (
                folder.exists() &&
                runCatching { folder.type and Folder.HOLDS_MESSAGES != 0 }.getOrDefault(false)
            ) {
                return folder
            }
            throw ImapAccessException(
                "无法读取已选择的邮箱文件夹，请在邮箱设置中重新选择。",
            )
        }
        savedAutomaticFolderName?.let { name ->
            store.getFolder(name).takeIf { it.exists() }?.let { return it }
        }
        val allMailFolder = runCatching {
            store.defaultFolder.list("*")
                .filterIsInstance<IMAPFolder>()
                .firstOrNull { folder ->
                    folder.attributes.any { it.equals(ALL_MAIL_ATTRIBUTE, ignoreCase = true) }
                }
        }.getOrNull()
        return allMailFolder ?: store.getFolder(INBOX_FOLDER)
    }

    private fun connect(config: ImapAccountConfig): ImapConnection {
        val properties = Properties().apply {
            setProperty("mail.store.protocol", "imaps")
            setProperty("mail.imaps.ssl.enable", "true")
            setProperty("mail.imaps.ssl.checkserveridentity", "true")
            setProperty("mail.imaps.connectiontimeout", CONNECTION_TIMEOUT_MILLIS.toString())
            setProperty("mail.imaps.timeout", READ_TIMEOUT_MILLIS.toString())
            setProperty("mail.imaps.writetimeout", WRITE_TIMEOUT_MILLIS.toString())
            setProperty("mail.imaps.peek", "true")
        }
        val store = Session.getInstance(properties).getStore("imaps")
        try {
            store.connect(config.host, config.port, config.username, config.credential)
        } catch (error: AuthenticationFailedException) {
            runCatching { store.close() }
            throw ImapAuthenticationException(
                "邮箱验证失败，请检查专用密码或授权码。",
                error,
            )
        } catch (error: Exception) {
            runCatching { store.close() }
            throw ImapAccessException(
                "无法连接邮箱，请检查服务器、邮箱地址和专用密码或授权码。",
                error,
            )
        }
        return ImapConnection(store)
    }

    private fun Message.toRawDocument(sourceId: String): RawDocument? {
        val body = extractText() ?: return null
        val senderAddress = from
            ?.filterIsInstance<InternetAddress>()
            ?.firstOrNull()
            ?.address
        return RawDocument(
            sourceId = sourceId,
            subject = subject,
            sender = senderAddress,
            body = body,
            receivedAtEpochMillis = receivedDate?.time ?: sentDate?.time,
        )
    }

    private fun javax.mail.Part.extractText(): String? {
        if (isMimeType("text/plain")) return content as? String
        if (isMimeType("text/html")) {
            val html = content as? String ?: return null
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
        }
        if (!isMimeType("multipart/*")) return null
        val multipart = content as? Multipart ?: return null
        val parts = (0 until multipart.count).map { multipart.getBodyPart(it) }
            .filterNot { it.isAttachment() }
        return parts.firstNotNullOfOrNull { part ->
            part.takeIf { it.isMimeType("text/plain") }?.extractText()
        } ?: parts.firstNotNullOfOrNull { it.extractText() }
    }

    private fun BodyPart.isAttachment(): Boolean =
        disposition.equals(javax.mail.Part.ATTACHMENT, ignoreCase = true)

    private class ImapConnection(val store: javax.mail.Store) : AutoCloseable {
        override fun close() {
            if (store.isConnected) store.close()
        }
    }

    private companion object {
        const val INBOX_FOLDER = "INBOX"
        const val ALL_MAIL_ATTRIBUTE = "\\All"
        const val CHINA_RAILWAY_SENDER = "12306@rails.com.cn"
        const val CONNECTION_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val WRITE_TIMEOUT_MILLIS = 30_000
    }
}

sealed interface ImapSyncProgress {
    data object Connecting : ImapSyncProgress
    data object CheckingMessages : ImapSyncProgress
    data class ReadingRailwayMessage(val completed: Int, val total: Int) : ImapSyncProgress
}

data class ImapSearchResult(
    val messages: List<RawDocument>,
    val nextCheckpoint: ImapSyncCheckpoint,
    val fullScan: Boolean,
)

open class ImapAccessException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ImapAuthenticationException(message: String, cause: Throwable? = null) :
    ImapAccessException(message, cause)
