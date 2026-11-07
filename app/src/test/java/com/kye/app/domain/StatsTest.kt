package com.kye.app.domain

import com.kye.app.data.Category
import com.kye.app.data.PaymentMode
import com.kye.app.data.RecurringRule
import com.kye.app.data.RepeatUnit
import com.kye.app.data.Txn
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class StatsTest {

    private val today = LocalDate.of(2026, 11, 7)
    private val food = Category(id = 1, name = "Food & Dining", emoji = "🍛", color = 1)
    private val home = Category(id = 2, name = "Home & Help", emoji = "🏠", color = 2)
    private val cats = mapOf(1L to food, 2L to home)
    private var nextId = 1L

    private fun row(
        date: LocalDate,
        rupees: Long,
        type: TxnType = TxnType.EXPENSE,
        cat: Category? = food,
        sub: String? = null,
        subIsDefault: Boolean = false,
        payment: PaymentMode = PaymentMode.UPI,
        ruleId: Long? = null
    ) = TxnRow(
        txn = Txn(
            id = nextId++, amountPaise = rupees * 100, type = type, categoryId = cat?.id, subcategoryId = null,
            dateTime = Dates.atMillis(date, LocalTime.NOON), paymentMode = payment, recurringRuleId = ruleId
        ),
        catName = cat?.name, catEmoji = cat?.emoji, catColor = cat?.color,
        subName = sub, subIsDefault = if (sub == null) null else subIsDefault
    )

    @Test fun homeTotalsForThisMonthOnly() {
        val rows = listOf(
            row(today, 100),
            row(today.minusDays(3), 250),
            row(today.withDayOfMonth(1), 50, type = TxnType.SAVING),
            row(today.minusDays(1), 60_000, type = TxnType.INCOME, cat = null),
            row(today, 999, type = TxnType.TRANSFER),
            row(today.minusMonths(1), 5_000) // last month: ignored
        )
        val s = Stats.home(rows, emptyList(), mapOf(0L to 1_000_00L), cats, today)
        assertEquals(350_00L, s.spent)
        assertEquals(100_00L, s.today)
        assertEquals(50_00L, s.saved)
        assertEquals(60_000_00L, s.income)
        assertEquals(1_000_00L, s.budget)
    }

    @Test fun monthBoundariesAreInclusive() {
        val rows = listOf(
            row(LocalDate.of(2026, 11, 1), 10),
            row(LocalDate.of(2026, 11, 30), 20),
            row(LocalDate.of(2026, 10, 31), 40),
            row(LocalDate.of(2026, 12, 1), 80)
        )
        assertEquals(30_00L, Stats.home(rows, emptyList(), emptyMap(), cats, today).spent)
    }

    @Test fun analyticsThisMonth() {
        val rows = listOf(
            row(today, 450, sub = "Swiggy"),
            row(today.minusDays(1), 300, sub = "Swiggy"),
            row(today.minusDays(2), 50, sub = "Others", subIsDefault = true),
            row(today, 20_000, cat = home, sub = "Rent", ruleId = 9, payment = PaymentMode.NET_BANKING),
            row(today, 75, cat = null),
            row(today, 1_00_000, type = TxnType.INCOME, cat = null),
            row(today.minusMonths(1), 10_000, cat = home)
        )
        val a = Stats.analytics(rows, Period.THIS_MONTH, emptyList(), emptyMap(), cats, today)
        assertEquals("November 2026", a.rangeLabel)
        assertEquals(20_875_00L, a.spent)
        assertEquals(1_00_000_00L, a.income)
        assertEquals(10_000_00L, a.prevSpent)
        assertEquals(5, a.count)

        assertEquals(listOf("Home & Help", "Food & Dining", "Uncategorised"), a.slices.map { it.name })
        assertEquals(800_00L, a.slices.first { it.name == "Food & Dining" }.total)

        // Merchants come from named subcategories; the catch-all "Others" is left out.
        assertEquals(listOf("Rent", "Swiggy"), a.merchants.map { it.name })
        assertEquals(2, a.merchants.first { it.name == "Swiggy" }.count)

        assertEquals(20_000_00L, a.fixed)
        assertEquals(875_00L, a.variable)
        assertEquals(PaymentMode.NET_BANKING, a.payments.first().first)
        assertEquals(30, a.trend.size)
        assertTrue(a.trendDaily)
    }

    @Test fun financialYearAndThreeMonthRanges() {
        val feb = LocalDate.of(2027, 2, 10)
        val rows = listOf(
            row(LocalDate.of(2026, 4, 1), 100),   // first day of FY 2026–27
            row(LocalDate.of(2027, 3, 31), 200),  // last day
            row(LocalDate.of(2026, 3, 31), 400),  // previous FY
            row(LocalDate.of(2026, 12, 15), 800)
        )
        val fy = Stats.analytics(rows, Period.THIS_FY, emptyList(), emptyMap(), cats, feb)
        assertEquals("FY 2026–27", fy.rangeLabel)
        assertEquals(1_100_00L, fy.spent)
        assertEquals(400_00L, fy.prevSpent)
        assertEquals(12, fy.trend.size)

        val three = Stats.analytics(rows, Period.LAST_3, emptyList(), emptyMap(), cats, feb)
        assertEquals("Dec – Feb 2027", three.rangeLabel)
        assertEquals(800_00L, three.spent)
    }

    @Test fun lastMonthComparesWithTheMonthBefore() {
        val rows = listOf(row(LocalDate.of(2026, 10, 5), 300), row(LocalDate.of(2026, 9, 5), 100))
        val a = Stats.analytics(rows, Period.LAST_MONTH, emptyList(), emptyMap(), cats, today)
        assertEquals("October 2026", a.rangeLabel)
        assertEquals(300_00L, a.spent)
        assertEquals(100_00L, a.prevSpent)
    }

    @Test fun budgetRowsUseThisMonthAndSortByHowFull() {
        val rows = listOf(row(today, 900), row(today, 500, cat = home), row(today.minusMonths(1), 5_000))
        val budgets = mapOf(0L to 2_000_00L, 1L to 1_000_00L, 2L to 10_000_00L)
        val a = Stats.analytics(rows, Period.LAST_MONTH, emptyList(), budgets, cats, today)
        // Budgets always describe the current month, whatever period is shown.
        assertEquals(listOf("Food & Dining", "Overall", "Home & Help"), a.budgets.map { it.name })
        assertEquals(900_00L, a.budgets.first().spent)
        assertEquals(1_400_00L, a.budgets.first { it.name == "Overall" }.spent)
    }

    @Test fun upcomingBillsWithin30Days() {
        fun rule(due: LocalDate, active: Boolean = true) = RecurringRule(
            name = "r$due", amountPaise = 100, categoryId = 2, subcategoryId = null,
            unit = RepeatUnit.MONTH, anchorDate = due.toEpochDay(), nextDueDate = due.toEpochDay(), isActive = active
        )
        val rules = listOf(
            rule(today.plusDays(30)), rule(today.plusDays(31)), rule(today.minusDays(2)),
            rule(today.plusDays(1), active = false), rule(today.plusDays(5))
        )
        val up = Stats.upcoming(rules, cats, today, 30)
        assertEquals(listOf(today.minusDays(2), today.plusDays(5), today.plusDays(30)), up.map { it.due })
        assertEquals("🏠", up.first().emoji)
    }

    @Test fun monthSpendByCategoryIgnoresOtherTypesAndMonths() {
        val rows = listOf(
            row(today, 100), row(today, 50, cat = home), row(today, 70, cat = null),
            row(today, 999, type = TxnType.SAVING), row(today.minusMonths(1), 999)
        )
        val m = Stats.monthSpendByCategory(rows, today)
        assertEquals(100_00L, m[1L])
        assertEquals(50_00L, m[2L])
        assertEquals(70_00L, m[null])
        assertEquals(3, m.size)
    }
}
