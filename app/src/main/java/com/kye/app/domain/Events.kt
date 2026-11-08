package com.kye.app.domain

import com.kye.app.data.Event
import com.kye.app.data.EventRow
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import java.time.LocalDate
import java.util.Locale

data class EventSlice(val name: String, val emoji: String, val total: Long)

/** Event budget rules. Pure Kotlin, unit-tested. */
object Events {

    /** Open, and today is within its dates (an open event without dates counts as running). */
    fun isActive(e: Event, today: LocalDate): Boolean {
        if (e.isClosed) return false
        val d = today.toEpochDay()
        return (e.startDate == null || e.startDate <= d) && (e.endDate == null || e.endDate >= d)
    }

    /** Pre-selected for a new entry: the most recently created active event that has dates. */
    fun preselect(events: List<Event>, today: LocalDate): Event? =
        events.filter { isActive(it, today) && (it.startDate != null || it.endDate != null) }.maxByOrNull { it.createdAt }

    /** Offered when adding or tagging entries: everything not marked done. */
    fun open(events: List<Event>): List<Event> = events.filter { !it.isClosed }

    private val IGNORE = setOf("the", "and", "for", "trip", "event", "function", "visit", "day", "days")

    /**
     * An open event whose name is mentioned: "crackers 2000 for deepavali" → Deepavali 2026.
     * Uses words of 4+ letters from the name (years and filler like "trip" don't count).
     */
    fun mentioned(text: String, events: List<Event>): Event? {
        val words = text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.toSet()
        return open(events).firstOrNull { e ->
            val keys = e.name.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length >= 4 && it !in IGNORE && it.toIntOrNull() == null }
            keys.isNotEmpty() && keys.any { k -> words.any { w -> w == k || (w.startsWith(k) && w.length - k.length <= 3) } }
        }
    }

    /** Spending by category within an event, biggest first. */
    fun breakdown(rows: List<TxnRow>): List<EventSlice> =
        rows.filter { it.txn.type == TxnType.EXPENSE }
            .groupBy { it.txn.categoryId }
            .map { (_, rs) -> EventSlice(rs.first().catName ?: "Uncategorised", rs.first().catEmoji ?: "❔", rs.sumOf { it.txn.amountPaise }) }
            .sortedByDescending { it.total }

    fun left(row: EventRow): Long? = row.event.budgetPaise?.let { it - row.spentPaise }

    /** "Deepavali budget 80% used" when this entry crosses 80% or 100%; null otherwise. */
    fun alert(name: String, before: Long, after: Long, budget: Long?): String? {
        if (budget == null || budget <= 0) return null
        val eighty = budget * 8 / 10
        return when {
            before < budget && after >= budget -> "$name budget exceeded"
            before < eighty && after >= eighty -> "$name budget 80% used"
            else -> null
        }
    }

    /** "25 Oct – 5 Nov", "from 25 Oct", "until 5 Nov" or null. */
    fun dates(e: Event): String? {
        val s = e.startDate?.let { Dates.short(LocalDate.ofEpochDay(it)) }
        val t = e.endDate?.let { Dates.short(LocalDate.ofEpochDay(it)) }
        return when {
            s != null && t != null -> "$s – $t"
            s != null -> "from $s"
            t != null -> "until $t"
            else -> null
        }
    }
}
