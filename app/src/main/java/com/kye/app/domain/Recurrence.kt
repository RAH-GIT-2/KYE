package com.kye.app.domain

import com.kye.app.data.RecurringRule
import com.kye.app.data.RepeatUnit
import com.kye.app.data.Txn
import java.time.LocalDate

object Recurrence {
    /**
     * The n-th occurrence counted from the anchor. Always computed from the anchor, so a rule on the
     * 31st lands on the last day of short months and returns to the 31st afterwards.
     */
    fun occurrence(anchor: LocalDate, unit: RepeatUnit, interval: Int, index: Int): LocalDate {
        val step = interval.coerceAtLeast(1).toLong() * index
        return when (unit) {
            RepeatUnit.DAY -> anchor.plusDays(step)
            RepeatUnit.WEEK -> anchor.plusWeeks(step)
            RepeatUnit.MONTH -> anchor.plusMonths(step)
            RepeatUnit.YEAR -> anchor.plusYears(step)
        }
    }

    /** Index of the first occurrence on or after [date]. */
    fun firstIndexOnOrAfter(anchor: LocalDate, unit: RepeatUnit, interval: Int, date: LocalDate): Int {
        var i = 0
        while (i < 100_000 && occurrence(anchor, unit, interval, i).isBefore(date)) i++
        return i
    }

    /**
     * Past due dates a new (or re-dated) Auto-add rule could fill in: occurrences from [fromIndex]
     * up to yesterday (today's is added by the normal run), within the rule's end date. Dates this rule
     * already has an entry on are left out. A date is marked [BackfillDate.looksAdded] when an entry
     * of the same type, amount and category is already near it (entered by hand).
     */
    fun backfill(
        rule: RecurringRule,
        fromIndex: Int,
        existing: List<Txn>,
        today: LocalDate,
        max: Int = 1000
    ): List<BackfillDate> {
        val anchor = LocalDate.ofEpochDay(rule.anchorDate)
        val window = when (rule.unit) {
            RepeatUnit.DAY -> 0L
            RepeatUnit.WEEK -> 1L
            RepeatUnit.MONTH, RepeatUnit.YEAR -> 3L
        }
        val days = existing.map { it to Dates.toLocalDate(it.dateTime).toEpochDay() }
        val out = ArrayList<BackfillDate>()
        var i = fromIndex
        while (out.size < max) {
            val d = occurrence(anchor, rule.unit, rule.interval, i++)
            if (!d.isBefore(today)) break
            if (rule.endDate != null && d.toEpochDay() > rule.endDate) break
            val day = d.toEpochDay()
            if (rule.id != 0L && days.any { (t, td) -> t.recurringRuleId == rule.id && td == day }) continue
            val similar = days.any { (t, td) ->
                t.type == rule.type && t.amountPaise == rule.amountPaise && t.categoryId == rule.categoryId &&
                    (rule.subcategoryId == null || t.subcategoryId == rule.subcategoryId) &&
                    kotlin.math.abs(td - day) <= window
            }
            out += BackfillDate(d, similar)
        }
        return out
    }

    /** "After N times": the date of the N-th occurrence, which becomes the rule's end date. */
    fun endAfter(anchor: LocalDate, unit: RepeatUnit, interval: Int, times: Int): LocalDate =
        occurrence(anchor, unit, interval, (times - 1).coerceAtLeast(0))

    /** If [end] falls exactly on an occurrence, how many times the rule runs in all; otherwise null. */
    fun timesUntil(anchor: LocalDate, unit: RepeatUnit, interval: Int, end: LocalDate): Int? {
        val i = firstIndexOnOrAfter(anchor, unit, interval, end)
        return if (occurrence(anchor, unit, interval, i) == end) i + 1 else null
    }

    /** Occurrences still to come (from the next due date up to the end date), or null for a rule with no end. */
    fun remaining(rule: RecurringRule): Int? {
        val end = rule.endDate ?: return null
        if (!rule.isActive || rule.nextDueDate > end) return 0
        val anchor = LocalDate.ofEpochDay(rule.anchorDate)
        var n = 0
        while (n < 100_000 && occurrence(anchor, rule.unit, rule.interval, rule.occurrenceIndex + n).toEpochDay() <= end) n++
        return n
    }

    fun monthlyEquivalent(rule: RecurringRule): Long {
        val n = rule.interval.coerceAtLeast(1)
        return when (rule.unit) {
            RepeatUnit.DAY -> rule.amountPaise * 365 / 12 / n
            RepeatUnit.WEEK -> rule.amountPaise * 52 / 12 / n
            RepeatUnit.MONTH -> rule.amountPaise / n
            RepeatUnit.YEAR -> rule.amountPaise / (12L * n)
        }
    }

    fun describe(unit: RepeatUnit, interval: Int): String =
        if (interval <= 1) {
            when (unit) {
                RepeatUnit.DAY -> "Daily"
                RepeatUnit.WEEK -> "Weekly"
                RepeatUnit.MONTH -> "Monthly"
                RepeatUnit.YEAR -> "Yearly"
            }
        } else "Every $interval ${unit.plural}"
}

/** A past due date that can be filled in; [looksAdded] = a matching entry already exists near it. */
data class BackfillDate(val date: LocalDate, val looksAdded: Boolean)
