package com.kye.app.domain

import com.kye.app.data.Loan
import com.kye.app.data.LoanDirection
import com.kye.app.data.LoanRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LoansTest {
    private val today = LocalDate.of(2026, 11, 15)

    private fun row(
        rupees: Long, paid: Long = 0, dir: LoanDirection = LoanDirection.LENT,
        due: LocalDate? = null, closed: Boolean = false, reminded: LocalDate? = null, person: String = "Ramesh Kumar"
    ) = LoanRow(
        Loan(
            person = person, direction = dir, amountPaise = rupees * 100, date = LocalDate.of(2026, 11, 7).toEpochDay(),
            dueDate = due?.toEpochDay(), isClosed = closed, lastRemindedOn = reminded?.toEpochDay()
        ),
        paidPaise = paid * 100
    )

    @Test fun summaryAddsWhatsLeftOnOpenLoans() {
        val s = Loans.summary(
            listOf(
                row(2_000, paid = 500),                                // 1,500 to get
                row(1_000, dir = LoanDirection.BORROWED),              // 1,000 to give
                row(800, due = today.minusDays(1)),                    // 800 to get, overdue
                row(5_000, closed = true),                             // settled: ignored
                row(300, paid = 300)                                   // fully repaid: ignored
            ),
            today
        )
        assertEquals(2_300_00L, s.toGet)
        assertEquals(1_000_00L, s.toGive)
        assertEquals(1, s.overdue)
        assertEquals(3, s.open)
    }

    @Test fun overdueOnlyAfterTheDueDay() {
        assertFalse(Loans.isOverdue(row(100, due = today), today))
        assertTrue(Loans.isOverdue(row(100, due = today.minusDays(1)), today))
        assertFalse("no due date", Loans.isOverdue(row(100), today))
        assertFalse("settled", Loans.isOverdue(row(100, due = today.minusDays(9), closed = true), today))
    }

    @Test fun remindOnTheDueDayThenWeekly() {
        assertFalse("not due yet", Loans.shouldRemind(row(100, due = today.plusDays(1)), today))
        assertTrue("due today", Loans.shouldRemind(row(100, due = today), today))
        assertFalse("reminded 3 days ago", Loans.shouldRemind(row(100, due = today.minusDays(10), reminded = today.minusDays(3)), today))
        assertTrue("reminded a week ago", Loans.shouldRemind(row(100, due = today.minusDays(10), reminded = today.minusDays(7)), today))
        assertFalse("repaid", Loans.shouldRemind(row(100, paid = 100, due = today), today))
        assertFalse("no due date", Loans.shouldRemind(row(100), today))
    }

    @Test fun paymentChecks() {
        val r = row(2_000, paid = 500)
        assertNull(Loans.checkPayment(r, 1_500_00))
        assertNull(Loans.checkPayment(r, 200_00))
        assertEquals("That's more than what's left (₹1,500)", Loans.checkPayment(r, 1_600_00))
        assertEquals("Enter an amount", Loans.checkPayment(r, 0))
    }

    @Test fun reminderMessageUsesFirstNameAndWhatsLeft() {
        assertEquals(
            "Hi Ramesh, a gentle reminder about the ₹1,500 from 7 Nov. Please send it when you can. Thanks!",
            Loans.reminderMessage(row(2_000, paid = 500))
        )
        assertTrue(Loans.reminderMessage(row(1_000, dir = LoanDirection.BORROWED, person = "Selvi")).startsWith("Hi Selvi, I haven't forgotten the ₹1,000"))
    }
}
