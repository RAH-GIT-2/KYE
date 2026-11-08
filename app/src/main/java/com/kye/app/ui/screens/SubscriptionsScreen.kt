@file:OptIn(ExperimentalMaterial3Api::class)

package com.kye.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.data.RecurringMode
import com.kye.app.domain.Dates
import com.kye.app.domain.Money
import com.kye.app.domain.Recurrence
import com.kye.app.domain.Subscriptions
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.CategoryAvatar
import com.kye.app.ui.components.EmptyState
import com.kye.app.domain.UNCATEGORISED_COLOR
import java.time.LocalDate

/** Netflix, Prime, Jio…: recurring rules under OTT / subscription / recharge categories, with the yearly total. */
@Composable
fun SubscriptionsScreen(vm: KyeViewModel, onOpenRule: (Long) -> Unit, onNew: () -> Unit, onBack: () -> Unit) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val cats by vm.categoryMap.collectAsStateWithLifecycle()
    val subs by vm.allSubcategories.collectAsStateWithLifecycle()
    val subMap = remember(subs) { subs.associateBy { it.id } }
    val list = rules.filter { Subscriptions.isSubscription(it, cats) }
        .sortedWith(compareBy({ !it.isActive }, { it.nextDueDate }))
    val active = list.filter { it.isActive }
    val yearly = active.sumOf { Subscriptions.yearlyCost(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscriptions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Rounded.Add, contentDescription = null) }, text = { Text("Add") })
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "total") {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("${Money.format(yearly)} a year", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "About ${Money.format(yearly / 12)} a month · ${active.size} active",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item(key = "hint") {
                Text(
                    "Recurring entries under OTT & Subscriptions or Recharges. Yearly ones get a reminder ${Subscriptions.NOTICE_DAYS} days " +
                        "before they renew, so you can cancel what you don't use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (list.isEmpty()) {
                item(key = "empty") {
                    EmptyState("🎬", "No subscriptions yet", "Add Netflix, Prime or your Jio recharge as a recurring entry under OTT & Subscriptions or Recharges")
                }
            }
            items(list, key = { it.id }) { rule ->
                val cat = rule.categoryId?.let { cats[it] }
                val sub = rule.subcategoryId?.let { subMap[it] }?.takeIf { !it.isDefault }?.name
                val title = sub ?: cat?.name ?: rule.name
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenRule(rule.id) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryAvatar(cat?.emoji ?: "🔁", cat?.color ?: UNCATEGORISED_COLOR, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    rule.name.trim().takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) },
                                    Recurrence.describe(rule.unit, rule.interval),
                                    if (!rule.isActive) "Paused"
                                    else "Next ${Dates.label(LocalDate.ofEpochDay(rule.nextDueDate))}",
                                    if (rule.mode == RecurringMode.REMIND_ONLY) "Remind me" else null
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (rule.amountPaise > 0) Money.format(rule.amountPaise) else "Varies", style = MaterialTheme.typography.titleSmall)
                            if (rule.amountPaise > 0) {
                                Text(
                                    "${Money.format(Subscriptions.yearlyCost(rule))}/yr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
