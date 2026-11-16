package com.kye.app.domain

import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** The month grid in Insights: each day shaded by how much was spent on it. */
object SpendCalendar {

    data class Month(
        val month: YearMonth,
        /** Expenses per day; days without spending are left out. */
        val byDay: Map<LocalDate, Long>,
        val total: Long,
        /** Days so far (up to today) with no spending at all. */
        val noSpendDays: Int,
        /** The biggest day, for shading. */
        val max: Long
    )

    fun month(txns: List<TxnRow>, month: YearMonth, today: LocalDate): Month {
        val from = Dates.startOfDay(month.atDay(1))
        val to = Dates.endOfDay(month.atEndOfMonth())
        val byDay = HashMap<LocalDate, Long>()
        txns.forEach { r ->
            val t = r.txn
            if (t.type == TxnType.EXPENSE && t.dateTime in from..to) {
                val d = Dates.toLocalDate(t.dateTime)
                byDay[d] = (byDay[d] ?: 0L) + t.amountPaise
            }
        }
        val lastDay = when {
            month.isAfter(YearMonth.from(today)) -> null
            month == YearMonth.from(today) -> today
            else -> month.atEndOfMonth()
        }
        val noSpend = if (lastDay == null) 0 else (1..lastDay.dayOfMonth).count { month.atDay(it) !in byDay }
        return Month(month, byDay, byDay.values.sum(), noSpend, byDay.values.maxOrNull() ?: 0L)
    }

    /** 0 = nothing spent, 1..4 = light to deep, relative to the month's biggest day. */
    fun level(spent: Long, max: Long): Int = when {
        spent <= 0 || max <= 0 -> 0
        else -> ((spent * 4 + max - 1) / max).toInt().coerceIn(1, 4)
    }

    /** Weeks of the month, Sunday first, with nulls before the 1st and after the last day. */
    fun weeks(month: YearMonth): List<List<LocalDate?>> {
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value % 7 // Sunday = 0
        val cells = List<LocalDate?>(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        val padded = cells + List((7 - cells.size % 7) % 7) { null }
        return padded.chunked(7)
    }

    val WEEKDAYS = listOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
    )
}
