package com.neko7ina.wallet.assistant

import com.neko7ina.wallet.assistant.core.model.TravelDocument
import java.time.Instant

internal fun TravelDocument.hasDeparted(now: Instant = Instant.now()): Boolean =
    segments.minOf { it.departureTime.toInstant() } <= now
