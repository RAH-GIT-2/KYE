package com.kye.app.domain

import java.time.LocalDate
import java.time.MonthDay

/** A festival or national day shown in Home's greeting, with its own sky colours. */
data class Festival(val greeting: String, val colors: List<Long>)

/** Home's header sky: changes through the day, and dresses up on festival days. */
object Sky {

    enum class Time(val emoji: String, val greet: String, val colors: List<Long>) {
        DAWN("🌅", "Good morning", listOf(0xFFFF9A76, 0xFFFFD3A5)),
        DAY("☀️", "Good afternoon", listOf(0xFF4FACFE, 0xFFA1D8FF)),
        DUSK("🌇", "Good evening", listOf(0xFF8E7CFF, 0xFFFD79A8)),
        NIGHT("🌙", "Good night", listOf(0xFF1F2A5C, 0xFF5B4BDB))
    }

    fun time(hour: Int): Time = when (hour) {
        in 5..10 -> Time.DAWN
        in 11..16 -> Time.DAY
        in 17..20 -> Time.DUSK
        else -> Time.NIGHT
    }

    /** Morning until noon still says "Good morning" even once the sun is up. */
    fun greeting(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }

    private val SAFFRON_FLAG = listOf(0xFFFF9933, 0xFFFFE0B2, 0xFF7CC576)
    private val DIYA = listOf(0xFFFF7A00, 0xFFFFC94D)
    private val HOLI = listOf(0xFFFD79A8, 0xFF74B9FF, 0xFF55EFC4)

    /** Same date every year. */
    private val fixed = mapOf(
        MonthDay.of(1, 1) to Festival("Happy New Year 🎉", listOf(0xFF6C5CE7, 0xFFFD79A8)),
        MonthDay.of(1, 14) to Festival("Happy Pongal 🌾", listOf(0xFFFDCB6E, 0xFF55EFC4)),
        MonthDay.of(1, 15) to Festival("Happy Pongal 🌾", listOf(0xFFFDCB6E, 0xFF55EFC4)),
        MonthDay.of(1, 26) to Festival("Happy Republic Day 🇮🇳", SAFFRON_FLAG),
        MonthDay.of(4, 14) to Festival("Happy Puthandu 🌼", listOf(0xFFFFC048, 0xFFFF7675)),
        MonthDay.of(8, 15) to Festival("Happy Independence Day 🇮🇳", SAFFRON_FLAG),
        MonthDay.of(12, 25) to Festival("Merry Christmas 🎄", listOf(0xFFD63031, 0xFF00B894))
    )

    /** Moon-calendar festivals: the dates move, so they're listed per year up to 2028 (the eve counts too for Deepavali). */
    private val moving: Map<LocalDate, Festival> = buildMap {
        listOf("2026-11-08", "2027-10-29", "2028-10-17").forEach {
            val d = LocalDate.parse(it)
            put(d, Festival("Happy Deepavali 🪔", DIYA))
            put(d.minusDays(1), Festival("Happy Deepavali 🪔", DIYA))
        }
        listOf("2026-03-04", "2027-03-22", "2028-03-11").forEach {
            put(LocalDate.parse(it), Festival("Happy Holi 🎨", HOLI))
        }
    }

    fun festival(date: LocalDate): Festival? = moving[date] ?: fixed[MonthDay.from(date)]
}
