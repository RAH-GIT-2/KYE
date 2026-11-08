package com.kye.app.domain

import com.kye.app.data.Category
import com.kye.app.data.RecurringRule
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import java.time.DayOfWeek
import java.time.LocalDate

/** Where this month's spending is heading. All amounts in paise. */
data class Forecast(
    val spentSoFar: Long,
    /** Recurring bills (expense) still due between tomorrow and month end, plus overdue reminders. */
    val billsToCome: Long,
    /** Projected everyday (non-recurring) spending for the rest of the month. */
    val projectedEveryday: Long,
    val monthEnd: LocalDate,
    val budget: Long?
) {
    val total: Long get() = spentSoFar + billsToCome + projectedEveryday

    /** Positive = over budget by this much. Null without an overall budget. */
    val overBudgetBy: Long? get() = budget?.let { total - it }

    /** What can still be spent per day without crossing the budget, after known bills. */
    fun safePerDay(today: LocalDate): Long? {
        val b = budget ?: return null
        val daysLeft = (monthEnd.toEpochDay() - today.toEpochDay() + 1).coerceAtLeast(1)
        return ((b - spentSoFar - billsToCome) / daysLeft).coerceAtLeast(0)
    }
}

enum class Tone { GOOD, NEUTRAL, WARN }

data class Highlight(val emoji: String, val text: String, val tone: Tone)

/**
 * Month-end forecast and plain-language highlights for Home. Pure Kotlin, unit-tested.
 * Only expenses count (not savings, income or transfers).
 */
object Insights {

    private fun TxnRow.date(): LocalDate = Dates.toLocalDate(txn.dateTime)
    private fun TxnRow.isExpense() = txn.type == TxnType.EXPENSE

    /**
     * Spent so far + recurring bills still due this month + everyday spending projected at the
     * current daily pace. In the first week the pace comes from last month, if there is any,
     * because a few days are too few to judge. Null when there's nothing to go on.
     */
    fun forecast(txns: List<TxnRow>, rules: List<RecurringRule>, budget: Long?, today: LocalDate): Forecast? {
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        val thisMonth = txns.filter { it.isExpense() && !it.date().isBefore(monthStart) && !it.date().isAfter(today) }
        val spent = thisMonth.sumOf { it.txn.amountPaise }
        val everydaySoFar = thisMonth.filter { it.txn.recurringRuleId == null }.sumOf { it.txn.amountPaise }

        val day = today.dayOfMonth
        val prevStart = monthStart.minusMonths(1)
        val prevEveryday = txns.filter {
            it.isExpense() && it.txn.recurringRuleId == null && !it.date().isBefore(prevStart) && it.date().isBefore(monthStart)
        }.sumOf { it.txn.amountPaise }
        val dailyPace = when {
            day < 7 && prevEveryday > 0 -> prevEveryday / prevStart.lengthOfMonth()
            else -> everydaySoFar / day
        }
        val daysLeft = (monthEnd.dayOfMonth - day).toLong()

        val bills = billsDue(rules, today, monthEnd)
        if (spent == 0L && bills == 0L && prevEveryday == 0L) return null
        return Forecast(
            spentSoFar = spent,
            billsToCome = bills,
            projectedEveryday = dailyPace * daysLeft,
            monthEnd = monthEnd,
            budget = budget
        )
    }

    /** Expense bills from active rules due after today and up to [until]; overdue reminders count once. */
    fun billsDue(rules: List<RecurringRule>, today: LocalDate, until: LocalDate): Long {
        var total = 0L
        for (r in rules) {
            if (!r.isActive || r.type != TxnType.EXPENSE || r.amountPaise <= 0) continue
            val anchor = LocalDate.ofEpochDay(r.anchorDate)
            var i = r.occurrenceIndex
            var due = LocalDate.ofEpochDay(r.nextDueDate)
            var guard = 0
            // An unpaid reminder from before today still has to be paid this month.
            if (!due.isAfter(today)) {
                total += r.amountPaise
                i++
                due = Recurrence.occurrence(anchor, r.unit, r.interval, i)
            }
            while (!due.isAfter(until) && guard++ < 400) {
                if (r.endDate != null && due.toEpochDay() > r.endDate) break
                total += r.amountPaise
                i++
                due = Recurrence.occurrence(anchor, r.unit, r.interval, i)
            }
        }
        return total
    }

