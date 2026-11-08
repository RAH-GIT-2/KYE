package com.kye.app.domain

import com.kye.app.data.Category
import com.kye.app.data.CategoryKind
import com.kye.app.data.GoldScheme
import com.kye.app.data.GoldSchemeRow
import com.kye.app.data.PaymentMode
import com.kye.app.data.Txn
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class GoldSchemesTest {
    private val start = LocalDate.of(2026, 6, 5)

    private fun row(paidRupees: Long, closed: Boolean = false, remindedFor: Long? = null, remind: Boolean = true) = GoldSchemeRow(
        GoldScheme(
            id = 1, name = "GRT Golden 11", monthlyPaise = 5_000_00, months = 11, startDate = start.toEpochDay(),
            bonusPaise = 5_000_00, isClosed = closed, lastRemindedFor = remindedFor, remind = remind
        ),
        paidRupees * 100, 0
    )

    @Test fun datesAndTotals() {
        val s = row(0).scheme
        assertEquals(LocalDate.of(2026, 7, 5), GoldSchemes.dueDate(s, 1))
        assertEquals(55_000_00L, GoldSchemes.totalToPay(s))
        assertEquals(60_000_00L, GoldSchemes.maturityValue(s))
        assertEquals(LocalDate.of(2027, 5, 5), GoldSchemes.maturityDate(s))
    }

    @Test fun day31FallsBackToMonthEndWithoutDrifting() {
        val s = row(0).scheme.copy(startDate = LocalDate.of(2026, 1, 31).toEpochDay())
        assertEquals(LocalDate.of(2026, 2, 28), GoldSchemes.dueDate(s, 1))
        assertEquals(LocalDate.of(2026, 3, 31), GoldSchemes.dueDate(s, 2))
    }

    @Test fun paidCountsWholeInstalmentsAndNextDue() {
        val r = row(15_000)
        assertEquals(3, GoldSchemes.instalmentsPaid(r))
        assertEquals(LocalDate.of(2026, 9, 5), GoldSchemes.nextDue(r))
        assertEquals(27, GoldSchemes.percent(r))
        assertEquals(40_000_00L, GoldSchemes.remaining(r))
        // A part payment doesn't count as an instalment.
        assertEquals(3, GoldSchemes.instalmentsPaid(row(17_000)))
    }

    @Test fun overdueCount() {
        // By 15 Nov, instalments for Jun–Nov (6) were due; 3 paid → 3 missed.
        assertEquals(3, GoldSchemes.overdueCount(row(15_000), LocalDate.of(2026, 11, 15)))
        assertEquals(0, GoldSchemes.overdueCount(row(30_000), LocalDate.of(2026, 11, 15)))
        assertEquals(0, GoldSchemes.overdueCount(row(15_000, closed = true), LocalDate.of(2026, 11, 15)))
    }

    @Test fun fullyPaidAndRedeem() {
        val r = row(55_000)
        assertTrue(GoldSchemes.isFullyPaid(r))
        assertNull(GoldSchemes.nextDue(r))
        assertFalse(GoldSchemes.isReadyToRedeem(r, LocalDate.of(2027, 5, 4)))
        assertTrue(GoldSchemes.isReadyToRedeem(r, LocalDate.of(2027, 5, 5)))
        assertFalse(GoldSchemes.isReadyToRedeem(row(55_000, closed = true), LocalDate.of(2027, 6, 1)))
    }

    @Test fun remindsOncePerInstalmentOnTheDueDay() {
        val due = LocalDate.of(2026, 9, 5)
        val added = start
        assertFalse(GoldSchemes.shouldRemind(row(15_000), due.minusDays(1), added))
        assertTrue(GoldSchemes.shouldRemind(row(15_000), due, added))
        assertTrue("missed: first check after", GoldSchemes.shouldRemind(row(15_000), due.plusDays(3), added))
        assertFalse(GoldSchemes.shouldRemind(row(15_000, remindedFor = due.toEpochDay()), due.plusDays(1), added))
        assertFalse(GoldSchemes.shouldRemind(row(15_000, remind = false), due, added))
        assertFalse(GoldSchemes.shouldRemind(row(55_000), LocalDate.of(2027, 6, 1), added))
        assertFalse(GoldSchemes.shouldRemind(row(15_000, closed = true), due, added))
    }

    @Test fun noReminderForInstalmentsDueBeforeTheSchemeWasAdded() {
        // Added to KYE in November for a scheme that started in June: the June instalment isn't nagged.
        assertFalse(GoldSchemes.shouldRemind(row(0), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 8)))
        // Once paid up to date, the next one (5 Dec) is reminded as usual.
        assertTrue(GoldSchemes.shouldRemind(row(30_000), LocalDate.of(2026, 12, 5), LocalDate.of(2026, 11, 8)))
    }

    @Test fun checkRejectsBadInput() {
        val s = row(0).scheme
        assertNull(GoldSchemes.check(s))
        assertNotNull(GoldSchemes.check(s.copy(name = " ")))
        assertNotNull(GoldSchemes.check(s.copy(monthlyPaise = 0)))
        assertNotNull(GoldSchemes.check(s.copy(months = 0)))
        assertNotNull(GoldSchemes.check(s.copy(months = 61)))
        assertNotNull(GoldSchemes.check(s.copy(bonusPaise = -1)))
    }
}

class ReportsTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val food = Category(id = 1, name = "Food", emoji = "🍛", color = 0)
    private val rent = Category(id = 2, name = "Rent", emoji = "🏠", color = 0)
    private val cats = mapOf(1L to food, 2L to rent)
    private var nextId = 1L

    private fun t(rupees: Long, type: TxnType, cat: Long?, day: LocalDate, mode: PaymentMode = PaymentMode.UPI) = TxnRow(
        Txn(id = nextId++, amountPaise = rupees * 100, type = type, categoryId = cat, subcategoryId = null,
            dateTime = com.kye.app.domain.Dates.atMillis(day, LocalTime.NOON), paymentMode = mode),
        catName = cats[cat]?.name, catEmoji = null, catColor = null, subName = null, subIsDefault = null
    )

    @Test fun buildsTotalsCategoriesBudgetsAndComparison() {
        val oct = LocalDate.of(2026, 10, 1)
        val txns = listOf(
            t(50_000, TxnType.INCOME, null, oct),
            t(15_000, TxnType.EXPENSE, 2, oct.plusDays(1)),
            t(400, TxnType.EXPENSE, 1, oct.plusDays(2), PaymentMode.CASH),
            t(600, TxnType.EXPENSE, 1, oct.plusDays(3)),
            t(5_000, TxnType.SAVING, null, oct.plusDays(4)),
            t(10_000, TxnType.EXPENSE, 2, LocalDate.of(2026, 9, 3)), // September
            t(99, TxnType.EXPENSE, 1, LocalDate.of(2026, 11, 1))      // November
        )
        val r = Reports.build(txns, YearMonth.of(2026, 10), mapOf(0L to 20_000_00L, 1L to 800_00L), cats, today)
        assertEquals(50_000_00L, r.income)
        assertEquals(16_000_00L, r.expense)
        assertEquals(5_000_00L, r.saving)
        assertEquals(29_000_00L, r.leftOver)
        assertEquals(5, r.entries.size)
        assertEquals(listOf("Rent", "Food"), r.categories.map { it.name })
        assertEquals(2, r.categories[1].count)
        assertEquals(31, r.days)
        assertEquals(16_000_00L / 31, r.dailyAverage)
        assertEquals(60, r.changePercent) // 16,000 vs 10,000
        assertEquals(listOf("Overall", "Food"), r.budgets.map { it.name })
        assertEquals(1_000_00L, r.budgets[1].spent)
        assertEquals(PaymentMode.UPI, r.payments.first().first)
        assertEquals(15_000_00L, r.topExpenses.first().txn.amountPaise)
        assertTrue(r.entries.zipWithNext().all { (a, b) -> a.txn.dateTime <= b.txn.dateTime })
    }

    @Test fun currentMonthCountsDaysSoFarAndEmptyMonth() {
        val r = Reports.build(emptyList(), YearMonth.of(2026, 11), emptyMap(), cats, today)
        assertTrue(r.isEmpty)
        assertEquals(8, r.days)
        assertNull(r.changePercent)
    }

    @Test fun autoSaveMonth() {
        assertEquals(YearMonth.of(2026, 10), Reports.monthToSave(null, today))
        assertEquals(YearMonth.of(2026, 10), Reports.monthToSave("2026-09", today))
        assertNull(Reports.monthToSave("2026-10", today))
        assertEquals(YearMonth.of(2026, 10), Reports.monthToSave("garbage", today))
        assertEquals("KYE-report-2026-10.pdf", Reports.fileName(YearMonth.of(2026, 10)))
        assertEquals(12, Reports.recentMonths(today).size)
        assertEquals(YearMonth.of(2026, 11), Reports.recentMonths(today).first())
    }

class YearlyReportTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val food = Category(id = 1, name = "Food", emoji = "🍛", color = 0)
    private val cats = mapOf(1L to food)
    private var nextId = 1L

    private fun t(rupees: Long, type: TxnType, day: LocalDate) = TxnRow(
        Txn(id = nextId++, amountPaise = rupees * 100, type = type, categoryId = 1, subcategoryId = null,
            dateTime = Dates.atMillis(day, LocalTime.NOON), paymentMode = PaymentMode.UPI),
        catName = "Food", catEmoji = null, catColor = null, subName = null, subIsDefault = null
    )

    @Test fun yearKeysLabelsAndFileNames() {
        val fy = ReportYear(2025, true)
        assertEquals("FY 2025–26", fy.label)
        assertEquals("KYE-report-FY2025-26.pdf", fy.fileName)
        assertEquals(YearMonth.of(2025, 4), fy.months.first())
        assertEquals(YearMonth.of(2026, 3), fy.months.last())
        assertEquals(fy, ReportYear.parse(fy.key))
        assertEquals("2026", ReportYear(2026, false).label)
        assertEquals("KYE-report-2026.pdf", ReportYear(2026, false).fileName)
        assertEquals("FY 2099–00", ReportYear(2099, true).label)
        assertNull(ReportYear.parse("xx2025"))
        assertEquals(
            listOf(ReportYear(2026, true), ReportYear(2025, true), ReportYear(2026, false), ReportYear(2025, false)),
            ReportYear.choices(today)
        )
    }

    @Test fun financialYearTotalsAndMonthRows() {
        val txns = listOf(
            t(50_000, TxnType.INCOME, LocalDate.of(2025, 4, 1)),
            t(1_000, TxnType.EXPENSE, LocalDate.of(2025, 4, 10)),
            t(2_000, TxnType.EXPENSE, LocalDate.of(2026, 3, 31)),
            t(5_000, TxnType.SAVING, LocalDate.of(2025, 12, 1)),
            t(9_999, TxnType.EXPENSE, LocalDate.of(2026, 4, 1)),  // next FY
            t(1_500, TxnType.EXPENSE, LocalDate.of(2024, 6, 1))   // previous FY
        )
        val r = Reports.buildYear(txns, ReportYear(2025, true), cats, today)
        assertEquals(12, r.months.size)
        assertEquals(50_000_00L, r.income)
        assertEquals(3_000_00L, r.expense)
        assertEquals(5_000_00L, r.saving)
        assertEquals(42_000_00L, r.leftOver)
        assertEquals(1_000_00L, r.months.first().expense)
        assertEquals(49_000_00L, r.months.first().leftOver)
        assertEquals(2_000_00L, r.months.last().expense)
        assertEquals(4, r.entryCount)
        assertEquals(12, r.monthsSoFar)
        assertEquals(100, r.changePercent) // 3,000 vs 1,500
        assertEquals(3_000_00L / 12, r.monthlyAverage)
        assertEquals(2_000_00L, r.topExpenses.first().txn.amountPaise)
    }

    @Test fun currentYearCountsMonthsSoFarAndSkipsComparison() {
        val r = Reports.buildYear(listOf(t(1_000, TxnType.EXPENSE, LocalDate.of(2026, 5, 1))), ReportYear(2026, true), cats, today)
        assertEquals(8, r.monthsSoFar) // Apr–Nov
        assertNull(r.changePercent)
        assertTrue(Reports.buildYear(emptyList(), ReportYear(2026, false), cats, today).isEmpty)
    }
}
