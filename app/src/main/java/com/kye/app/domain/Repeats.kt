package com.kye.app.domain

import com.kye.app.data.Source
import com.kye.app.data.Txn
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import java.time.LocalDate
import java.util.Locale

/** "☕ Tea ₹20": something you add often, ready to add again with one tap. */
data class RepeatSuggestion(
    val label: String,
    val emoji: String,
    val amountPaise: Long,
    val times: Int,
    /** The most recent matching entry; adding again copies it for today. */
    val template: Txn
)

/** Spots entries you keep adding with the same amount and category. Pure Kotlin, unit-tested. */
object Repeats {
    const val LOOKBACK_DAYS = 30L
    const val MIN_TIMES = 3

    fun suggest(rows: List<TxnRow>, today: LocalDate, max: Int = 4): List<RepeatSuggestion> {
        val from = today.minusDays(LOOKBACK_DAYS)
        return rows.asSequence()
            .filter { it.txn.type == TxnType.EXPENSE && it.txn.source != Source.RECURRING }
            .filter {
                val d = Dates.toLocalDate(it.txn.dateTime)
                d.isAfter(from) && !d.isAfter(today)
            }
            .groupBy { Key(it.txn.categoryId, it.txn.subcategoryId, it.txn.amountPaise, it.txn.note.trim().lowercase(Locale.ROOT)) }
            .filter { it.value.size >= MIN_TIMES }
            .map { (_, group) ->
                val latest = group.maxBy { it.txn.dateTime }
                RepeatSuggestion(
                    label = latest.txn.note.trim().ifBlank { latest.title },
                    emoji = latest.catEmoji ?: "🔁",
                    amountPaise = latest.txn.amountPaise,
                    times = group.size,
                    template = latest.txn
                )
            }
            .sortedWith(compareByDescending<RepeatSuggestion> { it.times }.thenByDescending { it.template.dateTime })
            .take(max)
            .toList()
    }

    /** A fresh entry for [at] with the same amount, category, payment and note. */
    fun again(template: Txn, at: Long): Txn = template.copy(
        id = 0, dateTime = at, source = Source.MANUAL, recurringRuleId = null, rawVoiceText = null,
        createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
    )

    private data class Key(val categoryId: Long?, val subcategoryId: Long?, val amount: Long, val note: String)
}