    /** Up to [max] notes, most useful first. */
    fun highlights(
        txns: List<TxnRow>,
        budgets: Map<Long, Long>,
        cats: Map<Long, Category>,
        today: LocalDate,
        max: Int = 3
    ): List<Highlight> {
        val out = ArrayList<Pair<Int, Highlight>>() // priority (higher first) to note
        val monthStart = today.withDayOfMonth(1)
        val day = today.dayOfMonth
        val expenses = txns.filter { it.isExpense() }
        val thisMonth = expenses.filter { !it.date().isBefore(monthStart) && !it.date().isAfter(today) }

        // 1. Category budgets heading over, by the date they'd cross at today's pace.
        val byCat = thisMonth.groupBy { it.txn.categoryId }.mapValues { (_, v) -> v.sumOf { it.txn.amountPaise } }
        for ((catId, limit) in budgets) {
            if (catId == 0L || limit <= 0) continue
            val name = cats[catId]?.name ?: continue
            val spent = byCat[catId] ?: 0L
            if (spent >= limit) {
                out += 100 to Highlight("🚨", "$name is over its ${Money.format(limit)} budget by ${Money.format(spent - limit)}", Tone.WARN)
            } else if (spent > 0 && day >= 5) {
                val perDay = spent / day
                val crossDay = if (perDay > 0) ((limit + perDay - 1) / perDay).toInt() else Int.MAX_VALUE
                if (crossDay <= today.lengthOfMonth()) {
                    val date = today.withDayOfMonth(crossDay.coerceAtLeast(day))
                    out += 90 to Highlight("⏳", "At this pace $name crosses its ${Money.format(limit)} budget around ${Dates.short(date)}", Tone.WARN)
                }
            }
        }

        // 2. Biggest change vs the same days last month (1st to today's date).
        if (day >= 5) {
            val prevStart = monthStart.minusMonths(1)
            val prevSameDay = prevStart.withDayOfMonth(minOf(day, prevStart.lengthOfMonth()))
            val prevByCat = expenses.filter { !it.date().isBefore(prevStart) && !it.date().isAfter(prevSameDay) }
                .groupBy { it.txn.categoryId }.mapValues { (_, v) -> v.sumOf { it.txn.amountPaise } }
            if (prevByCat.isNotEmpty()) {
                val changes = (byCat.keys + prevByCat.keys).mapNotNull { id ->
                    val now = byCat[id] ?: 0L
                    val before = prevByCat[id] ?: 0L
                    val name = id?.let { cats[it]?.name } ?: return@mapNotNull null
                    if (before == 0L) null else Triple(name, now, before)
                }
                changes.filter { (_, now, before) -> now - before >= 500_00 && now * 100 >= before * 130 }
                    .maxByOrNull { (_, now, before) -> now - before }
                    ?.let { (name, now, before) ->
                        val pct = (now - before) * 100 / before
                        out += 80 to Highlight("📈", "$name is up $pct% vs this time last month (${Money.format(now)} vs ${Money.format(before)})", Tone.WARN)
                    }
                changes.filter { (_, now, before) -> before - now >= 500_00 && now * 100 <= before * 70 }
                    .maxByOrNull { (_, now, before) -> before - now }
                    ?.let { (name, now, before) ->
                        val pct = (before - now) * 100 / before
                        out += 50 to Highlight("📉", "$name is down $pct% vs this time last month. Nice.", Tone.GOOD)
                    }
            }
        }

        // 3. Weekends vs weekdays, over the last 8 weeks.
        val from = today.minusWeeks(8)
        val recent = expenses.filter { it.date().isAfter(from) && !it.date().isAfter(today) && it.txn.recurringRuleId == null }
        if (recent.isNotEmpty()) {
            var weekendDays = 0
            var weekDays = 0
            var d = from.plusDays(1)
            while (!d.isAfter(today)) {
                if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) weekendDays++ else weekDays++
                d = d.plusDays(1)
            }
            val (weekend, weekday) = recent.partition { it.date().dayOfWeek == DayOfWeek.SATURDAY || it.date().dayOfWeek == DayOfWeek.SUNDAY }
            val weekendPerDay = weekend.sumOf { it.txn.amountPaise } / weekendDays.coerceAtLeast(1)
            val weekdayPerDay = weekday.sumOf { it.txn.amountPaise } / weekDays.coerceAtLeast(1)
            if (weekdayPerDay > 0 && weekendPerDay >= weekdayPerDay * 3 / 2 && weekendPerDay - weekdayPerDay >= 100_00) {
                val times = weekendPerDay.toDouble() / weekdayPerDay
                val label = if (times >= 1.95) "${"%.0f".format(times)}×" else "${"%.1f".format(times)}×"
                out += 60 to Highlight(
                    "🗓️",
                    "Weekends cost you $label weekdays (${Money.format(weekendPerDay)} vs ${Money.format(weekdayPerDay)} a day)",
                    Tone.NEUTRAL
                )
            }
        }

        // 4. Most-used merchant this month.
        thisMonth.filter { it.specificSub != null && it.txn.recurringRuleId == null }
            .groupBy { it.specificSub!! }
            .filter { it.value.size >= 4 }
            .maxByOrNull { it.value.size }
            ?.let { (name, rows) ->
                out += 40 to Highlight("🏪","$name: ${rows.size} times, ${Money.format(rows.sumOf { it.txn.amountPaise })} this month", Tone.NEUTRAL)
            }

        // 5. No-spend days so far.
        if (day >= 7) {
            val spendDays = thisMonth.map { it.date() }.toSet().size
            val free = day - spendDays
            if (free >= 3) out += 30 to Highlight("👏", "$free no-spend days this month", Tone.GOOD)
        }

        return out.sortedByDescending { it.first }.map { it.second }.take(max)
    }
}
