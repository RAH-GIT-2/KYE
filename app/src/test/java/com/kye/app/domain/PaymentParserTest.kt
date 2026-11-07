package com.kye.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentParserTest {

    private fun p(text: String) = PaymentParser.parse(text)

    @Test fun gpayPaid() {
        val r = p("₹450 paid to Swiggy")!!
        assertEquals(45000L, r.amountPaise)
        assertFalse(r.isCredit)
        assertEquals("Swiggy", r.merchant)
    }

    @Test fun phonepePaidWithDecimals() {
        val r = p("Paid ₹1,249.50 to RELIANCE FRESH. UPI Ref No 432109876543")!!
        assertEquals(124950L, r.amountPaise)
        assertFalse(r.isCredit)
        assertEquals("Reliance Fresh", r.merchant)
        assertEquals("432109876543", r.ref)
    }

    @Test fun paytmRs() {
        val r = p("Paid Rs.120 to Murugan Auto Stand successfully")!!
        assertEquals(12000L, r.amountPaise)
        assertEquals("Murugan Auto Stand", r.merchant)
    }

    @Test fun bankSmsDebitToVpa() {
        val r = p("Rs.349.00 debited from A/c XX1234 on 07-11-26 to VPA jio@icici (UPI Ref No 512345678901). Avl Bal Rs 10,000")!!
        assertEquals(34900L, r.amountPaise)
        assertFalse(r.isCredit)
        assertEquals("Jio", r.merchant)
        assertEquals("512345678901", r.ref)
    }

    @Test fun bankSmsDebitedAndCreditedToPayee() {
        val r = p("Your a/c XX9876 is debited for INR 2,000.00 on 05-Nov and credited to RAMESH K. UPI Ref 998877665544")!!
        assertEquals(200000L, r.amountPaise)
        assertFalse(r.isCredit)
        assertEquals("Ramesh K", r.merchant)
    }

    @Test fun cardSpendAt() {
        val r = p("Rs 799 spent on HDFC Bank Card XX4321 at AMAZON on 2026-11-07. Avl limit: Rs 50,000")!!
        assertEquals(79900L, r.amountPaise)
        assertFalse(r.isCredit)
        assertEquals("Amazon", r.merchant)
    }

    @Test fun receivedMoney() {
        val r = p("Received ₹5,000 from Selvi")!!
        assertEquals(500000L, r.amountPaise)
        assertTrue(r.isCredit)
        assertEquals("Selvi", r.merchant)
    }

    @Test fun salaryCredit() {
        val r = p("INR 65,000.00 credited to your A/c XX1234 on 01-11-26 by NEFT from ACME TECHNOLOGIES PVT LTD")!!
        assertEquals(6500000L, r.amountPaise)
        assertTrue(r.isCredit)
    }

    @Test fun ignoresOtp() = assertNull(p("123456 is your OTP for payment of Rs 450 to Swiggy"))

    @Test fun ignoresCollectRequest() = assertNull(p("Swiggy has requested ₹450 from you"))

    @Test fun ignoresBillReminder() = assertNull(p("Your EB bill of Rs 1,240 is due on 10-Nov"))

    @Test fun ignoresFailed() = assertNull(p("Payment of ₹450 to Swiggy failed"))

    @Test fun ignoresNoAmount() = assertNull(p("Swiggy: your order is on the way"))

    @Test fun ignoresNoDirection() = assertNull(p("Flat ₹100 on your next order"))
}
