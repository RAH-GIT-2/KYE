package com.kye.app.domain

import com.kye.app.data.Category
import com.kye.app.data.PaymentMode
import com.kye.app.data.RecurringMode
import com.kye.app.data.RecurringRule
import com.kye.app.data.RepeatUnit
import com.kye.app.data.Txn
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class InsightsTest {

    private val today = LocalDate.of(2026, 11, 15) // a Sunday; November has 30 days
    private val food = Category(id = 1, name = "Food & Dining", emoji = "🍛", color = 1)
    private val shop = Category(id = 2, name = "Shopping", emoji = "🛍️", color = 2)
    private val cats = mapOf(1L to food, 2L to shop)
    private var nextId = 1L

    private fun row(
        date: LocalDate, rupees: Long, cat: Category? = food, type: TxnType = TxnType.EXPENSE,
        ruleId: Long? = null, sub: String? = null
    ) = TxnRow(
        txn = Txn(
            id = nextId++, amountPaise = rupees * 100, type = type, categoryId = cat?.id, subcategoryId = null,
            dateTime = Dates.atMillis(date, LocalTime.NOON), paymentMode = PaymentMode.UPI, recurringRuleId = ruleId
        ),
        catName = cat?.name, catEmoji = cat?.emoji, catColor = cat?.color, subName = sub, subIsDefault = sub?.let { false }
    )

    private fun rule(due: LocalDate, rupees: Long, unit: RepeatUnit = RepeatUnit.MONTH, type: TxnType = TxnType.EXPENSE, active: Boolean = true) =
        RecurringRule(
            name = "bill", amountPaise = rupees * 100, type = type, categoryId = null, subcategoryId = null,
            unit = unit, anchorDate = due.toEpochDay(), nextDueDate = due.toEpochDay(), isActive = active,
            mode = RecurringMode.REMIND_ONLY
        )

    // ---------- Forecast ----------

    @Test fun projectsEverydayPacePlusBillsToCome() {
        // ₹15,000 everyday over 15 days = ₹1,000/day; 15 days left → ₹15,000 more.
        val rows = (1..15).map { row(today.withDayOfMonth(it), 1_000) } + row(today.withDayOfMonth(5), 20_000, ruleId = 7)
        val f = Insights.forecast(rows, listOf(rule(today.withDayOfMonth(25), 3_000)), 50_000_00, today)!!
        assertEquals(35_000_00L, f.spentSoFar)
        assertEquals(3_000_00L, f.billsToCome)
        assertEquals(15_000_00L, f.projectedEveryday)
        assertEquals(53_000_00L, f.total)
        assertEquals(3_000_00L, f.overBudgetBy)
        assertEquals(LocalDate.of(2026, 11, 30), f.monthEnd)
        // (50,000 − 35,000 − 3,000) / 16 days (15th–30th) = 750
        assertEquals(750_00L, f.safePerDay(today))
    }

    @Test fun firstWeekUsesLastMonthsPace() {
        val early = LocalDate.of(2026, 11, 3)
        val oct = (1..31).map { row(LocalDate.of(2026, 10, it), 310) } // ₹310/day in October
        val f = Insights.forecast(oct + row(early, 5_000), emptyList(), null, early)!!
        // One big day early in the month doesn't mean ₹1,667/day for the rest of it.
        assertEquals(310_00L * 27, f.projectedEveryday)
        assertNull(f.overBudgetBy)
        assertNull(f.safePerDay(early))
    }

    @Test fun nothingToGoOn() {
        assertNull(Insights.forecast(emptyList(), emptyList(), 10_000_00, today))
    }

    @Test fun billsCountEveryOccurrenceIncludingOverdueOnes() {
        val end = today.withDayOfMonth(30)
        assertEquals("weekly from 20th: 20, 27", 200_00L, Insights.billsDue(listOf(rule(today.plusDays(5), 100, RepeatUnit.WEEK)), today, end))
        assertEquals("unpaid reminder from the 10th still counts", 500_00L, Insights.billsDue(listOf(rule(today.minusDays(5), 500)), today, end))
        assertEquals("next month's isn't counted", 0L, Insights.billsDue(listOf(rule(end.plusDays(1), 500)), today, end))
        assertEquals("savings and inactive rules don't count", 0L,
            Insights.billsDue(listOf(rule(today.plusDays(2), 500, type = TxnType.SAVING), rule(today.plusDays(2), 500, active = false)), today, end))
    }

    @Test fun onlyExpensesCount() {
        val rows = listOf(row(today, 1_000), row(today, 50_000, type = TxnType.INCOME), row(today, 9_000, type = TxnType.SAVING))
        assertEquals(1_000_00L, Insights.forecast(rows, emptyList(), null, today)!!.spentSoFar)
    }

    // ---------- Highlights ----------

    @Test fun categoryBudgetPaceAndOverBudget() {
        // Food: ₹6,000 in 15 days = ₹400/day → crosses ₹10,000 on day 25.
        val rows = (1..15).map { row(today.withDayOfMonth(it), 400) } + row(today, 7_000, cat = shop)
        val h = Insights.highlights(rows, mapOf(1L to 10_000_00L, 2L to 5_000_00L), cats, today)
        assertTrue(h[0].text, h[0].text.startsWith("Shopping is over its ₹5,000 budget by ₹2,000"))
        assertTrue(h[1].text, h[1].text.contains("Food & Dining crosses its ₹10,000 budget around 25 Nov"))
    }

    @Test fun bigChangeVsSameDaysLastMonth() {
        val rows = listOf(
            row(LocalDate.of(2026, 10, 5), 2_000), row(LocalDate.of(2026, 10, 25), 9_000), // 25 Oct is after "same day" → ignored
            row(today.withDayOfMonth(6), 3_000),
            row(LocalDate.of(2026, 10, 3), 4_000, cat = shop), row(today.withDayOfMonth(2), 1_000, cat = shop)
        )
        val texts = Insights.highlights(rows, emptyMap(), cats, today, max = 5).map { it.text }
        assertTrue(texts.toString(), texts.any { it.startsWith("Food & Dining is up 50% vs this time last month") })
        assertTrue(texts.toString(), texts.any { it.startsWith("Shopping is down 75%") })
    }

    @Test fun weekendsVsWeekdays() {
        // Every Saturday ₹1,000, every weekday ₹100, over the last 8 weeks.
        var d = today.minusWeeks(8).plusDays(1)
        val rows = ArrayList<TxnRow>()
        while (!d.isAfter(today)) {
            when (d.dayOfWeek) {
                java.time.DayOfWeek.SATURDAY -> rows += row(d, 1_000)
                java.time.DayOfWeek.SUNDAY -> Unit
                else -> rows += row(d, 100)
            }
            d = d.plusDays(1)
        }
        val texts = Insights.highlights(rows, emptyMap(), cats, today, max = 5).map { it.text }
        // Weekend per day = 1000/2 = ₹500, weekday = ₹100 → 5×
        assertTrue(texts.toString(), texts.any { it.startsWith("Weekends cost you 5× weekdays (₹500 vs ₹100 a day)") })
    }

    @Test fun frequentMerchantAndNoSpendDays() {
        val rows = (1..5).map { row(today.withDayOfMonth(it * 2), 200, sub = "Swiggy") }
        val texts = Insights.highlights(rows, emptyMap(), cats, today, max = 5).map { it.text }
        assertTrue(texts.toString(), texts.contains("Swiggy: 5 times, ₹1,000 this month"))
        assertTrue(texts.toString(), texts.contains("10 no-spend days this month"))
    }

    @Test fun quietMonthSaysNothing() {
        assertTrue(Insights.highlights(listOf(row(today, 50)), emptyMap(), cats, LocalDate.of(2026, 11, 3)).isEmpty())
    }
}
