@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kye.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.data.PaymentMode
import com.kye.app.data.RecurringMode
import com.kye.app.data.RecurringRule
import com.kye.app.data.RepeatUnit
import com.kye.app.domain.Dates
import com.kye.app.domain.Money
import com.kye.app.domain.Recurrence
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.CategoryPicker
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.ui.components.DatePickerSheet
import com.kye.app.ui.components.PaymentChips
import java.time.LocalDate

@Composable
fun RuleEditScreen(vm: KyeViewModel, ruleId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val subcategories by vm.subcategories.collectAsStateWithLifecycle()

    var base by remember { mutableStateOf<RecurringRule?>(null) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf<Long?>(null) }
    var subId by remember { mutableStateOf<Long?>(null) }
    var payment by remember { mutableStateOf(PaymentMode.UPI) }
    var unit by remember { mutableStateOf(RepeatUnit.MONTH) }
    var every by remember { mutableIntStateOf(1) }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var mode by remember { mutableStateOf(RecurringMode.AUTO_ADD) }
    var remindBefore by remember { mutableIntStateOf(1) }
    var active by remember { mutableStateOf(true) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(ruleId) {
        if (ruleId > 0) {
            vm.loadRule(ruleId)?.let { r ->
                base = r
                name = r.name
                amount = if (r.amountPaise > 0) Money.plain(r.amountPaise) else ""
                catId = r.categoryId
                subId = r.subcategoryId
                payment = r.paymentMode
                unit = r.unit
                every = r.interval
                start = LocalDate.ofEpochDay(r.anchorDate)
                mode = r.mode
                remindBefore = r.remindDaysBefore
                active = r.isActive
            }
        }
    }

    fun save() {
        val paise = if (amount.isBlank()) 0L else Money.parseToPaise(amount)
        when {
            name.isBlank() -> Toast.makeText(context, "Give it a name, like \"House rent\"", Toast.LENGTH_SHORT).show()
            paise == null -> Toast.makeText(context, "Check the amount", Toast.LENGTH_SHORT).show()
            mode == RecurringMode.AUTO_ADD && paise <= 0 ->
                Toast.makeText(context, "Auto-add needs a fixed amount. Use \"Remind me\" for bills that change.", Toast.LENGTH_LONG).show()
            else -> {
                val rule = vm.newRule(
                    base, name.trim(), paise, catId, subId, payment, unit, every, start, mode,
                    if (mode == RecurringMode.REMIND_ONLY) remindBefore else 0, active
                )
                vm.saveRule(rule) { onBack() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId > 0) "Edit recurring" else "New recurring") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (base != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp).height(56.dp),
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") }, placeholder = { Text("House rent, Maid, EB bill…") },
                singleLine = true, shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = amount, onValueChange = { amount = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (mode == RecurringMode.REMIND_ONLY) "Amount (leave blank if it changes)" else "Amount") },
                prefix = { Text("₹ ") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Label("How often")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Every", modifier = Modifier.width(56.dp))
                TextButton(onClick = { if (every > 1) every-- }) { Text("–", fontSize = 20.sp) }
                Text("$every", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { if (every < 365) every++ }) { Text("+", fontSize = 20.sp) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatUnit.entries.forEach { u -> ChoiceChip(if (every == 1) u.singular else u.plural, u == unit, { unit = u }) }
            }
            Text(
                "${Recurrence.describe(unit, every)}, starting ${Dates.label(start)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { showDate = true }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("First due date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Dates.label(start), style = MaterialTheme.typography.titleMedium)
                }
                Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }

            Label("When it's due")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Add automatically", mode == RecurringMode.AUTO_ADD, { mode = RecurringMode.AUTO_ADD })
                ChoiceChip("Remind me", mode == RecurringMode.REMIND_ONLY, { mode = RecurringMode.REMIND_ONLY })
            }
            AnimatedVisibility(mode == RecurringMode.REMIND_ONLY) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind", modifier = Modifier.width(64.dp))
                    TextButton(onClick = { if (remindBefore > 0) remindBefore-- }) { Text("–", fontSize = 20.sp) }
                    Text(if (remindBefore == 0) "on the day" else "$remindBefore day${if (remindBefore > 1) "s" else ""} before", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { if (remindBefore < 30) remindBefore++ }) { Text("+", fontSize = 20.sp) }
                }
            }

            Label("Category")
            CategoryPicker(
                categories = categories,
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

            Label("Paid with")
            PaymentChips(payment) { payment = it }

            if (base != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) DatePickerSheet(start, onDismiss = { showDate = false }) { start = it }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this rule?") },
            text = { Text("Entries it already added stay in your history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    base?.let { vm.deleteRule(it) { onBack() } }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
