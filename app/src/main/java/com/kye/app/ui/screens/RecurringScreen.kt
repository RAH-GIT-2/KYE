package com.kye.app.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.data.RecurringMode
import com.kye.app.data.RecurringRule
import com.kye.app.domain.Dates
import com.kye.app.domain.Money
import com.kye.app.domain.Recurrence
import com.kye.app.domain.UNCATEGORISED_COLOR
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.CategoryAvatar
import com.kye.app.ui.components.EmptyState
import com.kye.app.ui.theme.HeroBrush
import java.time.LocalDate

@Composable
fun RecurringScreen(vm: KyeViewModel, onOpenRule: (Long) -> Unit) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val cats by vm.categoryMap.collectAsStateWithLifecycle()
    val subs by vm.allSubcategories.collectAsStateWithLifecycle()
    val subMap = remember(subs) { subs.associateBy { it.id } }
    var paying by remember { mutableStateOf<RecurringRule?>(null) }
    val today = LocalDate.now()
    val monthly = rules.filter { it.isActive }.sumOf { Recurrence.monthlyEquivalent(it) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            // Adding a bill is the "+" at the bottom right, like on History.
            Text("Recurring", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp))
        }
        item(key = "summary") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(HeroBrush)
                    .padding(20.dp)
            ) {
                Column {
                    Text("Fixed costs per month", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                    Text(Money.format(monthly), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${rules.count { it.isActive }} active rules",
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        if (rules.isEmpty()) {
            item(key = "empty") {
                EmptyState("🔁", "No recurring expenses yet", "Add rent, maid salary, EB bill, OTT and recharges here")
            }
        }
        items(rules, key = { it.id }) { rule ->
            val cat = rule.categoryId?.let { cats[it] }
            val due = LocalDate.ofEpochDay(rule.nextDueDate)
            val needsAction = rule.isActive && rule.mode == RecurringMode.REMIND_ONLY &&
                !today.isBefore(due.minusDays(rule.remindDaysBefore.toLong()))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .alpha(if (rule.isActive) 1f else 0.55f),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                Column(Modifier.clickable { onOpenRule(rule.id) }.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(cat?.emoji ?: "🔁", cat?.color ?: UNCATEGORISED_COLOR, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            // Same as History: subcategory (or category) on top, the name/note below.
                            val sub = rule.subcategoryId?.let { subMap[it] }?.takeIf { !it.isDefault }?.name
                            val title = sub ?: cat?.name ?: rule.name
                            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val modeText = if (rule.mode == RecurringMode.AUTO_ADD) "Auto-add" else "Remind me"
                            Text(
                                listOfNotNull(
                                    rule.name.trim().takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) },
                                    Recurrence.describe(rule.unit, rule.interval),
                                    modeText,
                                    // EMIs and other rules with an end: "17 left", or "Ended".
                                    Recurrence.remaining(rule)?.let { if (it == 0) "Ended" else "$it left" }
                                ).joinToString(" · "),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (rule.amountPaise > 0) Money.format(rule.amountPaise) else "Varies", style = MaterialTheme.typography.titleSmall)
                            val overdue = due.isBefore(today)
                            Text(
                                if (!rule.isActive) "Paused" else (if (overdue) "Overdue · " else "Next · ") + Dates.label(due, today),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (overdue && rule.isActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (needsAction) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { vm.skip(rule) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                Text("Skip")
                            }
                            Button(onClick = { paying = rule }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                                Text("Mark paid")
                            }
                        }
                    }
                }
            }
        }
    }

    paying?.let { rule ->
        var amount by remember(rule.id) { mutableStateOf(if (rule.amountPaise > 0) Money.plain(rule.amountPaise) else "") }
        AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text("${rule.name} paid") },
            text = {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount paid") },
                    prefix = { Text("₹ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = Money.parseToPaise(amount)
                    if (p != null && p > 0) {
                        vm.markPaid(rule, p)
                        paying = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { paying = null }) { Text("Cancel") } }
        )
    }
}
