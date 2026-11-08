package com.kye.app.domain

import com.kye.app.data.GoldScheme
import com.kye.app.data.GoldSchemeRow
import java.time.LocalDate

/** Gold savings scheme maths. Pure Kotlin, unit-tested. */
object GoldSchemes {

    /** Instalment [index] (0-based) falls due [index] months after the first, same day of month. */
    fun dueDate(s: GoldScheme, index: Int): LocalDate = LocalDate.ofEpochDay(s.startDate).plusMonths(index.toLong())

    fun totalToPay(s: GoldScheme): Long = s.monthlyPaise * s.months

    /** What the jeweller gives at the end: everything paid plus their bonus. */
    fun maturityValue(s: GoldScheme): Long = totalToPay(s) + s.bonusPaise

    /** Redeemable from a month after the last instalment. */
    fun maturityDate(s: GoldScheme): LocalDate = dueDate(s, s.months)

    /** Whole instalments covered by what's been paid (two months paid at once counts as two). */
    fun instalmentsPaid(r: GoldSchemeRow): Int =
        if (r.scheme.monthlyPaise <= 0) 0 else (r.paidPaise / r.scheme.monthlyPaise).coerceAtMost(r.scheme.months.toLong()).toInt()

    fun isFullyPaid(r: GoldSchemeRow): Boolean = r.paidPaise >= totalToPay(r.scheme)

    fun remaining(r: GoldSchemeRow): Long = (totalToPay(r.scheme) - r.paidPaise).coerceAtLeast(0)

    /** The next unpaid instalment's due date, or null once all are paid or the scheme is closed. */
    fun nextDue(r: GoldSchemeRow): LocalDate? =
        if (r.scheme.isClosed || isFullyPaid(r)) null else dueDate(r.scheme, instalmentsPaid(r))

    /** Instalments due by [today] that haven't been paid. */
    fun overdueCount(r: GoldSchemeRow, today: LocalDate): Int {
        if (r.scheme.isClosed) return 0
        val dueSoFar = (0 until r.scheme.months).count { !dueDate(r.scheme, it).isAfter(today) }
        return (dueSoFar - instalmentsPaid(r)).coerceAtLeast(0)
    }

    fun isReadyToRedeem(r: GoldSchemeRow, today: LocalDate): Boolean =
        !r.scheme.isClosed && isFullyPaid(r) && !today.isBefore(maturityDate(r.scheme))

    /**
     * Remind once per instalment, on its due day (or the first check after it). Instalments that fell
     * due before the scheme was added to KYE aren't reminded: the user is entering an older scheme.
     */
    fun shouldRemind(r: GoldSchemeRow, today: LocalDate, addedOn: LocalDate): Boolean {
        if (!r.scheme.remind) return false
        val due = nextDue(r) ?: return false
        if (due.isBefore(addedOn)) return false
        return !today.isBefore(due) && r.scheme.lastRemindedFor != due.toEpochDay()
    }

    fun percent(r: GoldSchemeRow): Int {
        val total = totalToPay(r.scheme)
        return if (total <= 0) 0 else ((r.paidPaise * 100) / total).toInt().coerceIn(0, 100)
    }

    /** Problems with a scheme the user typed, or null when it's fine. */
    fun check(s: GoldScheme): String? = when {
        s.name.isBlank() -> "Enter the scheme or jeweller's name"
        s.monthlyPaise <= 0 -> "Enter the monthly amount"
        s.months !in 1..60 -> "Months should be between 1 and 60"
        s.bonusPaise < 0 -> "The bonus can't be negative"
        else -> null
    }
}
