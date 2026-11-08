package com.kye.app.voice

import com.kye.app.data.PaymentMode
import com.kye.app.domain.Money
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/** A keyword the parser can match: phrase → category (and optional subcategory). */
data class KeywordEntry(val phrase: String, val categoryId: Long, val subcategoryId: Long?, val weight: Int)

/** What the parser understood from one spoken item. */
data class ParsedEntry(
    val amountPaise: Long?,
    val categoryId: Long?,
    val subcategoryId: Long?,
    val date: LocalDate,
    val paymentMode: PaymentMode?,
    val note: String,
    val matchedPhrase: String?,
    val raw: String,
    /** People the bill was split between; [amountPaise] is already your share. 1 = not split. */
    val splitWays: Int = 1
)

/**
 * Rule-based parser for English, Tanglish and Tamil-script expense sentences:
 * "Swiggy la 450 biryani", "auto 120 nethu", "EB bill rendu aayiram", "பால் 60 ரூபாய்".
 * Pure Kotlin, so it is unit-tested on the JVM.
 */
object VoiceParser {

    private val CONNECTORS = setOf("and", "um", "apram", "appuram", "aprom", "then", "அப்புறம்", "அப்றம்", "மற்றும்", ",")

    private val FILLERS = setOf(
        "rs", "rupees", "rupee", "rupai", "roobai", "rubai", "ruba", "rupay", "ரூபாய்", "ரூ", "inr",
        "for", "to", "paid", "pay", "spent", "spend", "on", "at", "the", "a", "an", "la", "le", "ku", "kku",
        "ikku", "ukku", "ah", "nu", "oda", "kodutha", "kuduthen", "koduthen", "kuduthathu", "panninen", "pannen",
        "vaanginen", "vanginen", "bought", "buy", "got", "from", "via", "through", "using", "by", "is", "was",
        "my", "in", "of", "with", "last", "கொடுத்தேன்", "செலவு", "expense", "add"
    )

    private val FAMILY = listOf(
        "amma", "appa", "wife", "husband", "mom", "dad", "mother", "father", "family", "thambi", "akka",
        "anna", "thangachi", "paati", "thatha", "son", "daughter", "magan", "magal", "brother", "sister",
        "அம்மா", "அப்பா", "மனைவி", "தம்பி", "அக்கா", "அண்ணா", "தங்கச்சி"
    )

