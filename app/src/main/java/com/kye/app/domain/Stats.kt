package com.kye.app.domain

import com.kye.app.data.Category
import com.kye.app.data.PaymentMode
import com.kye.app.data.RecurringRule
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import java.time.LocalDate

const val UNCATEGORISED_COLOR = 0xFFB2BEC3

data class UpcomingBill(val rule: RecurringRule, val due: LocalDate, val emoji: String, val color: Long)

data class HomeState(
    val loaded: Boolean = false,
    val spent: Long = 0,
    val saved: Long = 0,
    val income: Long = 0,
    val today: Long = 0,
    val budget: Long? = null,
    val recent: List<TxnRow> = emptyList(),
    val upcoming: List<UpcomingBill> = emptyList()
)

enum class Period(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3("3 months"),
    THIS_FY("This FY")
}

data class Slice(
    val categoryId: Long?,
    val name: String,
    val emoji: String,
    val color: Long,
    val total: Long,
    val subs: List<Pair<String, Long>>
)

data class TrendBar(val label: String, val value: Long)
data class CompareRow(val name: String, val emoji: String, val color: Long, val current: Long, val previous: Long)
data class MerchantRow(val name: String, val emoji: String, val total: Long, val count: Int)
data class BudgetRow(val categoryId: Long, val name: String, val emoji: String, val color: Long, val spent: Long, val limit: Long)

data class AnalyticsState(
    val period: Period,
    val rangeLabel: String,
    val spent: Long,
    val saved: Long,
    val income: Long,
    val prevSpent: Long,
    val count: Int,
    val slices: List<Slice>,
    val trend: List<TrendBar>,
    val trendDaily: Boolean,
    val compare: List<CompareRow>,
    val merchants: List<MerchantRow>,
    val payments: List<Pair<PaymentMode, Long>>,
    val fixed: Long,
    val variable: Long,
    val budgets: List<BudgetRow>,
    val upcoming: List<UpcomingBill>,
    val upcomingTotal: Long
)

object Stats {

    fun upcoming(rules: List<RecurringRule>, cats: Map<Long, Category>, today: LocalDate, days: Long): List<UpcomingBill> =
        rules.asSequence()
            .filter { it.isActive }
            .map { r ->
                val c = r.categoryId?.let { cats[it] }
                UpcomingBill(r, LocalDate.ofEpochDay(r.nextDueDate), c?.emoji ?: "🔁", c?.color ?: UNCATEGORISED_COLOR)
            }
            .filter { !it.due.isAfter(today.plusDays(days)) }
            .sortedBy { it.due }
            .toList()

    fun home(
        txns: List<TxnRow>,
        rules: List<RecurringRule>,
        budgets: Map<Long, Long>,
        cats: Map<Long, Category>,
        today: LocalDate
    ): HomeState {
        val from = Dates.startOfDay(today.withDayOfMonth(1))
        val to = Dates.endOfDay(today.withDayOfMonth(today.lengthOfMonth()))
        val dayFrom = Dates.startOfDay(today)
        val dayTo = Dates.endOfDay(today)
        var spent = 0L
        var saved = 0L
        var income = 0L
        var todaySum = 0L
        for (r in txns) {
            val t = r.txn
            if (t.dateTime in from..to) {
                when (t.type) {
                    TxnType.EXPENSE -> spent += t.amountPaise
                    TxnType.SAVING -> saved += t.amountPaise
                    TxnType.INCOME -> income += t.amountPaise
                    else -> Unit
                }
            }
            if (t.type == TxnType.EXPENSE && t.dateTime in dayFrom..dayTo) todaySum += t.amountPaise
        }
        return HomeState(
            loaded = true,
            spent = spent,
            saved = saved,
            income = income,
            today = todaySum,
            budget = budgets[0L],
            recent = txns.take(8),
            upcoming = upcoming(rules, cats, today, 30).take(3)
        )
    }

    fun monthSpendByCategory(txns: List<TxnRow>, today: LocalDate): Map<Long?, Long> {
        val from = Dates.startOfDay(today.withDayOfMonth(1))
        val to = Dates.endOfDay(today.withDayOfMonth(today.lengthOfMonth()))
        val out = HashMap<Long?, Long>()
        txns.forEach { r ->
            val t = r.txn
            if (t.type == TxnType.EXPENSE && t.dateTime in from..to) {
                out[t.categoryId] = (out[t.categoryId] ?: 0L) + t.amountPaise
            }
        }
        return out
    }

    private fun ranges(p: Period, today: LocalDate): Pair<Pair<LocalDate, LocalDate>, Pair<LocalDate, LocalDate>> {
        val monthStart = today.withDayOfMonth(1)
        return when (p) {
            Period.THIS_MONTH -> {
                val s = monthStart
                val e = s.plusMonths(1).minusDays(1)
                val ps = s.minusMonths(1)
                (s to e) to (ps to s.minusDays(1))
            }
            Period.LAST_MONTH -> {
                val s = monthStart.minusMonths(1)
                val ps = s.minusMonths(1)
                (s to monthStart.minusDays(1)) to (ps to s.minusDays(1))
            }
            Period.LAST_3 -> {
                val s = monthStart.minusMonths(2)
                val e = monthStart.plusMonths(1).minusDays(1)
                val ps = s.minusMonths(3)
                (s to e) to (ps to s.minusDays(1))
            }
            Period.THIS_FY -> {
                val s = Dates.fyStart(today)
                val e = s.plusYears(1).minusDays(1)
                val ps = s.minusYears(1)
                (s to e) to (ps to s.minusDays(1))
            }
        }
    }

