package com.neko7ina.wallet.assistant.core.parser

fun normalizeOcrTextForStructuredParsing(text: String): String = text
    .replace('\u00A0', ' ')
    .replace('：', ':')
    .replace(OCR_WHITESPACE_REGEX, "")

private val OCR_WHITESPACE_REGEX = Regex("\\s+")
