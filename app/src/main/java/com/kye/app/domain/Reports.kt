package com.kye.app.domain

import com.kye.app.data.Category
import com.kye.app.data.PaymentMode
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import java.time.LocalDate
import java.time.YearMonth

data class ReportLine(val name: String, val amount: Long, val count: Int)
data class ReportBudget(val name: String, val spent: Long, val limit: Long)

/** Everything the monthly PDF shows, worked out from the entries. */
data class MonthlyReport(
    val month: YearMonth,
    val income: Long,
    val expense: Long,
    val saving: Long,
    val previousExpense: Long,
    /** Expense by category, biggest first. */
    val categories: List<ReportLine>,
    /** Saving by category, biggest first. */
    val savings: List<ReportLine>,
    val budgets: List<ReportBudget>,
    val payments: List<Pair<PaymentMode, Long>>,
    val topExpenses: List<TxnRow>,
    /** Every entry in the month, oldest first. */
    val entries: List<TxnRow>,
    /** Days counted for the daily average: the whole month, or up to today for the current one. */
    val days: Int
) {
    val leftOver: Long get() = income - expense - saving
    val dailyAverage: Long get() = if (days <= 0) 0 else expense / days
    val isEmpty: Boolean get() = entries.isEmpty()

    /** Change in spending against the month before, in %, or null when there's nothing to compare. */
    val changePercent: Int?
        get() = if (previousExpense <= 0) null else (((expense - previousExpense) * 100) / previousExpense).toInt()
}

/** One row of the yearly report's month-by-month table. */
data class MonthTotals(val month: YearMonth, val income: Long, val expense: Long, val saving: Long) {
    val leftOver: Long get() = income - expense - saving
}

/**
 * A year: April–March (financial year, [fy] = true, named by its start year) or January–December.
 * [key] is "fy2025" / "cy2025", handy for saving the choice.
 */
data class ReportYear(val startYear: Int, val fy: Boolean) {
    val first: YearMonth get() = if (fy) YearMonth.of(startYear, 4) else YearMonth.of(startYear, 1)
    val months: List<YearMonth> get() = (0L until 12L).map { first.plusMonths(it) }
    val label: String get() = if (fy) "FY $startYear–${"%02d".format((startYear + 1) % 100)}" else "$startYear"
    val key: String get() = (if (fy) "fy" else "cy") + startYear
    val fileName: String get() = if (fy) "KYE-report-FY$startYear-${"%02d".format((startYear + 1) % 100)}.pdf" else "KYE-report-$startYear.pdf"
    fun previous() = copy(startYear = startYear - 1)

    companion object {
        fun parse(key: String): ReportYear? {
            val y = key.drop(2).toIntOrNull() ?: return null
            return when (key.take(2)) { "fy" -> ReportYear(y, true); "cy" -> ReportYear(y, false); else -> null }
        }

        /** The current and last financial year, then the current and last calendar year. */
        fun choices(today: LocalDate): List<ReportYear> {
            val fy = Dates.fyStart(today).year
            return listOf(ReportYear(fy, true), ReportYear(fy - 1, true), ReportYear(today.year, false), ReportYear(today.year - 1, false))
        }
    }
}

/** Everything the yearly PDF shows. */
data class YearlyReport(
    val year: ReportYear,
    val months: List<MonthTotals>,
    val categories: List<ReportLine>,
    val savings: List<ReportLine>,
    val payments: List<Pair<PaymentMode, Long>>,
    val topExpenses: List<TxnRow>,
    val previousExpense: Long,
    val entryCount: Int,
    /** Months counted so far: 12 for a finished year, fewer for the current one. */
    val monthsSoFar: Int
) {
    val income: Long get() = months.sumOf { it.income }
    val expense: Long get() = months.sumOf { it.expense }
    val saving: Long get() = months.sumOf { it.saving }
    val leftOver: Long get() = income - expense - saving
    val monthlyAverage: Long get() = if (monthsSoFar <= 0) 0 else expense / monthsSoFar
    val isEmpty: Boolean get() = entryCount == 0
    val changePercent: Int?
        get() = if (previousExpense <= 0 || monthsSoFar < 12) null else (((expense - previousExpense) * 100) / previousExpense).toInt()
}

/** Builds the monthly and yearly reports and decides when the automatic one is due. Pure Kotlin, unit-tested. */
object Reports {
    const val PREFIX = "KYE-report-"

