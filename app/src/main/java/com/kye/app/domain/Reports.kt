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

/** Builds the monthly report and decides when the automatic one is due. Pure Kotlin, unit-tested. */
object Reports {
    const val PREFIX = "KYE-report-"

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
