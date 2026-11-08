package com.kye.app.domain

import com.kye.app.data.Category
import com.kye.app.data.CategoryKind
import com.kye.app.data.Loan
import com.kye.app.data.LoanDirection
import com.kye.app.data.LoanRow
import com.kye.app.data.PaymentMode
import com.kye.app.data.Subcategory
import com.kye.app.data.Txn
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AskTest {
    private val today = LocalDate.of(2026, 11, 15) // Sunday
    private fun nov(d: Int) = LocalDate.of(2026, 11, d)

    private val food = Category(id = 1, name = "Food & Dining", emoji = "🍛", color = 1)
    private val commute = Category(id = 2, name = "Commute", emoji = "🛺", color = 2)
    private val home = Category(id = 3, name = "Home & Help", emoji = "🏠", color = 3)
    private val salary = Category(id = 4, name = "Salary", emoji = "💼", color = 4, kind = CategoryKind.INCOME)
    private val cats = listOf(food, commute, home, salary)

    private val swiggy = Subcategory(id = 10, categoryId = 1, name = "Swiggy")
    private val petrol = Subcategory(id = 20, categoryId = 2, name = "Petrol")
    private val rent = Subcategory(id = 30, categoryId = 3, name = "Rent")
    private val subs = listOf(swiggy, petrol, rent, Subcategory(id = 11, categoryId = 1, name = "Others", isDefault = true))

    private val keywords = listOf(
        AskKeyword("swiggy", 1, 10), AskKeyword("petrol", 2, 20), AskKeyword("rent", 3, 30), AskKeyword("food", 1, null)
    )

    private var nextId = 1L
    private fun row(
        date: LocalDate, rupees: Long, cat: Category, sub: Subcategory? = null,
        type: TxnType = TxnType.EXPENSE, pay: PaymentMode = PaymentMode.UPI, note: String = ""
    ) = TxnRow(
        Txn(
            id = nextId++, amountPaise = rupees * 100, type = type, categoryId = cat.id, subcategoryId = sub?.id,
            dateTime = Dates.atMillis(date, LocalTime.NOON), paymentMode = pay, note = note
        ),
        catName = cat.name, catEmoji = cat.emoji, catColor = cat.color, subName = sub?.name, subIsDefault = sub?.isDefault
    )

    private val txns = listOf(
        row(nov(14), 450, food, swiggy, note = "biryani"),
        row(nov(10), 300, food, swiggy),
        row(LocalDate.of(2026, 10, 12), 500, food, swiggy),
        row(nov(15), 1_000, commute, petrol, pay = PaymentMode.CASH),
        row(LocalDate.of(2026, 9, 20), 900, commute, petrol),
        row(LocalDate.of(2026, 10, 5), 800, commute, petrol),
        row(nov(5), 20_000, home, rent, pay = PaymentMode.NET_BANKING),
        row(nov(1), 65_000, salary, type = TxnType.INCOME, pay = PaymentMode.NET_BANKING),
        row(nov(14), 20, food, note = "tea")
    )

    private val loans = listOf(
        LoanRow(Loan(person = "Ramesh K", direction = LoanDirection.LENT, amountPaise = 2_000_00, date = nov(1).toEpochDay()), 500_00),
        LoanRow(Loan(person = "Selvi", direction = LoanDirection.BORROWED, amountPaise = 1_000_00, date = nov(2).toEpochDay()), 0)
    )

    private fun ask(q: String) = Ask.answer(q, txns, cats, subs, keywords, loans, today)

    @Test fun swiggyThisMonthWithComparison() {
        val a = ask("How much on Swiggy this month?")
        assertEquals("₹750 spent on Swiggy this month", a.headline)
        assertTrue(a.details.toString(), a.details.contains("2 entries · ₹375 on average"))
        assertTrue(a.details.toString(), a.details.contains("vs ₹500 the same days last month (↑50%)"))
        assertEquals("Expenses · Swiggy · 1 Nov – 15 Nov", a.understood)
        assertEquals(2, a.rows.size)
    }

    @Test fun lastThreeMonths() = assertEquals("₹2,700 spent on Petrol in the last 3 months", ask("petrol last 3 months").headline)

    @Test fun yesterdayEverything() {
        assertEquals("₹470 spent yesterday", ask("what did I spend yesterday").headline)
        assertEquals("₹470 spent yesterday", ask("nethu evlo selavu").headline)
    }

    @Test fun tanglishThisMonth() = assertEquals("₹750 spent on Swiggy this month", ask("intha maasam swiggy evlo").headline)

    @Test fun biggest() = assertEquals("Biggest: ₹20,000 · Rent · 5 Nov", ask("biggest expense this month").headline)

    @Test fun incomeAndPaymentMode() {
        assertEquals("₹65,000 income this month", ask("income this month").headline)
        assertEquals("₹1,000 spent by Cash this month", ask("cash spending this month").headline)
    }

    @Test fun countAndAverage() {
        assertEquals("2 times on Swiggy this month", ask("how many times swiggy this month").headline)
        // Food this month: 450 + 300 + 20 = 770 over 15 days.
        assertEquals("₹51 a day on Food & Dining this month", ask("food per day this month").headline)
    }

    @Test fun namedMonthAndLastWeek() {
        assertEquals("₹500 spent on Food & Dining in October", ask("food in october").headline)
        assertEquals("₹20,000 spent last week", ask("last week").headline)
    }

    @Test fun wordsInNotesAndNothingFound() {
        assertEquals("₹450 spent on \"biryani\" this month", ask("biryani").headline)
        assertEquals("Nothing spent on \"zomato\" this month", ask("zomato").headline)
    }

    @Test fun loans() {
        assertEquals("₹1,500 to get back · ₹1,000 to give back", ask("who owes me").headline)
        assertEquals("Ramesh K owes you ₹1,500", ask("how much does ramesh owe me").headline)
        assertEquals("You owe Selvi ₹1,000", ask("how much kadan to selvi").headline)
    }

    @Test fun emptyQuestion() = assertTrue(ask("  ").headline.startsWith("Ask something"))
}
