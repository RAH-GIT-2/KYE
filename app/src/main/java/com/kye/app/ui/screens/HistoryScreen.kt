@file:OptIn(ExperimentalFoundationApi::class)

package com.kye.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.data.CategoryKind
import com.kye.app.data.PaymentMode
import com.kye.app.data.TxnType
import com.kye.app.domain.Dates
import com.kye.app.domain.Events
import com.kye.app.domain.Money
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.CategoryPicker
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.ui.components.EmptyState
import com.kye.app.ui.components.EventChips
import com.kye.app.ui.components.PaymentChips
import com.kye.app.ui.components.TxnItem

/** Which category kind an entry type uses (transfers live under expense categories). */
private fun kindFor(type: TxnType): CategoryKind = when (type) {
    TxnType.SAVING -> CategoryKind.SAVING
    TxnType.INCOME -> CategoryKind.INCOME
    TxnType.EXPENSE, TxnType.TRANSFER -> CategoryKind.EXPENSE
}

@Composable
fun HistoryScreen(vm: KyeViewModel, onOpenTxn: (Long) -> Unit) {
    val all by vm.txns.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val subcategories by vm.subcategories.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<TxnType?>(null) }
    var catFilter by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    // Selection: long-press an entry to start, tap to add or remove more.
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var action by remember { mutableStateOf<String?>(null) } // category | payment | event | delete
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }
    val chosen = all.filter { it.txn.id in selected }
    // Entries deleted elsewhere drop out of the selection.
    if (selecting && chosen.size != selected.size) selected = chosen.map { it.txn.id }.toSet()
    val done: (String) -> Unit = { msg ->
        toast(msg)
        selected = emptySet()
        action = null
    }

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
    val shownIds = remember(grouped) { grouped.values.flatten().map { it.txn.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
    ) {
        if (selecting) {
            stickyHeader(key = "selection") {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Rounded.Close, contentDescription = "Cancel selection") }
                        Column(Modifier.weight(1f)) {
                            Text("${selected.size} selected", style = MaterialTheme.typography.titleMedium)
                            val total = chosen.filter { it.txn.type == TxnType.EXPENSE }.sumOf { it.txn.amountPaise }
                            if (total > 0) {
                                Text(
                                    "Spent ${Money.format(total, showPaise = true)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (shownIds.any { it !in selected }) {
                            TextButton(onClick = { selected = selected + shownIds }) { Text("Select all") }
                        }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(onClick = {
                            val kinds = chosen.map { kindFor(it.txn.type) }.toSet()
                            if (kinds.size > 1) toast("Pick entries of one type (all expenses, all income or all savings)")
                            else action = "category"
                        }, label = { Text("Category") })
                        AssistChip(onClick = { action = "payment" }, label = { Text("Paid with") })
                        if (events.isNotEmpty()) AssistChip(onClick = { action = "event" }, label = { Text("Event") })
                        if (chosen.size == 1) {
                            AssistChip(onClick = {
                                vm.addAgain(chosen.first().txn) { _, msg -> done(msg ?: "Added for today") }
                            }, label = { Text("Add again for now") })
                        }
                        AssistChip(onClick = { action = "delete" }, label = { Text("Delete", color = MaterialTheme.colorScheme.error) })
                    }
                }
            }
        } else {
            item(key = "title") {
                Text("History", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp))
            }
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
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("All", typeFilter == null, { typeFilter = null })
                    ChoiceChip("Expenses", typeFilter == TxnType.EXPENSE, { typeFilter = TxnType.EXPENSE })
                    ChoiceChip("Income", typeFilter == TxnType.INCOME, { typeFilter = TxnType.INCOME })
                    ChoiceChip("Savings", typeFilter == TxnType.SAVING, { typeFilter = TxnType.SAVING })
                    ChoiceChip("Transfers", typeFilter == TxnType.TRANSFER, { typeFilter = TxnType.TRANSFER })
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { c ->
                        ChoiceChip(c.name, catFilter == c.id, { catFilter = if (catFilter == c.id) null else c.id }, leading = c.emoji)
                    }
                }
                if (!selecting && all.isNotEmpty()) {
                    Text(
                        "Long-press an entry to select several and change or delete them together.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
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
                val id = row.txn.id
                val isOn = id in selected
                val toggle = { selected = if (isOn) selected - id else selected + id }
                TxnItem(
                    row,
                    Modifier
                        .animateItem()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isOn) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    showDate = false,
                    onLongClick = toggle
                ) { if (selecting) toggle() else onOpenTxn(id) }
            }
        }
    }

    when (action) {
        "category" -> {
            val kind = chosen.firstOrNull()?.let { kindFor(it.txn.type) } ?: CategoryKind.EXPENSE
            var catId by remember { mutableStateOf<Long?>(null) }
            var subId by remember { mutableStateOf<Long?>(null) }
            AlertDialog(
                onDismissRequest = { action = null },
                title = { Text("Category for ${selected.size} entries") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        CategoryPicker(
                            categories = categories.filter { it.kind == kind },
                            subcategories = subcategories,
                            catId = catId,
                            subId = subId,
                            onCategory = { c ->
                                if (catId != c.id) {
                                    catId = c.id
                                    subId = null
                                }
                            },
                            onSubcategory = { subId = it.id }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val c = catId
                        if (c == null) toast("Pick a category") else vm.bulkCategory(selected, c, subId, done)
                    }) { Text("Change") }
                },
                dismissButton = { TextButton(onClick = { action = null }) { Text("Cancel") } }
            )
        }
        "payment" -> {
            var mode by remember { mutableStateOf(chosen.firstOrNull()?.txn?.paymentMode ?: PaymentMode.UPI) }
            AlertDialog(
                onDismissRequest = { action = null },
                title = { Text("Paid with, for ${selected.size} entries") },
                text = { PaymentChips(mode) { mode = it } },
                confirmButton = { TextButton(onClick = { vm.bulkPayment(selected, mode, done) }) { Text("Change") } },
                dismissButton = { TextButton(onClick = { action = null }) { Text("Cancel") } }
            )
        }
        "event" -> {
            val common = chosen.map { it.txn.eventId }.distinct().singleOrNull()
            var eventId by remember { mutableStateOf(common) }
            AlertDialog(
                onDismissRequest = { action = null },
                title = { Text("Event for ${selected.size} entries") },
                text = {
                    EventChips(
                        events = Events.open(events.map { it.event }),
                        selectedId = eventId,
                        onSelect = { eventId = it },
                        extra = events.firstOrNull { it.event.id == eventId }?.event
                    )
                },
                confirmButton = { TextButton(onClick = { vm.bulkEvent(selected, eventId, done) }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { action = null }) { Text("Cancel") } }
            )
        }
        "delete" -> AlertDialog(
            onDismissRequest = { action = null },
            title = { Text("Delete ${selected.size} ${if (selected.size == 1) "entry" else "entries"}?") },
            text = { Text("Their bill photos are deleted too. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.bulkDelete(selected, done) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { action = null }) { Text("Cancel") } }
        )
    }
}
