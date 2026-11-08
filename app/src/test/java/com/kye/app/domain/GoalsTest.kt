package com.kye.app.domain

import com.kye.app.data.Goal
import com.kye.app.data.GoalRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalsTest {
    private val today = LocalDate.of(2026, 11, 15)

    private fun row(target: Long, saved: Long, withdrawn: Long = 0, by: LocalDate? = null, closed: Boolean = false, name: String = "Goa trip") =
        GoalRow(Goal(name = name, emoji = "✈️", targetPaise = target * 100, targetDate = by?.toEpochDay(), isClosed = closed), saved * 100, withdrawn * 100)

    @Test fun progressCountsWithdrawals() {
        val r = row(50_000, saved = 35_000, withdrawn = 3_000)
        assertEquals(32_000_00L, r.progress)
        assertEquals(64, Goals.percent(r))
        assertEquals(18_000_00L, Goals.remaining(r))
        assertFalse(Goals.isReached(r))
        assertTrue(Goals.isReached(row(1_000, 1_200)))
        assertEquals(100, Goals.percent(row(1_000, 1_200)))
    }

    @Test fun monthlyNeededCountsThisMonth() {
        // ₹18,000 left; Nov, Dec, Jan, Feb, Mar = 5 months → ₹3,600 a month.
        assertEquals(3_600_00L, Goals.monthlyNeeded(row(50_000, 32_000, by = LocalDate.of(2027, 3, 31)), today))
        // Due this month: all of it now.
        assertEquals(18_000_00L, Goals.monthlyNeeded(row(50_000, 32_000, by = LocalDate.of(2026, 11, 30)), today))
        // Rounds up so the target is reached: ₹1,000 over 3 months = ₹333.34
        assertEquals(333_34L, Goals.monthlyNeeded(row(1_000, 0, by = LocalDate.of(2027, 1, 1)), today))
        assertNull("no date", Goals.monthlyNeeded(row(50_000, 0), today))
        assertNull("reached", Goals.monthlyNeeded(row(50_000, 50_000, by = LocalDate.of(2027, 3, 1)), today))
        assertNull("date passed", Goals.monthlyNeeded(row(50_000, 0, by = LocalDate.of(2026, 10, 1)), today))
    }

    @Test fun overdueGoals() {
        assertTrue(Goals.isOverdue(row(50_000, 10_000, by = LocalDate.of(2026, 11, 1)), today))
        assertFalse(Goals.isOverdue(row(50_000, 50_000, by = LocalDate.of(2026, 11, 1)), today))
        assertFalse(Goals.isOverdue(row(50_000, 10_000, by = LocalDate.of(2026, 11, 1), closed = true), today))
    }

    @Test fun mentionedByName() {
        val goals = listOf(row(1, 0).goal, row(1, 0, name = "New bike").goal.copy(id = 2))
        assertEquals("Goa trip", Goals.mentioned("saved 5000 for goa", goals)?.name)
        assertEquals("New bike", Goals.mentioned("bike fund 2000", goals)?.name)
        assertNull(Goals.mentioned("rent 20000", goals))
    }

    @Test fun warrantyStages() {
        val until = LocalDate.of(2027, 11, 6).toEpochDay()
        assertNull("far away", Warranties.dueStage(until, 0, LocalDate.of(2027, 10, 6)))
        assertEquals("30 days before", 1, Warranties.dueStage(until, 0, LocalDate.of(2027, 10, 7)))
        assertNull("already sent", Warranties.dueStage(until, 1, LocalDate.of(2027, 10, 20)))
        assertEquals("on the day", 2, Warranties.dueStage(until, 1, LocalDate.of(2027, 11, 6)))
        assertNull("done", Warranties.dueStage(until, 2, LocalDate.of(2027, 12, 1)))
        assertEquals("first seen after it ended: straight to the last one", 2, Warranties.dueStage(until, 0, LocalDate.of(2028, 1, 1)))
    }

    @Test fun warrantyYears() {
        assertEquals(LocalDate.of(2027, 11, 14), Warranties.until(today, 1))
        assertEquals(LocalDate.of(2028, 11, 14), Warranties.until(today, 2))
        assertTrue(Warranties.isCovered(today.toEpochDay(), today))
        assertFalse(Warranties.isCovered(today.minusDays(1).toEpochDay(), today))
    }
}
