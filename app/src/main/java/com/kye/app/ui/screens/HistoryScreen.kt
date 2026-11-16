@file:OptIn(ExperimentalFoundationApi::class)

package com.kye.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.ExperimentalMaterial3Api
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.produceState
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
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import com.kye.app.domain.Dates
import com.kye.app.domain.Events
import com.kye.app.domain.HistoryFilter
import com.kye.app.domain.Money
import com.kye.app.ui.KyeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.kye.app.ui.components.CategoryPicker
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.ui.components.EmptyState
import com.kye.app.ui.components.EventChips
import com.kye.app.ui.components.PaymentChips
import com.kye.app.ui.components.TxnItem
import com.kye.app.ui.components.LoadingRows
import com.kye.app.ui.components.tabBottomPadding

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
    val chosen = remember(all, selected) { if (selected.isEmpty()) emptyList() else all.filter { it.txn.id in selected } }
    // Entries deleted elsewhere drop out of the selection.
    if (selecting && chosen.size != selected.size) selected = chosen.map { it.txn.id }.toSet()
    val done: (String) -> Unit = { msg ->
        toast(msg)
        selected = emptySet()
        action = null
    }

    // Searching and grouping years of entries runs in the background so typing never stutters.
    // Until the first result is ready (null) the list stays blank rather than saying "No entries".
    val index by produceState<List<HistoryFilter.Indexed>?>(null, all) {
        value = withContext(Dispatchers.Default) { HistoryFilter.index(all) }
    }
    val result by produceState<Map<LocalDate, List<TxnRow>>?>(null, index, query, typeFilter, catFilter) {
        val idx = index ?: return@produceState
        value = withContext(Dispatchers.Default) { HistoryFilter.apply(idx, query, typeFilter, catFilter) }
    }
    val loading = result == null
    val grouped = result ?: emptyMap()
    val shownIds = remember(grouped) { grouped.values.flatten().map { it.txn.id } }

    // Swipe left deletes, swipe right adds the same entry again for now. Each shows a short "Undo"
    // bar. A deleted entry only hides at first; it is really deleted when the bar goes, so Undo
    // keeps its photos too.
    var offer by remember { mutableStateOf<UndoOffer?>(null) }
    fun showOffer(o: UndoOffer) {
        offer?.expire?.invoke()
        offer = o
    }
    LaunchedEffect(offer) {
        val o = offer ?: return@LaunchedEffect
        delay(5_000)
        if (offer === o) {
            o.expire()
            offer = null
        }
    }
    // Leaving History finishes a pending delete.
    DisposableEffect(Unit) { onDispose { offer?.expire?.invoke() } }
    val hiddenId = offer?.hideId

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = tabBottomPadding(96.dp))
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

        if (loading) item(key = "loading") { LoadingRows(6) }
        if (!loading && grouped.isEmpty()) {
            item(key = "empty") {
                EmptyState("🧾", if (all.isEmpty()) "No entries yet" else "Nothing matches", "Entries you add show up here, newest first")
            }
        }

        grouped.forEach { (day, dayRows) ->
            val rows = if (hiddenId == null) dayRows else dayRows.filter { it.txn.id != hiddenId }
            if (rows.isEmpty()) return@forEach
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
                SwipeRow(
                    enabled = !selecting,
                    modifier = Modifier.animateItem(),
                    onDelete = {
                        showOffer(UndoOffer("Deleted ${row.title}", hideId = id, expire = { vm.deleteTxn(row.txn) {} }))
                    },
                    onAgain = {
                        vm.addAgain(row.txn) { newId, msg ->
                            if (msg != null) toast(msg)
                            showOffer(UndoOffer("Added ${row.title} again", undo = { vm.undoAdd(newId) }))
                        }
                    }
                ) {
                    TxnItem(
                        row,
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background),
                        showDate = false,
                        onLongClick = toggle
                    ) { if (selecting) toggle() else onOpenTxn(id) }
                }
            }
        }
    }

    offer?.let { o ->
        UndoBar(
            o.text,
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 96.dp, bottom = tabBottomPadding(0.dp) - 2.dp)
        ) {
            o.undo()
            offer = null
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

/** What the Undo bar offers: [undo] when tapped, [expire] when it goes away untouched. */
private class UndoOffer(
    val text: String,
    val hideId: Long? = null,
    val undo: () -> Unit = {},
    val expire: () -> Unit = {}
)

/**
 * An entry row that can be swiped: right (green) adds it again for now, left (red) deletes it.
 * The row springs back either way; the list itself hides a deleted entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(
    enabled: Boolean,
    modifier: Modifier,
    onDelete: () -> Unit,
    onAgain: () -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.StartToEnd -> onAgain()
                SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState true
            }
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            false
        },
        positionalThreshold = { total -> total * 0.35f }
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            val dir = state.dismissDirection
            // Nothing behind the row until it is actually being swiped: keeps scrolling light.
            if (dir == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
            val again = dir == SwipeToDismissBoxValue.StartToEnd
            val color = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondary
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                else -> Color.Transparent
            }
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (again) Arrangement.Start else Arrangement.End
            ) {
                if (dir != SwipeToDismissBoxValue.Settled) {
                    val scale = 0.8f + 0.4f * state.progress.coerceIn(0f, 1f)
                    Icon(
                        if (again) Icons.Rounded.Replay else Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (again) "Add again" else "Delete", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    ) { content() }
}

/** Dark pill near the bottom: "Deleted Swiggy · Undo". */
@Composable
private fun UndoBar(text: String, modifier: Modifier, onUndo: () -> Unit) {
    Row(
        modifier
            .shadow(10.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        TextButton(onClick = onUndo) { Text("Undo", color = MaterialTheme.colorScheme.inversePrimary) }
    }
}