    fun buildYear(txns: List<TxnRow>, year: ReportYear, cats: Map<Long, Category>, today: LocalDate): YearlyReport {
        val months = year.months.toSet()
        fun monthOf(r: TxnRow) = YearMonth.from(Dates.toLocalDate(r.txn.dateTime))
        val inYear = txns.filter { monthOf(it) in months }
        val prevMonths = year.previous().months.toSet()
        val previousExpense = txns.filter { it.txn.type == TxnType.EXPENSE && monthOf(it) in prevMonths }.sumOf { it.txn.amountPaise }
        val byMonth = inYear.groupBy { monthOf(it) }
        val rows = year.months.map { m ->
            val list = byMonth[m].orEmpty()
            fun sum(t: TxnType) = list.filter { it.txn.type == t }.sumOf { it.txn.amountPaise }
            MonthTotals(m, sum(TxnType.INCOME), sum(TxnType.EXPENSE), sum(TxnType.SAVING))
        }
        fun byCategory(type: TxnType) = inYear.filter { it.txn.type == type }
            .groupBy { it.txn.categoryId }
            .map { (id, list) ->
                ReportLine(id?.let { cats[it]?.name } ?: list.first().catName ?: "Uncategorised", list.sumOf { it.txn.amountPaise }, list.size)
            }
            .sortedByDescending { it.amount }
        val expenses = inYear.filter { it.txn.type == TxnType.EXPENSE }
        val now = YearMonth.from(today)
        return YearlyReport(
            year = year,
            months = rows,
            categories = byCategory(TxnType.EXPENSE),
            savings = byCategory(TxnType.SAVING),
            payments = expenses.groupBy { it.txn.paymentMode }.map { (m, v) -> m to v.sumOf { it.txn.amountPaise } }.sortedByDescending { it.second },
            topExpenses = expenses.sortedByDescending { it.txn.amountPaise }.take(10),
            previousExpense = previousExpense,
            entryCount = inYear.size,
            monthsSoFar = year.months.count { !it.isAfter(now) }
        )
    }

    fun build(
        txns: List<TxnRow>,
        month: YearMonth,
        budgets: Map<Long, Long>,
        cats: Map<Long, Category>,
        today: LocalDate
    ): MonthlyReport {
        val inMonth = txns.filter { YearMonth.from(Dates.toLocalDate(it.txn.dateTime)) == month }
            .sortedWith(compareBy({ it.txn.dateTime }, { it.txn.id }))
        val prev = month.minusMonths(1)
        val previousExpense = txns.filter {
            it.txn.type == TxnType.EXPENSE && YearMonth.from(Dates.toLocalDate(it.txn.dateTime)) == prev
        }.sumOf { it.txn.amountPaise }

        fun sum(type: TxnType) = inMonth.filter { it.txn.type == type }.sumOf { it.txn.amountPaise }
        fun byCategory(type: TxnType) = inMonth.filter { it.txn.type == type }
            .groupBy { it.txn.categoryId }
            .map { (id, rows) ->
                ReportLine(id?.let { cats[it]?.name } ?: rows.first().catName ?: "Uncategorised", rows.sumOf { it.txn.amountPaise }, rows.size)
            }
            .sortedByDescending { it.amount }

        val expenses = inMonth.filter { it.txn.type == TxnType.EXPENSE }
        val spentByCat = expenses.groupBy { it.txn.categoryId }.mapValues { (_, v) -> v.sumOf { it.txn.amountPaise } }
        val budgetRows = budgets.filter { it.value > 0 }.mapNotNull { (catId, limit) ->
            if (catId == 0L) ReportBudget("Overall", expenses.sumOf { it.txn.amountPaise }, limit)
            else cats[catId]?.let { ReportBudget(it.name, spentByCat[catId] ?: 0L, limit) }
        }.sortedWith(compareBy({ it.name != "Overall" }, { it.name }))

        val days = when {
            month == YearMonth.from(today) -> today.dayOfMonth
            month.isAfter(YearMonth.from(today)) -> 0
            else -> month.lengthOfMonth()
        }
        return MonthlyReport(
            month = month,
            income = sum(TxnType.INCOME),
            expense = sum(TxnType.EXPENSE),
            saving = sum(TxnType.SAVING),
            previousExpense = previousExpense,
            categories = byCategory(TxnType.EXPENSE),
            savings = byCategory(TxnType.SAVING),
            budgets = budgetRows,
            payments = expenses.groupBy { it.txn.paymentMode }
                .map { (m, v) -> m to v.sumOf { it.txn.amountPaise } }
                .sortedByDescending { it.second },
            topExpenses = expenses.sortedByDescending { it.txn.amountPaise }.take(10),
            entries = inMonth,
            days = days
        )
    }

    /**
     * The month whose report should be saved automatically today: last month, unless it's already
     * been saved. [lastSaved] is the last month saved automatically ("2026-10"), or null.
     */
    fun monthToSave(lastSaved: String?, today: LocalDate): YearMonth? {
        val last = YearMonth.from(today).minusMonths(1)
        val saved = lastSaved?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        return if (saved != null && !saved.isBefore(last)) null else last
    }

    /** "KYE-report-2026-10.pdf" */
    fun fileName(month: YearMonth): String = "$PREFIX$month.pdf"

    /** The last [count] months, newest first, for the month picker. */
    fun recentMonths(today: LocalDate, count: Int = 12): List<YearMonth> =
        (0 until count).map { YearMonth.from(today).minusMonths(it.toLong()) }
}
