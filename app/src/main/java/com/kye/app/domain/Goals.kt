package com.kye.app.domain

import com.kye.app.data.Goal
import com.kye.app.data.GoalRow
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Savings goal maths. Pure Kotlin, unit-tested. */
object Goals {

    fun isReached(row: GoalRow): Boolean = row.progress >= row.goal.targetPaise

    fun remaining(row: GoalRow): Long = (row.goal.targetPaise - row.progress).coerceAtLeast(0)

    fun percent(row: GoalRow): Int =
        if (row.goal.targetPaise <= 0) 0 else ((row.progress * 100) / row.goal.targetPaise).toInt().coerceIn(0, 100)

    /**
     * What to put aside each month to reach the target by its date: what's left divided by the
     * months left, counting the current month. Null without a date, once reached, or after the date.
     */
    fun monthlyNeeded(row: GoalRow, today: LocalDate): Long? {
        val date = row.goal.targetDate?.let { LocalDate.ofEpochDay(it) } ?: return null
        val left = remaining(row)
        if (left <= 0 || date.isBefore(today)) return null
        val months = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), date.withDayOfMonth(1)) + 1
        return (left + months - 1) / months
    }

    fun isOverdue(row: GoalRow, today: LocalDate): Boolean =
        !row.goal.isClosed && !isReached(row) && row.goal.targetDate?.let { it < today.toEpochDay() } == true

    /** "saved 5000 for goa" → the open goal named in the text (4+ letter words of its name). */
    fun mentioned(text: String, goals: List<Goal>): Goal? {
        val words = text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.toSet()
        return goals.filter { !it.isClosed }.firstOrNull { g ->
            val keys = g.name.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length >= 3 && it !in setOf("the", "and", "for", "new", "fund", "goal") && it.toIntOrNull() == null }
            keys.isNotEmpty() && keys.any { k -> words.any { w -> w == k || (w.startsWith(k) && w.length - k.length <= 3) } }
        }
    }
}

/** When to remind about a warranty. Pure Kotlin, unit-tested. */
object Warranties {
    const val NOTICE_DAYS = 30L

    /**
     * The reminder stage due today: 1 = "ends in 30 days", 2 = "ends today / has ended", or null
     * if nothing new. A warranty already past its date when first checked goes straight to 2.
     */
    fun dueStage(untilDate: Long, sentStage: Int, today: LocalDate): Int? {
        val t = today.toEpochDay()
        return when {
            sentStage < 2 && untilDate <= t -> 2
            sentStage < 1 && untilDate - NOTICE_DAYS <= t -> 1
            else -> null
        }
    }

    fun isCovered(untilDate: Long, today: LocalDate): Boolean = untilDate >= today.toEpochDay()

    /** Common choices on the + screen: years from the purchase date. */
    fun until(purchase: LocalDate, years: Int): LocalDate = purchase.plusYears(years.toLong()).minusDays(1)
}
