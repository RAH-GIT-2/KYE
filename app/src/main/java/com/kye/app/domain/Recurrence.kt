package com.kye.app.domain

import com.kye.app.data.RecurringRule
import com.kye.app.data.RepeatUnit
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