    /** Number words that add to the running value. 100 ("nooru") multiplies a preceding 1–9. */
    private val NUMBERS: Map<String, Long> = buildMap {
        listOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
            "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
            "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
            "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40, "fourty" to 40, "fifty" to 50,
            "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
            // Tanglish
            "onnu" to 1, "oru" to 1, "rendu" to 2, "randu" to 2, "irandu" to 2, "moonu" to 3, "munu" to 3,
            "naalu" to 4, "nalu" to 4, "anju" to 5, "ainthu" to 5, "aaru" to 6, "ezhu" to 7, "ettu" to 8,
            "onbathu" to 9, "pathu" to 10, "iruvathu" to 20, "irubathu" to 20, "muppathu" to 30,
            "naarpathu" to 40, "narpathu" to 40, "aimbathu" to 50, "ambathu" to 50, "arubathu" to 60,
            "ezhubathu" to 70, "enbathu" to 80, "thonnooru" to 90, "thonnuru" to 90, "nooru" to 100,
            "nuru" to 100, "irunooru" to 200, "irunuru" to 200, "munnooru" to 300, "naanooru" to 400,
            "ainooru" to 500, "anjooru" to 500, "arunooru" to 600, "ezhunooru" to 700, "ennooru" to 800,
            "thollayiram" to 900,
            // Tamil script
            "ஒன்று" to 1, "ஒரு" to 1, "இரண்டு" to 2, "ரெண்டு" to 2, "மூன்று" to 3, "மூணு" to 3,
            "நான்கு" to 4, "நாலு" to 4, "ஐந்து" to 5, "அஞ்சு" to 5, "ஆறு" to 6, "ஏழு" to 7, "எட்டு" to 8,
            "ஒன்பது" to 9, "பத்து" to 10, "இருபது" to 20, "முப்பது" to 30, "நாற்பது" to 40, "ஐம்பது" to 50,
            "அம்பது" to 50, "அறுபது" to 60, "எழுபது" to 70, "எண்பது" to 80, "தொண்ணூறு" to 90, "நூறு" to 100,
            "இருநூறு" to 200, "முந்நூறு" to 300, "முன்னூறு" to 300, "நானூறு" to 400, "ஐந்நூறு" to 500,
            "ஐநூறு" to 500, "அறுநூறு" to 600, "எழுநூறு" to 700, "எண்ணூறு" to 800, "தொள்ளாயிரம்" to 900
        ).forEach { (k, v) -> put(k, v.toLong()) }
    }

    private val MULTIPLIERS: Map<String, Long> = mapOf(
        "hundred" to 100L, "thousand" to 1000L, "aayiram" to 1000L, "ayiram" to 1000L, "aayirathu" to 1000L,
        "aayirathi" to 1000L, "ஆயிரம்" to 1000L, "ஆயிரத்து" to 1000L, "lakh" to 100_000L, "lakhs" to 100_000L,
        "lac" to 100_000L, "laksham" to 100_000L, "latcham" to 100_000L, "லட்சம்" to 100_000L, "k" to 1000L
    )

    private val DATE_PHRASES: List<Pair<String, Long>> = listOf(
        "day before yesterday" to 2L, "munthanethu" to 2L, "munthaanethu" to 2L, "munthanaal" to 2L,
        "முந்தாநேத்து" to 2L, "முந்தாநாள்" to 2L,
        "yesterday" to 1L, "nethu" to 1L, "neththu" to 1L, "netru" to 1L, "naethu" to 1L, "நேற்று" to 1L, "நேத்து" to 1L,
        "today" to 0L, "innaiku" to 0L, "innaikku" to 0L, "inniki" to 0L, "innikku" to 0L, "indru" to 0L,
        "இன்று" to 0L, "இன்னைக்கு" to 0L
    )

    private val WEEKDAYS: Map<String, DayOfWeek> = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY
    )

    private val PAYMENTS: List<Pair<String, PaymentMode>> = listOf(
        "credit card" to PaymentMode.CREDIT_CARD, "debit card" to PaymentMode.DEBIT_CARD,
        "net banking" to PaymentMode.NET_BANKING, "netbanking" to PaymentMode.NET_BANKING,
        "google pay" to PaymentMode.UPI, "g pay" to PaymentMode.UPI, "gpay" to PaymentMode.UPI,
        "phone pe" to PaymentMode.UPI, "phonepe" to PaymentMode.UPI, "paytm" to PaymentMode.UPI,
        "bhim" to PaymentMode.UPI, "upi" to PaymentMode.UPI, "scan" to PaymentMode.UPI,
        "cash" to PaymentMode.CASH, "kaasu" to PaymentMode.CASH, "காசு" to PaymentMode.CASH,
        "rokkam" to PaymentMode.CASH, "ரொக்கம்" to PaymentMode.CASH,
        "card" to PaymentMode.DEBIT_CARD, "wallet" to PaymentMode.WALLET
    )

    private val DIGIT = Regex("^(\\d+(?:\\.\\d+)?)(?:(k|lakhs?|l)(?![a-z]))?")

    private val NUMERIC_DATE = Regex("(?<![\\d.])(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{4}|\\d{2}))?(?![\\d/-])")
    private val DATE_TOKEN = Regex("^#d(\\d+)m(\\d+)(?:y(\\d+))?$")
    private val DAY = Regex("^(\\d{1,2})(st|nd|rd|th)?$")
    private val YEAR = Regex("^(\\d{4})$")

    private val MONTHS: Map<String, Int> = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9, "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11, "dec" to 12, "december" to 12
    )

    /** A real date, or null ("31 feb"). Without a year: the latest such date that isn't in the future. */
    private fun makeDate(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        if (year != null) return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
        if (thisYear != null && !thisYear.isAfter(today)) return thisYear
        // Not yet this year (or 29 Feb): last year's, going back to the last year it exists.
        for (back in 1..8) runCatching { return LocalDate.of(today.year - back, month, day) }
        return null
    }

    /**
     * Finds a calendar date ("2nd nov", "nov 2", "2 of november 2025", "2/11", "02/11/26"), marks its
     * words as used and returns it. Done before the amount, so "tea 15 20th nov" is ₹15, not ₹20.
     */
    private fun findCalendarDate(tokens: List<String>, used: BooleanArray, today: LocalDate): LocalDate? {
        fun yearAt(i: Int): Int? = tokens.getOrNull(i)?.takeIf { i < used.size && !used[i] }
            ?.let { YEAR.find(it)?.groupValues?.get(1)?.toInt() }
            ?.takeIf { it in today.year - 5..today.year + 1 }
        fun dayAt(i: Int): Int? = tokens.getOrNull(i)?.takeIf { i < used.size && !used[i] }
            ?.let { DAY.find(it)?.groupValues?.get(1)?.toInt() }
            ?.takeIf { it in 1..31 }

        for (i in tokens.indices) {
            if (used[i]) continue
            // Numeric form, already one token.
            DATE_TOKEN.find(tokens[i])?.let { m ->
                val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
                val d = makeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), y, today)
                if (d != null) {
                    used[i] = true
                    return d
                }
            }
            val month = MONTHS[tokens[i]] ?: continue
            // "2nd nov", "2 of november"
            val ofGap = if (i >= 2 && tokens[i - 1] == "of") 1 else 0
            val before = i - 1 - ofGap
            dayAt(before)?.let { day ->
                val year = yearAt(i + 1)
                makeDate(day, month, year, today)?.let { d ->
                    (before..i).forEach { used[it] = true }
                    if (year != null) used[i + 1] = true
                    return d
                }
            }
            // "nov 2", "november 2nd 2025"
            dayAt(i + 1)?.let { day ->
                val year = yearAt(i + 2)
                makeDate(day, month, year, today)?.let { d ->
                    used[i] = true
                    used[i + 1] = true
                    if (year != null) used[i + 2] = true
                    return d
                }
            }
        }
        return null
    }

    /** Amount in a piece, ignoring any calendar date in it ("2nd nov" is not ₹2). */
    private fun amountOf(piece: List<String>, today: LocalDate): Amount? {
        val used = BooleanArray(piece.size)
        findCalendarDate(piece, used, today)
        // No fallback here: a piece that is only a date ("…, 2nd nov") joins its neighbour.
        return findAmount(piece, used)
    }

    // ---------------------------------------------------------------------------------------

    fun normalize(input: String): String {
        var x = input.lowercase(Locale.ROOT)
        x = x.replace("₹", " ")
        // Numeric dates, day first: "2/11", "02-11-2026", "2/11/26" → one token "#d2m11y2026" (kept whole below).
        x = NUMERIC_DATE.replace(x) { m ->
            val y = m.groupValues[3]
            " #d${m.groupValues[1].toInt()}m${m.groupValues[2].toInt()}" + (if (y.isNotEmpty()) "y$y" else "") + " "
        }
        repeat(3) { x = Regex("(\\d),(\\d)").replace(x, "\$1\$2") }
        x = x.replace("'", "").replace("’", "")
        x = x.replace(Regex("[,;]"), " , ")
        x = Regex("(?<!\\d)\\.|\\.(?!\\d)").replace(x, " ")
        x = x.replace(Regex("[!?:\"()\\[\\]–—_/-]"), " ")
        return x.replace(Regex("\\s+"), " ").trim()
    }

    fun tokenize(s: String): List<String> = normalize(s).split(' ').filter { it.isNotBlank() }

    /** A spoken word matches a keyword word exactly, or with a short Tanglish/Tamil suffix ("swiggyla"). */
    private fun wordMatches(token: String, word: String, allowSuffix: Boolean): Boolean =
        token == word || (allowSuffix && word.length >= 4 && token.startsWith(word) && token.length - word.length <= 6)

    private fun find(tokens: List<String>, used: BooleanArray, phrase: List<String>): IntRange? {
        if (phrase.isEmpty() || phrase.size > tokens.size) return null
        for (i in 0..tokens.size - phrase.size) {
            var ok = true
            for (j in phrase.indices) {
                if (used[i + j] || !wordMatches(tokens[i + j], phrase[j], j == phrase.lastIndex)) {
                    ok = false
                    break
                }
            }
            if (ok) return i until i + phrase.size
        }
        return null
    }

    fun parse(
        text: String,
        keywords: List<KeywordEntry>,
        familySubFor: (Long) -> Long? = { null },
        today: LocalDate = LocalDate.now()
    ): List<ParsedEntry> {
        val all = tokenize(text)
        if (all.isEmpty()) return emptyList()

        // Split into items on connectors, then merge items that have no amount into their neighbour.
        val pieces = ArrayList<MutableList<String>>()
        var current = ArrayList<String>()
        for (t in all) {
            if (t in CONNECTORS) {
                if (current.isNotEmpty()) pieces += current
                current = ArrayList()
            } else current += t
        }
        if (current.isNotEmpty()) pieces += current

        val merged = ArrayList<MutableList<String>>()
        for (p in pieces) {
            if (amountOf(p, today) == null && merged.isNotEmpty()) merged.last().addAll(p)
            else merged += p
        }
        if (merged.size > 1 && amountOf(merged.first(), today) == null) {
            val first = merged.removeAt(0)
            merged[0] = (first + merged[0]).toMutableList()
        }

        val prepared = keywords.map { it to tokenize(it.phrase) }.filter { it.second.isNotEmpty() }
        return merged.map { parseItem(it, prepared, familySubFor, today, text) }
    }

    private data class Amount(val paise: Long, val range: IntRange)

    private val SPLIT_WORDS = setOf("split", "share", "divide", "divided", "pirichu", "pangu")
    private val SPLIT_LINKS = setOf("in", "into", "between", "among", "by", "with", "equally", "to", "for")
    private val PEOPLE_WORDS = setOf("ways", "way", "people", "persons", "members", "friends", "others", "peru", "perukku")
    private val SPLIT_NUMBERS = mapOf(
        "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "rendu" to 2, "moonu" to 3, "naalu" to 4, "nalu" to 4, "anju" to 5, "aaru" to 6, "ezhu" to 7, "ettu" to 8, "pathu" to 10
    )

    /**
     * People a bill is split between. "split 4" / "split 4 ways" / "split in 3" / "4 ways" = that many
     * people in all; "split with 2 (friends)" = you and 2 others = 3. Marks the words as used.
     */
    private fun splitWays(tokens: List<String>, used: BooleanArray): Int {
        fun num(t: String?): Int? = t?.toIntOrNull()?.takeIf { it in 2..50 } ?: SPLIT_NUMBERS[t]
        for (i in tokens.indices) {
            if (used[i]) continue
            if (tokens[i] in SPLIT_WORDS) {
                var j = i + 1
                var with = false
                while (j < tokens.size && tokens[j] in SPLIT_LINKS) {
                    if (tokens[j] == "with") with = true
                    j++
                }
                val n = num(tokens.getOrNull(j)) ?: continue
                val end = if (tokens.getOrNull(j + 1) in PEOPLE_WORDS) j + 1 else j
                for (k in i..end) used[k] = true
                return if (with) n + 1 else n
            }
            // "1200 4 ways" without the word split
            val n = num(tokens[i])
            if (n != null && tokens.getOrNull(i + 1) == "ways") {
                used[i] = true
                used[i + 1] = true
                return n
            }
        }
        return 1
    }

    /** Your share, rounded to the paisa. */
    fun splitShare(totalPaise: Long, ways: Int): Long =
        if (ways <= 1) totalPaise else (totalPaise + ways / 2) / ways

    private fun findAmount(tokens: List<String>, used: BooleanArray): Amount? {
        // 1) Digits first: "450", "1.5k", "2 thousand", "450la". The largest wins ("1mg 450" → 450).
        var bestDigit: Amount? = null
        for (i in tokens.indices) {
            if (used[i]) continue
            val m = DIGIT.find(tokens[i]) ?: continue
            var value = BigDecimal(m.groupValues[1])
            var end = i
            when (m.groupValues[2]) {
                "k" -> value = value.multiply(BigDecimal(1000))
                "l", "lakh", "lakhs" -> value = value.multiply(BigDecimal(100_000))
                else -> {
                    val next = tokens.getOrNull(i + 1)
                    val mult = next?.let { MULTIPLIERS[it] }
                    if (mult != null) {
                        value = value.multiply(BigDecimal(mult))
                        end = i + 1
                    }
                }
            }
            val paise = value.multiply(BigDecimal(100)).toLong()
            if (paise > 0 && (bestDigit == null || paise > bestDigit.paise)) bestDigit = Amount(paise, i..end)
        }
        if (bestDigit != null) return bestDigit
        // 2) Number words: "four fifty", "rendu aayiram", "இருநூறு ஐம்பது"
        var i = 0
        while (i < tokens.size) {
            if (!used[i]) {
                val r = wordNumber(tokens, i)
                if (r != null) return Amount(r.first * 100, i until i + r.second)
            }
            i++
        }
        return null
    }

    /** Returns (value, tokens consumed) for a run of number words starting at [start]. */
    private fun wordNumber(tokens: List<String>, start: Int): Pair<Long, Int>? {
        var total = 0L
        var current = 0L
        var any = false
        var hasMult = false
        var lastSingleDigit = false
        var i = start
        while (i < tokens.size) {
            val t = tokens[i]
            val n = NUMBERS[t]
            val mult = MULTIPLIERS[t]
            if (n != null) {
                current = when {
                    n == 100L && current in 1L..9L -> current * 100
                    n in 10L..99L && lastSingleDigit && current in 1L..9L -> current * 100 + n   // "four fifty" = 450
                    else -> current + n
                }
                lastSingleDigit = n in 1L..9L
                any = true
            } else if (mult != null && (any || t != "k")) {
                current = (if (current == 0L) 1L else current) * mult
                if (mult >= 1000) {
                    total += current
                    current = 0
                }
                hasMult = true
                any = true
                lastSingleDigit = false
            } else break
            i++
        }
        if (!any) return null
        val value = total + current
        if (value < 2 && !hasMult) return null
        return value to (i - start)
    }

    private fun parseItem(
        tokens: List<String>,
        keywords: List<Pair<KeywordEntry, List<String>>>,
        familySubFor: (Long) -> Long?,
        today: LocalDate,
        raw: String
    ): ParsedEntry {
        val used = BooleanArray(tokens.size)

        // "split 4" must not be read as the amount, so it's found first.
        val ways = splitWays(tokens, used)
        // A calendar date comes before the amount so its day number is never read as rupees.
        // If that leaves no amount at all ("tea 15 nov"), the number was the amount after all.
        val before = used.copyOf()
        var calendarDate = findCalendarDate(tokens, used, today)
        var amount = findAmount(tokens, used)
        if (amount == null && calendarDate != null) {
            before.copyInto(used)
            calendarDate = null
            amount = findAmount(tokens, used)
        }
        amount?.range?.forEach { used[it] = true }

        // Date
        var date = calendarDate ?: today
        if (calendarDate == null) run {
            for ((phrase, back) in DATE_PHRASES) {
                val r = find(tokens, used, phrase.split(' '))
                if (r != null) {
                    r.forEach { used[it] = true }
                    date = today.minusDays(back)
                    return@run
                }
            }
            for (i in tokens.indices) {
                val wd = WEEKDAYS[tokens[i]] ?: continue
                if (used[i]) continue
                var diff = ((today.dayOfWeek.value - wd.value + 7) % 7).toLong()
                if (diff == 0L && i > 0 && tokens[i - 1] == "last") diff = 7
                date = today.minusDays(diff)
                used[i] = true
                return@run
            }
        }

        // Category: highest weight wins, then the longest phrase
        var best: Pair<KeywordEntry, IntRange>? = null
        var bestScore = -1
        for ((kw, words) in keywords) {
            val r = find(tokens, used, words) ?: continue
            val score = kw.weight * 1000 + kw.phrase.length
            if (score > bestScore) {
                bestScore = score
                best = kw to r
            }
        }
        best?.second?.forEach { used[it] = true }
        var categoryId = best?.first?.categoryId
        var subId = best?.first?.subcategoryId

        // Family recharge: "Jio recharge amma ku 349"
        if (categoryId != null) {
            val familySub = familySubFor(categoryId)
            if (familySub != null && tokens.indices.any { i -> FAMILY.any { wordMatches(tokens[i], it, true) } }) {
                subId = familySub
            }
        }

        // Payment mode
        var payment: PaymentMode? = null
        for ((phrase, mode) in PAYMENTS) {
            val r = find(tokens, used, phrase.split(' '))
            if (r != null) {
                r.forEach { used[it] = true }
                payment = mode
                break
            }
        }

        val words = tokens.indices
            .filter { !used[it] && tokens[it] !in FILLERS && tokens[it] != "," }
            .joinToString(" ") { tokens[it] }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        val share = amount?.paise?.let { splitShare(it, ways) }
        // Keep the full bill in the note: "Dinner · ₹1,200 split 4 ways".
        val note = if (ways > 1 && amount != null) {
            listOf(words, "${Money.format(amount.paise, showPaise = true)} split $ways ways").filter { it.isNotBlank() }.joinToString(" · ")
        } else words

        if (categoryId == null) subId = null
        return ParsedEntry(
            splitWays = ways,
            amountPaise = share,
            categoryId = categoryId,
            subcategoryId = subId,
            date = date,
            paymentMode = payment,
            note = note,
            matchedPhrase = best?.first?.phrase,
            raw = raw
        )
    }
}
