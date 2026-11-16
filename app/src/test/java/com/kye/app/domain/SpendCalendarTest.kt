package com.kye.app.domain

import com.kye.app.data.PaymentMode
import com.kye.app.data.Txn
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class SpendCalendarTest {
    private var id = 1L
    private fun row(d: LocalDate, rupees: Long, type: TxnType = TxnType.EXPENSE) = TxnRow(
        txn = Txn(
            id = id++, amountPaise = rupees * 100, type = type, categoryId = null, subcategoryId = null,
            dateTime = Dates.atMillis(d, LocalTime.NOON), paymentMode = PaymentMode.UPI
        ),
        catName = null, catEmoji = null, catColor = null, subName = null, subIsDefault = null
    )

    private val today = LocalDate.of(2026, 11, 16)

    @Test fun totalsPerDayOnlyExpensesInTheMonth() {
        val rows = listOf(
            row(LocalDate.of(2026, 11, 2), 100),
            row(LocalDate.of(2026, 11, 2), 50),
            row(LocalDate.of(2026, 11, 5), 400),
            row(LocalDate.of(2026, 11, 5), 999, TxnType.SAVING),
            row(LocalDate.of(2026, 10, 31), 70)
        )
        val m = SpendCalendar.month(rows, YearMonth.of(2026, 11), today)
        assertEquals(150_00L, m.byDay[LocalDate.of(2026, 11, 2)])
        assertEquals(400_00L, m.byDay[LocalDate.of(2026, 11, 5)])
        assertNull(m.byDay[LocalDate.of(2026, 11, 3)])
        assertEquals(550_00L, m.total)
        assertEquals(400_00L, m.max)
        // 16 days so far, 2 with spending.
        assertEquals(14, m.noSpendDays)
    }

    @Test fun pastMonthCountsEveryDayFutureMonthNone() {
        assertEquals(31, SpendCalendar.month(emptyList(), YearMonth.of(2026, 10), today).noSpendDays)
        assertEquals(0, SpendCalendar.month(emptyList(), YearMonth.of(2026, 12), today).noSpendDays)
    }

    @Test fun levels() {
        assertEquals(0, SpendCalendar.level(0, 1000))
        assertEquals(1, SpendCalendar.level(1, 1000))
        assertEquals(2, SpendCalendar.level(500, 1000))
        assertEquals(4, SpendCalendar.level(1000, 1000))
    }

    @Test fun weeksStartOnSunday() {
        // 1 Nov 2026 is a Sunday; November has 30 days -> 5 weeks.
        val w = SpendCalendar.weeks(YearMonth.of(2026, 11))
        assertEquals(5, w.size)
        assertEquals(LocalDate.of(2026, 11, 1), w[0][0])
        assertEquals(LocalDate.of(2026, 11, 30), w[4][1])
        assertNull(w[4][2])
        // 1 Oct 2026 is a Thursday -> four blanks first.
        val oct = SpendCalendar.weeks(YearMonth.of(2026, 10))
        assertNull(oct[0][3])
        assertEquals(LocalDate.of(2026, 10, 1), oct[0][4])
    }
}
