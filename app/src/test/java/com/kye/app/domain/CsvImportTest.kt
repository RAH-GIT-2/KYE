package com.kye.app.domain

import com.kye.app.data.PaymentMode
import com.kye.app.data.TxnType
import com.kye.app.data.UserError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvImportTest {

    private val header = CsvImport.TEMPLATE

    @Test fun templateIsHeaderOnly() {
        val f = CsvImport.read(CsvImport.TEMPLATE)
        assertTrue(f.rows.isEmpty())
        assertTrue(f.problems.isEmpty())
    }

    @Test fun readsAFilledRow() {
        val f = CsvImport.read(header + "07-11-2026,Expense,450,Food & Dining,Swiggy,UPI,biryani\r\n")
        val r = f.rows.single()
        assertEquals(LocalDate.of(2026, 11, 7), r.date)
        assertEquals(TxnType.EXPENSE, r.type)
        assertEquals(45000L, r.amountPaise)
        assertEquals("Food & Dining", r.category)
        assertEquals("Swiggy", r.subcategory)
        assertEquals(PaymentMode.UPI, r.payment)
        assertEquals("biryani", r.note)
    }

    @Test fun eventColumn() {
        val r = CsvImport.read(header + "01-11-2026,Expense,4500,,,UPI,crackers,Deepavali 2026\n").rows.single()
        assertEquals("Deepavali 2026", r.event)
        // Older 7-column files (before events) still read, with no event.
        val old = CsvImport.read("Date,Type,Amount,Category,Subcategory,Payment,Note\n01-11-2026,Expense,10,,,,\n").rows.single()
        assertEquals("", old.event)
    }

    @Test fun blanksUseDefaults() {
        val r = CsvImport.read(header + "01/11/2026,,\"1,250.50\",,,,\n").rows.single()
        assertNull(r.type)
        assertEquals(125050L, r.amountPaise)
        assertEquals(PaymentMode.UPI, r.payment)
        assertEquals("", r.category)
    }

    @Test fun quotedFieldsWithCommasQuotesAndTamil() {
        val r = CsvImport.read(header + "2026-11-05,Income,65000,Salary,,Net banking,\"Nov salary, \"\"ACME\"\" பணம்\"\n").rows.single()
        assertEquals(TxnType.INCOME, r.type)
        assertEquals(PaymentMode.NET_BANKING, r.payment)
        assertEquals("Nov salary, \"ACME\" பணம்", r.note)
    }

    @Test fun indianDateStyles() {
        assertEquals(LocalDate.of(2026, 11, 7), CsvImport.parseDate("07-Nov-26"))
        assertEquals(LocalDate.of(2026, 11, 7), CsvImport.parseDate("7/11/2026"))
        assertEquals(LocalDate.of(2026, 11, 7), CsvImport.parseDate("07.11.2026"))
        assertEquals(LocalDate.of(2026, 11, 7), CsvImport.parseDate("7 nov 2026"))
        assertNull(CsvImport.parseDate("31-02-2026"))
        assertNull(CsvImport.parseDate("yesterday"))
    }

    @Test fun amountsWithRupeeSigns() {
        assertEquals(45000L, CsvImport.parseAmount("₹450"))
        assertEquals(9000L, CsvImport.parseAmount("Rs. 90"))
        assertEquals(-30000L, CsvImport.parseAmount("-300"))
        assertNull(CsvImport.parseAmount("abc"))
    }

    @Test fun badRowsAreReportedWithTheirLineNumber() {
        val f = CsvImport.read(
            header +
                "07-11-2026,Expense,450,,,,ok\n" +
                ",Expense,100,,,,no date\n" +
                "07-11-2026,Expense,,,,,no amount\n" +
                "07-11-2026,Spending,10,,,,bad type\n" +
                "07-11-2026,Expense,10,,,,\n" +
                "07-11-2026,Expense,10,,,Gold coins,bad payment\n"
        )
        assertEquals(listOf(2, 6), f.rows.map { it.line })
        assertEquals(listOf(3, 4, 5, 7), f.problems.map { it.line })
    }

    @Test fun blankLinesAreIgnored() {
        val f = CsvImport.read(header + "\n,,,,,,\n07-11-2026,Expense,1,,,,\n\n")
        assertEquals(1, f.rows.size)
        assertTrue(f.problems.isEmpty())
    }

    @Test fun olderExportFormatStillImports() {
        val old = "Date,Time,Type,Category,Subcategory,Amount (INR),Payment,Note,Source\n" +
            "2026-11-07,13:05,EXPENSE,Food & Dining,Swiggy,450.00,UPI,biryani,VOICE\n"
        val r = CsvImport.read(old).rows.single()
        assertEquals(45000L, r.amountPaise)
        assertEquals("Swiggy", r.subcategory)
    }

    @Test(expected = UserError::class)
    fun fileWithoutHeaderIsRefused() {
        CsvImport.read("07-11-2026,Expense,450\n")
    }

    @Test fun paymentAliases() {
        assertEquals(PaymentMode.UPI, CsvImport.parsePayment("GPay"))
        assertEquals(PaymentMode.CREDIT_CARD, CsvImport.parsePayment("credit card"))
        assertEquals(PaymentMode.CASH, CsvImport.parsePayment("cash"))
    }
}
