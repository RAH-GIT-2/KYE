package com.kye.app.domain

import java.math.BigDecimal
import java.util.Locale

data class DetectedPayment(
    val amountPaise: Long,
    val isCredit: Boolean,
    /** Who was paid (or who paid you). Empty when the message doesn't say. */
    val merchant: String,
    /** UPI / bank reference number, when present. Lets the app and the bank SMS for one payment merge. */
    val ref: String?
)

/**
 * Reads a completed payment out of a UPI app notification or a bank SMS:
 * "₹450 paid to Swiggy", "Rs.1,200.00 debited from A/c XX1234 to VPA swiggy@icici",
 * "Received ₹5,000 from Ramesh". Returns null for anything that isn't a finished payment
 * (OTPs, reminders, collect requests, offers, failed payments).
 * Pure Kotlin, so it is unit-tested on the JVM.
 */
object PaymentParser {

    private val AMOUNT = Regex("""(?:₹|\brs\.?|\binr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    private val DEBIT = Regex(
        """\b(debited|paid|sent|spent|withdrawn|payment of|purchase of|transferred|txn of|used for)\b""",
        RegexOption.IGNORE_CASE
    )
    private val CREDIT = Regex("""\b(credited|received|deposited|refund(ed)?|added to your)\b""", RegexOption.IGNORE_CASE)

    /** Not a finished payment. */
    private val IGNORE = Regex(
        """\b(otp|one time password|verification code|is due|due on|due date|due by|reminder|requested|requesting|""" +
            """collect request|request(ed)? money|will be debited|to be debited|scheduled|failed|declined|unsuccessful|""" +
            """reversed|pending|offer|cashback up ?to|win|minimum|min\. amt|statement|bill generated)\b""",
        RegexOption.IGNORE_CASE
    )

    private val REF = Regex("""(?:upi\s*ref(?:\.|erence)?\s*(?:no\.?|number|id)?|ref\s*no\.?|rrn|utr)\s*[:\-]?\s*([0-9]{6,})""", RegexOption.IGNORE_CASE)

    private val VPA = Regex("""\b([a-z0-9.\-_]{2,})@[a-z]{2,}\b""", RegexOption.IGNORE_CASE)

    /** Where the counter-party name stops. */
    private const val STOP = """(?=\s+(?:on|via|using|ref|upi|from|for|through|a/c|ac|account|at|is|was|has|successfully|txn|dated)\b|[.,;:()\n]|\s+-|$)"""

    private val TO = Regex("""\b(?:paid to|sent to|transferred to|to vpa|credited to|payment to|to)\s+(.+?)$STOP""", RegexOption.IGNORE_CASE)
    private val FROM = Regex("""\b(?:received from|from vpa|by vpa|from|by)\s+(.+?)$STOP""", RegexOption.IGNORE_CASE)
    private val AT = Regex("""\b(?:at|@ ?merchant)\s+(.+?)$STOP""", RegexOption.IGNORE_CASE)

    fun parse(text: String): DetectedPayment? {
        val t = text.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty() || IGNORE.containsMatchIn(t)) return null

        val amountMatch = AMOUNT.find(t) ?: return null
        val paise = runCatching {
            BigDecimal(amountMatch.groupValues[1].replace(",", "")).movePointRight(2).toLong()
        }.getOrNull() ?: return null
        if (paise <= 0) return null

        val debit = DEBIT.containsMatchIn(t)
        val credit = CREDIT.containsMatchIn(t)
        // Bank SMS for a payment often says "debited … credited to <payee>": that's a debit.
        val isCredit = when {
            debit && credit -> CREDIT.find(t)!!.range.first < DEBIT.find(t)!!.range.first &&
                !Regex("""credited to""", RegexOption.IGNORE_CASE).containsMatchIn(t)
            credit -> true
            debit -> false
            else -> return null
        }

        val merchant = merchantOf(t, isCredit)
        val ref = REF.find(t)?.groupValues?.get(1)
        return DetectedPayment(paise, isCredit, merchant, ref)
    }

    private fun merchantOf(t: String, isCredit: Boolean): String {
        val patterns = if (isCredit) listOf(FROM) else listOf(TO, AT)
        for (p in patterns) {
            for (m in p.findAll(t)) {
                val cleaned = clean(m.groupValues[1])
                if (cleaned != null) return cleaned
            }
        }
        // Fall back to a VPA anywhere in the text: "swiggy@icici" → "Swiggy"
        VPA.findAll(t).forEach { m -> clean(m.groupValues[1])?.let { return it } }
        return ""
    }

    private fun clean(raw: String): String? {
        var s = raw.trim().trimEnd('.', ',', '-')
        VPA.matchEntire(s)?.let { s = it.groupValues[1] }
        s = s.replace(Regex("""^(vpa|mr\.?|mrs\.?|ms\.?|m/s\.?)\s+""", RegexOption.IGNORE_CASE), "")
        if (s.length < 2 || s.length > 40) return null
        // Not a name: an account number, an amount, or a generic word.
        if (Regex("""^[x*0-9\s]+$""", RegexOption.IGNORE_CASE).matches(s)) return null
        if (AMOUNT.containsMatchIn(s)) return null
        val lower = s.lowercase(Locale.ROOT)
        if (lower in GENERIC || lower.startsWith("your ") || lower.startsWith("a/c") || lower.startsWith("ac ")) return null
        return s.split(' ', '.', '_', '-').filter { it.isNotBlank() }.joinToString(" ") { w ->
            if (w.length <= 3 && w.all { it.isUpperCase() }) w // keep KFC, EB, LIC
            else w.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    private val GENERIC = setOf(
        "you", "your account", "account", "bank", "the merchant", "merchant", "beneficiary", "upi", "self",
        "neft", "imps", "rtgs", "nach", "ecs"
    )
}
