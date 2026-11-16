package com.kye.app.domain

import com.kye.app.data.PaymentMode
import com.kye.app.data.Txn
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HistoryFilterTest {
    private val nov15 = LocalDate.of(2026, 11, 15)
    private var nextId = 1L

    private fun row(day: LocalDate, rupees: Long, note: String, cat: String?, catId: Long?, type: TxnType = TxnType.EXPENSE) = TxnRow(
        Txn(
            id = nextId++, amountPaise = rupees * 100, type = type, categoryId = catId, subcategoryId = null,
            dateTime = Dates.atMillis(day, LocalTime.NOON), paymentMode = PaymentMode.UPI, note = note
        ),
        catName = cat, catEmoji = null, catColor = null, subName = null, subIsDefault = null
    )

    private val rows = listOf(
        row(nov15, 450, "Swiggy dinner", "Food", 1),
        row(nov15, 2000, "Petrol", "Transport", 2),
        row(nov15.minusDays(1), 60_000, "Salary", "Salary", 3, TxnType.INCOME),
        row(nov15.minusDays(1), 120, "Chai", "Food", 1)
    )
    private val index = HistoryFilter.index(rows)

    private fun notes(m: Map<LocalDate, List<TxnRow>>) = m.values.flatten().map { it.txn.note }

    @Test fun everythingGroupedByDayNewestFirst() {
        val out = HistoryFilter.apply(index, "", null, null)
        assertEquals(listOf(nov15, nov15.minusDays(1)), out.keys.toList())
        assertEquals(listOf("Swiggy dinner", "Petrol", "Salary", "Chai"), notes(out))
    }

    @Test fun searchMatchesNoteCategoryAndAmountIgnoringCase() {
        assertEquals(listOf("Swiggy dinner"), notes(HistoryFilter.apply(index, "  SWIG ", null, null)))
        assertEquals(listOf("Swiggy dinner", "Chai"), notes(HistoryFilter.apply(index, "food", null, null)))
        assertEquals(listOf("Petrol"), notes(HistoryFilter.apply(index, "2000", null, null)))
        assertEquals(emptyList<String>(), notes(HistoryFilter.apply(index, "rent", null, null)))
    }

    @Test fun typeAndCategoryFiltersCombineWithSearch() {
        assertEquals(listOf("Salary"), notes(HistoryFilter.apply(index, "", TxnType.INCOME, null)))
        assertEquals(listOf("Swiggy dinner", "Chai"), notes(HistoryFilter.apply(index, "", null, 1)))
        assertEquals(listOf("Chai"), notes(HistoryFilter.apply(index, "chai", TxnType.EXPENSE, 1)))
    }
}
