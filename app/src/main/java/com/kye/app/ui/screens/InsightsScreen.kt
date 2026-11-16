package com.kye.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.domain.AnalyticsState
import com.kye.app.domain.Dates
import com.kye.app.domain.Money
import com.kye.app.domain.Period
import com.kye.app.domain.Slice
import com.kye.app.domain.TrendBar
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.CategoryAvatar
import com.kye.app.ui.components.tabBottomPadding
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.ui.components.EmptyState
import com.kye.app.ui.components.ProgressBar
import com.kye.app.ui.components.SectionCard
import com.kye.app.ui.components.LoadingCards
import com.kye.app.ui.theme.paymentColor
import java.time.LocalDate

@Composable
fun InsightsScreen(vm: KyeViewModel, onOpenTxn: (Long) -> Unit = {}, onStory: (String) -> Unit = {}, onBudgets: () -> Unit) {
    val allTxns by vm.txns.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val a by vm.analytics.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = tabBottomPadding(24.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") {
            Column {
                Text("Insights", style = MaterialTheme.typography.headlineSmall)
                Text(a?.rangeLabel ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "periods") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Period.entries) { p -> ChoiceChip(p.label, p == period, { vm.period.value = p }) }
            }
        }
        val s = a
        if (s == null) {
            item(key = "loading") { LoadingCards() }
            return@LazyColumn
        }
        item(key = "summary") { Column { Summary(s) } }
        item(key = "calendar") { SpendCalendarCard(allTxns, onOpenTxn, onStory) }

        if (s.count == 0) {
            item(key = "empty") { EmptyState("📊", "No spending in this period", "Add a few entries and your charts appear here") }
        } else {
            item(key = "donut") { CategoryCard(s) }
            item(key = "trend") {
                SectionCard(title = "Trend", subtitle = if (s.trendDaily) "Spend per day · tap a bar" else "Spend per month · tap a bar") {
                    BarChart(s.trend)
                }
            }
            if (s.compare.isNotEmpty()) item(key = "compare") { CompareCard(s) }
            if (s.merchants.isNotEmpty()) item(key = "merchants") { MerchantsCard(s) }
            item(key = "payments") { PaymentsCard(s) }
            item(key = "fixed") { FixedCard(s) }
        }
        item(key = "budgets") { BudgetsCard(s, onBudgets) }
        if (s.upcoming.isNotEmpty()) item(key = "upcoming") { UpcomingCard(s) }
    }
}

