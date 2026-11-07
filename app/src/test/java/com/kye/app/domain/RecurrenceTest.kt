package com.kye.app.domain

import com.kye.app.data.RecurringRule
import com.kye.app.data.RepeatUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun occ(anchor: LocalDate, unit: RepeatUnit, interval: Int, i: Int) = Recurrence.occurrence(anchor, unit, interval, i)

    @Test fun monthlyOnThe31stUsesLastDayOfShortMonthsAndComesBack() {
        val a = d(2026, 1, 31)
        assertEquals(d(2026, 2, 28), occ(a, RepeatUnit.MONTH, 1, 1))
        assertEquals(d(2026, 3, 31), occ(a, RepeatUnit.MONTH, 1, 2))
        assertEquals(d(2026, 4, 30), occ(a, RepeatUnit.MONTH, 1, 3))
        assertEquals(d(2026, 5, 31), occ(a, RepeatUnit.MONTH, 1, 4))
    }

    @Test fun monthlyOnThe30thInALeapYear() {
        assertEquals(d(2028, 2, 29), occ(d(2028, 1, 30), RepeatUnit.MONTH, 1, 1))
    }

    @Test fun yearlyOn29Feb() {
        val a = d(2024, 2, 29)
        assertEquals(d(2025, 2, 28), occ(a, RepeatUnit.YEAR, 1, 1))
        assertEquals(d(2028, 2, 29), occ(a, RepeatUnit.YEAR, 1, 4))
    }

    @Test fun everyNDaysWeeksMonths() {
        val a = d(2026, 11, 7)
        assertEquals(d(2026, 11, 17), occ(a, RepeatUnit.DAY, 10, 1))
        assertEquals(d(2026, 11, 21), occ(a, RepeatUnit.WEEK, 2, 1))
        assertEquals(d(2027, 2, 7), occ(a, RepeatUnit.MONTH, 3, 1))
        assertEquals(d(2026, 11, 7), occ(a, RepeatUnit.MONTH, 3, 0))
    }

    @Test fun intervalBelowOneIsTreatedAsOne() {
        assertEquals(d(2026, 12, 7), occ(d(2026, 11, 7), RepeatUnit.MONTH, 0, 1))
    }

    @Test fun firstIndexOnOrAfter() {
        val a = d(2026, 1, 15)
        assertEquals(0, Recurrence.firstIndexOnOrAfter(a, RepeatUnit.MONTH, 1, d(2026, 1, 10)))
        assertEquals(0, Recurrence.firstIndexOnOrAfter(a, RepeatUnit.MONTH, 1, d(2026, 1, 15)))
        assertEquals(3, Recurrence.firstIndexOnOrAfter(a, RepeatUnit.MONTH, 1, d(2026, 3, 16)))
        assertEquals(2, Recurrence.firstIndexOnOrAfter(a, RepeatUnit.MONTH, 1, d(2026, 3, 15)))
    }

    private fun rule(unit: RepeatUnit, interval: Int, amount: Long) = RecurringRule(
        name = "x", amountPaise = amount, categoryId = null, subcategoryId = null,
        unit = unit, interval = interval, anchorDate = 0, nextDueDate = 0
    )

    @Test fun monthlyEquivalent() {
        assertEquals(1_000_00L, Recurrence.monthlyEquivalent(rule(RepeatUnit.YEAR, 1, 12_000_00)))
        assertEquals(1_000_00L, Recurrence.monthlyEquivalent(rule(RepeatUnit.MONTH, 3, 3_000_00)))
        assertEquals(5_000_00L, Recurrence.monthlyEquivalent(rule(RepeatUnit.MONTH, 1, 5_000_00)))
        assertEquals(433_33L, Recurrence.monthlyEquivalent(rule(RepeatUnit.WEEK, 1, 100_00)))
        assertEquals(3_041_66L, Recurrence.monthlyEquivalent(rule(RepeatUnit.DAY, 1, 100_00)))
    }

    @Test fun describe() {
        assertEquals("Monthly", Recurrence.describe(RepeatUnit.MONTH, 1))
        assertEquals("Every 3 months", Recurrence.describe(RepeatUnit.MONTH, 3))
        assertEquals("Every 2 weeks", Recurrence.describe(RepeatUnit.WEEK, 2))
    }

    @Test fun financialYearStartsInApril() {
        assertEquals(d(2026, 4, 1), Dates.fyStart(d(2026, 4, 1)))
        assertEquals(d(2026, 4, 1), Dates.fyStart(d(2027, 3, 31)))
        assertEquals(d(2025, 4, 1), Dates.fyStart(d(2026, 3, 31)))
    }
}
