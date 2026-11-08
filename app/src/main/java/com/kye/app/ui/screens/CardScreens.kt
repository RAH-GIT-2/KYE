@file:OptIn(ExperimentalMaterial3Api::class)

package com.kye.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.data.CreditCard
import com.kye.app.data.PaymentMode
import com.kye.app.domain.CardStatus
import com.kye.app.domain.Dates
import com.kye.app.domain.Money
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.DatePickerSheet
import com.kye.app.ui.components.EmptyState
import com.kye.app.ui.components.PaymentChips
import com.kye.app.ui.components.TxnItem
import java.time.LocalDate

/** "₹12,340 due 25 Nov" / "Nothing due" / "₹3,200 overdue since 25 Nov". */
@Composable
fun CardBillLine(s: CardStatus, today: LocalDate) {
    val overdue = s.isOverdue(today)
    Column {
        Text(
            when {
                s.card.isClosed -> "Closed"
                s.due <= 0 -> "Nothing due"
                overdue -> "${Money.format(s.due)} overdue since ${Dates.label(s.dueDate)}"
                else -> "${Money.format(s.due)} due ${Dates.label(s.dueDate)}"
            },
            style = MaterialTheme.typography.titleSmall,
            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            "${Money.format(s.unbilled)} on the next bill (${Dates.label(s.nextBillDate)})" +
                (s.available?.let { " · ${Money.format(it)} available" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CardsScreen(vm: KyeViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val statuses by vm.cardStatuses.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credit cards") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add card") }
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
                    "Expenses paid by credit card go on the card's bill. Paying the bill is saved as a transfer, " +
                        "so the money isn't counted as spending twice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (statuses.isEmpty()) item(key = "empty") { EmptyState("💳", "No cards yet", "Add your card with its bill date to see what's due and when") }
            items(statuses, key = { it.card.id }) { s ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Row(Modifier.fillMaxWidth().clickable { onOpen(s.card.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Text("💳", fontSize = 22.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.card.name + (if (s.card.last4.isNotBlank()) " ··${s.card.last4}" else ""),
                                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            CardBillLine(s, today)
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CardEditDialog(null, onDismiss = { creating = false }) { c ->
            vm.saveCard(c, { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }) { creating = false }
        }
    }
}

@Composable
fun CardDetailScreen(vm: KyeViewModel, cardId: Long, onOpenTxn: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val statuses by vm.cardStatuses.collectAsStateWithLifecycle()
    val rows by remember(cardId) { vm.cardRows(cardId) }.collectAsState(initial = emptyList())
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) } // edit | pay | delete
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    val today = LocalDate.now()

    val s = statuses.firstOrNull { it.card.id == cardId }
    if (s == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val c = s.card

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("💳 ${c.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; dialog = "edit" })
                        DropdownMenuItem(text = { Text(if (c.isClosed) "Reopen" else "Close card") }, onClick = {
                            menu = false
                            vm.setCardClosed(c.id, !c.isClosed, toast)
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
                        Text(
                            Money.format(s.due),
                            style = MaterialTheme.typography.displaySmall,
                            color = if (s.isOverdue(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (s.due > 0) "to pay by ${Dates.label(s.dueDate)}" else "nothing due right now",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        CardFact("Last bill", "${Money.format(s.lastBill)} on ${Dates.label(s.lastBillDate)}")
                        if (s.paidSinceBill > 0) CardFact("Paid since", Money.format(s.paidSinceBill))
                        CardFact("Next bill", "${Money.format(s.unbilled)} so far, made ${Dates.label(s.nextBillDate)}")
                        CardFact("Total owed", Money.format(s.outstanding))
                        c.limitPaise?.let { CardFact("Limit", "${Money.format(it)} · ${Money.format(s.available ?: 0)} available") }
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { dialog = "pay" }, enabled = s.outstanding > 0) { Text("Pay bill") }
                    }
                }
            }
            item(key = "list-title") {
                Text(
                    if (rows.isEmpty()) "Nothing on this card yet. Pick it when you add an expense paid by credit card."
                    else "On this card",
                    style = if (rows.isEmpty()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
                )
            }
            items(rows, key = { it.txn.id }) { row -> TxnItem(row) { onOpenTxn(row.txn.id) } }
        }
    }

    when (dialog) {
        "edit" -> CardEditDialog(c, onDismiss = { dialog = null }) { updated -> vm.saveCard(updated, toast) { dialog = null } }
        "pay" -> PayBillDialog(Money.plain(if (s.due > 0) s.due else s.outstanding), onDismiss = { dialog = null }) { amount, date, payment ->
            vm.payCardBill(c.id, amount, date, payment, toast) { dialog = null }
        }
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${c.name}?") },
            text = { Text("Only the card is deleted. Its ${rows.size} entries stay in your history.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    vm.deleteCard(c.id, toast) { onBack() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CardFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CardEditDialog(initial: CreditCard?, onDismiss: () -> Unit, onSave: (CreditCard) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var last4 by remember { mutableStateOf(initial?.last4 ?: "") }
    var billDay by remember { mutableStateOf(initial?.billDay?.toString() ?: "") }
    var dueDays by remember { mutableStateOf(initial?.dueDays?.toString() ?: "20") }
    var limit by remember { mutableStateOf(initial?.limitPaise?.let { Money.plain(it) } ?: "") }
    var remind by remember { mutableStateOf(initial?.remind ?: true) }
    val digits = KeyboardOptions(keyboardType = KeyboardType.Number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add credit card" else "Edit card") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, label = { Text("Name") }, placeholder = { Text("HDFC Millennia") }, singleLine = true)
                OutlinedTextField(
                    value = last4, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 4) last4 = v },
                    label = { Text("Last 4 digits (optional)") }, singleLine = true, keyboardOptions = digits
                )
                OutlinedTextField(
                    value = billDay, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) billDay = v },
                    label = { Text("Bill date (day of month)") }, placeholder = { Text("e.g. 5") }, singleLine = true, keyboardOptions = digits,
                    supportingText = { Text("The statement date printed on your bill") }
                )
                OutlinedTextField(
                    value = dueDays, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) dueDays = v },
                    label = { Text("Days to pay after the bill") }, singleLine = true, keyboardOptions = digits,
                    supportingText = { Text("Usually 18–20") }
                )
                OutlinedTextField(
                    value = limit, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) limit = v },
                    label = { Text("Credit limit (optional)") }, prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me ${com.kye.app.domain.Cards.REMIND_DAYS} days before it's due", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = remind, onCheckedChange = { remind = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val day = billDay.toIntOrNull()
                val days = dueDays.toIntOrNull()
                val lim = if (limit.isBlank()) null else Money.parseToPaise(limit)
                when {
                    day == null || day !in 1..31 -> Toast.makeText(context, "Enter the bill date (1–31)", Toast.LENGTH_SHORT).show()
                    days == null -> Toast.makeText(context, "Enter the days to pay", Toast.LENGTH_SHORT).show()
                    limit.isNotBlank() && lim == null -> Toast.makeText(context, "Check the limit", Toast.LENGTH_SHORT).show()
                    else -> onSave(
                        (initial ?: CreditCard(name = "", billDay = day)).copy(
                            name = name, last4 = last4, billDay = day, dueDays = days, limitPaise = lim, remind = remind
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PayBillDialog(initial: String, onDismiss: () -> Unit, onSave: (Long, LocalDate, PaymentMode) -> Unit) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf(initial) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var payment by remember { mutableStateOf(PaymentMode.NET_BANKING) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay bill") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Saved as a transfer from your bank: not counted as spending again.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = amount, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) amount = v },
                    label = { Text("Amount paid") }, prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Date", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp))
                    TextButton(onClick = { picking = true }) { Text(Dates.label(date)) }
                }
                Text("Paid from", style = MaterialTheme.typography.labelLarge)
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
                onSave(p, date, payment)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (picking) DatePickerSheet(date, onDismiss = { picking = false }) { date = it }
}
