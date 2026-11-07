package com.kye.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val dayYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
    private val monthShortFmt = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endOfDay(d: LocalDate): Long = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    fun toLocalDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun toLocalTime(millis: Long): LocalTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    fun atMillis(d: LocalDate, t: LocalTime): Long = d.atTime(t).atZone(zone).toInstant().toEpochMilli()

    /** Material date pickers work in UTC midnight millis. */
    fun toPickerMillis(d: LocalDate): Long = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun fromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    fun label(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> if (d.year == today.year) d.format(dayFmt) else d.format(dayYearFmt)
    }

    fun short(d: LocalDate): String = d.format(shortFmt)
    fun monthName(d: LocalDate): String = d.format(monthFmt)
    fun monthShort(d: LocalDate): String = d.format(monthShortFmt)

    /** Indian financial year starts on 1 April. */
    fun fyStart(today: LocalDate): LocalDate =
        if (today.monthValue >= 4) LocalDate.of(today.year, 4, 1) else LocalDate.of(today.year - 1, 4, 1)
}
