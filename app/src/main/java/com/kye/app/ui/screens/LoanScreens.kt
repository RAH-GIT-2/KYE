@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kye.app.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.data.Loan
import com.kye.app.data.LoanDirection
import com.kye.app.data.LoanPayment
import com.kye.app.data.LoanRow
import com.kye.app.domain.Dates
import com.kye.app.domain.Loans
import com.kye.app.domain.Money
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.ui.components.DatePickerSheet
import com.kye.app.ui.components.EmptyState
import com.kye.app.ui.components.ProgressBar
import java.time.LocalDate

private fun day(epochDay: Long) = Dates.label(LocalDate.ofEpochDay(epochDay))

// ---------------------------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------------------------

@Composable
fun LoansScreen(vm: KyeViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val loans by vm.loans.collectAsStateWithLifecycle()
    val summary by vm.loanSummary.collectAsStateWithLifecycle()
    val people by vm.loanPeople.collectAsStateWithLifecycle()
    var showSettled by rememberSaveable { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val shown = loans.filter { (it.loan.isClosed || it.remaining <= 0) == showSettled }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lent & borrowed") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "summary") { Column {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile("To get back", Money.format(summary.toGet), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    Tile("To give back", Money.format(summary.toGive), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                }
                if (summary.overdue > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${summary.overdue} overdue",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "Kept apart from your spending: lending isn't an expense.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } }
            item(key = "filter") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Open", !showSettled, { showSettled = false })
                    ChoiceChip("Settled", showSettled, { showSettled = true })
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        if (showSettled) "✅" else "🤝",
                        if (showSettled) "Nothing settled yet" else "No open loans",
                        if (showSettled) "Fully repaid loans move here" else "Tap Add when you lend or borrow money"
                    )
                }
            }
            items(shown, key = { it.loan.id }) { row -> LoanItem(row, today) { onOpen(row.loan.id) } }
        }
    }

    if (adding) {
        LoanEditDialog(
            initial = null,
            people = people,
            onDismiss = { adding = false },
            onSave = { loan ->
                vm.saveLoan(loan, { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }) { adding = false }
            }
        )
    }
}

