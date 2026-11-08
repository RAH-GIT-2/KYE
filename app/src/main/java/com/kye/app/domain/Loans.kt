package com.kye.app.domain

import com.kye.app.data.Loan
import com.kye.app.data.LoanDirection
import com.kye.app.data.LoanRow
import java.time.LocalDate

data class LoanSummary(
    /** Still owed to you. */
    val toGet: Long = 0,
    /** Still owed by you. */
    val toGive: Long = 0,
    val overdue: Int = 0,
    val open: Int = 0
)

/** Lent / borrowed rules. Pure Kotlin, unit-tested. */
object Loans {
    /** Reminders repeat this often while a loan stays overdue. */
    const val REMIND_EVERY_DAYS = 7L

    fun isOverdue(row: LoanRow, today: LocalDate): Boolean =
        !row.loan.isClosed && row.remaining > 0 && row.loan.dueDate != null && row.loan.dueDate < today.toEpochDay()

    fun summary(rows: List<LoanRow>, today: LocalDate): LoanSummary {
        val open = rows.filter { !it.loan.isClosed && it.remaining > 0 }
        return LoanSummary(
            toGet = open.filter { it.loan.direction == LoanDirection.LENT }.sumOf { it.remaining },
            toGive = open.filter { it.loan.direction == LoanDirection.BORROWED }.sumOf { it.remaining },
            overdue = open.count { isOverdue(it, today) },
            open = open.size
        )
    }

    /** On the due date, then every [REMIND_EVERY_DAYS] days while still unpaid. */
    fun shouldRemind(row: LoanRow, today: LocalDate): Boolean {
        val due = row.loan.dueDate ?: return false
        if (row.loan.isClosed || row.remaining <= 0 || due > today.toEpochDay()) return false
        val last = row.loan.lastRemindedOn ?: return true
        return today.toEpochDay() - last >= REMIND_EVERY_DAYS
    }

    /** A paid amount can't be more than what's left; null when it's fine, otherwise the problem. */
    fun checkPayment(row: LoanRow, amount: Long): String? = when {
        amount <= 0 -> "Enter an amount"
        amount > row.remaining -> "That's more than what's left (${Money.format(row.remaining, showPaise = true)})"
        else -> null
    }

    /** Closed once nothing is left; reopened if a repayment is deleted. */
    fun closedAfter(loan: Loan, paid: Long): Boolean = paid >= loan.amountPaise

    /** Friendly text to send from WhatsApp / SMS. */
    fun reminderMessage(row: LoanRow): String {
        val first = row.loan.person.trim().substringBefore(' ')
        val amount = Money.format(row.remaining, showPaise = row.remaining % 100 != 0L)
        val since = Dates.short(LocalDate.ofEpochDay(row.loan.date))
        return when (row.loan.direction) {
            LoanDirection.LENT -> "Hi $first, a gentle reminder about the $amount from $since. Please send it when you can. Thanks!"
            LoanDirection.BORROWED -> "Hi $first, I haven't forgotten the $amount from $since. I'll send it soon. Thanks!"
        }
    }
}
