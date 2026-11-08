@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kye.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.kye.app.data.CategoryKind
import com.kye.app.data.Goal
import com.kye.app.data.GoalRow
import com.kye.app.data.PaymentMode
import com.kye.app.domain.Dates
import com.kye.app.domain.Goals
import com.kye.app.domain.Money
import com.kye.app.domain.Warranties
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.ui.components.DatePickerSheet
import com.kye.app.ui.components.EmptyState
import com.kye.app.ui.components.PaymentChips
import com.kye.app.ui.components.ProgressBar
import com.kye.app.ui.components.ReceiptViewer
import com.kye.app.ui.components.TxnItem
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDate

private val GOAL_EMOJIS = listOf("🎯", "✈️", "🏍️", "🚗", "🏠", "💍", "🎓", "📱", "💻", "🏥", "🪙", "🎁", "🛕", "👶", "🛋️", "🌴")

@Composable
fun GoalProgress(r: GoalRow, today: LocalDate) {
    Column {
        Text(
            "${Money.format(r.progress)} of ${Money.format(r.goal.targetPaise)} · ${Goals.percent(r)}%",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(6.dp))
        ProgressBar(r.progress.toFloat() / r.goal.targetPaise.coerceAtLeast(1), MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(4.dp))
        val line = when {
            Goals.isReached(r) -> "Reached 🎉"
            Goals.isOverdue(r, today) -> "${Money.format(Goals.remaining(r))} to go · target date has passed"
            else -> Goals.monthlyNeeded(r, today)?.let { m ->
                "Save ${Money.format(m)} a month to reach it by ${Dates.monthShort(LocalDate.ofEpochDay(r.goal.targetDate!!))} " +
                    "${LocalDate.ofEpochDay(r.goal.targetDate).year}"
            } ?: "${Money.format(Goals.remaining(r))} to go"
        }
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = if (Goals.isOverdue(r, today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun GoalsScreen(vm: KyeViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val goals by vm.goals.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Savings goals") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New goal") }
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
                    "Money you add to a goal is saved as a Saving entry, so it counts in your savings as usual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (goals.isEmpty()) item(key = "empty") { EmptyState("🎯", "No goals yet", "A trip, a bike, a phone… set a target and save towards it") }
            items(goals, key = { it.goal.id }) { r ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Row(Modifier.fillMaxWidth().clickable { onOpen(r.goal.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Text(r.goal.emoji, fontSize = 22.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.goal.name + if (r.goal.isClosed) " · done" else "",
                                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            GoalProgress(r, today)
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        GoalEditDialog(null, onDismiss = { creating = false }) { g ->
            vm.saveGoal(g, { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }) { creating = false }
        }
    }
}

@Composable
fun GoalDetailScreen(vm: KyeViewModel, goalId: Long, onOpenTxn: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val goals by vm.goals.collectAsStateWithLifecycle()
    val rows by remember(goalId) { vm.goalTxns(goalId) }.collectAsState(initial = emptyList())
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) } // edit | add | take | delete
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    val today = LocalDate.now()

    val r = goals.firstOrNull { it.goal.id == goalId }
    if (r == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val g = r.goal

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${g.emoji} ${g.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; dialog = "edit" })
                        DropdownMenuItem(text = { Text(if (g.isClosed) "Reopen" else "Mark as done") }, onClick = {
                            menu = false
                            vm.setGoalClosed(g.id, !g.isClosed, toast)
                        })
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; dialog = "delete" })
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
                        Text(Money.format(r.progress), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.secondary)
                        Text(
                            listOfNotNull(
                                "saved of ${Money.format(g.targetPaise)}",
                                g.targetDate?.let { "by ${Dates.label(LocalDate.ofEpochDay(it))}" },
                                if (r.withdrawnPaise > 0) "${Money.format(r.withdrawnPaise)} taken out" else null
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        GoalProgress(r, today)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { dialog = "add" }, enabled = !g.isClosed) { Text("Add ₹") }
                            OutlinedButton(onClick = { dialog = "take" }, enabled = r.progress > 0) { Text("Take out") }
                        }
                    }
                }
            }
            item(key = "list-title") {
                Text(
                    if (rows.isEmpty()) "Nothing added yet. Tap Add ₹, or tag a Saving entry to this goal on the + screen."
                    else "Added and taken out",
                    style = if (rows.isEmpty()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
                )
            }
            items(rows, key = { it.txn.id }) { row -> TxnItem(row) { onOpenTxn(row.txn.id) } }
        }
    }

    when (dialog) {
        "edit" -> GoalEditDialog(g, onDismiss = { dialog = null }) { updated -> vm.saveGoal(updated, toast) { dialog = null } }
        "add" -> AddToGoalDialog(vm, onDismiss = { dialog = null }) { amount, date, payment, catId ->
            vm.addToGoal(g.id, amount, date, payment, catId, null, toast) { dialog = null }
        }
        "take" -> AmountDialog(
            title = "Take money out",
            note = "${Money.format(r.progress, showPaise = true)} saved. Saved as a transfer: not spending, not income.",
            initial = "",
            onDismiss = { dialog = null }
        ) { amount, date -> vm.withdrawFromGoal(g.id, amount, date, r.progress, toast) { dialog = null } }
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${g.name}?") },
            text = { Text("Only the goal is deleted. Its ${rows.size} entries stay in your history as savings.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    vm.deleteGoal(g.id, toast) { onBack() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GoalEditDialog(initial: Goal?, onDismiss: () -> Unit, onSave: (Goal) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: GOAL_EMOJIS.first()) }
    var target by remember { mutableStateOf(initial?.targetPaise?.let { Money.plain(it) } ?: "") }
    var date by remember { mutableStateOf(initial?.targetDate?.let { LocalDate.ofEpochDay(it) }) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New goal" else "Edit goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, placeholder = { Text("Goa trip") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GOAL_EMOJIS.forEach { em ->
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (em == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { emoji = em },
                            contentAlignment = Alignment.Center
                        ) { Text(em, fontSize = 20.sp) }
                    }
                }
                OutlinedTextField(
                    value = target, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) target = v },
                    label = { Text("Target") }, prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("By", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp))
                    TextButton(onClick = { picking = true }) { Text(date?.let { Dates.label(it) } ?: "Add a date (optional)") }
                    if (date != null) TextButton(onClick = { date = null }) { Text("Remove") }
                }
                Text(
                    "With a date, KYE shows how much to save each month to get there.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = Money.parseToPaise(target)
                if (t == null || t <= 0) {
                    Toast.makeText(context, "Enter a target amount", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                onSave((initial ?: Goal(name = "", emoji = emoji, targetPaise = t)).copy(name = name, emoji = emoji, targetPaise = t, targetDate = date?.toEpochDay()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (picking) DatePickerSheet(date ?: LocalDate.now().plusMonths(6), onDismiss = { picking = false }) { date = it }
}

@Composable
private fun AddToGoalDialog(vm: KyeViewModel, onDismiss: () -> Unit, onSave: (Long, LocalDate, PaymentMode, Long?) -> Unit) {
    val context = LocalContext.current
    val cats by vm.categories.collectAsStateWithLifecycle()
    val savingCats = cats.filter { it.kind == CategoryKind.SAVING }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var payment by remember { mutableStateOf(PaymentMode.UPI) }
    var catId by remember { mutableStateOf(savingCats.firstOrNull()?.id) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) amount = v },
                    label = { Text("Amount") }, prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Date", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp))
                    TextButton(onClick = { picking = true }) { Text(Dates.label(date)) }
                }
                if (savingCats.isNotEmpty()) {
                    Text("Saved in", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        savingCats.forEach { c -> ChoiceChip(c.name, catId == c.id, { catId = c.id }, leading = c.emoji) }
                    }
                }
                PaymentChips(payment) { payment = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = Money.parseToPaise(amount)
                if (p == null || p <= 0) {
                    Toast.makeText(context, "Enter an amount", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                onSave(p, date, payment, catId)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (picking) DatePickerSheet(date, onDismiss = { picking = false }) { date = it }
}

@Composable
private fun AmountDialog(title: String, note: String, initial: String, onDismiss: () -> Unit, onSave: (Long, LocalDate) -> Unit) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf(initial) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(note, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = amount, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) amount = v },
                    label = { Text("Amount") }, prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Date", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp))
                    TextButton(onClick = { picking = true }) { Text(Dates.label(date)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = Money.parseToPaise(amount)
                if (p == null || p <= 0) {
                    Toast.makeText(context, "Enter an amount", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                onSave(p, date)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (picking) DatePickerSheet(date, onDismiss = { picking = false }) { date = it }
}

// ---------------------------------------------------------------------------------------------
// Warranties
// ---------------------------------------------------------------------------------------------

@Composable
fun WarrantiesScreen(vm: KyeViewModel, onOpenTxn: (Long) -> Unit, onBack: () -> Unit) {
    val rows by vm.warranties.collectAsStateWithLifecycle()
    var showEnded by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val shown = rows.filter { Warranties.isCovered(it.untilDate, today) != showEnded }
        .let { if (showEnded) it.sortedByDescending { r -> r.untilDate } else it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Warranties") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "filter") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Covered", !showEnded, { showEnded = false })
                    ChoiceChip("Ended", showEnded, { showEnded = true })
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        "🛡️",
                        if (showEnded) "No ended warranties" else "No warranties yet",
                        "Set a warranty on a purchase in the + screen, and add the bill photo"
                    )
                }
            }
            items(shown, key = { it.txn.id }) { w ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Row(Modifier.fillMaxWidth().clickable { onOpenTxn(w.txn.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(w.catEmoji ?: "🛡️", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(w.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${Money.format(w.txn.amountPaise)} · bought ${Dates.label(Dates.toLocalDate(w.txn.dateTime))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val until = LocalDate.ofEpochDay(w.untilDate)
                            val covered = Warranties.isCovered(w.untilDate, today)
                            Text(
                                if (covered) "Covered till ${Dates.label(until)}" else "Ended ${Dates.label(until)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!covered) MaterialTheme.colorScheme.error
                                else if (w.untilDate - today.toEpochDay() <= Warranties.NOTICE_DAYS) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (w.attachments > 0) {
                            TextButton(onClick = {
                                scope.launch {
                                    viewing = vm.attachments(w.txn.id).firstOrNull()?.firstOrNull()?.fileName
                                }
                            }) { Text("📎 Bill") }
                        }
                    }
                }
            }
        }
    }
    viewing?.let { ReceiptViewer(it, onClose = { viewing = null }) }
}
