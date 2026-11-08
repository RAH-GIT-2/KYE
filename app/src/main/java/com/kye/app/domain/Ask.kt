package com.kye.app.domain

import com.kye.app.data.Category
import com.kye.app.data.LoanDirection
import com.kye.app.data.LoanRow
import com.kye.app.data.PaymentMode
import com.kye.app.data.Subcategory
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A voice keyword: phrase → category (and optional subcategory). */
data class AskKeyword(val phrase: String, val categoryId: Long, val subcategoryId: Long?)

data class AskAnswer(
    val headline: String,
    val details: List<String> = emptyList(),
    /** How the question was read, e.g. "Expenses · Swiggy · 1 Nov – 15 Nov". */
    val understood: String = "",
    val rows: List<TxnRow> = emptyList()
)

/**
 * Answers money questions offline: "how much on Swiggy this month", "petrol last 3 months",
 * "nethu evlo selavu", "biggest expense this month", "who owes me". English, Tanglish and
 * Tamil keywords. Pure Kotlin, unit-tested.
 */
object Ask {

    private enum class Intent { TOTAL, COUNT, BIGGEST, AVERAGE }

    private data class Range(val from: LocalDate, val to: LocalDate, val label: String, val byMonths: Int)

    private sealed interface Subject {
        data object All : Subject
        data class Cat(val id: Long, val name: String) : Subject
        data class Sub(val id: Long, val name: String) : Subject
        data class Text(val words: List<String>) : Subject
    }

    private val STOP = setOf(
        "how", "much", "many", "did", "do", "does", "i", "my", "me", "on", "for", "in", "at", "the", "a", "an", "of",
        "what", "was", "is", "are", "total", "show", "tell", "all", "money", "rupees", "rs", "₹", "from", "to", "with",
        "by", "using", "via", "spend", "spent", "spending", "expense", "expenses", "paid", "pay", "cost", "costs",
        "evlo", "evvalavu", "ethana", "enna", "la", "le", "ku", "kku", "ah", "naan", "nan", "panninen", "selavu",
        "செலவு", "எவ்வளவு", "and", "so", "far", "please", "?", "have", "has", "been", "it", "this", "that", "there",
        "who", "whom", "from"
    )

    private val INTENTS = listOf(
        "how many times" to Intent.COUNT, "how many" to Intent.COUNT, "times" to Intent.COUNT, "count" to Intent.COUNT,
        "biggest" to Intent.BIGGEST, "largest" to Intent.BIGGEST, "highest" to Intent.BIGGEST, "costliest" to Intent.BIGGEST,
        "top" to Intent.BIGGEST, "max" to Intent.BIGGEST,
        "per day" to Intent.AVERAGE, "a day" to Intent.AVERAGE, "average" to Intent.AVERAGE, "avg" to Intent.AVERAGE,
        "daily" to Intent.AVERAGE
    )

    private val TYPES = listOf(
        "income" to TxnType.INCOME, "earned" to TxnType.INCOME, "earn" to TxnType.INCOME, "received" to TxnType.INCOME,
        "savings" to TxnType.SAVING, "saving" to TxnType.SAVING, "saved" to TxnType.SAVING, "invested" to TxnType.SAVING,
        "investments" to TxnType.SAVING, "transfers" to TxnType.TRANSFER, "transfer" to TxnType.TRANSFER
    )

    private val PAYMENTS = listOf(
        "credit card" to PaymentMode.CREDIT_CARD, "debit card" to PaymentMode.DEBIT_CARD, "net banking" to PaymentMode.NET_BANKING,
        "netbanking" to PaymentMode.NET_BANKING, "cash" to PaymentMode.CASH, "upi" to PaymentMode.UPI, "gpay" to PaymentMode.UPI,
        "phonepe" to PaymentMode.UPI, "paytm" to PaymentMode.UPI, "wallet" to PaymentMode.WALLET, "card" to PaymentMode.CREDIT_CARD
    )

    private val LOAN_WORDS = setOf("owe", "owes", "owed", "lent", "lend", "borrowed", "borrow", "kadan", "கடன்", "loan", "loans", "back")

