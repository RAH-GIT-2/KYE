package com.kye.app.domain

import com.kye.app.data.CreditCard
import com.kye.app.data.PaymentMode
import com.kye.app.data.Txn
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

class CardsTest {
    private val card = CreditCard(id = 1, name = "HDFC", billDay = 5, dueDays = 20)
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)
    private fun spend(rupees: Long, on: LocalDate, cardId: Long = 1) = Txn(
        amountPaise = rupees * 100, type = TxnType.EXPENSE, categoryId = null, subcategoryId = null,
        dateTime = Dates.atMillis(on, LocalTime.NOON), paymentMode = PaymentMode.CREDIT_CARD, cardId = cardId
    )
    private fun pay(rupees: Long, on: LocalDate) = spend(rupees, on).copy(type = TxnType.TRANSFER, paymentMode = PaymentMode.NET_BANKING)

    @Test fun billDates() {
        assertEquals(d(11, 5), Cards.lastBillDate(card, d(11, 8)))
        assertEquals(d(10, 5), Cards.lastBillDate(card, d(11, 4)))
        assertEquals(d(11, 5), Cards.lastBillDate(card, d(11, 5)))
        // A 31st bill day falls on the month's last day.
        assertEquals(d(2, 28), Cards.billDateIn(card.copy(billDay = 31), YearMonth.of(2026, 2)))
    }

    @Test fun statusSplitsBilledAndUnbilledAndCountsPayments() {
        val txns = listOf(
            spend(1_000, d(10, 1)),   // previous cycle (bill 5 Oct)
            spend(2_000, d(10, 20)),  // bill of 5 Nov
            spend(3_000, d(11, 5)),   // bill of 5 Nov (on the bill date)
            spend(400, d(11, 7)),     // next bill
            spend(9_999, d(11, 7), cardId = 2), // another card
            pay(1_000, d(10, 15))     // paid the October bill
        )
        val s = Cards.status(card, txns, d(11, 8))
        assertEquals(d(11, 5), s.lastBillDate)
        assertEquals(d(12, 5), s.nextBillDate)
        assertEquals(d(11, 25), s.dueDate)
        assertEquals(5_000_00L, s.lastBill)
        assertEquals(400_00L, s.unbilled)
        assertEquals(5_000_00L, s.due)
        assertEquals(5_400_00L, s.outstanding)
        assertFalse(s.isOverdue(d(11, 25)))
        assertTrue(s.isOverdue(d(11, 26)))

        // Part payment after the bill, then the rest.
        val part = Cards.status(card, txns + pay(2_000, d(11, 10)), d(11, 12))
        assertEquals(3_000_00L, part.due)
        assertEquals(2_000_00L, part.paidSinceBill)
        val full = Cards.status(card, txns + pay(5_000, d(11, 10)), d(11, 12))
        assertEquals(0L, full.due)
        assertEquals(400_00L, full.outstanding)
    }

    @Test fun unpaidOldBillCarriesOver() {
        val txns = listOf(spend(1_000, d(9, 20)), spend(2_000, d(10, 20)))
        val s = Cards.status(card, txns, d(11, 8))
        assertEquals(2_000_00L, s.lastBill)
        assertEquals(3_000_00L, s.due)
    }

    @Test fun availableLimit() {
        val s = Cards.status(card.copy(limitPaise = 50_000_00), listOf(spend(2_000, d(11, 7))), d(11, 8))
        assertEquals(48_000_00L, s.available)
        assertNull(Cards.status(card, emptyList(), d(11, 8)).available)
    }

    @Test fun remindsOnceFromThreeDaysBefore() {
        val s = Cards.status(card, listOf(spend(2_000, d(10, 20))), d(11, 22))
        assertTrue(Cards.shouldRemind(s, d(11, 22)))
        assertFalse(Cards.shouldRemind(s, d(11, 21)))
        val told = Cards.status(card.copy(lastRemindedFor = d(11, 25).toEpochDay()), listOf(spend(2_000, d(10, 20))), d(11, 22))
        assertFalse(Cards.shouldRemind(told, d(11, 23)))
        assertFalse("nothing due", Cards.shouldRemind(Cards.status(card, emptyList(), d(11, 22)), d(11, 22)))
        assertFalse("off", Cards.shouldRemind(Cards.status(card.copy(remind = false), listOf(spend(2_000, d(10, 20))), d(11, 22)), d(11, 22)))
    }

    @Test fun checkCatchesBadInput() {
        assertNull(Cards.check(card))
        assertNotNull(Cards.check(card.copy(name = " ")))
        assertNotNull(Cards.check(card.copy(billDay = 0)))
        assertNotNull(Cards.check(card.copy(dueDays = 0)))
        assertNotNull(Cards.check(card.copy(last4 = "12a4")))
        assertNotNull(Cards.check(card.copy(limitPaise = 0)))
    }
}