@Composable
private fun Summary(s: AnalyticsState) {
    if (s.income > 0) {
        // Only once income is being tracked: what's left after spending, and how much of income that is.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Income", Money.format(s.income), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
            val left = s.income - s.spent
            StatTile(
                if (left >= 0) "Left over" else "Overspent",
                Money.format(kotlin.math.abs(left)),
                Modifier.weight(1f),
                if (left >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
            )
            StatTile("Kept", "${(left * 100 / s.income).coerceAtLeast(0)}%", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Spent", Money.format(s.spent), Modifier.weight(1f))
        StatTile("Saved", Money.format(s.saved), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
        val change = if (s.prevSpent > 0) ((s.spent - s.prevSpent) * 100 / s.prevSpent) else null
        StatTile(
            "vs previous",
            change?.let { (if (it > 0) "▲ " else if (it < 0) "▼ " else "") + "${kotlin.math.abs(it)}%" } ?: "—",
            Modifier.weight(1f),
            when {
                change == null -> MaterialTheme.colorScheme.onSurface
                change > 0 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.secondary
            }
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CategoryCard(s: AnalyticsState) {
    var expanded by remember(s.period) { mutableStateOf<Long?>(-1L) }
    SectionCard(title = "Where it went", subtitle = "Tap a category for its breakdown") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Donut(s.slices, s.spent, expanded ?: -1L) { key -> expanded = if (expanded == key) -1L else key }
        }
        Spacer(Modifier.height(16.dp))
        s.slices.forEach { slice ->
            val key = slice.categoryId ?: 0L
            val open = expanded == key
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { expanded = if (open) -1L else key }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(slice.emoji, slice.color, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(slice.name, style = MaterialTheme.typography.titleSmall)
                        val pct = if (s.spent > 0) slice.total * 100 / s.spent else 0
                        Text("$pct%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.format(slice.total), style = MaterialTheme.typography.titleSmall)
                    Icon(
                        if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(open) {
                    Column(Modifier.padding(start = 44.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        slice.subs.forEach { (name, total) ->
                            Column {
                                Row {
                                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(Money.format(total), style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(Modifier.height(4.dp))
                                ProgressBar(
                                    fraction = if (slice.total > 0) total.toFloat() / slice.total else 0f,
                                    color = Color(slice.color),
                                    height = 5.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
/**
 * Category donut. It sweeps round when it appears; tapping a slice pops it out (the others dim)
 * and shows its name and amount in the middle. [selected] is the open category key, -1 for none.
 */
private fun Donut(slices: List<Slice>, total: Long, selected: Long, onSelect: (Long) -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        pop.snapTo(0f)
        if (selected != -1L) pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    // Top 7 categories, the rest merged into one grey arc
    val shown = if (slices.size <= 8) slices else {
        val rest = slices.drop(7).sumOf { it.total }
        slices.take(7) + Slice(null, "Rest", "•", 0xFFB2BEC3, rest, emptyList())
    }
    val keyOf = { sl: Slice -> sl.categoryId ?: 0L }
    val pick = shown.firstOrNull { it.categoryId != null && keyOf(it) == selected }
    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(shown, total) {
                    detectTapGestures { pos ->
                        if (total <= 0) return@detectTapGestures
                        val dx = pos.x - size.width / 2f
                        val dy = pos.y - size.height / 2f
                        val r = kotlin.math.sqrt(dx * dx + dy * dy)
                        // Only taps on the ring itself, not the middle.
                        if (r < size.width * 0.28f) return@detectTapGestures
                        // Angle from 12 o'clock, clockwise, 0..360.
                        val deg = (Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f + 360f) % 360f
                        var acc = 0f
                        shown.firstOrNull { sl ->
                            acc += 360f * sl.total / total
                            deg < acc
                        }?.takeIf { it.categoryId != null }?.let { onSelect(keyOf(it)) }
                    }
                }
        ) {
            val stroke = 30.dp.toPx()
            val inset = stroke / 2 + 10.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arc, style = Stroke(stroke))
            if (total > 0) {
                var start = -90f
                val gap = if (shown.size > 1) 1.6f else 0f
                shown.forEach { sl ->
                    val sweep = 360f * sl.total / total * progress.value
                    if (sweep > gap) {
                        val isPick = pick != null && sl === pick
                        // The tapped slice moves out along its middle and thickens a little.
                        val mid = Math.toRadians((start + sweep / 2).toDouble())
                        val out = if (isPick) 9.dp.toPx() * pop.value else 0f
                        val shift = Offset((kotlin.math.cos(mid) * out).toFloat(), (kotlin.math.sin(mid) * out).toFloat())
                        drawArc(
                            color = Color(sl.color).copy(alpha = if (pick == null || isPick) 1f else 0.35f),
                            startAngle = start + gap / 2,
                            sweepAngle = sweep - gap,
                            useCenter = false,
                            topLeft = Offset(inset, inset) + shift,
                            size = arc,
                            style = Stroke(stroke + (if (isPick) 6.dp.toPx() * pop.value else 0f))
                        )
                    }
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp)) {
            Text(
                if (pick != null) "${pick.emoji} ${pick.name}" else "Spent",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(Money.format(pick?.total ?: total), style = MaterialTheme.typography.titleLarge)
            if (pick != null && total > 0) {
                Text("${pick.total * 100 / total}%", style = MaterialTheme.typography.labelMedium, color = Color(pick.color))
            }
        }
    }
}

@Composable
private fun BarChart(bars: List<TrendBar>) {
    if (bars.isEmpty()) return
    val max = (bars.maxOf { it.value }).coerceAtLeast(1L)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(1100, easing = LinearEasing))
    }
    var selected by remember(bars) { mutableIntStateOf(bars.indexOfLast { it.value > 0 }) }
    val color = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Column {
        val sel = bars.getOrNull(selected)
        Text(
            if (sel != null) "${sel.label} · ${Money.format(sel.value)}" else "Tap a bar",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(bars) {
                    detectTapGestures { pos ->
                        val slot = size.width.toFloat() / bars.size
                        selected = (pos.x / slot).toInt().coerceIn(0, bars.lastIndex)
                    }
                }
        ) {
            val n = bars.size
            val slot = size.width / n
            val bw = (slot * 0.62f).coerceAtMost(26.dp.toPx())
            val radius = CornerRadius(bw / 2, bw / 2)
            bars.forEachIndexed { i, b ->
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(track, topLeft = Offset(x, 0f), size = Size(bw, size.height), cornerRadius = radius)
                // Bars rise one after another, left to right, each easing to a stop.
                val local = (anim.value * 1.6f - 0.6f * i / n).coerceIn(0f, 1f)
                val h = size.height * (b.value.toFloat() / max) * FastOutSlowInEasing.transform(local)
                if (h > 0f) {
                    drawRoundRect(
                        if (i == selected) color else dim,
                        topLeft = Offset(x, size.height - h),
                        size = Size(bw, h),
                        cornerRadius = radius
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(bars.first().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (bars.size > 2) {
                Text(bars[bars.size / 2].label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
            }
            Text(bars.last().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompareCard(s: AnalyticsState) {
    SectionCard(title = "This vs previous period", subtitle = "Top categories") {
        val max = s.compare.maxOf { maxOf(it.current, it.previous) }.coerceAtLeast(1L)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend(MaterialTheme.colorScheme.primary, "Now")
            Spacer(Modifier.width(16.dp))
            Legend(MaterialTheme.colorScheme.outline, "Before")
        }
        Spacer(Modifier.height(12.dp))
        s.compare.forEach { r ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.emoji}  ${r.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(r.current), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar(r.current.toFloat() / max, MaterialTheme.colorScheme.primary, height = 7.dp)
                Spacer(Modifier.height(4.dp))
                ProgressBar(r.previous.toFloat() / max, MaterialTheme.colorScheme.outline, height = 7.dp)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MerchantsCard(s: AnalyticsState) {
    SectionCard(title = "Top merchants", subtitle = "Where you spend most") {
        s.merchants.forEachIndexed { i, m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(22.dp))
                Text(m.emoji)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.titleSmall)
                    Text("${m.count} ${if (m.count == 1) "time" else "times"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.format(m.total), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun PaymentsCard(s: AnalyticsState) {
    SectionCard(title = "Paid with") {
        val total = s.payments.sumOf { it.second }.coerceAtLeast(1L)
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50))) {
            s.payments.forEachIndexed { i, (_, amount) ->
                val w = amount.toFloat() / total
                if (w > 0f) Box(Modifier.weight(w).fillMaxSize().background(paymentColor(i)))
            }
        }
        Spacer(Modifier.height(12.dp))
        s.payments.forEachIndexed { i, (mode, amount) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(paymentColor(i)))
                Spacer(Modifier.width(8.dp))
                Text(mode.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${amount * 100 / total}%  ·  ${Money.format(amount)}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FixedCard(s: AnalyticsState) {
    SectionCard(title = "Fixed vs variable", subtitle = "Fixed = added by recurring rules") {
        val total = (s.fixed + s.variable).coerceAtLeast(1L)
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Fixed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(s.fixed), style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Variable", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(s.variable), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(s.fixed.toFloat() / total, MaterialTheme.colorScheme.secondary, track = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), height = 10.dp)
    }
}

@Composable
private fun BudgetsCard(s: AnalyticsState, onBudgets: () -> Unit) {
    SectionCard(
        title = "Budgets",
        subtitle = "This month",
        trailing = { androidx.compose.material3.TextButton(onClick = onBudgets) { Text(if (s.budgets.isEmpty()) "Set up" else "Edit") } }
    ) {
        if (s.budgets.isEmpty()) {
            Text("No budgets yet. Set a monthly limit to get alerts at 80% and 100%.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.budgets.forEach { b ->
            val f = if (b.limit > 0) b.spent.toFloat() / b.limit else 0f
            Column(Modifier.padding(vertical = 6.dp)) {
                Row {
                    Text("${b.emoji}  ${b.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${Money.format(b.spent)} / ${Money.format(b.limit)}", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar(
                    f,
                    when {
                        f >= 1f -> MaterialTheme.colorScheme.error
                        f >= 0.8f -> Color(0xFFF39C12)
                        else -> MaterialTheme.colorScheme.secondary
                    }
                )
            }
        }
    }
}

@Composable
private fun UpcomingCard(s: AnalyticsState) {
    val today = LocalDate.now()
    SectionCard(title = "Coming up", subtitle = "Next 30 days · ${Money.format(s.upcomingTotal)} to keep aside") {
        s.upcoming.forEach { u ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(u.emoji, u.color, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.rule.name, style = MaterialTheme.typography.titleSmall)
                    Text(Dates.label(u.due, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (u.rule.amountPaise > 0) Money.format(u.rule.amountPaise) else "—", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
