@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kye.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.data.Event
import com.kye.app.data.EventRow
import com.kye.app.domain.Events
import com.kye.app.domain.Money
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.DatePickerSheet
import com.kye.app.ui.components.EmptyState
import com.kye.app.ui.components.ProgressBar
import com.kye.app.ui.components.TxnItem
import java.time.LocalDate

private val EVENT_EMOJIS = listOf("🪔", "🎆", "🌾", "🎄", "🌙", "💍", "🎂", "✈️", "🏖️", "🎒", "🏠", "🏥", "🛕", "🎁", "🎉", "🚗")

@Composable
fun EventProgress(row: EventRow) {
    val budget = row.event.budgetPaise
    Column {
        Text(
            if (budget != null) "${Money.format(row.spentPaise)} of ${Money.format(budget)}" else "${Money.format(row.spentPaise)} spent",
            style = MaterialTheme.typography.titleSmall
        )
        if (budget != null && budget > 0) {
            Spacer(Modifier.height(6.dp))
            val f = row.spentPaise.toFloat() / budget
            ProgressBar(
                f,
                when {
                    f >= 1f -> MaterialTheme.colorScheme.error
                    f >= 0.8f -> Color(0xFFF39C12)
                    else -> MaterialTheme.colorScheme.secondary
                }
            )
            Spacer(Modifier.height(4.dp))
            val left = budget - row.spentPaise
            Text(
                if (left >= 0) "${Money.format(left)} left" else "${Money.format(-left)} over budget",
                style = MaterialTheme.typography.bodySmall,
                color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------------------------

@Composable
fun EventsScreen(vm: KyeViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val events by vm.events.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val open = events.filter { !it.event.isClosed }
    val done = events.filter { it.event.isClosed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Events") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New event") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "hint") {
                Text(
                    "A festival, wedding, trip or school reopening with its own budget. Entries tagged to an event " +
                        "still count in your normal monthly spending.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (events.isEmpty()) {
                item(key = "empty") { EmptyState("🪔", "No events yet", "Create one for Deepavali, a wedding or a trip") }
            }
            items(open, key = { it.event.id }) { r -> EventItem(r, Events.isActive(r.event, today)) { onOpen(r.event.id) } }
            if (done.isNotEmpty()) {
                item(key = "done-title") {
                    Text(
                        "Done",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(done, key = { it.event.id }) { r -> EventItem(r, false) { onOpen(r.event.id) } }
            }
        }
    }

    if (creating) {
        EventEditDialog(null, onDismiss = { creating = false }) { e ->
            vm.saveEvent(e, { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }) { creating = false }
        }
    }
}

@Composable
private fun EventItem(r: EventRow, active: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Text(r.event.emoji, fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.event.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        if (active) "On now" else null,
                        Events.dates(r.event),
                        "${r.entries} entr${if (r.entries == 1) "y" else "ies"}"
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                EventProgress(r)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------------------------

@Composable
fun EventDetailScreen(vm: KyeViewModel, eventId: Long, onOpenTxn: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val events by vm.events.collectAsStateWithLifecycle()
    val rows by remember(eventId) { vm.eventTxns(eventId) }.collectAsState(initial = emptyList())
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }

    val r = events.firstOrNull { it.event.id == eventId }
    if (r == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val e = r.event
    val slices = Events.breakdown(rows)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${e.emoji} ${e.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = true })
                        DropdownMenuItem(text = { Text(if (e.isClosed) "Reopen" else "Mark as done") }, onClick = {
                            menu = false
                            vm.setEventClosed(e.id, !e.isClosed, toast)
                        })
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; deleting = true }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "summary") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(Money.format(r.spentPaise), style = MaterialTheme.typography.displaySmall)
                        Text(
                            listOfNotNull(Events.dates(e), if (e.isClosed) "done" else null, "${r.entries} entries").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        EventProgress(r)
                        if (slices.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            slices.forEach { s ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text("${s.emoji}  ${s.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(Money.format(s.total), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            item(key = "how") {
                Text(
                    "Tag entries from the + screen or the voice card (\"crackers 2000 for ${e.name.substringBefore(' ')}\"), " +
                        "or long-press an older entry in History.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(rows, key = { it.txn.id }) { row -> TxnItem(row) { onOpenTxn(row.txn.id) } }
        }
    }

    if (editing) {
        EventEditDialog(e, onDismiss = { editing = false }) { updated -> vm.saveEvent(updated, toast) { editing = false } }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete ${e.name}?") },
            text = { Text("Only the event is deleted. Its ${r.entries} entries stay in your history, just without the tag.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.deleteEvent(e.id, toast) { onBack() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Create / edit
// ---------------------------------------------------------------------------------------------

@Composable
private fun EventEditDialog(initial: Event?, onDismiss: () -> Unit, onSave: (Event) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: EVENT_EMOJIS.first()) }
    var budget by remember { mutableStateOf(initial?.budgetPaise?.let { Money.plain(it) } ?: "") }
    var start by remember { mutableStateOf(initial?.startDate?.let { LocalDate.ofEpochDay(it) }) }
    var end by remember { mutableStateOf(initial?.endDate?.let { LocalDate.ofEpochDay(it) }) }
    var picking by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New event" else "Edit event") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) },
                    label = { Text("Name") }, placeholder = { Text("Deepavali 2026") }, singleLine = true
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EVENT_EMOJIS.forEach { em ->
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (em == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { emoji = em },
                            contentAlignment = Alignment.Center
                        ) { Text(em, fontSize = 20.sp) }
                    }
                }
                OutlinedTextField(
                    value = budget, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) budget = v },
                    label = { Text("Budget (optional)") }, prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                listOf("From" to start, "To" to end).forEach { (label, d) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp))
                        TextButton(onClick = { picking = label }) { Text(d?.let { com.kye.app.domain.Dates.label(it) } ?: "Add date") }
                        if (d != null) TextButton(onClick = { if (label == "From") start = null else end = null }) { Text("Remove") }
                    }
                }
                Text(
                    "While today is between these dates, new entries start tagged to this event (one tap removes it).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val b = budget.takeIf { it.isNotBlank() }?.let { Money.parseToPaise(it) }
                if (budget.isNotBlank() && (b == null || b <= 0)) {
                    Toast.makeText(context, "Budget must be a number, or leave it empty", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                onSave(
                    (initial ?: Event(name = "", emoji = emoji)).copy(
                        name = name, emoji = emoji, budgetPaise = b,
                        startDate = start?.toEpochDay(), endDate = end?.toEpochDay()
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    when (picking) {
        "From" -> DatePickerSheet(start ?: LocalDate.now(), onDismiss = { picking = null }) { start = it }
        "To" -> DatePickerSheet(end ?: (start ?: LocalDate.now()).plusDays(7), onDismiss = { picking = null }) { end = it }
    }
}
