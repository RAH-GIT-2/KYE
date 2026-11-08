@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kye.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.data.CategoryKind
import com.kye.app.data.UserError
import com.kye.app.ui.CatChoice
import com.kye.app.ui.ImportPreview
import com.kye.app.ui.ImportResult
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.UnknownCategory
import com.kye.app.ui.components.ChoiceChip
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(vm: KyeViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allCats by vm.allCategories.collectAsStateWithLifecycle()
    val allSubs by vm.allSubcategories.collectAsStateWithLifecycle()
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }

    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var choices by remember { mutableStateOf<Map<String, CatChoice>>(emptyMap()) }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showNames by remember { mutableStateOf(false) }
    var pickingFor by remember { mutableStateOf<UnknownCategory?>(null) }

    val templateSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.writeTemplate(uri, toast)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        result = null
        scope.launch {
            try {
                preview = vm.prepareImport(uri)
                choices = emptyMap()
            } catch (e: UserError) {
                preview = null
                toast(e.message ?: "Couldn't read the file")
            } catch (e: Exception) {
                preview = null
                toast("Couldn't read the file. Is it a CSV?")
            }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from CSV") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "step1") {
                StepCard("1. Get the blank template") {
                    Text(
                        "A CSV file with just the column names. Fill it in Excel, Google Sheets or any spreadsheet app, one entry per row.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { templateSaver.launch("KYE-import-template.csv") }) {
                        Icon(Icons.Rounded.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save blank template")
                    }
                }
            }

            item(key = "howto") {
                StepCard("How to fill it") {
                    HowRow("Date", "Required. 07-11-2026 (day first). 07/11/2026 and 2026-11-07 work too.")
                    HowRow("Type", "Expense, Income, Saving or Transfer. Blank: taken from the category, else Expense.")
                    HowRow("Amount", "Required. 450 or 1,250.50")
                    HowRow("Category", "A category name from KYE. Blank: guessed from the Note. New names: you'll be asked.")
                    HowRow("Subcategory", "Optional, e.g. Swiggy. New ones are created. Blank: Others.")
                    HowRow("Payment", "UPI, Cash, Debit card, Credit card, Net banking or Wallet. Blank: UPI.")
                    HowRow("Note", "Optional.")
                    HowRow("Event", "Optional. The name of an event you've created, e.g. Deepavali 2026.")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "In Excel, use Save As → \"CSV UTF-8\", or Tamil text turns into ????. " +
                            "Existing entries are never changed; import only adds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showNames = !showNames }) {
                        Text(if (showNames) "Hide category names" else "See category names")
                    }
                    if (showNames) {
                        CategoryKind.entries.forEach { kind ->
                            val list = allCats.filter { it.kind == kind && !it.isArchived }
                            if (list.isEmpty()) return@forEach
                            Text(
                                when (kind) {
                                    CategoryKind.EXPENSE -> "Expense"
                                    CategoryKind.INCOME -> "Income"
                                    CategoryKind.SAVING -> "Saving"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            list.forEach { c ->
                                val subs = allSubs.filter { it.categoryId == c.id && !it.isDefault && !it.isArchived }.joinToString(", ") { it.name }
                                Text(
                                    "${c.emoji} ${c.name}" + if (subs.isNotEmpty()) "  ·  $subs" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "step2") {
                StepCard("2. Upload the filled file") {
                    Button(onClick = {
                        filePicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                    }, enabled = !busy) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose CSV file")
                    }
                    if (busy) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Reading…")
                        }
                    }
                }
            }

            val p = preview
            if (p != null && result == null) {
                item(key = "preview") {
                    PreviewCard(
                        p = p,
                        choices = choices,
                        categoryName = { id -> allCats.firstOrNull { it.id == id }?.let { "${it.emoji} ${it.name}" } ?: "?" },
                        onChoice = { key, choice -> choices = choices + (key to choice) },
                        onPickExisting = { pickingFor = it },
                        busy = busy,
                        onImport = {
                            busy = true
                            vm.runImport(p, choices) { r, err ->
                                busy = false
                                if (r != null) {
                                    result = r
                                    preview = null
                                } else toast(err ?: "Import failed")
                            }
                        }
                    )
                }
            }

            val r = result
            if (r != null) {
                item(key = "result") {
                    StepCard("Imported ${r.imported} entr${if (r.imported == 1) "y" else "ies"}") {
                        val skipped = listOfNotNull(
                            r.duplicates.takeIf { it > 0 }?.let { "$it duplicate${if (it == 1) "" else "s"} skipped" },
                            r.problems.takeIf { it > 0 }?.let { "$it row${if (it == 1) "" else "s"} with problems left out" }
                        )
                        if (skipped.isNotEmpty()) {
                            Text(skipped.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            "They're in History now. Not what you expected? Undo removes exactly these entries.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            vm.undoImport(r.batch) { msg ->
                                toast(msg)
                                result = null
                            }
                        }) { Text("Undo this import") }
                    }
                }
            }
        }
    }

    pickingFor?.let { u ->
        val options = allCats.filter { it.kind == u.kind && !it.isArchived }
        AlertDialog(
            onDismissRequest = { pickingFor = null },
            title = { Text("Put \"${u.name}\" into…") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(options, key = { it.id }) { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    choices = choices + (u.key to CatChoice.Existing(c.id))
                                    pickingFor = null
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) { Text("${c.emoji}  ${c.name}") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickingFor = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PreviewCard(
    p: ImportPreview,
    choices: Map<String, CatChoice>,
    categoryName: (Long) -> String,
    onChoice: (String, CatChoice) -> Unit,
    onPickExisting: (UnknownCategory) -> Unit,
    busy: Boolean,
    onImport: () -> Unit
) {
    StepCard("Preview") {
        val parts = listOfNotNull(
            "${p.rows.size + p.problems.size} rows",
            "${p.ready} ready",
            p.duplicateLines.size.takeIf { it > 0 }?.let { "$it duplicate${if (it == 1) "" else "s"}" },
            p.problems.size.takeIf { it > 0 }?.let { "$it with problems" }
        )
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.titleSmall)

        if (p.duplicateLines.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Duplicates have the same date, amount and note as an entry you already have. They'll be skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (p.problems.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Rows that will be left out", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            p.problems.take(15).forEach {
                Text("Row ${it.line}: ${it.message}", style = MaterialTheme.typography.bodySmall)
            }
            if (p.problems.size > 15) {
                Text("…and ${p.problems.size - 15} more", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Fix them in the file and upload again, or import the rest now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (p.unknownCategories.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Text("Categories KYE doesn't have", style = MaterialTheme.typography.labelLarge)
            Text("Choose what to do with each one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            p.unknownCategories.forEach { u ->
                Spacer(Modifier.height(10.dp))
                Text("\"${u.name}\" · ${u.rows} row${if (u.rows == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall)
                val c = choices[u.key]
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Create it", c == CatChoice.Create, { onChoice(u.key, CatChoice.Create) })
                    ChoiceChip(
                        if (c is CatChoice.Existing) "Use ${categoryName(c.id)}" else "Use existing…",
                        c is CatChoice.Existing,
                        { onPickExisting(u) }
                    )
                    ChoiceChip("Leave uncategorised", c == CatChoice.Uncategorised, { onChoice(u.key, CatChoice.Uncategorised) })
                }
            }
        }

        if (p.newSubcategories.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "New subcategories will be created: " + p.newSubcategories.take(10).joinToString(", ") +
                    if (p.newSubcategories.size > 10) " and ${p.newSubcategories.size - 10} more" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(14.dp))
        val undecided = p.unknownCategories.count { choices[it.key] == null }
        Button(
            onClick = onImport,
            enabled = !busy && p.ready > 0 && undecided == 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    p.ready == 0 -> "Nothing to import"
                    undecided > 0 -> "Choose for $undecided categor${if (undecided == 1) "y" else "ies"} above"
                    else -> "Import ${p.ready} entr${if (p.ready == 1) "y" else "ies"}"
                }
            )
        }
    }
}

@Composable
private fun StepCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun HowRow(column: String, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(column, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(92.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}
