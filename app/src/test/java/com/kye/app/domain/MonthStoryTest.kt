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

class MonthStoryTest {
    private var id = 1L
    private fun row(
        d: LocalDate, rupees: Long, cat: String? = "Food", emoji: String? = "🍛", sub: String? = null,
        type: TxnType = TxnType.EXPENSE
    ) = TxnRow(
        txn = Txn(
            id = id++, amountPaise = rupees * 100, type = type, categoryId = null, subcategoryId = null,
            dateTime = Dates.atMillis(d, LocalTime.NOON), paymentMode = PaymentMode.UPI
        ),
        catName = cat, catEmoji = emoji, catColor = null, subName = sub, subIsDefault = if (sub == null) null else false
    )

    private val oct = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 11, 16)
    private fun d(day: Int) = oct.atDay(day)

    @Test fun totalsTopCategoriesAndComparison() {
        val rows = listOf(
            row(d(2), 300, sub = "Swiggy"),
            row(d(9), 200, sub = "Swiggy"),
            row(d(9), 1000, cat = "Shopping", emoji = "🛍️"),
            row(d(20), 100, cat = "Travel", emoji = "🚕"),
            row(d(21), 50, cat = "Bills", emoji = "💡"),
            row(d(5), 5000, type = TxnType.SAVING, cat = "Savings"),
            row(d(1), 60000, type = TxnType.INCOME, cat = "Salary"),
            row(LocalDate.of(2026, 9, 10), 2200)
        )
        val s = MonthStory.build(rows, oct, today)
        assertEquals(1650_00L, s.spent)
        assertEquals(5, s.count)
        assertEquals(2200_00L, s.prevSpent)
        assertEquals(-25, s.changePercent)
        assertEquals(listOf("Shopping", "Food", "Travel"), s.topCategories.map { it.first })
        assertEquals(d(9), s.biggestDay)
        assertEquals(1200_00L, s.biggestDayTotal)
        assertEquals("Shopping", s.biggestDayOn)
        assertEquals(MonthStory.Favourite("Swiggy", "🍛", 2, 500_00L), s.favourite)
        assertEquals(5000_00L, s.saved)
        assertEquals(60000_00L, s.income)
    }

    @Test fun noSpendDaysAndBestStreak() {
        // Spending on the 2nd, 9th, 20th and 21st of 31 days -> 27 free days; longest run is 10..19.
        val rows = listOf(row(d(2), 1), row(d(9), 1), row(d(20), 1), row(d(21), 1))
        val s = MonthStory.build(rows, oct, today)
        assertEquals(27, s.noSpendDays)
        assertEquals(10, s.bestStreak)
    }

    @Test fun currentMonthCountsOnlyUpToToday() {
        val s = MonthStory.build(listOf(row(today, 10)), YearMonth.from(today), today)
        assertEquals(15, s.noSpendDays)
        assertNull(s.changePercent)
        assertNull(s.favourite)
    }
}
