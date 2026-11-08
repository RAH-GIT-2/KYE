package com.kye.app.domain

import com.kye.app.data.PaymentMode
import com.kye.app.data.Source
import com.kye.app.data.Txn
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RepeatsTest {
    private val today = LocalDate.of(2026, 11, 15)
    private var nextId = 1L

    private fun row(
        daysAgo: Long, rupees: Long, note: String = "", cat: Long = 1, sub: Long? = 10, subName: String? = "Tea & Snacks",
        type: TxnType = TxnType.EXPENSE, source: Source = Source.MANUAL
    ) = TxnRow(
        Txn(
            id = nextId++, amountPaise = rupees * 100, type = type, categoryId = cat, subcategoryId = sub,
            dateTime = Dates.atMillis(today.minusDays(daysAgo), LocalTime.of(8, 0)), paymentMode = PaymentMode.CASH,
            note = note, source = source
        ),
        catName = "Food & Dining", catEmoji = "🍛", catColor = 1, subName = subName, subIsDefault = false
    )

    @Test fun suggestsThingsAddedThreeTimesWithTheSameAmount() {
        val rows = listOf(
            row(0, 20, "Tea"), row(1, 20, "tea "), row(2, 20, "Tea"), row(3, 20, "Tea"), // 4× tea ₹20
            row(1, 120, sub = 20, subName = "Auto"), row(5, 120, sub = 20, subName = "Auto"), row(9, 120, sub = 20, subName = "Auto"),
            row(1, 25, "Tea"), row(2, 30, "Tea") // different amounts: not the same thing
        )
        val s = Repeats.suggest(rows, today)
        assertEquals(listOf("Tea", "Auto"), s.map { it.label })
        assertEquals(4, s[0].times)
        assertEquals(20_00L, s[0].amountPaise)
        assertEquals(PaymentMode.CASH, s[0].template.paymentMode)
    }

    @Test fun ignoresOldRecurringAndNonExpenses() {
        val rows = listOf(
            row(31, 20, "Tea"), row(40, 20, "Tea"), row(45, 20, "Tea"),                 // older than 30 days
            row(1, 500, source = Source.RECURRING), row(2, 500, source = Source.RECURRING), row(3, 500, source = Source.RECURRING),
            row(1, 900, type = TxnType.SAVING), row(2, 900, type = TxnType.SAVING), row(3, 900, type = TxnType.SAVING)
        )
        assertTrue(Repeats.suggest(rows, today).isEmpty())
    }

    @Test fun againMakesAFreshEntry() {
        val t = row(3, 20, "Tea", source = Source.VOICE).txn.copy(rawVoiceText = "tea 20", recurringRuleId = 5)
        val now = Dates.atMillis(today, LocalTime.NOON)
        val a = Repeats.again(t, now)
        assertEquals(0L, a.id)
        assertEquals(now, a.dateTime)
        assertEquals(Source.MANUAL, a.source)
        assertEquals(null, a.rawVoiceText)
        assertEquals(null, a.recurringRuleId)
        assertEquals(t.amountPaise, a.amountPaise)
        assertEquals(t.note, a.note)
        assertEquals(t.subcategoryId, a.subcategoryId)
    }

    @Test fun atMostFour() {
        val rows = (1..6).flatMap { k -> (0..2).map { d -> row(d.toLong(), k * 10L, "Item $k", sub = k.toLong()) } }
        assertEquals(4, Repeats.suggest(rows, today).size)
    }
}
