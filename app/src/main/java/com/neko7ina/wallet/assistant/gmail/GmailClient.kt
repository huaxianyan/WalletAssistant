package com.neko7ina.wallet.assistant.gmail

import android.text.Html
import com.neko7ina.wallet.assistant.core.parser.RawDocument
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GmailClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchMessages(
        accessToken: String,
        query: String,
        maxResults: Int = 10,
    ): List<RawDocument> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val listResponse = get<ListMessagesResponse>(
            url = "https://gmail.googleapis.com/gmail/v1/users/me/messages" +
                "?maxResults=$maxResults&q=$encodedQuery",
            accessToken = accessToken,
        )

        listResponse.messages.mapNotNull { reference ->
            val message = get<GmailMessage>(
                url = "https://gmail.googleapis.com/gmail/v1/users/me/messages/${reference.id}?format=full",
                accessToken = accessToken,
            )
            message.toRawDocument()
        }
    }

    private inline fun <reified T> get(url: String, accessToken: String): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw GmailAccessException(
                    when (responseCode) {
                        HttpURLConnection.HTTP_UNAUTHORIZED ->
                            "Gmail 授权已过期，请重新授权后再试。"

                        HttpURLConnection.HTTP_FORBIDDEN ->
                            "Gmail 尚未允许读取邮件，请重新授权或改用粘贴邮件正文。"

                        else -> "暂时无法读取 Gmail，请检查网络后重试。"
                    },
                )
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun GmailMessage.toRawDocument(): RawDocument? {
        val plainText = payload.findBody("text/plain")
        val htmlText = payload.findBody("text/html")?.let {
            Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString()
        }
        val body = plainText ?: htmlText ?: return null
        return RawDocument(
            subject = payload.header("Subject"),
            sender = payload.header("From"),
            body = body,
        )
    }

    private fun MessagePart.findBody(targetMimeType: String): String? {
        if (mimeType.equals(targetMimeType, ignoreCase = true)) {
            body.data?.decodeBase64Url()?.let { return it }
        }
        return parts.firstNotNullOfOrNull { it.findBody(targetMimeType) }
    }

    private fun MessagePart.header(name: String): String? =
        headers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

    private fun String.decodeBase64Url(): String? = runCatching {
        String(Base64.getUrlDecoder().decode(this), StandardCharsets.UTF_8)
    }.getOrNull()
}

class GmailAccessException(message: String) : Exception(message)

@Serializable
private data class ListMessagesResponse(
    val messages: List<MessageReference> = emptyList(),
)

@Serializable
private data class MessageReference(
    val id: String,
)

@Serializable
private data class GmailMessage(
    val payload: MessagePart,
)

@Serializable
private data class MessagePart(
    @SerialName("mimeType") val mimeType: String = "",
    val headers: List<MessageHeader> = emptyList(),
    val body: MessageBody = MessageBody(),
    val parts: List<MessagePart> = emptyList(),
)

@Serializable
private data class MessageHeader(
    val name: String,
    val value: String,
)

@Serializable
private data class MessageBody(
    val data: String? = null,
)
