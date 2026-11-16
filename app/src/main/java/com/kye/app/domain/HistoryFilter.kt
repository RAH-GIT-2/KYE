package com.kye.app.domain

import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import java.time.LocalDate

/**
 * History's search and filters. The lower-cased search text of each entry is worked out once when
 * entries change, not on every letter typed, and all of it runs off the screen's own thread.
 */
object HistoryFilter {
    class Indexed(val row: TxnRow, val text: String)

    fun index(rows: List<TxnRow>): List<Indexed> = rows.map { r ->
        Indexed(
            r,
            listOfNotNull(r.title, r.txn.note, r.catName, Money.plain(r.txn.amountPaise))
                .joinToString("\n")
                .lowercase()
        )
    }

    /** Entries grouped by day, newest first (the order they come in). */
    fun apply(index: List<Indexed>, query: String, type: TxnType?, categoryId: Long?): Map<LocalDate, List<TxnRow>> {
        val q = query.trim().lowercase()
        val out = LinkedHashMap<LocalDate, MutableList<TxnRow>>()
        for (e in index) {
            val t = e.row.txn
            if (type != null && t.type != type) continue
            if (categoryId != null && t.categoryId != categoryId) continue
            if (q.isNotEmpty() && !e.text.contains(q)) continue
            out.getOrPut(Dates.toLocalDate(t.dateTime)) { ArrayList() }.add(e.row)
        }
        return out
    }
}
