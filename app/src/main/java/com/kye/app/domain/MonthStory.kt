package com.kye.app.domain

import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import java.time.LocalDate
import java.time.YearMonth

/** A month told as a few full-screen cards: totals, top category, biggest day, streaks, savings. */
data class MonthStory(
    val month: YearMonth,
    val spent: Long,
    val count: Int,
    val prevSpent: Long,
    /** Up to three categories by spending: name, emoji, total. */
    val topCategories: List<Triple<String, String, Long>>,
    val biggestDay: LocalDate?,
    val biggestDayTotal: Long,
    /** What most of the biggest day went on. */
    val biggestDayOn: String?,
    /** Days with no spending, up to today for the current month. */
    val noSpendDays: Int,
    val bestStreak: Int,
    /** The place or thing added most often: name, emoji, times, total. */
    val favourite: Favourite?,
    val saved: Long,
    val income: Long
) {
    data class Favourite(val name: String, val emoji: String, val times: Int, val total: Long)

    /** Change against the month before, in whole percent; null when there is nothing to compare. */
    val changePercent: Int?
        get() = if (prevSpent <= 0) null else (((spent - prevSpent) * 100.0) / prevSpent).let { Math.round(it).toInt() }

    companion object {
        fun build(txns: List<TxnRow>, month: YearMonth, today: LocalDate): MonthStory {
            fun inMonth(m: YearMonth, r: TxnRow): Boolean {
                val t = r.txn.dateTime
                return t in Dates.startOfDay(m.atDay(1))..Dates.endOfDay(m.atEndOfMonth())
            }
            val rows = txns.filter { inMonth(month, it) }
            val expenses = rows.filter { it.txn.type == TxnType.EXPENSE }
            val prev = month.minusMonths(1)
            val prevSpent = txns.filter { it.txn.type == TxnType.EXPENSE && inMonth(prev, it) }.sumOf { it.txn.amountPaise }

            val topCats = expenses.groupBy { it.catName ?: "Uncategorised" }
                .map { (name, rs) -> Triple(name, rs.first().catEmoji ?: "❔", rs.sumOf { it.txn.amountPaise }) }
                .sortedByDescending { it.third }
                .take(3)

            val byDay = expenses.groupBy { Dates.toLocalDate(it.txn.dateTime) }
            val biggest = byDay.maxByOrNull { (_, rs) -> rs.sumOf { it.txn.amountPaise } }
            val biggestOn = biggest?.value?.maxByOrNull { it.txn.amountPaise }?.title

            // Days counted: the whole month, or up to today while it's still running.
            val lastDay = when {
                month.isAfter(YearMonth.from(today)) -> 0
                month == YearMonth.from(today) -> today.dayOfMonth
                else -> month.lengthOfMonth()
            }
            var noSpend = 0
            var streak = 0
            var best = 0
            for (d in 1..lastDay) {
                if (month.atDay(d) in byDay) {
                    streak = 0
                } else {
                    noSpend++
                    streak++
                    best = maxOf(best, streak)
                }
            }

            val fav = expenses.filter { it.specificSub != null }
                .groupBy { it.specificSub!! }
                .maxWithOrNull(compareBy<Map.Entry<String, List<TxnRow>>> { it.value.size }.thenBy { e -> e.value.sumOf { it.txn.amountPaise } })
                ?.takeIf { it.value.size >= 2 }
                ?.let { (name, rs) -> Favourite(name, rs.first().catEmoji ?: "❔", rs.size, rs.sumOf { it.txn.amountPaise }) }

            return MonthStory(
                month = month,
                spent = expenses.sumOf { it.txn.amountPaise },
                count = expenses.size,
                prevSpent = prevSpent,
                topCategories = topCats,
                biggestDay = biggest?.key,
                biggestDayTotal = biggest?.value?.sumOf { it.txn.amountPaise } ?: 0L,
                biggestDayOn = biggestOn,
                noSpendDays = noSpend,
                bestStreak = best,
                favourite = fav,
                saved = rows.filter { it.txn.type == TxnType.SAVING }.sumOf { it.txn.amountPaise },
                income = rows.filter { it.txn.type == TxnType.INCOME }.sumOf { it.txn.amountPaise }
            )
        }
    }
}
