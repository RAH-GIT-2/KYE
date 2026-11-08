package com.kye.app.domain

import com.kye.app.data.Event
import com.kye.app.data.PaymentMode
import com.kye.app.data.Txn
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EventsTest {
    private val today = LocalDate.of(2026, 11, 1)

    private fun ev(
        id: Long, name: String, start: LocalDate? = null, end: LocalDate? = null, closed: Boolean = false, created: Long = id
    ) = Event(id = id, name = name, emoji = "🪔", startDate = start?.toEpochDay(), endDate = end?.toEpochDay(), isClosed = closed, createdAt = created)

    private val deepavali = ev(1, "Deepavali 2026", today.minusDays(7), today.plusDays(7))
    private val goa = ev(2, "Goa trip")                       // no dates
    private val wedding = ev(3, "Priya's wedding", today.plusDays(20), today.plusDays(22))
    private val old = ev(4, "Pongal 2026", closed = true)

    @Test fun activeMeansOpenAndWithinDates() {
        assertTrue(Events.isActive(deepavali, today))
        assertTrue("no dates = running until marked done", Events.isActive(goa, today))
        assertFalse("not started", Events.isActive(wedding, today))
        assertFalse("done", Events.isActive(old, today))
        assertTrue("last day counts", Events.isActive(deepavali, today.plusDays(7)))
        assertFalse(Events.isActive(deepavali, today.plusDays(8)))
    }

    @Test fun preselectOnlyDatedRunningEvents() {
        // An undated event would tag everything forever, so it's never pre-selected.
        assertEquals(deepavali, Events.preselect(listOf(goa, deepavali, wedding, old), today))
        assertNull(Events.preselect(listOf(goa, wedding, old), today))
        val newer = ev(5, "Diwali shopping", today.minusDays(1), today.plusDays(2), created = 99)
        assertEquals("most recently created wins", newer, Events.preselect(listOf(deepavali, newer), today))
    }

    @Test fun mentionedByName() {
        val all = listOf(deepavali, goa, wedding, old)
        assertEquals(deepavali, Events.mentioned("crackers for deepavali", all))
        assertEquals(deepavali, Events.mentioned("Deepavalikku dress", all))
        assertEquals(wedding, Events.mentioned("gift priya", all))
        assertNull("'trip' and years don't count on their own", Events.mentioned("bus trip 2026", all))
        assertNull("done events aren't offered", Events.mentioned("pongal sweets", all))
        assertNull(Events.mentioned("petrol", all))
    }

    private fun row(rupees: Long, cat: String, type: TxnType = TxnType.EXPENSE) = TxnRow(
        Txn(amountPaise = rupees * 100, type = type, categoryId = cat.hashCode().toLong(), subcategoryId = null,
            dateTime = Dates.atMillis(today, LocalTime.NOON), paymentMode = PaymentMode.UPI),
        catName = cat, catEmoji = "•", catColor = 0, subName = null, subIsDefault = null
    )

    @Test fun breakdownByCategory() {
        val b = Events.breakdown(listOf(row(4_500, "Crackers"), row(8_000, "Clothes"), row(500, "Crackers"), row(9_000, "Gift", TxnType.INCOME)))
        assertEquals(listOf("Clothes" to 8_000_00L, "Crackers" to 5_000_00L), b.map { it.name to it.total })
    }

    @Test fun budgetAlerts() {
        assertEquals("Deepavali budget 80% used", Events.alert("Deepavali", 15_000_00, 20_000_00, 25_000_00))
        assertEquals("Deepavali budget exceeded", Events.alert("Deepavali", 24_000_00, 26_000_00, 25_000_00))
        assertNull(Events.alert("Deepavali", 21_000_00, 22_000_00, 25_000_00))
        assertNull("no budget", Events.alert("Goa", 0, 99_000_00, null))
    }

    @Test fun dateLabels() {
        assertEquals("25 Oct – 8 Nov", Events.dates(deepavali))
        assertNull(Events.dates(goa))
        assertEquals("from 1 Nov", Events.dates(ev(9, "x", start = today)))
    }
}