    fun analytics(
        txns: List<TxnRow>,
        period: Period,
        rules: List<RecurringRule>,
        budgets: Map<Long, Long>,
        cats: Map<Long, Category>,
        today: LocalDate
    ): AnalyticsState {
        val (cur, prev) = ranges(period, today)
        val from = Dates.startOfDay(cur.first)
        val to = Dates.endOfDay(cur.second)
        val pFrom = Dates.startOfDay(prev.first)
        val pTo = Dates.endOfDay(prev.second)

        val inRange = txns.filter { it.txn.dateTime in from..to }
        val expenses = inRange.filter { it.txn.type == TxnType.EXPENSE }
        val prevExpenses = txns.filter { it.txn.type == TxnType.EXPENSE && it.txn.dateTime in pFrom..pTo }

        val spent = expenses.sumOf { it.txn.amountPaise }
        val saved = inRange.filter { it.txn.type == TxnType.SAVING }.sumOf { it.txn.amountPaise }
        val income = inRange.filter { it.txn.type == TxnType.INCOME }.sumOf { it.txn.amountPaise }
        val prevSpent = prevExpenses.sumOf { it.txn.amountPaise }

        // Category slices with subcategory breakdown
        val slices = expenses.groupBy { it.txn.categoryId }.map { (catId, rows) ->
            val c = catId?.let { cats[it] }
            Slice(
                categoryId = catId,
                name = c?.name ?: "Uncategorised",
                emoji = c?.emoji ?: "❔",
                color = c?.color ?: UNCATEGORISED_COLOR,
                total = rows.sumOf { it.txn.amountPaise },
                subs = rows.groupBy { it.subName ?: "Others" }
                    .map { (n, rs) -> n to rs.sumOf { it.txn.amountPaise } }
                    .sortedByDescending { it.second }
            )
        }.sortedByDescending { it.total }

        // Trend
        val days = cur.second.toEpochDay() - cur.first.toEpochDay() + 1
        val daily = days <= 31
        val trend = if (daily) {
            val byDay = expenses.groupBy { Dates.toLocalDate(it.txn.dateTime) }
            (0 until days).map { i ->
                val d = cur.first.plusDays(i)
                TrendBar(Dates.short(d), byDay[d]?.sumOf { it.txn.amountPaise } ?: 0L)
            }
        } else {
            val byMonth = expenses.groupBy { Dates.toLocalDate(it.txn.dateTime).withDayOfMonth(1) }
            val out = ArrayList<TrendBar>()
            var m = cur.first.withDayOfMonth(1)
            while (!m.isAfter(cur.second)) {
                out += TrendBar(Dates.monthShort(m), byMonth[m]?.sumOf { it.txn.amountPaise } ?: 0L)
                m = m.plusMonths(1)
            }
            out
        }

        // This vs previous period, top 6 categories
        val prevByCat = prevExpenses.groupBy { it.txn.categoryId }.mapValues { (_, v) -> v.sumOf { it.txn.amountPaise } }
        val compare = slices.take(6).map { s ->
            CompareRow(s.name, s.emoji, s.color, s.total, prevByCat[s.categoryId] ?: 0L)
        }

        // Top merchants = most-spent subcategories (excluding each category's catch-all)
        val merchants = expenses.filter { it.specificSub != null }
            .groupBy { it.specificSub!! }
            .map { (n, rs) -> MerchantRow(n, rs.first().catEmoji ?: "❔", rs.sumOf { it.txn.amountPaise }, rs.size) }
            .sortedByDescending { it.total }
            .take(6)

        val payments = expenses.groupBy { it.txn.paymentMode }
            .map { (mode, rs) -> mode to rs.sumOf { it.txn.amountPaise } }
            .sortedByDescending { it.second }

        val fixed = expenses.filter { it.txn.recurringRuleId != null }.sumOf { it.txn.amountPaise }

        // Budgets always describe the current month
        val monthSpend = monthSpendByCategory(txns, today)
        val budgetRows = budgets.mapNotNull { (catId, limit) ->
            if (catId == 0L) {
                BudgetRow(0L, "Overall", "📊", 0xFF6C5CE7, monthSpend.values.sum(), limit)
            } else {
                val c = cats[catId] ?: return@mapNotNull null
                BudgetRow(catId, c.name, c.emoji, c.color, monthSpend[catId] ?: 0L, limit)
            }
        }.sortedByDescending { if (it.limit > 0) it.spent.toDouble() / it.limit else 0.0 }

        val upcoming = upcoming(rules, cats, today, 30)

        val label = if (period == Period.THIS_FY) {
            "FY ${cur.first.year}–${(cur.second.year % 100).toString().padStart(2, '0')}"
        } else if (cur.first.monthValue == cur.second.monthValue && cur.first.year == cur.second.year) {
            "${Dates.monthName(cur.first)} ${cur.first.year}"
        } else {
            "${Dates.monthShort(cur.first)} – ${Dates.monthShort(cur.second)} ${cur.second.year}"
        }

        return AnalyticsState(
            period = period,
            rangeLabel = label,
            spent = spent,
            saved = saved,
            income = income,
            prevSpent = prevSpent,
            count = expenses.size,
            slices = slices,
            trend = trend,
            trendDaily = daily,
            compare = compare,
            merchants = merchants,
            payments = payments,
            fixed = fixed,
            variable = spent - fixed,
            budgets = budgetRows,
            upcoming = upcoming,
            upcomingTotal = upcoming.sumOf { it.rule.amountPaise }
        )
    }
}
