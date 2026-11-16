@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kye.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kye.app.data.Category
import com.kye.app.data.Event
import com.kye.app.data.PaymentMode
import com.kye.app.data.Subcategory
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import com.kye.app.domain.Dates
import com.kye.app.domain.Money
import com.kye.app.domain.UNCATEGORISED_COLOR
import java.time.LocalDate

@Composable
fun CategoryAvatar(emoji: String, color: Long, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size.value * 0.46f).sp)
    }
}

/**
 * Rolls like a car's odometer when the amount changes: only the digits that change slide, up when
 * the amount grows and down when it shrinks. Read out as one amount by screen readers.
 */
@Composable
fun AnimatedAmount(paise: Long, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val text = Money.format(paise)
    val last = remember { mutableLongStateOf(paise) }
    val up = paise >= last.longValue
    LaunchedEffect(paise) { last.longValue = paise }
    Row(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
        // Keyed from the right, so the units digit stays the units digit when ₹999 becomes ₹1,000.
        text.forEachIndexed { i, ch ->
            key(text.length - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val dir = if (up) 1 else -1
                        (slideInVertically(tween(420)) { h -> h * dir } + fadeIn(tween(420)))
                            .togetherWith(slideOutVertically(tween(420)) { h -> -h * dir } + fadeOut(tween(300)))
                            .using(SizeTransform(clip = true))
                    },
                    label = "digit"
                ) { c -> Text(c.toString(), style = style, color = color, maxLines = 1) }
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(18.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TxnItem(
    row: TxnRow,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val t = row.txn
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(row.catEmoji ?: "❔", row.catColor ?: UNCATEGORISED_COLOR)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                t.note.takeIf { it.isNotBlank() },
                t.paymentMode.label,
                when (t.source) {
                    com.kye.app.data.Source.VOICE -> "🎙"
                    com.kye.app.data.Source.RECURRING -> "🔁"
                    else -> null
                },
                if (row.attachments > 0) "📎" else null,
                if (row.warrantyUntil != null) "🛡️" else null
            ).joinToString(" · ")
            Text(
                meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            val (prefix, color) = when (t.type) {
                TxnType.SAVING -> "+" to MaterialTheme.colorScheme.secondary
                TxnType.TRANSFER -> "⇄ " to MaterialTheme.colorScheme.onSurfaceVariant
                TxnType.INCOME -> "+" to MaterialTheme.colorScheme.secondary
                TxnType.EXPENSE -> "" to MaterialTheme.colorScheme.onSurface
            }
            Text(prefix + Money.format(t.amountPaise, showPaise = true), style = MaterialTheme.typography.titleSmall, color = color)
            if (showDate) {
                Text(
                    Dates.label(Dates.toLocalDate(t.dateTime)), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, leading: String? = null) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(if (leading != null) "$leading  $label" else label, maxLines = 1) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
fun CategoryGrid(categories: List<Category>, selectedId: Long?, onSelect: (Category) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { c ->
            val selected = c.id == selectedId
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) Color(c.color).copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable { onSelect(c) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(c.emoji, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    c.name, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Category, then subcategory, without the subcategories ending up below a long list.
 * With no category picked the full list shows. Picking one folds the list into a single
 * "🏠 Home & Help · Change" line with the subcategory chips right under it.
 */
@Composable
fun CategoryPicker(
    categories: List<Category>,
    subcategories: List<Subcategory>,
    catId: Long?,
    subId: Long?,
    onCategory: (Category) -> Unit,
    onSubcategory: (Subcategory) -> Unit
) {
    var expanded by remember { mutableStateOf(catId == null) }
    // An entry being edited loads its category a moment later: fold the list then too.
    LaunchedEffect(catId) { if (catId != null) expanded = false }
    val selected = categories.firstOrNull { it.id == catId }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (expanded || selected == null) {
            CategoryGrid(categories, catId) { c ->
                onCategory(c)
                expanded = false
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(selected.color).copy(alpha = 0.22f))
                        .clickable { expanded = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selected.emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(selected.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { expanded = true }) { Text("Change") }
            }
        }

        if (selected != null && !expanded) {
            val subs = subcategories.filter { it.categoryId == selected.id }
            Column {
                Text(
                    "Subcategory",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                SubcategoryChips(subs, subId, onSubcategory)
                if (subs.none { !it.isDefault }) {
                    Text(
                        "Add subcategories in Settings → Categories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * "Event: None · 🪔 Deepavali 2026 · ✈️ Goa trip". Hidden when there are no events.
 * Tapping the selected one again clears it. [extra] keeps an old tag visible (e.g. a finished event).
 */
@Composable
fun EventChips(events: List<Event>, selectedId: Long?, onSelect: (Long?) -> Unit, extra: Event? = null) {
    val shown = (events + listOfNotNull(extra?.takeIf { e -> events.none { it.id == e.id } }))
    if (shown.isEmpty()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Event", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceChip("None", selectedId == null, { onSelect(null) })
        shown.forEach { e ->
            ChoiceChip(e.name, selectedId == e.id, { onSelect(if (selectedId == e.id) null else e.id) }, leading = e.emoji)
        }
    }
}

@Composable
fun SubcategoryChips(subs: List<Subcategory>, selectedId: Long?, onSelect: (Subcategory) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        subs.forEach { s -> ChoiceChip(s.name, s.id == selectedId, { onSelect(s) }) }
    }
}

@Composable
fun PaymentChips(selected: PaymentMode, onSelect: (PaymentMode) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaymentMode.entries.forEach { m -> ChoiceChip(m.label, m == selected, { onSelect(m) }) }
    }
}

@Composable
fun DatePickerSheet(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = Dates.toPickerMillis(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Dates.fromPickerMillis(it)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = MaterialTheme.colorScheme.surfaceVariant, height: Dp = 8.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Bottom room for a tab screen's list: [extra] (e.g. for the + button) plus the floating bottom
 * bar and the phone's own navigation buttons, so the last entry can scroll clear of them.
 */
@Composable
fun tabBottomPadding(extra: Dp): Dp =
    extra + 86.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()

/** A soft light that sweeps across placeholders while data loads. */
@Composable
fun shimmerBrush(): androidx.compose.ui.graphics.Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val shine = MaterialTheme.colorScheme.surfaceContainerLowest
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -400f,
        targetValue = 1400f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(1300, easing = androidx.compose.animation.core.LinearEasing)),
        label = "shimmerX"
    )
    return androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(base, shine, base),
        start = androidx.compose.ui.geometry.Offset(x, 0f),
        end = androidx.compose.ui.geometry.Offset(x + 400f, 200f)
    )
}

/** Grey stand-ins for entry rows, shown until the real list is ready. */
@Composable
fun LoadingRows(count: Int = 5) {
    val brush = shimmerBrush()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
        repeat(count) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(brush))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(50)).background(brush))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(50)).background(brush))
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(60.dp).height(14.dp).clip(RoundedCornerShape(50)).background(brush))
            }
        }
    }
}

/** Grey stand-ins for cards, shown until the first numbers are ready. */
@Composable
fun LoadingCards(count: Int = 3) {
    val brush = shimmerBrush()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(count) { i ->
            Box(Modifier.fillMaxWidth().height(if (i == 0) 120.dp else 180.dp).clip(RoundedCornerShape(24.dp)).background(brush))
        }
    }
}
