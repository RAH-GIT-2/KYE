@file:OptIn(ExperimentalFoundationApi::class)

package com.kye.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.data.TxnType
import com.kye.app.domain.Dates
import com.kye.app.domain.Money
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.ui.components.EmptyState
import com.kye.app.ui.components.TxnItem

@Composable
fun HistoryScreen(vm: KyeViewModel, onOpenTxn: (Long) -> Unit) {
    val all by vm.txns.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<TxnType?>(null) }
    var catFilter by remember { mutableStateOf<Long?>(null) }

    val grouped = remember(all, query, typeFilter, catFilter) {
        val q = query.trim().lowercase()
        all.asSequence()
            .filter { typeFilter == null || it.txn.type == typeFilter }
            .filter { catFilter == null || it.txn.categoryId == catFilter }
            .filter {
                q.isEmpty() || it.title.lowercase().contains(q) || it.txn.note.lowercase().contains(q) ||
                    (it.catName?.lowercase()?.contains(q) == true) || Money.plain(it.txn.amountPaise).contains(q)
            }
            .toList()
            .groupBy { Dates.toLocalDate(it.txn.dateTime) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 180.dp)
    ) {
        item(key = "title") {
            Text("History", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp))
        }
        item(key = "search") {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search notes, merchants, amounts") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
            Spacer(Modifier.height(10.dp))
        }
        item(key = "filters") {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("All", typeFilter == null, { typeFilter = null })
                    ChoiceChip("Expenses", typeFilter == TxnType.EXPENSE, { typeFilter = TxnType.EXPENSE })
                    ChoiceChip("Savings", typeFilter == TxnType.SAVING, { typeFilter = TxnType.SAVING })
                    ChoiceChip("Transfers", typeFilter == TxnType.TRANSFER, { typeFilter = TxnType.TRANSFER })
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { c ->
                        ChoiceChip(c.name, catFilter == c.id, { catFilter = if (catFilter == c.id) null else c.id }, leading = c.emoji)
                    }
                }
            }
        }

        if (grouped.isEmpty()) {
            item(key = "empty") {
                EmptyState("🧾", if (all.isEmpty()) "No entries yet" else "Nothing matches", "Entries you add show up here, newest first")
            }
        }

        grouped.forEach { (day, rows) ->
            stickyHeader(key = "h-$day") {
                val spent = rows.filter { it.txn.type == TxnType.EXPENSE }.sumOf { it.txn.amountPaise }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(Dates.label(day), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (spent > 0) {
                        Text(
                            "Spent ${Money.format(spent)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(rows, key = { it.txn.id }) { row ->
                TxnItem(row, Modifier.animateItem(), showDate = false) { onOpenTxn(row.txn.id) }
            }
        }
    }
}

