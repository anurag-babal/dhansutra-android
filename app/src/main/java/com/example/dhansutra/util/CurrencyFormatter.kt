package com.example.dhansutra.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** Formats paise amounts as Indian-rupee strings with lakh/crore digit grouping. */
object CurrencyFormatter {
    private const val SYMBOL = "₹"

    /** e.g. 12345600 -> "₹1,23,456.00"; negatives -> "-₹1,234.00". */
    fun format(paise: Long, showDecimals: Boolean = true): String {
        val sign = if (paise < 0) "-" else ""
        val abs = abs(paise)
        val rupees = abs / 100
        val fraction = abs % 100
        val grouped = groupIndian(rupees.toString())
        return if (showDecimals) "$sign$SYMBOL$grouped.${fraction.toString().padStart(2, '0')}" else "$sign$SYMBOL$grouped"
    }

    /** Compact for chart axes: 1250000 paise -> "₹12.5K"; 1.2 lakh -> "₹1.2L"; crore -> "₹1.2Cr". */
    fun compact(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val rupees = BigDecimal(abs(paise)).divide(BigDecimal(100))
        val (value, suffix) = when {
            rupees >= BigDecimal(10_000_000) -> rupees.divide(BigDecimal(10_000_000)) to "Cr"
            rupees >= BigDecimal(100_000) -> rupees.divide(BigDecimal(100_000)) to "L"
            rupees >= BigDecimal(1_000) -> rupees.divide(BigDecimal(1_000)) to "K"
            else -> rupees to ""
        }
        val text = value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return "$sign$SYMBOL$text$suffix"
    }

    /** Parses user input like "1,250.50" into paise; null if invalid or not positive. */
    fun parseToPaise(input: String): Long? {
        val cleaned = input.replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.scale() > 2 && value.stripTrailingZeros().scale() > 2) return null
        val paise = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        return if (paise.signum() > 0 && paise.toBigInteger().bitLength() < 63) paise.toLong() else null
    }

    /** Paise to plain editable text, e.g. 125050 -> "1250.5"; 100000 -> "1000". */
    fun toInputText(paise: Long): String =
        BigDecimal(paise).movePointLeft(2).stripTrailingZeros().toPlainString()

    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$rest,$last3"
    }
}
