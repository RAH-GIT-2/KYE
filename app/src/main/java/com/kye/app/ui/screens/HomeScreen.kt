@file:OptIn(ExperimentalLayoutApi::class)

package com.kye.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.domain.Events
import kotlinx.coroutines.delay
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
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
import com.kye.app.domain.GoldSchemes
import com.kye.app.domain.HomeState
import com.kye.app.domain.Money
import com.kye.app.domain.Tone
import com.kye.app.domain.Sky
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.size
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.AnimatedAmount
import com.kye.app.ui.components.CategoryAvatar
import com.kye.app.ui.components.EmptyState
import com.kye.app.ui.components.ProgressBar
import com.kye.app.ui.components.SectionCard
import com.kye.app.ui.components.TxnItem
import com.kye.app.ui.components.LoadingRows
import com.kye.app.ui.theme.LocalHeroBrush
import com.kye.app.ui.components.tabBottomPadding
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(
    vm: KyeViewModel,
    onOpenTxn: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onSettings: () -> Unit,
    onBudgets: () -> Unit,
    onRecurring: () -> Unit,
    onLoans: () -> Unit,
    onAsk: (Boolean) -> Unit,
    onEvent: (Long) -> Unit,
    onGoal: (Long) -> Unit,
    onScheme: (Long) -> Unit,
    onCard: (Long) -> Unit
) {
    val cardStatuses by vm.cardStatuses.collectAsStateWithLifecycle()
    // Card bills due within 5 days, or overdue.
    val cardsDue = cardStatuses.filter { s ->
        !s.card.isClosed && s.due > 0 && !s.dueDate.isAfter(LocalDate.now().plusDays(5))
    }
    val catMap by vm.categoryMap.collectAsStateWithLifecycle()
    val allSubs by vm.allSubcategories.collectAsStateWithLifecycle()
    val subMap = remember(allSubs) { allSubs.associateBy { it.id } }
    val schemeRows by vm.schemes.collectAsStateWithLifecycle()
    // Only schemes that need something soon: an instalment due within 5 days, missed, or ready to redeem.
    val schemesDue = schemeRows.filter { r ->
        val now = LocalDate.now()
        !r.scheme.isClosed && (GoldSchemes.isReadyToRedeem(r, now) ||
            GoldSchemes.nextDue(r)?.let { !it.isAfter(now.plusDays(5)) } == true)
    }
    val goalRows by vm.goals.collectAsStateWithLifecycle()
    val openGoals = goalRows.filter { !it.goal.isClosed }.take(3)
    val eventRows by vm.events.collectAsStateWithLifecycle()
    val runningEvents = eventRows.filter { Events.isActive(it.event, LocalDate.now()) }
    val s by vm.home.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loans by vm.loanSummary.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var justAdded by remember { mutableStateOf<Pair<Long, String>?>(null) }
    // The Undo offer fades after a few seconds.
    LaunchedEffect(justAdded) {
        if (justAdded != null) {
            delay(8_000)
            justAdded = null
        }
    }
    val today = LocalDate.now()

    val hour = LocalTime.now().hour
    val festival = remember(today) { Sky.festival(today) }
    Box(Modifier.fillMaxSize()) {
    SkyBackdrop(festival?.colors ?: Sky.time(hour).colors)
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = tabBottomPadding(136.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val greet = festival?.greeting ?: "Vanakkam 🙏 ${Sky.greeting(hour)} ${Sky.time(hour).emoji}"
                    Text(greet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    Text("Know Your Expense", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
            }
        }

        item(key = "hero") { HeroCard(s, today, onBudgets) }

        // First week of a month: last month stayed within the overall budget. Confetti once.
        val budget = s.budget
        if (s.loaded && budget != null && today.dayOfMonth <= 7 && s.lastMonthSpent in 1..budget) {
            item(key = "kept") {
                val last = today.minusMonths(1)
                LaunchedEffect(Unit) { vm.celebrateMonthOnce(last.toString().take(7)) }
                SectionCard(title = "🎉 Within budget in ${Dates.monthName(last)}") {
                    Text(
                        "${Money.format(budget - s.lastMonthSpent)} under your ${Money.format(budget)} budget. Well done!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Ask KYE: tap to type, mic to speak.
        item(key = "ask") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .clickable { onAsk(false) }
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Ask: how much on Swiggy this month?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = { onAsk(true) }) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Ask by voice", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (s.repeats.isNotEmpty()) {
            item(key = "repeats") {
                SectionCard(title = "Add again", subtitle = "Things you add often · one tap adds it for now") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        s.repeats.forEach { r ->
                            ChoiceChip(
                                "${r.label} ${Money.format(r.amountPaise, showPaise = true)}",
                                false,
                                {
                                    vm.addAgain(r.template) { id, msg ->
                                        justAdded = id to "${r.label} ${Money.format(r.amountPaise, showPaise = true)}"
                                        if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                leading = r.emoji
                            )
                        }
                    }
                    justAdded?.let { (id, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Added $label", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                vm.undoAdd(id)
                                justAdded = null
                            }) { Text("Undo") }
                        }
                    }
                }
            }
        }

        // Events on now (Deepavali, a trip…) with their own budget.
        items(runningEvents, key = { "event-${it.event.id}" }) { r ->
            SectionCard(
                title = "${r.event.emoji} ${r.event.name}",
                subtitle = listOfNotNull("On now", Events.dates(r.event)).joinToString(" · "),
                trailing = { TextButton(onClick = { onEvent(r.event.id) }) { Text("Open") } }
            ) {
                Box(Modifier.fillMaxWidth().clickable { onEvent(r.event.id) }) { EventProgress(r) }
            }
        }

        s.forecast?.let { f -> item(key = "forecast") { ForecastCard(f, today, onBudgets) } }

        if (cardsDue.isNotEmpty()) {
            item(key = "cards") {
                SectionCard(title = "Credit card bills") {
                    cardsDue.forEachIndexed { i, s ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        Column(Modifier.fillMaxWidth().clickable { onCard(s.card.id) }) {
                            Text("💳 ${s.card.name}", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(2.dp))
                            CardBillLine(s, today)
                        }
                    }
                }
            }
        }

        if (schemesDue.isNotEmpty()) {
            item(key = "schemes") {
                SectionCard(title = "Gold schemes") {
                    schemesDue.forEachIndexed { i, r ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        Column(Modifier.fillMaxWidth().clickable { onScheme(r.scheme.id) }) {
                            Text("🪙 ${r.scheme.name}", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(4.dp))
                            SchemeProgress(r, today)
                        }
                    }
                }
            }
        }

        if (openGoals.isNotEmpty()) {
            item(key = "goals") {
                SectionCard(title = "Savings goals") {
                    openGoals.forEachIndexed { i, r ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        Column(Modifier.fillMaxWidth().clickable { onGoal(r.goal.id) }) {
                            Text("${r.goal.emoji} ${r.goal.name}", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(4.dp))
                            GoalProgress(r, today)
                        }
                    }
                }
            }
        }

        if (loans.open > 0) {
            item(key = "loans") {
                SectionCard(
                    title = "Lent & borrowed",
                    subtitle = if (loans.overdue > 0) "${loans.overdue} overdue" else null,
                    trailing = { TextButton(onClick = onLoans) { Text("Open") } }
                ) {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onLoans)) {
                        Column(Modifier.weight(1f)) {
                            Text("To get back", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(loans.toGet), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("To give back", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(loans.toGive), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }

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
                                // Same as History and Recurring: subcategory (or category) on top, the name below.
                                val sub = u.rule.subcategoryId?.let { subMap[it] }?.takeIf { !it.isDefault }?.name
                                val title = sub ?: u.rule.categoryId?.let { catMap[it]?.name } ?: u.rule.name
                                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val overdue = u.due.isBefore(today)
                                val name = u.rule.name.trim().takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
                                val whenText = if (overdue) "Overdue · ${Dates.label(u.due, today)}" else Dates.label(u.due, today)
                                Text(
                                    listOfNotNull(name, whenText).joinToString(" · "),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
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

        if (!s.loaded) item(key = "loading") { LoadingRows(4) }

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
            .background(LocalHeroBrush.current)
            .clickable(onClick = onBudgets)
            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
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
            }
            val ringBudget = s.budget
            if (ringBudget != null && ringBudget > 0) {
                Spacer(Modifier.width(12.dp))
                BudgetRing(s.spent.toFloat() / ringBudget)
            }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                MiniStat("Today", s.today, Modifier.weight(1f))
                MiniStat("Saved", s.saved, Modifier.weight(1f))
                val budget = s.budget
                MiniStat(
                    if (budget != null && s.spent > budget) "Over budget" else "Budget left",
                    if (budget != null) Money.format(kotlin.math.abs(budget - s.spent)) else "Set →",
                    Modifier.weight(1f)
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

/**
 * Soft sky behind the top of Home: dawn orange, day blue, dusk purple, night navy, or a festival's
 * colours. It fades into the page, and cards scroll up over it.
 */
@Composable
private fun SkyBackdrop(colors: List<Long>) {
    val bg = MaterialTheme.colorScheme.background
    val strength = if (bg.luminance() < 0.5f) 0.42f else 0.5f
    val stops = colors.map { Color(it).copy(alpha = strength).compositeOver(bg) } + bg
    Box(
        Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(Brush.verticalGradient(stops))
    ) {
        // A faint glow, like the sun or moon, in the top corner.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 28.dp)
                .size(150.dp)
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = if (bg.luminance() < 0.5f) 0.10f else 0.35f), Color.Transparent)),
                    androidx.compose.foundation.shape.CircleShape
                )
        )
    }
}

/** A hero stat whose amount rolls when it changes. */
@Composable
private fun MiniStat(label: String, paise: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        AnimatedAmount(paise, MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

/**
 * How much of the month's budget is used, as a ring that fills: green, then amber from 60%,
 * coral once it's all gone. It gives one gentle pulse when spending crosses 80%.
 */
@Composable
private fun BudgetRing(fraction: Float) {
    val f = fraction.coerceAtLeast(0f)
    val sweep by animateFloatAsState(f.coerceAtMost(1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    val green = Color(0xFF55EFC4)
    val amber = Color(0xFFFDCB6E)
    val coral = Color(0xFFFF8A80)
    val color by animateColorAsState(
        when {
            f >= 1f -> coral
            f >= 0.6f -> lerp(amber, coral, ((f - 0.6f) / 0.4f).coerceIn(0f, 1f) * 0.6f)
            else -> lerp(green, amber, (f / 0.6f).coerceIn(0f, 1f) * 0.35f)
        },
        tween(600), label = "ringColor"
    )
    val pulse = remember { Animatable(1f) }
    var before by remember { mutableStateOf(f) }
    LaunchedEffect(f) {
        if (before < 0.8f && f >= 0.8f) {
            pulse.animateTo(1.14f, tween(180))
            pulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        before = f
    }
    Box(
        Modifier
            .size(86.dp)
            .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(color, -90f, 360f * sweep, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(f * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("of budget", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
}
