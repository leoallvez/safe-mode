package com.poc.safemode.domain

import java.util.Locale

object CoordinateParser {

    private val decimalPattern = Regex("""[+-]?(\d+([.,]\d*)?|[.,]\d+)""")

    fun parseLatitude(text: String): Double? = parse(text, limit = 90.0)

    fun parseLongitude(text: String): Double? = parse(text, limit = 180.0)

    fun format(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun parse(text: String, limit: Double): Double? {
        val trimmed = text.trim()
        if (!decimalPattern.matches(trimmed)) return null
        val value = trimmed.replace(',', '.').toDoubleOrNull() ?: return null
        return value.takeIf { it in -limit..limit }
    }
}
