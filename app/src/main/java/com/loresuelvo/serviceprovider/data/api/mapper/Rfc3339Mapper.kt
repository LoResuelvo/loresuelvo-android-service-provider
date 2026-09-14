package com.loresuelvo.serviceprovider.data.api.mapper

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

internal fun String.toEpochMillis(): Long {
    val match = RFC3339_PATTERN.matchEntire(this)
        ?: throw IllegalArgumentException("Invalid RFC3339 timestamp")
    val fraction = match.groupValues[2]
        .padEnd(3, '0')
        .take(3)
    val offset = match.groupValues[3]
        .replace(":", "")
        .let { if (it == "Z") "+0000" else it }
    val normalized = "${match.groupValues[1]}.$fraction$offset"
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply {
        isLenient = false
    }
    val position = ParsePosition(0)
    val date = parser.parse(normalized, position)
    if (date == null || position.index != normalized.length) {
        throw IllegalArgumentException("Invalid RFC3339 timestamp")
    }
    return date.time
}

private val RFC3339_PATTERN = Regex("^(.*?)(?:\\.(\\d+))?(Z|[+-]\\d{2}:?\\d{2})$")
