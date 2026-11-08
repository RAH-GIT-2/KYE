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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.kye.app.voice.VoiceParser
import com.kye.app.domain.Events
import com.kye.app.domain.Warranties
import com.kye.app.data.Receipts
import com.kye.app.ui.components.EventChips
import com.kye.app.ui.components.GoalChips
import com.kye.app.ui.components.SchemeChips
import com.kye.app.ui.components.CardChips
import com.kye.app.ui.components.ReceiptItem
import com.kye.app.ui.components.ReceiptStrip
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
    var splitWays by remember { mutableIntStateOf(1) }
    var eventId by remember { mutableStateOf<Long?>(null) }
    var goalId by remember { mutableStateOf<Long?>(null) }
    val goals by vm.goals.collectAsStateWithLifecycle()
    var schemeId by remember { mutableStateOf<Long?>(null) }
    var cardId by remember { mutableStateOf<Long?>(null) }
    val cards by vm.cards.collectAsStateWithLifecycle()
    // With one card, a new credit-card expense goes on it.
    LaunchedEffect(payment, cards) {
        if (txnId <= 0 && payment == PaymentMode.CREDIT_CARD && cardId == null) cardId = cards.filter { !it.isClosed }.singleOrNull()?.id
    }
    val schemes by vm.schemes.collectAsStateWithLifecycle()
    // Bill photos added in this visit (saved with the entry) and the warranty end date.
    var newPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var warranty by remember { mutableStateOf<LocalDate?>(null) }
    var pickingWarranty by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
    var deletingPhoto by remember { mutableStateOf<ReceiptItem?>(null) }
    val savedPhotos by remember(txnId) { if (txnId > 0) vm.attachments(txnId) else kotlinx.coroutines.flow.flowOf(emptyList()) }
        .collectAsState(initial = emptyList())
    val events by vm.events.collectAsStateWithLifecycle()
    var repeat by remember { mutableStateOf(false) }
    var repeatUnit by remember { mutableStateOf(RepeatUnit.MONTH) }
    var repeatEvery by remember { mutableIntStateOf(1) }
    var repeatBackfill by remember { mutableStateOf<Pair<Txn, List<com.kye.app.domain.BackfillDate>>?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun addPhoto(uri: android.net.Uri) {
        photoBusy = true
        scope.launch {
            try {
                newPhotos = newPhotos + vm.importPhoto(uri)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Couldn't add that photo", Toast.LENGTH_SHORT).show()
            }
            photoBusy = false
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) {
            photoBusy = true
            scope.launch {
                runCatching { newPhotos = newPhotos + vm.importPhoto(android.net.Uri.fromFile(f)) }
                f.delete()
                photoBusy = false
            }
        } else f?.delete()
        cameraFile = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addPhoto(uri)
    }
    // Photos picked for an entry that's then abandoned are removed.
    DisposableEffect(Unit) {
        onDispose { if (!saved) newPhotos.forEach { vm.discardPhoto(it) } }
    }
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
                eventId = t.eventId
                goalId = t.goalId
                schemeId = t.schemeId
                cardId = t.cardId
                warranty = vm.warrantyOf(t.id)?.untilDate?.let { LocalDate.ofEpochDay(it) }
            }
        } else {
            // A running event is pre-selected; one tap on "None" removes it.
            eventId = vm.preselectedEvent()?.id
            runCatching { focus.requestFocus() }
        }
    }

    val kind = kindOf(type)
    // An old entry may use a category that's hidden now; keep it selectable while editing.
    val visibleCats = (categories + allCategories.filter { it.id == catId && it.isArchived })
        .filter { it.kind == kind }

    fun commit(txn: Txn, backfill: List<LocalDate>) {
        val old = existing
        saved = true
        vm.saveTxnFull(txn, newPhotos, if (type == TxnType.EXPENSE) warranty else null) { msg ->
            if (repeat && old == null) {
                val name = note.trim().ifBlank {
                    allSubs.firstOrNull { it.id == subId }?.takeIf { !it.isDefault }?.name
                        ?: categories.firstOrNull { it.id == catId }?.name ?: "Recurring"
                }
                vm.ruleFromTxn(txn, name, repeatUnit, repeatEvery, backfill)
            }
            if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            onBack()
        }
    }


    fun save() {
        val total = Money.parseToPaise(amount)
        if (total == null || total <= 0) {
            Toast.makeText(context, "Enter an amount", Toast.LENGTH_SHORT).show()
            return
        }
        val split = existing == null && splitWays > 1
        val paise = if (split) VoiceParser.splitShare(total, splitWays) else total
        if (split) {
            val tag = "${Money.format(total, showPaise = true)} split $splitWays ways"
            if (!note.contains(tag)) note = listOf(note.trim(), tag).filter { it.isNotBlank() }.joinToString(" · ")
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
            note = note.trim(),
            eventId = eventId,
            goalId = if (type == TxnType.SAVING) goalId else null,
            // A gold scheme instalment stays one only while it's a Saving entry.
            schemeId = if (type == TxnType.SAVING) schemeId else null,
            // Card: for credit-card expenses; a bill payment (transfer) keeps its card.
            cardId = when {
                type == TxnType.EXPENSE && payment == PaymentMode.CREDIT_CARD -> cardId
                type == TxnType.TRANSFER -> old?.cardId
                else -> null
            }
        )
        // "Repeat" on a past date: offer to fill in the due dates between then and today.
        if (repeat && old == null) {
            val options = vm.backfillForRepeat(txn, repeatUnit, repeatEvery)
            if (options.isNotEmpty()) {
                repeatBackfill = txn to options
                return
            }
        }
        commit(txn, emptyList())
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

            // Split a shared bill: only your share is saved.
            if (existing == null) {
                Column {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Label("Split")
                        Spacer(Modifier.width(4.dp))
                        listOf(1, 2, 3, 4, 5, 6).forEach { n ->
                            ChoiceChip(if (n == 1) "No" else "$n", splitWays == n, { splitWays = n })
                        }
                    }
                    val total = Money.parseToPaise(amount)
                    if (splitWays > 1 && total != null && total > 0) {
                        Text(
                            "Your share: ${Money.format(VoiceParser.splitShare(total, splitWays), showPaise = true)} " +
                                "of ${Money.format(total, showPaise = true)} · only your share is saved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

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
            if (type == TxnType.EXPENSE && payment == PaymentMode.CREDIT_CARD) {
                CardChips(cards.filter { !it.isClosed || it.id == cardId }, cardId) { cardId = it }
            }

            EventChips(
                events = Events.open(events.map { it.event }),
                selectedId = eventId,
                onSelect = { eventId = it },
                extra = events.firstOrNull { it.event.id == eventId }?.event
            )

            // Savings goal (Saving entries only).
            if (type == TxnType.SAVING) {
                GoalChips(goals.filter { !it.goal.isClosed || it.goal.id == goalId }.map { it.goal }, goalId) { goalId = it }
                SchemeChips(schemes.filter { !it.scheme.isClosed || it.scheme.id == schemeId }.map { it.scheme }, schemeId) { schemeId = it }
            }

            // Bill photos.
            Column {
                Label("Bill photos")
                Spacer(Modifier.height(6.dp))
                ReceiptStrip(
                    items = savedPhotos.map { ReceiptItem(it.id, it.fileName) } + newPhotos.map { ReceiptItem(null, it) },
                    busy = photoBusy,
                    onCamera = {
                        val (file, uri) = Receipts.cameraTarget(context)
                        cameraFile = file
                        camera.launch(uri)
                    },
                    onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onDelete = { deletingPhoto = it }
                )
            }

            // Warranty (purchases only): 1 / 2 / 3 years from the purchase date, or a date.
            if (type == TxnType.EXPENSE) {
                Column {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Label("Warranty")
                        ChoiceChip("None", warranty == null, { warranty = null })
                        listOf(1, 2, 3).forEach { y ->
                            val until = Warranties.until(date, y)
                            ChoiceChip("$y yr", warranty == until, { warranty = until })
                        }
                        val custom = warranty?.takeIf { w -> (1..3).none { Warranties.until(date, it) == w } }
                        ChoiceChip(custom?.let { Dates.label(it) } ?: "Date…", custom != null, { pickingWarranty = true })
                    }
                    warranty?.let {
                        Text(
                            "Covered till ${Dates.label(it)}. KYE reminds you 30 days before, and on the day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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

    if (pickingWarranty) {
        DatePickerSheet(warranty ?: Warranties.until(date, 1), onDismiss = { pickingWarranty = false }) { warranty = it }
    }

    repeatBackfill?.let { (txn, options) ->
        com.kye.app.ui.components.BackfillDialog(
            options = options,
            amountPaise = txn.amountPaise,
            onAdd = { dates ->
                repeatBackfill = null
                commit(txn, dates)
            },
            onSkip = {
                repeatBackfill = null
                commit(txn, emptyList())
            },
            onDismiss = { repeatBackfill = null }
        )
    }

    deletingPhoto?.let { p ->
        AlertDialog(
            onDismissRequest = { deletingPhoto = null },
            title = { Text("Delete this photo?") },
            text = { Text("The bill photo is removed from KYE.") },
            confirmButton = {
                TextButton(onClick = {
                    deletingPhoto = null
                    if (p.id != null) vm.deleteAttachment(p.id) else {
                        newPhotos = newPhotos - p.fileName
                        vm.discardPhoto(p.fileName)
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingPhoto = null }) { Text("Cancel") } }
        )
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
