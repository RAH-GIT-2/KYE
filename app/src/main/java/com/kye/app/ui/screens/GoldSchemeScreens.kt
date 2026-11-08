@file:OptIn(ExperimentalMaterial3Api::class)

package com.kye.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.kye.app.data.GoldScheme
import com.kye.app.data.GoldSchemeRow
import com.kye.app.data.PaymentMode
import com.kye.app.domain.Dates
import com.kye.app.domain.GoldSchemes
import com.kye.app.domain.Money
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.ui.components.DatePickerSheet
import com.kye.app.ui.components.EmptyState
import com.kye.app.ui.components.PaymentChips
import com.kye.app.ui.components.ProgressBar
import com.kye.app.ui.components.TxnItem
import java.time.LocalDate

/** "4 of 11 paid · next ₹5,000 on 5 Dec" plus a progress bar. */
@Composable
fun SchemeProgress(r: GoldSchemeRow, today: LocalDate) {
    val s = r.scheme
    val paid = GoldSchemes.instalmentsPaid(r)
    val overdue = GoldSchemes.overdueCount(r, today)
    Column {
        Text(
            "$paid of ${s.months} paid · ${Money.format(r.paidPaise)} of ${Money.format(GoldSchemes.totalToPay(s))}",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(6.dp))
        ProgressBar(GoldSchemes.percent(r) / 100f, MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(4.dp))
        val next = GoldSchemes.nextDue(r)
        val line = when {
            s.isClosed -> s.closedOn?.let { "Closed ${Dates.label(LocalDate.ofEpochDay(it))}" } ?: "Closed"
            GoldSchemes.isReadyToRedeem(r, today) -> "All paid · ready to redeem ${Money.format(GoldSchemes.maturityValue(s))} 🎉"
            GoldSchemes.isFullyPaid(r) -> "All paid · redeem from ${Dates.label(GoldSchemes.maturityDate(s))}"
            overdue > 1 -> "$overdue instalments missed · ${Money.format(s.monthlyPaise * overdue)} to catch up"
            next != null && next.isBefore(today) -> "Instalment was due ${Dates.label(next)}"
            next != null -> "Next ${Money.format(s.monthlyPaise)} ${Dates.label(next).let { if (it == "Today") "due today" else "on $it" }}"
            else -> ""
        }
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = if (overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun GoldSchemesScreen(vm: KyeViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val rows by vm.schemes.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gold schemes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New scheme") }
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
                    "A jeweller's monthly scheme: pay the same amount each month, they add a bonus at the end. " +
                        "Each instalment is saved as a Saving entry, and KYE reminds you on the due day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (rows.isEmpty()) item(key = "empty") { EmptyState("🪙", "No gold schemes yet", "Add the scheme you pay into every month") }
            items(rows, key = { it.scheme.id }) { r ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Row(Modifier.fillMaxWidth().clickable { onOpen(r.scheme.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Text("🪙", fontSize = 22.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.scheme.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            SchemeProgress(r, today)
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        SchemeEditDialog(null, onDismiss = { creating = false }) { s ->
            vm.saveScheme(s, { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }) { creating = false }
        }
    }
}

@Composable
fun GoldSchemeDetailScreen(vm: KyeViewModel, schemeId: Long, onOpenTxn: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val all by vm.schemes.collectAsStateWithLifecycle()
    val rows by remember(schemeId) { vm.schemeTxns(schemeId) }.collectAsState(initial = emptyList())
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) } // edit | pay | close | delete
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    val today = LocalDate.now()

    val r = all.firstOrNull { it.scheme.id == schemeId }
    if (r == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val s = r.scheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🪙 ${s.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; dialog = "edit" })
                        DropdownMenuItem(
                            text = { Text(if (s.isClosed) "Reopen" else "Close (stopped early)") },
                            onClick = {
                                menu = false
                                if (s.isClosed) vm.setSchemeClosed(s.id, false, toast) else dialog = "close"
                            }
                        )
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
                        Text(Money.format(r.paidPaise), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.tertiary)
                        Text(
                            "paid in · ${Money.format(s.monthlyPaise)} a month for ${s.months} months",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        SchemeProgress(r, today)
                        Spacer(Modifier.height(12.dp))
                        Fact("At maturity", Money.format(GoldSchemes.maturityValue(s)) + if (s.bonusPaise > 0) " (with ${Money.format(s.bonusPaise)} bonus)" else "")
                        Fact("Redeem from", Dates.label(GoldSchemes.maturityDate(s)))
                        Fact("Started", Dates.label(LocalDate.ofEpochDay(s.startDate)))
                        if (s.note.isNotBlank()) Fact("Note", s.note)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!s.isClosed && !GoldSchemes.isFullyPaid(r)) {
                                Button(onClick = { dialog = "pay" }) { Text("Pay ${Money.format(s.monthlyPaise)}") }
                            }
                            if (!s.isClosed && GoldSchemes.isFullyPaid(r)) {
                                Button(onClick = { vm.setSchemeClosed(s.id, true, toast) }) { Text("Mark redeemed") }
                            }
                        }
                    }
                }
            }
            if (!s.isClosed && GoldSchemes.overdueCount(r, today) > 0) {
                item(key = "catch-up") {
                    Text(
                        "Paid some of these before adding the scheme? Use Pay and pick the date for each, or, if they're " +
                            "already Saving entries in KYE, open one and choose this scheme under \"Gold scheme\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "list-title") {
                Text(
                    if (rows.isEmpty()) "No instalments yet. Tap Pay when you pay the jeweller." else "Instalments",
                    style = if (rows.isEmpty()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
                )
            }
            items(rows, key = { it.txn.id }) { row -> TxnItem(row) { onOpenTxn(row.txn.id) } }
        }
    }

    when (dialog) {
        "edit" -> SchemeEditDialog(s, onDismiss = { dialog = null }) { updated -> vm.saveScheme(updated, toast) { dialog = null } }
        "pay" -> PayInstalmentDialog(Money.plain(s.monthlyPaise), onDismiss = { dialog = null }) { amount, date, payment ->
            vm.payScheme(s.id, amount, date, payment, toast) { dialog = null }
        }
        "close" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Close ${s.name}?") },
            text = { Text("Use this if you stopped paying or took the money early. Reminders stop; the instalments stay in your savings. You can reopen it.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    vm.setSchemeClosed(s.id, true, toast)
                }) { Text("Close") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete ${s.name}?") },
            text = { Text("Only the scheme is deleted. Its ${rows.size} instalments stay in your history as savings.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    vm.deleteScheme(s.id, toast) { onBack() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun digitsOnly(v: String) = v.all { it.isDigit() || it == '.' || it == ',' }

@Composable
private fun SchemeEditDialog(initial: GoldScheme?, onDismiss: () -> Unit, onSave: (GoldScheme) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var monthly by remember { mutableStateOf(initial?.monthlyPaise?.let { Money.plain(it) } ?: "") }
    var months by remember { mutableStateOf(initial?.months?.toString() ?: "11") }
    var bonus by remember { mutableStateOf(initial?.bonusPaise?.takeIf { it > 0 }?.let { Money.plain(it) } ?: "") }
    var start by remember { mutableStateOf(initial?.startDate?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()) }
    var remind by remember { mutableStateOf(initial?.remind ?: true) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New gold scheme" else "Edit scheme") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, label = { Text("Jeweller / scheme") },
                    placeholder = { Text("GRT Golden 11") }, singleLine = true
                )
                OutlinedTextField(
                    value = monthly, onValueChange = { if (digitsOnly(it)) monthly = it },
                    label = { Text("Every month") }, prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text("For how many months", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("10", "11", "12").forEach { m -> ChoiceChip("$m months", months == m, { months = m }) }
                }
                OutlinedTextField(
                    value = months, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) months = v },
                    label = { Text("Months") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = bonus, onValueChange = { if (digitsOnly(it)) bonus = it },
                    label = { Text("Jeweller's bonus (optional)") }, prefix = { Text("₹ ") }, singleLine = true,
                    supportingText = { Text("Often one month's amount, added at the end") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("First due", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(80.dp))
                    TextButton(onClick = { picking = true }) { Text(Dates.label(start)) }
                }
                Text(
                    "Later instalments fall due on the same day each month.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me on the due day", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = remind, onCheckedChange = { remind = it })
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(80) }, label = { Text("Note (optional)") },
                    placeholder = { Text("Card no. / branch") }, singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = Money.parseToPaise(monthly)
                val n = months.toIntOrNull()
                val b = if (bonus.isBlank()) 0L else Money.parseToPaise(bonus)
                when {
                    m == null || m <= 0 -> Toast.makeText(context, "Enter the monthly amount", Toast.LENGTH_SHORT).show()
                    n == null || n <= 0 -> Toast.makeText(context, "Enter the number of months", Toast.LENGTH_SHORT).show()
                    b == null -> Toast.makeText(context, "Check the bonus amount", Toast.LENGTH_SHORT).show()
                    else -> onSave(
                        (initial ?: GoldScheme(name = "", monthlyPaise = m, months = n, startDate = start.toEpochDay())).copy(
                            name = name, monthlyPaise = m, months = n, bonusPaise = b,
                            startDate = start.toEpochDay(), remind = remind, note = note
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (picking) DatePickerSheet(start, onDismiss = { picking = false }) { start = it }
}

@Composable
private fun PayInstalmentDialog(initial: String, onDismiss: () -> Unit, onSave: (Long, LocalDate, PaymentMode) -> Unit) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf(initial) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var payment by remember { mutableStateOf(PaymentMode.UPI) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay instalment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Saved as a Saving entry under Gold Scheme. Paying two months at once? Enter both together.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = amount, onValueChange = { if (digitsOnly(it)) amount = it },
                    label = { Text("Amount") }, prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Date", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp))
                    TextButton(onClick = { picking = true }) { Text(Dates.label(date)) }
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
                onSave(p, date, payment)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (picking) DatePickerSheet(date, onDismiss = { picking = false }) { date = it }
}
