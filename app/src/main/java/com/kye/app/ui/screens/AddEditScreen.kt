@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kye.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.data.CategoryKind
import com.kye.app.data.PaymentMode
import com.kye.app.data.RepeatUnit
import com.kye.app.data.Source
import com.kye.app.data.Txn
import com.kye.app.data.TxnType
import com.kye.app.domain.Dates
import com.kye.app.domain.Money
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.CategoryPicker
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.ui.components.DatePickerSheet
import com.kye.app.ui.components.PaymentChips
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun AddEditScreen(vm: KyeViewModel, txnId: Long, onManageCategories: () -> Unit = {}, onBack: () -> Unit) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val allCategories by vm.allCategories.collectAsStateWithLifecycle()
    val allSubs by vm.allSubcategories.collectAsStateWithLifecycle()
    val subcategories by vm.subcategories.collectAsStateWithLifecycle()

    var existing by remember { mutableStateOf<Txn?>(null) }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxnType.EXPENSE) }
    var catId by remember { mutableStateOf<Long?>(null) }
    var subId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var payment by remember { mutableStateOf(PaymentMode.UPI) }
    var note by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf(false) }
    var repeatUnit by remember { mutableStateOf(RepeatUnit.MONTH) }
    var repeatEvery by remember { mutableIntStateOf(1) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var quick by remember { mutableStateOf("") }

    /** Quick type: "swiggy 450 biryani" fills the form, using the same parser as voice entry. */
    fun fillFromQuick() {
        val text = quick.trim()
        if (text.isEmpty()) return
        scope.launch {
            val drafts = vm.parseVoice(text)
            val d = drafts.firstOrNull()
            if (d == null || (d.amountText.isBlank() && d.categoryId == null)) {
                Toast.makeText(context, "Couldn't read that. Try \"swiggy 450\".", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (d.amountText.isNotBlank()) amount = d.amountText
            if (d.categoryId != null) {
                catId = d.categoryId
                subId = d.subcategoryId
                type = vm.typeFor(d.categoryId, d.subcategoryId)
            }
            date = d.date
            payment = d.paymentMode
            if (d.note.isNotBlank()) note = d.note
            quick = ""
            if (drafts.size > 1) {
                Toast.makeText(context, "Filled the first item only. Add the others separately.", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(txnId) {
        if (txnId > 0) {
            vm.loadTxn(txnId)?.let { t ->
                existing = t
                amount = Money.plain(t.amountPaise)
                type = t.type
                catId = t.categoryId
                subId = t.subcategoryId
                date = Dates.toLocalDate(t.dateTime)
                payment = t.paymentMode
                note = t.note
            }
        } else {
            runCatching { focus.requestFocus() }
        }
    }

    val kind = kindOf(type)
    // An old entry may use a category that's hidden now; keep it selectable while editing.
    val visibleCats = (categories + allCategories.filter { it.id == catId && it.isArchived })
        .filter { it.kind == kind }

    fun save() {
        val paise = Money.parseToPaise(amount)
        if (paise == null || paise <= 0) {
            Toast.makeText(context, "Enter an amount", Toast.LENGTH_SHORT).show()
            return
        }
        val old = existing
        val time = when {
            old != null && Dates.toLocalDate(old.dateTime) == date -> Dates.toLocalTime(old.dateTime)
            date == LocalDate.now() -> LocalTime.now()
            else -> LocalTime.of(12, 0)
        }
        val txn = (old ?: Txn(
            amountPaise = paise, type = type, categoryId = catId, subcategoryId = subId,
            dateTime = 0, paymentMode = payment, source = Source.MANUAL
        )).copy(
            amountPaise = paise,
            type = type,
            categoryId = catId,
            subcategoryId = subId,
            dateTime = Dates.atMillis(date, time),
            paymentMode = payment,
            note = note.trim()
        )
        vm.saveTxn(txn) { msg ->
            if (repeat && old == null) {
                val name = note.trim().ifBlank {
                    allSubs.firstOrNull { it.id == subId }?.takeIf { !it.isDefault }?.name
                        ?: categories.firstOrNull { it.id == catId }?.name ?: "Recurring"
                }
                vm.ruleFromTxn(txn, name, repeatUnit, repeatEvery)
            }
            if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (txnId > 0) "Edit entry" else "Add entry") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Button(
                onClick = { save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (txnId <= 0) {
                OutlinedTextField(
                    value = quick,
                    onValueChange = { quick = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Quick type") },
                    placeholder = { Text("swiggy 450 biryani") },
                    supportingText = { Text("Type it in one line and the form below fills itself") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { fillFromQuick() }),
                    trailingIcon = {
                        if (quick.isNotBlank()) {
                            IconButton(onClick = { fillFromQuick() }) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = "Fill the form")
                            }
                        }
                    }
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(
                    TxnType.EXPENSE to "Expense", TxnType.INCOME to "Income",
                    TxnType.SAVING to "Saving", TxnType.TRANSFER to "Transfer"
                )
                options.forEachIndexed { i, (t, label) ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            val kindChanged = kindOf(t) != kindOf(type)
                            type = t
                            if (kindChanged) {
                                catId = null
                                subId = null
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        icon = {}
                    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }

            TextField(
                value = amount,
                onValueChange = { v -> if (v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' || it == ',' }) amount = v },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                prefix = { Text("₹ ", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                placeholder = { Text("0", style = MaterialTheme.typography.displaySmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Label("Category") }
                TextButton(onClick = onManageCategories) { Text("Add / edit") }
            }
            CategoryPicker(
                categories = visibleCats,
                // A hidden subcategory an old entry uses stays selectable while editing it.
                subcategories = subcategories + allSubs.filter { it.id == subId && it.isArchived },
                catId = catId,
                subId = subId,
                onCategory = { c ->
                    if (catId != c.id) {
                        catId = c.id
                        subId = null
                    }
                },
                onSubcategory = { s ->
                    subId = s.id
                    type = vm.typeFor(catId, s.id, if (type == TxnType.TRANSFER && !s.isTransfer) TxnType.EXPENSE else type)
                }
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showDate = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(Dates.label(date), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }

            Label(if (type == TxnType.INCOME) "Received via" else "Paid with")
            PaymentChips(payment) { payment = it }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            if (existing == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Make this recurring", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Adds it automatically next time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = repeat, onCheckedChange = { repeat = it })
                }
                AnimatedVisibility(repeat) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Every", modifier = Modifier.width(56.dp))
                            TextButton(onClick = { if (repeatEvery > 1) repeatEvery-- }) { Text("–", fontSize = 20.sp) }
                            Text("$repeatEvery", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { if (repeatEvery < 365) repeatEvery++ }) { Text("+", fontSize = 20.sp) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RepeatUnit.entries.forEach { u ->
                                ChoiceChip(if (repeatEvery == 1) u.singular else u.plural, u == repeatUnit, { repeatUnit = u })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        DatePickerSheet(date, onDismiss = { showDate = false }) { date = it }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    existing?.let { vm.deleteTxn(it) { onBack() } }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

private fun kindOf(type: TxnType): CategoryKind = when (type) {
    TxnType.SAVING -> CategoryKind.SAVING
    TxnType.INCOME -> CategoryKind.INCOME
    TxnType.EXPENSE, TxnType.TRANSFER -> CategoryKind.EXPENSE
}

@Composable
internal fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
