package com.neko7ina.wallet.assistant.core.parser

import com.neko7ina.wallet.assistant.core.model.TravelDocument

data class RawDocument(
    val subject: String? = null,
    val sender: String? = null,
    val body: String,
)

data class DetectionResult(
    val supported: Boolean,
    val confidence: Float,
)

sealed interface ParseResult {
    data class Success(
        val document: TravelDocument,
        val warnings: List<String> = emptyList(),
    ) : ParseResult

    data class Failure(
        val message: String,
    ) : ParseResult
}

interface TravelDocumentParser {
    val parserId: String
    val version: Int

    fun detect(document: RawDocument): DetectionResult

    fun parse(document: RawDocument): ParseResult
}
