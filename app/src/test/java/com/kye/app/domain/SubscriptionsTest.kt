package com.kye.app.domain

import com.kye.app.data.Category
import com.kye.app.data.RecurringMode
import com.kye.app.data.RecurringRule
import com.kye.app.data.RepeatUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SubscriptionsTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val cats = mapOf(
        1L to Category(id = 1, name = "OTT & Subscriptions", emoji = "🎬", color = 0),
        2L to Category(id = 2, name = "Recharges", emoji = "📱", color = 0),
        3L to Category(id = 3, name = "Rent", emoji = "🏠", color = 0)
    )

    private fun rule(cat: Long, amount: Long, unit: RepeatUnit = RepeatUnit.MONTH, every: Int = 1, due: LocalDate = today.plusDays(5),
                     mode: RecurringMode = RecurringMode.AUTO_ADD, reminded: Long? = null, active: Boolean = true) =
        RecurringRule(
            name = "x", amountPaise = amount * 100, categoryId = cat, subcategoryId = null, unit = unit, interval = every,
            anchorDate = due.toEpochDay(), nextDueDate = due.toEpochDay(), mode = mode, lastRemindedFor = reminded, isActive = active
        )

    @Test fun whichRulesAreSubscriptions() {
        assertTrue(Subscriptions.isSubscription(rule(1, 649), cats))
        assertTrue(Subscriptions.isSubscription(rule(2, 349), cats))
        assertFalse(Subscriptions.isSubscription(rule(3, 15_000), cats))
    }

    @Test fun yearlyCost() {
        assertEquals(7_788_00L, Subscriptions.yearlyCost(rule(1, 649)))
        assertEquals(1_499_00L, Subscriptions.yearlyCost(rule(1, 1_499, RepeatUnit.YEAR)))
        assertEquals(1_396_00L, Subscriptions.yearlyCost(rule(2, 349, RepeatUnit.MONTH, 3)))
    }

    @Test fun renewalNoticeOnlyForLongCyclesWithinAWeek() {
        val prime = rule(1, 1_499, RepeatUnit.YEAR, due = today.plusDays(7))
        assertTrue(Subscriptions.needsRenewalNotice(prime, today))
        assertFalse("not yet", Subscriptions.needsRenewalNotice(prime.copy(nextDueDate = today.plusDays(8).toEpochDay()), today))
        assertFalse("already told", Subscriptions.needsRenewalNotice(prime.copy(lastRemindedFor = prime.nextDueDate), today))
        assertFalse("monthly", Subscriptions.needsRenewalNotice(rule(1, 649, due = today.plusDays(3)), today))
        assertTrue("quarterly", Subscriptions.needsRenewalNotice(rule(2, 349, RepeatUnit.MONTH, 3, due = today.plusDays(3)), today))
        assertFalse("remind-me has its own", Subscriptions.needsRenewalNotice(prime.copy(mode = RecurringMode.REMIND_ONLY), today))
        assertFalse("paused", Subscriptions.needsRenewalNotice(prime.copy(isActive = false), today))
        assertFalse("due today is added, not warned", Subscriptions.needsRenewalNotice(prime.copy(nextDueDate = today.toEpochDay()), today))
    }
}
