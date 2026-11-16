package com.kye.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kye.app.data.TxnRow
import com.kye.app.domain.Dates
import com.kye.app.domain.Money
import com.kye.app.domain.SpendCalendar
import com.kye.app.ui.components.SectionCard
import com.kye.app.ui.components.TxnItem
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * A month of days, each shaded by how much was spent: light for small days, deep for big ones.
 * Tap a day to list its entries underneath; arrows move between months.
 */
@Composable
fun SpendCalendarCard(txns: List<TxnRow>, onOpenTxn: (Long) -> Unit) {
    val today = LocalDate.now()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    var picked by remember(monthText) { mutableStateOf<LocalDate?>(null) }
    var forward by remember { mutableStateOf(true) }
    val data = remember(txns, monthText) { SpendCalendar.month(txns, month, today) }

    SectionCard(
        title = "Spending calendar",
        subtitle = "Deeper colour, bigger day · tap a day for its entries",
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { forward = false; monthText = month.minusMonths(1).toString() }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                IconButton(
                    onClick = { forward = true; monthText = month.plusMonths(1).toString() },
                    enabled = month < YearMonth.from(today)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
        }
    ) {
        AnimatedContent(
            targetState = data,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(260)) { it / 4 * dir } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it / 4 * dir } + fadeOut(tween(200)))
            },
            label = "month"
        ) { m ->
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${m.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${m.month.year}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(Money.format(m.total), style = MaterialTheme.typography.titleMedium)
                }
                if (m.noSpendDays > 0) {
                    Text(
                        "${m.noSpendDays} no-spend ${if (m.noSpendDays == 1) "day" else "days"} 🌱",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.height(12.dp))
                MonthGrid(m, today, picked) { d -> picked = if (picked == d) null else d }
                Spacer(Modifier.height(10.dp))
                Legend()
            }
        }

        picked?.let { day ->
            val rows = txns.filter { Dates.toLocalDate(it.txn.dateTime) == day }
            Spacer(Modifier.height(14.dp))
            Text(Dates.label(day), style = MaterialTheme.typography.titleSmall)
            if (rows.isEmpty()) {
                Text(
                    "Nothing added on this day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            rows.forEach { r -> TxnItem(r, showDate = false) { onOpenTxn(r.txn.id) } }
        }
    }
}

@Composable
private fun MonthGrid(m: SpendCalendar.Month, today: LocalDate, picked: LocalDate?, onPick: (LocalDate) -> Unit) {
    val heat = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpendCalendar.WEEKDAYS.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(bottom = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        SpendCalendar.weeks(m.month).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { d ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (d != null) {
                            val spent = m.byDay[d] ?: 0L
                            val level = SpendCalendar.level(spent, m.max)
                            val future = d.isAfter(today)
                            val fill by animateColorAsState(
                                when {
                                    future -> Color.Transparent
                                    level == 0 -> empty
                                    else -> heat.copy(alpha = 0.18f + 0.2f * level)
                                },
                                tween(300), label = "day"
                            )
                            val deep = level >= 3
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(fill)
                                    .then(
                                        if (d == picked) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(10.dp))
                                        else if (d == today) Modifier.border(1.5.dp, heat, RoundedCornerShape(10.dp))
                                        else Modifier
                                    )
                                    .clickable(enabled = !future) { onPick(d) }
                                    .semantics {
                                        contentDescription = "${Dates.label(d)}: " +
                                            if (spent > 0) Money.format(spent) else "nothing spent"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${d.dayOfMonth}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        deep -> MaterialTheme.colorScheme.onPrimary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Less ▢▢▢▢ More" key under the grid. */
@Composable
private fun Legend() {
    val heat = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        (1..4).forEach { l ->
            Box(Modifier.padding(horizontal = 2.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(heat.copy(alpha = 0.18f + 0.2f * l)))
        }
        Spacer(Modifier.width(6.dp))
        Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

