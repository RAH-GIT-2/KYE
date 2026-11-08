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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.domain.Dates
import com.kye.app.domain.Forecast
import com.kye.app.domain.HomeState
import com.kye.app.domain.Money
import com.kye.app.domain.Tone
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.AnimatedAmount
import com.kye.app.ui.components.CategoryAvatar
import com.kye.app.ui.components.EmptyState
import com.kye.app.ui.components.ProgressBar
import com.kye.app.ui.components.SectionCard
import com.kye.app.ui.components.TxnItem
import com.kye.app.ui.theme.HeroBrush
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(
    vm: KyeViewModel,
    onOpenTxn: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onSettings: () -> Unit,
    onBudgets: () -> Unit,
    onRecurring: () -> Unit
) {
    val s by vm.home.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 136.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val hour = LocalTime.now().hour
                    val greet = when (hour) {
                        in 5..11 -> "Good morning"
                        in 12..16 -> "Good afternoon"
                        else -> "Good evening"
                    }
                    Text("Vanakkam 🙏 $greet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Know Your Expense", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
            }
        }

        item(key = "hero") { HeroCard(s, today, onBudgets) }

        s.forecast?.let { f -> item(key = "forecast") { ForecastCard(f, today, onBudgets) } }

        if (s.highlights.isNotEmpty()) {
            item(key = "highlights") {
                SectionCard(title = "What stands out") {
                    s.highlights.forEach { h ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                            Text(h.emoji, modifier = Modifier.width(30.dp))
                            Text(
                                h.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (h.tone) {
                                    Tone.WARN -> MaterialTheme.colorScheme.tertiary
                                    Tone.GOOD -> MaterialTheme.colorScheme.secondary
                                    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }

        if (s.upcoming.isNotEmpty()) {
            item(key = "upcoming") {
                SectionCard(
                    title = "Coming up",
                    subtitle = "Recurring bills in the next 30 days",
                    trailing = { TextButton(onClick = onRecurring) { Text("All") } }
                ) {
                    s.upcoming.forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryAvatar(u.emoji, u.color, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.rule.name, style = MaterialTheme.typography.titleSmall)
                                val overdue = u.due.isBefore(today)
                                Text(
                                    if (overdue) "Overdue · ${Dates.label(u.due, today)}" else Dates.label(u.due, today),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (overdue) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                if (u.rule.amountPaise > 0) Money.format(u.rule.amountPaise) else "—",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
        }

        item(key = "recent-title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (s.recent.isNotEmpty()) TextButton(onClick = onSeeAll) { Text("See all") }
            }
        }

        if (s.loaded && s.recent.isEmpty()) {
            item(key = "empty") {
                if (settings?.voiceEnabled != false) {
                    EmptyState(
                        "🎙️",
                        "Say your first expense",
                        "Tap the mic and say \"Swiggy 450\" or \"auto 120 nethu\""
                    )
                } else {
                    EmptyState(
                        "🧾",
                        "Add your first expense",
                        "Tap + and type \"swiggy 450\" in Quick type"
                    )
                }
            }
        }

        items(s.recent, key = { it.txn.id }) { row ->
            TxnItem(row, Modifier.animateItem()) { onOpenTxn(row.txn.id) }
        }
    }
}

/** Rounded to the nearest ₹100: a forecast shouldn't look more exact than it is. */
private fun approx(paise: Long): String = "~" + Money.format((paise + 5_000) / 10_000 * 10_000)

@Composable
private fun ForecastCard(f: Forecast, today: LocalDate, onBudgets: () -> Unit) {
    SectionCard(
        title = "Month-end forecast",
        subtitle = "At your current pace, plus bills still due",
        trailing = { if (f.budget == null) TextButton(onClick = onBudgets) { Text("Set budget") } }
    ) {
        Text(
            "${approx(f.total)} by ${Dates.short(f.monthEnd)}",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(4.dp))
        val parts = listOfNotNull(
            "${Money.format(f.spentSoFar)} spent",
            f.billsToCome.takeIf { it > 0 }?.let { "${Money.format(it)} bills to come" },
            f.projectedEveryday.takeIf { it > 0 }?.let { "${approx(it)} everyday spending" }
        )
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val over = f.overBudgetBy
        val budget = f.budget
        if (over != null && budget != null) {
            Spacer(Modifier.height(12.dp))
            val fraction = (f.total.toFloat() / budget).coerceAtLeast(0f)
            ProgressBar(
                fraction,
                if (over > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(8.dp))
            if (over > 0) {
                Text(
                    "${approx(over)} over your ${Money.format(budget)} budget",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    "Within your ${Money.format(budget)} budget",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            f.safePerDay(today)?.let { perDay ->
                Text(
                    if (perDay > 0) "${Money.format(perDay)} a day left to spend, after bills"
                    else "Nothing left to spend this month after bills",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeroCard(s: HomeState, today: LocalDate, onBudgets: () -> Unit) {
    val onHero = Color.White
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(HeroBrush)
            .clickable(onClick = onBudgets)
            .padding(22.dp)
    ) {
        Column {
            Text("Spent in ${Dates.monthName(today)}", style = MaterialTheme.typography.bodyMedium, color = onHero.copy(alpha = 0.85f))
            Spacer(Modifier.height(4.dp))
            AnimatedAmount(s.spent, MaterialTheme.typography.displaySmall, color = onHero)
            if (s.income > 0) {
                val left = s.income - s.spent
                Text(
                    "Income ${Money.format(s.income)} · " +
                        if (left >= 0) "${Money.format(left)} left" else "${Money.format(-left)} over",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHero.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                MiniStat("Today", Money.format(s.today), Modifier.weight(1f))
                MiniStat("Saved", Money.format(s.saved), Modifier.weight(1f))
                val budget = s.budget
                MiniStat(
                    if (budget != null && s.spent > budget) "Over budget" else "Budget left",
                    if (budget != null) Money.format(kotlin.math.abs(budget - s.spent)) else "Set →",
                    Modifier.weight(1f)
                )
            }
            val budget = s.budget
            if (budget != null && budget > 0) {
                Spacer(Modifier.height(16.dp))
                val fraction = s.spent.toFloat() / budget
                ProgressBar(
                    fraction = fraction,
                    color = if (fraction >= 1f) Color(0xFFFFB3B0) else Color.White,
                    track = Color.White.copy(alpha = 0.25f),
                    height = 6.dp
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
    }
}
