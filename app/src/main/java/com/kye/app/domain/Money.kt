package com.kye.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** All money is whole paise in a Long. Formatting follows the Indian grouping (₹1,25,000). */
object Money {
    fun format(paise: Long, showPaise: Boolean = false): String {
        val sign = if (paise < 0) "-" else ""
        val a = abs(paise)
        val rupees = groupIndian(a / 100)
        val p = a % 100
        val body = if (showPaise && p != 0L) "$rupees.${p.toString().padStart(2, '0')}" else rupees
        return "$sign₹$body"
    }

    fun groupIndian(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }

    /** Compact form for charts: ₹950, ₹45K, ₹1.2L, ₹2Cr. */
    fun short(paise: Long): String {
        val r = abs(paise) / 100.0
        val sign = if (paise < 0) "-" else ""
        val body = when {
            r >= 1_00_00_000 -> trim(r / 1_00_00_000) + "Cr"
            r >= 1_00_000 -> trim(r / 1_00_000) + "L"
            r >= 1_000 -> trim(r / 1_000) + "K"
            else -> r.toLong().toString()
        }
        return "$sign₹$body"
    }

    private fun trim(v: Double): String {
        val x = (v * 10).toLong() / 10.0
        return if (x % 1.0 == 0.0) x.toLong().toString() else x.toString()
    }

    /** "450", "1,200.50" → paise. Null when the text is not a number. */
    fun parseToPaise(text: String): Long? {
        val clean = text.replace(",", "").trim()
        if (clean.isEmpty()) return null
        val d = clean.toBigDecimalOrNull() ?: return null
        return d.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** Paise → plain editable text without grouping: 45000 → "450", 45050 → "450.5". */
    fun plain(paise: Long): String =
        BigDecimal(paise).movePointLeft(2).stripTrailingZeros().toPlainString()
}
