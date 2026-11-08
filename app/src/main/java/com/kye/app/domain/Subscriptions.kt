package com.kye.app.domain

import com.kye.app.data.Category
import com.kye.app.data.RecurringMode
import com.kye.app.data.RecurringRule
import com.kye.app.data.RepeatUnit
import java.time.LocalDate

/**
 * Subscriptions are recurring rules filed under an OTT / subscription / recharge category
 * (Netflix, Prime, Jio…). Pure Kotlin, unit-tested.
 */
object Subscriptions {
    const val NOTICE_DAYS = 7L
    private val WORDS = listOf("subscription", "ott", "recharge")

    fun isSubscription(rule: RecurringRule, cats: Map<Long, Category>): Boolean {
        val name = rule.categoryId?.let { cats[it]?.name }?.lowercase() ?: return false
        return WORDS.any { name.contains(it) }
    }

    /** What the rule costs in a year: ₹649 a month → ₹7,788; ₹1,499 a year → ₹1,499. */
    fun yearlyCost(rule: RecurringRule): Long {
        val n = rule.interval.coerceAtLeast(1)
        return when (rule.unit) {
            RepeatUnit.DAY -> rule.amountPaise * 365 / n
            RepeatUnit.WEEK -> rule.amountPaise * 52 / n
            RepeatUnit.MONTH -> rule.amountPaise * 12 / n
            RepeatUnit.YEAR -> rule.amountPaise / n
        }
    }

    /** Long cycles (3 months or more) are easy to forget, so they get a notice before renewing. */
    fun isLongCycle(rule: RecurringRule): Boolean =
        rule.unit == RepeatUnit.YEAR || (rule.unit == RepeatUnit.MONTH && rule.interval >= 3)

    /**
     * True when an Auto-add subscription with a long cycle renews within [NOTICE_DAYS] and hasn't been
     * mentioned yet for this renewal. (Remind-me rules already have their own reminder.)
     */
    fun needsRenewalNotice(rule: RecurringRule, today: LocalDate): Boolean {
        if (!rule.isActive || rule.mode != RecurringMode.AUTO_ADD || !isLongCycle(rule)) return false
        val due = rule.nextDueDate
        val t = today.toEpochDay()
        return t < due && due - t <= NOTICE_DAYS && rule.lastRemindedFor != due
    }
}
