package com.kye.app.domain

import com.kye.app.data.CreditCard
import com.kye.app.data.Txn
import com.kye.app.data.TxnType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Where a credit card stands today.
 * - [unbilled]: spent since the last bill date (goes on the next bill).
 * - [lastBill]: what the last bill (statement) came to.
 * - [due]: what's still to pay from bills already made: everything spent up to the last bill date,
 *   minus everything paid so far. Older unpaid bills carry over.
 */
data class CardStatus(
    val card: CreditCard,
    val lastBillDate: LocalDate,
    val nextBillDate: LocalDate,
    val dueDate: LocalDate,
    val lastBill: Long,
    val unbilled: Long,
    val paidSinceBill: Long,
    val due: Long,
    val outstanding: Long
) {
    fun isOverdue(today: LocalDate): Boolean = due > 0 && today.isAfter(dueDate)
    val available: Long? get() = card.limitPaise?.let { (it - outstanding).coerceAtLeast(0) }
}

/** Credit card bill maths. Pure Kotlin, unit-tested. */
object Cards {
    const val REMIND_DAYS = 3L

    /** The bill date in [month]: the card's bill day, or the month's last day if shorter. */
    fun billDateIn(card: CreditCard, month: YearMonth): LocalDate =
        month.atDay(card.billDay.coerceIn(1, month.lengthOfMonth()))

    /** The latest bill date on or before [today]. */
    fun lastBillDate(card: CreditCard, today: LocalDate): LocalDate {
        val thisMonth = billDateIn(card, YearMonth.from(today))
        return if (!thisMonth.isAfter(today)) thisMonth else billDateIn(card, YearMonth.from(today).minusMonths(1))
    }

    fun status(card: CreditCard, txns: List<Txn>, today: LocalDate): CardStatus {
        val mine = txns.filter { it.cardId == card.id }
        val last = lastBillDate(card, today)
        val prev = billDateIn(card, YearMonth.from(last).minusMonths(1))
        val next = billDateIn(card, YearMonth.from(last).plusMonths(1))
        fun day(t: Txn) = Dates.toLocalDate(t.dateTime)
        val spends = mine.filter { it.type == TxnType.EXPENSE }
        val payments = mine.filter { it.type == TxnType.TRANSFER }
        val spentToBill = spends.filter { !day(it).isAfter(last) }.sumOf { it.amountPaise }
        val unbilled = spends.filter { day(it).isAfter(last) }.sumOf { it.amountPaise }
        val lastBill = spends.filter { day(it).isAfter(prev) && !day(it).isAfter(last) }.sumOf { it.amountPaise }
        val paid = payments.sumOf { it.amountPaise }
        val paidSince = payments.filter { day(it).isAfter(last) }.sumOf { it.amountPaise }
        return CardStatus(
            card = card,
            lastBillDate = last,
            nextBillDate = next,
            dueDate = last.plusDays(card.dueDays.toLong()),
            lastBill = lastBill,
            unbilled = unbilled,
            paidSinceBill = paidSince,
            due = (spentToBill - paid).coerceAtLeast(0),
            outstanding = (spentToBill + unbilled - paid).coerceAtLeast(0)
        )
    }

    /** Remind once per bill, from [REMIND_DAYS] days before it's due, while something is still due. */
    fun shouldRemind(s: CardStatus, today: LocalDate): Boolean =
        s.card.remind && !s.card.isClosed && s.due > 0 &&
            !today.isBefore(s.dueDate.minusDays(REMIND_DAYS)) && s.card.lastRemindedFor != s.dueDate.toEpochDay()

    fun check(c: CreditCard): String? = when {
        c.name.isBlank() -> "Enter the card's name"
        c.billDay !in 1..31 -> "Bill date should be a day of the month (1–31)"
        c.dueDays !in 1..60 -> "Days to pay should be between 1 and 60"
        c.last4.isNotEmpty() && !c.last4.matches(Regex("\\d{4}")) -> "Last 4 digits should be 4 numbers"
        c.limitPaise != null && c.limitPaise <= 0 -> "Limit must be more than zero, or leave it empty"
        else -> null
    }
}