@Composable
private fun Tile(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun Initial(name: String, lent: Boolean) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (lent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LoanItem(row: LoanRow, today: LocalDate, onClick: () -> Unit) {
    val l = row.loan
    val lent = l.direction == LoanDirection.LENT
    val overdue = Loans.isOverdue(row, today)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Initial(l.person, lent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(l.person, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val parts = listOfNotNull(
                    if (lent) "You lent" else "You borrowed",
                    day(l.date),
                    l.dueDate?.let { if (overdue) "overdue since ${day(it)}" else "due ${day(it)}" }
                )
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(if (l.isClosed) l.amountPaise else row.remaining, showPaise = true),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (lent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                )
                if (!l.isClosed && row.paidPaise > 0) {
                    Text("of ${Money.format(l.amountPaise)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------------------------

private sealed interface LoanDialog {
    data object Edit : LoanDialog
    data object Pay : LoanDialog
    data object Delete : LoanDialog
    data class DeletePayment(val p: LoanPayment) : LoanDialog
}

@Composable
fun LoanDetailScreen(vm: KyeViewModel, loanId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val loans by vm.loans.collectAsStateWithLifecycle()
    val people by vm.loanPeople.collectAsStateWithLifecycle()
    val payments by remember(loanId) { vm.loanPayments(loanId) }.collectAsState(initial = emptyList())
    var dialog by remember { mutableStateOf<LoanDialog?>(null) }
    var menu by remember { mutableStateOf(false) }
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    val today = LocalDate.now()

    val row = loans.firstOrNull { it.loan.id == loanId }
    if (row == null) {
        Box(Modifier.fillMaxSize()) // deleted
        return
    }
    val l = row.loan
    val lent = l.direction == LoanDirection.LENT
    val open = !l.isClosed && row.remaining > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(l.person, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; dialog = LoanDialog.Edit })
                        if (open) {
                            DropdownMenuItem(text = { Text("Mark as settled") }, onClick = {
                                menu = false
                                vm.setLoanClosed(l.id, true, toast)
                            })
                        } else if (row.remaining > 0) {
                            DropdownMenuItem(text = { Text("Reopen") }, onClick = {
                                menu = false
                                vm.setLoanClosed(l.id, false, toast)
                            })
                        }
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; dialog = LoanDialog.Delete }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(
                            when {
                                !open && row.remaining <= 0 -> "Fully repaid"
                                !open -> "Settled"
                                lent -> "${l.person} owes you"
                                else -> "You owe ${l.person}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            Money.format(if (open) row.remaining else l.amountPaise, showPaise = true),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (lent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(
                            row.paidPaise.toFloat() / l.amountPaise.coerceAtLeast(1),
                            MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(8.dp))
                        val facts = listOfNotNull(
                            "${if (lent) "Lent" else "Borrowed"} ${Money.format(l.amountPaise)} on ${day(l.date)}",
                            row.paidPaise.takeIf { it > 0 }?.let { "${Money.format(it)} repaid" },
                            l.dueDate?.let {
                                if (Loans.isOverdue(row, today)) "Overdue since ${day(it)}" else "Due ${day(it)}"
                            }
                        )
                        Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        if (l.note.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(l.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (open) {
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { dialog = LoanDialog.Pay }) {
                                    Text(if (lent) "Got money back" else "Paid back")
                                }
                                OutlinedButton(onClick = {
                                    val send = Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, Loans.reminderMessage(row))
                                    context.startActivity(Intent.createChooser(send, "Send reminder"))
                                }) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Remind")
                                }
                            }
                        }
                    }
                }
            }

            item(key = "payments-title") {
                Column {
                    Text("Repayments", style = MaterialTheme.typography.titleMedium)
                    if (payments.isEmpty()) {
                        Text("None yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(payments, key = { it.id }) { p ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Money.format(p.amountPaise, showPaise = true), style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(day(p.date), p.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { dialog = LoanDialog.DeletePayment(p) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete repayment")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        LoanDialog.Edit -> LoanEditDialog(
            initial = l,
            people = people,
            onDismiss = { dialog = null },
            onSave = { loan -> vm.saveLoan(loan, toast) { dialog = null } }
        )
        LoanDialog.Pay -> PaymentDialog(
            remaining = row.remaining,
            lent = lent,
            onDismiss = { dialog = null },
            onSave = { amount, date, note -> vm.addLoanPayment(l.id, amount, date, note, toast) { dialog = null } }
        )
        LoanDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete this loan?") },
            text = { Text("${l.person} · ${Money.format(l.amountPaise)}, and all its repayments. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    vm.deleteLoan(l.id, toast) { onBack() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
        is LoanDialog.DeletePayment -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete this repayment?") },
            text = { Text("${Money.format(d.p.amountPaise, showPaise = true)} on ${day(d.p.date)}. The loan reopens if money is left.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    vm.deleteLoanPayment(d.p.id, toast)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

@Composable
private fun DateRow(label: String, date: LocalDate?, onPick: () -> Unit, onClear: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
        TextButton(onClick = onPick) { Text(date?.let { Dates.label(it) } ?: "Add") }
        if (date != null && onClear != null) TextButton(onClick = onClear) { Text("Remove") }
    }
}

@Composable
private fun LoanEditDialog(initial: Loan?, people: List<String>, onDismiss: () -> Unit, onSave: (Loan) -> Unit) {
    var direction by remember { mutableStateOf(initial?.direction ?: LoanDirection.LENT) }
    var person by remember { mutableStateOf(initial?.person ?: "") }
    var amount by remember { mutableStateOf(initial?.amountPaise?.let { Money.plain(it) } ?: "") }
    var date by remember { mutableStateOf(initial?.date?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()) }
    var due by remember { mutableStateOf(initial?.dueDate?.let { LocalDate.ofEpochDay(it) }) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var picking by remember { mutableStateOf<String?>(null) } // "date" | "due"
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Lent or borrowed" else "Edit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("I lent", direction == LoanDirection.LENT, { direction = LoanDirection.LENT })
                    ChoiceChip("I borrowed", direction == LoanDirection.BORROWED, { direction = LoanDirection.BORROWED })
                }
                OutlinedTextField(
                    value = person, onValueChange = { person = it.take(40) },
                    label = { Text(if (direction == LoanDirection.LENT) "Lent to" else "Borrowed from") },
                    singleLine = true
                )
                val suggestions = people.filter {
                    it.startsWith(person.trim(), ignoreCase = true) && !it.equals(person.trim(), ignoreCase = true)
                }.take(5)
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { s -> ChoiceChip(s, false, { person = s }) }
                    }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) amount = v },
                    label = { Text("Amount") }, prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                DateRow("Date", date, onPick = { picking = "date" })
                DateRow("Due", due, onPick = { picking = "due" }, onClear = { due = null })
                Text(
                    "With a due date, KYE reminds you on that day and weekly after, until it's settled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(value = note, onValueChange = { note = it.take(80) }, label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val paise = Money.parseToPaise(amount)
                if (paise == null || paise <= 0) {
                    Toast.makeText(context, "Enter an amount", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                onSave(
                    (initial ?: Loan(person = "", direction = direction, amountPaise = 0, date = 0)).copy(
                        person = person, direction = direction, amountPaise = paise,
                        date = date.toEpochDay(), dueDate = due?.toEpochDay(), note = note.trim()
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    when (picking) {
        "date" -> DatePickerSheet(date, onDismiss = { picking = null }) { date = it }
        "due" -> DatePickerSheet(due ?: date.plusMonths(1), onDismiss = { picking = null }) { due = it }
    }
}

@Composable
private fun PaymentDialog(remaining: Long, lent: Boolean, onDismiss: () -> Unit, onSave: (Long, LocalDate, String) -> Unit) {
    var amount by remember { mutableStateOf(Money.plain(remaining)) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lent) "Money received back" else "Money paid back") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) amount = v },
                    label = { Text("Amount") }, prefix = { Text("₹ ") }, singleLine = true,
                    supportingText = { Text("${Money.format(remaining, showPaise = true)} left. Change it for a part payment.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                DateRow("Date", date, onPick = { picking = true })
                OutlinedTextField(value = note, onValueChange = { note = it.take(80) }, label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val paise = Money.parseToPaise(amount)
                if (paise == null || paise <= 0) {
                    Toast.makeText(context, "Enter an amount", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                onSave(paise, date, note)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (picking) DatePickerSheet(date, onDismiss = { picking = false }) { date = it }
}