    private val NUMBER_WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "twelve" to 12, "onnu" to 1, "rendu" to 2, "moonu" to 3, "naalu" to 4, "anju" to 5, "aaru" to 6
    )

    // Not useful on their own for matching a category name.
    private val NAME_FILLER = setOf("and", "&", "/", "-", "–", "others", "other", "the", "of", "bills", "help", "daily")

    fun tokenize(text: String): List<String> =
        text.lowercase(Locale.ROOT)
            .replace(Regex("[?!.,:;'\"()]"), " ")
            .replace("₹", " ₹ ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    private fun find(tokens: List<String>, used: BooleanArray, phrase: String): IntRange? {
        val p = phrase.split(' ')
        for (i in 0..tokens.size - p.size) {
            if (p.indices.all { j -> !used[i + j] && tokens[i + j] == p[j] }) return i until i + p.size
        }
        return null
    }

    private fun wordMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length >= 4 && b.length >= 4 && (a.startsWith(b) || b.startsWith(a))) return true
        val common = a.zip(b).takeWhile { it.first == it.second }.size
        return common >= 6
    }

    // ---------------------------------------------------------------------------------------

    fun answer(
        question: String,
        txns: List<TxnRow>,
        categories: List<Category>,
        subcategories: List<Subcategory>,
        keywords: List<AskKeyword>,
        loans: List<LoanRow>,
        today: LocalDate
    ): AskAnswer {
        val tokens = tokenize(question)
        if (tokens.isEmpty()) return AskAnswer("Ask something like \"How much on Swiggy this month?\"")
        val used = BooleanArray(tokens.size)

        // Lent / borrowed questions go to the loans.
        if (tokens.any { it in LOAN_WORDS }) return answerLoans(tokens, loans)

        val range = parseRange(tokens, used, today)

        var intent = Intent.TOTAL
        for ((p, i) in INTENTS) {
            val r = find(tokens, used, p) ?: continue
            r.forEach { used[it] = true }
            intent = i
            break
        }

        var type = TxnType.EXPENSE
        for ((p, t) in TYPES) {
            val r = find(tokens, used, p) ?: continue
            r.forEach { used[it] = true }
            type = t
            break
        }

        var payment: PaymentMode? = null
        for ((p, m) in PAYMENTS) {
            val r = find(tokens, used, p) ?: continue
            r.forEach { used[it] = true }
            payment = m
            break
        }

        tokens.forEachIndexed { i, t -> if (t in STOP) used[i] = true }
        val rest = tokens.filterIndexed { i, _ -> !used[i] }
        val subject = matchSubject(rest, categories, subcategories, keywords)

        val inRange = txns.filter {
            val d = Dates.toLocalDate(it.txn.dateTime)
            !d.isBefore(range.from) && !d.isAfter(range.to)
        }
        fun pick(rows: List<TxnRow>) = rows.filter { r ->
            r.txn.type == type && (payment == null || r.txn.paymentMode == payment) && when (val s = subject) {
                Subject.All -> true
                is Subject.Cat -> r.txn.categoryId == s.id
                is Subject.Sub -> r.txn.subcategoryId == s.id
                is Subject.Text -> {
                    val hay = listOfNotNull(r.txn.note, r.subName, r.catName).joinToString(" ").lowercase(Locale.ROOT)
                    s.words.all { w -> hay.contains(w) }
                }
            }
        }
        val rows = pick(inRange)
        val total = rows.sumOf { it.txn.amountPaise }

        val what = when (val s = subject) {
            Subject.All -> null
            is Subject.Cat -> s.name
            is Subject.Sub -> s.name
            is Subject.Text -> "\"${s.words.joinToString(" ")}\""
        }
        val typeWord = when (type) {
            TxnType.EXPENSE -> "Expenses"
            TxnType.INCOME -> "Income"
            TxnType.SAVING -> "Savings"
            TxnType.TRANSFER -> "Transfers"
        }
        val understood = listOfNotNull(
            typeWord, what, payment?.label,
            if (range.from == range.to) Dates.short(range.from) else "${Dates.short(range.from)} – ${Dates.short(range.to)}"
        ).joinToString(" · ")

        val verb = when (type) {
            TxnType.EXPENSE -> "spent"
            TxnType.INCOME -> "income"
            TxnType.SAVING -> "saved"
            TxnType.TRANSFER -> "transferred"
        }
        val on = what?.let { " on $it" } ?: ""
        val by = payment?.let { " by ${it.label}" } ?: ""

        if (rows.isEmpty()) {
            return AskAnswer(
                headline = "Nothing $verb$on$by ${range.label}",
                understood = understood
            )
        }

        val details = ArrayList<String>()
        val headline: String
        val shown: List<TxnRow>
        when (intent) {
            Intent.COUNT -> {
                headline = "${rows.size} time${if (rows.size == 1) "" else "s"}$on$by ${range.label}"
                details += "${Money.format(total)} in all"
                shown = rows
            }
            Intent.BIGGEST -> {
                val top = rows.maxByOrNull { it.txn.amountPaise }!!
                headline = "Biggest: ${Money.format(top.txn.amountPaise, showPaise = true)} · ${top.title} · ${Dates.short(Dates.toLocalDate(top.txn.dateTime))}"
                details += "${rows.size} entries, ${Money.format(total)} in all"
                shown = rows.sortedByDescending { it.txn.amountPaise }
            }
            Intent.AVERAGE -> {
                val days = ChronoUnit.DAYS.between(range.from, minOf(range.to, today)) + 1
                headline = "${Money.format(total / days.coerceAtLeast(1))} a day$on ${range.label}"
                details += "${Money.format(total)} over $days day${if (days == 1L) "" else "s"}"
                shown = rows
            }
            Intent.TOTAL -> {
                headline = if (type == TxnType.INCOME) "${Money.format(total)} income$on ${range.label}"
                else "${Money.format(total)} $verb$on$by ${range.label}"
                details += "${rows.size} entr${if (rows.size == 1) "y" else "ies"}" +
                    if (rows.size > 1) " · ${Money.format(total / rows.size)} on average" else ""
                shown = rows
            }
        }

        // The same stretch of time just before, for comparison.
        previous(range)?.let { (pFrom, pTo, pLabel) ->
            val prevTotal = pick(txns.filter {
                val d = Dates.toLocalDate(it.txn.dateTime)
                !d.isBefore(pFrom) && !d.isAfter(pTo)
            }).sumOf { it.txn.amountPaise }
            if (prevTotal > 0 && intent != Intent.COUNT) {
                val pct = (total - prevTotal) * 100 / prevTotal
                val arrow = when {
                    pct > 0 -> "↑$pct%"
                    pct < 0 -> "↓${-pct}%"
                    else -> "same"
                }
                details += "vs ${Money.format(prevTotal)} $pLabel ($arrow)"
            } else if (prevTotal == 0L && intent == Intent.TOTAL) {
                details += "Nothing $pLabel"
            }
        }

        return AskAnswer(headline, details, understood, shown.sortedWith(
            if (intent == Intent.BIGGEST) compareByDescending { it.txn.amountPaise } else compareByDescending { it.txn.dateTime }
        ).take(30))
    }

    // ---------------------------------------------------------------------------------------

    private fun parseRange(tokens: List<String>, used: BooleanArray, today: LocalDate): Range {
        fun take(r: IntRange) = r.forEach { used[it] = true }
        val monthStart = today.withDayOfMonth(1)

        // "last 3 months", "past 10 days", "last two weeks"
        for (i in tokens.indices) {
            if (used[i] || tokens[i] !in setOf("last", "past", "previous")) continue
            val n = tokens.getOrNull(i + 1)?.let { it.toIntOrNull() ?: NUMBER_WORDS[it] } ?: continue
            val unit = tokens.getOrNull(i + 2) ?: continue
            if (n <= 0) continue
            when {
                unit.startsWith("month") -> {
                    take(i..i + 2)
                    return Range(monthStart.minusMonths(n - 1L), today, "in the last $n month${if (n > 1) "s" else ""}", n)
                }
                unit.startsWith("week") -> {
                    take(i..i + 2)
                    return Range(today.minusDays(7L * n - 1), today, "in the last $n week${if (n > 1) "s" else ""}", 0)
                }
                unit.startsWith("day") -> {
                    take(i..i + 2)
                    return Range(today.minusDays(n - 1L), today, "in the last $n day${if (n > 1) "s" else ""}", 0)
                }
            }
        }

        val phrases: List<Pair<String, () -> Range>> = listOf(
            "today" to { Range(today, today, "today", 0) },
            "inniki" to { Range(today, today, "today", 0) },
            "indru" to { Range(today, today, "today", 0) },
            "இன்று" to { Range(today, today, "today", 0) },
            "yesterday" to { Range(today.minusDays(1), today.minusDays(1), "yesterday", 0) },
            "nethu" to { Range(today.minusDays(1), today.minusDays(1), "yesterday", 0) },
            "netru" to { Range(today.minusDays(1), today.minusDays(1), "yesterday", 0) },
            "நேற்று" to { Range(today.minusDays(1), today.minusDays(1), "yesterday", 0) },
            "this week" to { Range(today.with(DayOfWeek.MONDAY), today, "this week", 0) },
            "intha vaaram" to { Range(today.with(DayOfWeek.MONDAY), today, "this week", 0) },
            "last week" to { lastWeek(today) },
            "pona vaaram" to { lastWeek(today) },
            "last month" to { lastMonth(today) },
            "previous month" to { lastMonth(today) },
            "pona maasam" to { lastMonth(today) },
            "pona masam" to { lastMonth(today) },
            "this month" to { Range(monthStart, today, "this month", 1) },
            "intha maasam" to { Range(monthStart, today, "this month", 1) },
            "intha masam" to { Range(monthStart, today, "this month", 1) },
            "last year" to { Range(LocalDate.of(today.year - 1, 1, 1), LocalDate.of(today.year - 1, 12, 31), "last year", 12) },
            "this year" to { Range(LocalDate.of(today.year, 1, 1), today, "this year", 12) },
            "financial year" to { Range(Dates.fyStart(today), today, "this financial year", 12) },
            "this fy" to { Range(Dates.fyStart(today), today, "this financial year", 12) }
        )
        for ((p, make) in phrases) find(tokens, used, p)?.let { take(it); return make() }

        // "in October", "oct 2025"
        for (i in tokens.indices) {
            if (used[i]) continue
            val month = monthOf(tokens[i]) ?: continue
            val year = tokens.getOrNull(i + 1)?.toIntOrNull()?.takeIf { it in 2000..2100 }
            used[i] = true
            if (year != null) used[i + 1] = true
            var start = LocalDate.of(year ?: today.year, month, 1)
            if (year == null && start.isAfter(today)) start = start.minusYears(1) // most recent one that has started
            val end = minOf(start.withDayOfMonth(start.lengthOfMonth()), today)
            return Range(start, end, "in ${Dates.monthName(start)}${if (start.year != today.year) " ${start.year}" else ""}", 1)
        }

        return Range(monthStart, today, "this month", 1)
    }

    private fun lastWeek(today: LocalDate): Range {
        val start = today.with(DayOfWeek.MONDAY).minusWeeks(1)
        return Range(start, start.plusDays(6), "last week", 0)
    }

    private fun lastMonth(today: LocalDate): Range {
        val start = today.withDayOfMonth(1).minusMonths(1)
        return Range(start, start.withDayOfMonth(start.lengthOfMonth()), "last month", 1)
    }

    private fun monthOf(t: String): Month? {
        if (t.length < 3) return null
        return Month.entries.firstOrNull { m ->
            val name = m.name.lowercase(Locale.ROOT)
            t == name || (t.length >= 3 && name.startsWith(t) && t.length <= name.length && (t.length == 3 || t == "sept"))
        }
    }

    /** The same stretch just before [r]: same days of last month, the month before, the week before… */
    private fun previous(r: Range): Triple<LocalDate, LocalDate, String>? {
        if (r.byMonths > 0) {
            val m = r.byMonths.toLong()
            val fullMonth = r.to == r.to.withDayOfMonth(r.to.lengthOfMonth())
            val label = when {
                m == 1L && !fullMonth -> "the same days last month"
                m == 1L -> "the month before"
                m == 12L -> "the year before"
                else -> "the $m months before"
            }
            return Triple(r.from.minusMonths(m), r.to.minusMonths(m), label)
        }
        val days = ChronoUnit.DAYS.between(r.from, r.to) + 1
        val label = when (days) {
            1L -> "the day before"
            7L -> "the week before"
            else -> "the $days days before"
        }
        return Triple(r.from.minusDays(days), r.to.minusDays(days), label)
    }

    private fun matchSubject(
        rest: List<String>,
        categories: List<Category>,
        subcategories: List<Subcategory>,
        keywords: List<AskKeyword>
    ): Subject {
        if (rest.isEmpty()) return Subject.All
        val catName = categories.associate { it.id to it.name }
        val subById = subcategories.associateBy { it.id }
        var best: Subject? = null
        var bestScore = 0

        fun consider(s: Subject, score: Int) {
            if (score > bestScore) {
                bestScore = score
                best = s
            }
        }

        // Voice keywords: every word of the phrase must be in the question.
        for (k in keywords) {
            val words = tokenize(k.phrase)
            if (words.isEmpty() || !words.all { w -> rest.any { wordMatch(it, w) } }) continue
            val sub = k.subcategoryId?.let { subById[it] }
            val s = if (sub != null && !sub.isDefault) Subject.Sub(sub.id, sub.name)
            else Subject.Cat(k.categoryId, catName[k.categoryId] ?: continue)
            consider(s, words.size * 10 + (if (s is Subject.Sub) 3 else 0) + 1)
        }
        // Names: any meaningful word of a subcategory or category name.
        for (s in subcategories) {
            if (s.isDefault) continue
            val words = tokenize(s.name).filter { it !in NAME_FILLER && it.length >= 3 }
            val hits = words.count { w -> rest.any { wordMatch(it, w) } }
            if (hits > 0) consider(Subject.Sub(s.id, s.name), hits * 10 + 3 + 2)
        }
        for (c in categories) {
            val words = tokenize(c.name).filter { it !in NAME_FILLER && it.length >= 3 }
            val hits = words.count { w -> rest.any { wordMatch(it, w) } }
            if (hits > 0) consider(Subject.Cat(c.id, c.name), hits * 10 + 2)
        }
        return best ?: Subject.Text(rest)
    }

    private fun answerLoans(tokens: List<String>, loans: List<LoanRow>): AskAnswer {
        val open = loans.filter { !it.loan.isClosed && it.remaining > 0 }
        val candidates = tokens.filter { it.length >= 3 && it !in LOAN_WORDS && it !in STOP }
        val person = candidates.firstNotNullOfOrNull { t ->
            open.map { it.loan.person }.firstOrNull { p -> tokenize(p).any { wordMatch(it, t) } }
        }
        if (person != null) {
            val mine = open.filter { it.loan.person == person }
            val get = mine.filter { it.loan.direction == LoanDirection.LENT }.sumOf { it.remaining }
            val give = mine.filter { it.loan.direction == LoanDirection.BORROWED }.sumOf { it.remaining }
            val headline = when {
                get > 0 && give > 0 -> "$person owes you ${Money.format(get)}; you owe ${Money.format(give)}"
                get > 0 -> "$person owes you ${Money.format(get)}"
                else -> "You owe $person ${Money.format(give)}"
            }
            return AskAnswer(headline, listOf("${mine.size} open entr${if (mine.size == 1) "y" else "ies"} in Lent & borrowed"), "Lent & borrowed · $person")
        }
        if (open.isEmpty()) return AskAnswer("Nobody owes you, and you owe nobody", understood = "Lent & borrowed")
        val s = Loans.summary(open, LocalDate.now())
        val people = open.groupBy { it.loan.person }.map { (p, rows) ->
            val net = rows.sumOf { if (it.loan.direction == LoanDirection.LENT) it.remaining else -it.remaining }
            if (net >= 0) "$p owes you ${Money.format(net)}" else "You owe $p ${Money.format(-net)}"
        }
        return AskAnswer(
            "${Money.format(s.toGet)} to get back · ${Money.format(s.toGive)} to give back",
            people.take(6),
            "Lent & borrowed"
        )
    }
}
