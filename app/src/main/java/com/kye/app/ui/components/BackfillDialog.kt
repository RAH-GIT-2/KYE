package com.kye.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kye.app.domain.BackfillDate
import com.kye.app.domain.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * "Add the 4 past entries too?" for a recurring rule that starts in the past. Dates that look
 * already entered by hand start unticked. [onAdd] gets the ticked dates; [onSkip] = only from now on.
 */
@Composable
fun BackfillDialog(
    options: List<BackfillDate>,
    amountPaise: Long,
    onAdd: (List<LocalDate>) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    val picked = remember(options) { mutableStateListOf<LocalDate>().apply { addAll(options.filter { !it.looksAdded }.map { it.date }) } }
    val dupes = options.count { it.looksAdded }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add the ${options.size} past ${if (options.size == 1) "entry" else "entries"} too?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "This starts before today. KYE can add ${Money.format(amountPaise)} for each past due date, " +
                        "or start from the next one." +
                        if (dupes > 0) " $dupes look already added by you and are left unticked." else "",
                    style = MaterialTheme.typography.bodySmall
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(options, key = { it.date.toEpochDay() }) { o ->
                        val on = o.date in picked
                        Row(
                            Modifier.fillMaxWidth().clickable { if (on) picked.remove(o.date) else picked.add(o.date) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = { if (on) picked.remove(o.date) else picked.add(o.date) })
                            Column {
                                Text(o.date.format(DAY), style = MaterialTheme.typography.bodyMedium)
                                if (o.looksAdded) {
                                    Text(
                                        "Looks already added",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(picked.toList()) }, enabled = picked.isNotEmpty()) {
                Text(if (picked.size == options.size) "Add all ${picked.size}" else "Add ${picked.size}")
            }
        },
        dismissButton = { TextButton(onClick = onSkip) { Text("Only from now on") } }
    )
}
