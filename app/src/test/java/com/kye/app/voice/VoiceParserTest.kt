package com.kye.app.voice

import com.kye.app.data.PaymentMode
import com.kye.app.data.Seed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class VoiceParserTest {

    // Build keyword entries the same way the database seed does: ids follow insertion order.
    private data class Ids(val keywords: List<KeywordEntry>, val cat: Map<String, Long>, val sub: Map<String, Long>, val family: Map<Long, Long>)

    private val ids: Ids = run {
        val kws = ArrayList<KeywordEntry>()
        val cat = HashMap<String, Long>()
        val sub = HashMap<String, Long>()
        val family = HashMap<Long, Long>()
        var subId = 0L
        Seed.categories.forEachIndexed { i, sc ->
            val cid = i + 1L
            cat[sc.name] = cid
            sc.keywords.forEach { kws += KeywordEntry(it, cid, null, 1) }
            sc.subs.forEach { ss ->
                subId++
                sub[sc.name + "/" + ss.name] = subId
                if (ss.name.contains("Family")) family[cid] = subId
                ss.keywords.forEach { kws += KeywordEntry(it, cid, subId, if (ss.merchant) 3 else 2) }
            }
            subId++ // "Others"
        }
        Ids(kws, cat, sub, family)
    }

    private val today = LocalDate.of(2026, 11, 7) // a Saturday

    private fun parse(text: String) =
        VoiceParser.parse(text, ids.keywords, { ids.family[it] }, today)

    private fun one(text: String): ParsedEntry {
        val r = parse(text)
        assertEquals("items for '$text'", 1, r.size)
        return r.first()
    }

    private fun sub(path: String) = ids.sub[path] ?: error("no sub $path")
    private fun cat(name: String) = ids.cat[name] ?: error("no cat $name")

    @Test fun swiggyTanglish() {
        val e = one("Swiggy la 450 biryani")
        assertEquals(45_000L, e.amountPaise)
        assertEquals(sub("Food & Dining/Swiggy"), e.subcategoryId)
        assertEquals("Biryani", e.note)
        assertEquals(today, e.date)
    }

    @Test fun autoYesterday() {
        val e = one("auto 120 nethu")
        assertEquals(12_000L, e.amountPaise)
        assertEquals(sub("Commute/Auto"), e.subcategoryId)
        assertEquals(today.minusDays(1), e.date)
    }

    @Test fun maidCash() {
        val e = one("paid maid ku 3000 cash")
        assertEquals(3_00_000L, e.amountPaise)
        assertEquals(sub("Home & Help/Housemaid"), e.subcategoryId)
        assertEquals(PaymentMode.CASH, e.paymentMode)
    }

    @Test fun familyRecharge() {
        val e = one("Jio recharge amma ku 349 GPay")
        assertEquals(34_900L, e.amountPaise)
        assertEquals(cat("Recharges"), e.categoryId)
        assertEquals(sub("Recharges/Mobile – Family"), e.subcategoryId)
        assertEquals(PaymentMode.UPI, e.paymentMode)
    }

    @Test fun tamilMilk() {
        val e = one("பால் 60 ரூபாய்")
        assertEquals(6_000L, e.amountPaise)
        assertEquals(sub("Milk & Daily/Milkman"), e.subcategoryId)
    }

    @Test fun ebBillTanglishNumber() {
        val e = one("EB bill rendu aayiram")
        assertEquals(2_00_000L, e.amountPaise)
        assertEquals(sub("Bills & Utilities/Electricity (EB)"), e.subcategoryId)
    }

    @Test fun englishWordsFourFifty() {
        assertEquals(45_000L, one("zomato four fifty").amountPaise)
        assertEquals(2_50_000L, one("petrol two thousand five hundred").amountPaise)
    }

    @Test fun tamilScriptNumbers() {
        assertEquals(25_000L, one("ஆட்டோ இருநூறு ஐம்பது").amountPaise)
    }

    @Test fun kAndLakh() {
        assertEquals(1_50_000L, one("flipkart 1.5k").amountPaise)
        assertEquals(1_00_000_00L, one("gold 1 lakh").amountPaise)
        assertEquals(1_20_000L, one("rent 1,200").amountPaise)
    }

    @Test fun twoItems() {
        val r = parse("Swiggy 450, auto 120")
        assertEquals(2, r.size)
        assertEquals(sub("Food & Dining/Swiggy"), r[0].subcategoryId)
        assertEquals(sub("Commute/Auto"), r[1].subcategoryId)
        val r2 = parse("petrol 500 and parking 40")
        assertEquals(2, r2.size)
        assertEquals(4_000L, r2[1].amountPaise)
    }

    @Test fun longestPhraseWins() {
        assertEquals(sub("OTT & Subscriptions/Prime Video"), one("amazon prime 1499").subcategoryId)
        assertEquals(sub("Shopping/Amazon"), one("amazon 799 shoes").subcategoryId)
        assertEquals(sub("OTT & Subscriptions/JioHotstar"), one("jio hotstar 299").subcategoryId)
        assertEquals(sub("EMIs & Loans/Credit Card Bill"), one("credit card bill 12000").subcategoryId)
    }

    @Test fun largestDigitWins() {
        assertEquals(45_000L, one("1mg 450").amountPaise)
    }

    @Test fun weekday() {
        assertEquals(LocalDate.of(2026, 11, 2), one("movie 300 monday").date)
        assertEquals(LocalDate.of(2026, 10, 31), one("party 2000 last saturday").date)
    }

    @Test fun unknownCategory() {
        val e = one("something 250")
        assertEquals(25_000L, e.amountPaise)
        assertNull(e.categoryId)
        assertEquals("Something", e.note)
    }

    @Test fun noAmount() {
        val e = one("swiggy biryani")
        assertNull(e.amountPaise)
        assertEquals(sub("Food & Dining/Swiggy"), e.subcategoryId)
    }
}
