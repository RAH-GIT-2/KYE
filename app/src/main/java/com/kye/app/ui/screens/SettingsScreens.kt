@file:OptIn(ExperimentalMaterial3Api::class)

package com.kye.app.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.kye.app.ui.AppLock
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kye.app.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.PictureAsPdf
import com.kye.app.domain.Reports
import java.time.YearMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.data.CategoryKind
import com.kye.app.domain.Money
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.CategoryAvatar
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.ui.components.ProgressBar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import com.kye.app.data.BackupSettings
import com.kye.app.domain.BackupFrequency
import com.kye.app.domain.BackupSchedule
import com.kye.app.domain.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    vm: KyeViewModel,
    onBack: () -> Unit,
    onBudgets: () -> Unit,
    onCategories: () -> Unit,
    onImport: () -> Unit,
    onLoans: () -> Unit,
    onEvents: () -> Unit,
    onSubscriptions: () -> Unit,
    onGoals: () -> Unit,
    onSchemes: () -> Unit,
    onWarranties: () -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            vm.exportCsv(uri) { ok ->
                Toast.makeText(context, if (ok) "Exported" else "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportBackup(uri, toast)
    }
    var pickingReport by remember { mutableStateOf(false) }
    // Saveable: the file picker can outlive this screen (rotation, Android reclaiming memory).
    // A month ("2026-10") or a year key ("fy2025", "cy2026").
    var reportPick by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(YearMonth.now().minusMonths(1).toString()) }
    val reportSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val year = com.kye.app.domain.ReportYear.parse(reportPick)
            if (year != null) vm.exportYearReport(uri, year, toast) else vm.exportReport(uri, YearMonth.parse(reportPick), toast)
        }
    }
    if (pickingReport) {
        ReportMonthDialog(
            onDismiss = { pickingReport = false },
            onPickYear = { y ->
                pickingReport = false
                reportPick = y.key
                reportSaver.launch(y.fileName)
            }
        ) { m ->
            pickingReport = false
            reportPick = m.toString()
            reportSaver.launch(Reports.fileName(m))
        }
    }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePreview by remember { mutableStateOf<com.kye.app.data.BackupPreview?>(null) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Read the file first: a wrong file is refused here, before anything is replaced.
            vm.previewBackup(uri) { preview, error ->
                if (preview == null) toast(error ?: "Can't read that file")
                else {
                    restorePreview = preview
                    restoreUri = uri
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionLabel("Money")
            ActionRow(Icons.Rounded.Category, "Categories", "Add, rename, hide or merge categories and subcategories", onCategories)
            ActionRow(Icons.Rounded.Savings, "Budgets", "Monthly limits with 80% / 100% alerts", onBudgets)
            ActionRow(Icons.Rounded.Flag, "Savings goals", "A trip, a bike, a phone: a target, and how much to save each month", onGoals)
            ActionRow(Icons.Rounded.Diamond, "Gold schemes", "Monthly jewellery schemes: instalments, due-day reminders, maturity", onSchemes)
            ActionRow(Icons.Rounded.Subscriptions, "Subscriptions", "Netflix, Prime, Jio…: what they cost in a year, with a reminder before yearly renewals", onSubscriptions)
            ActionRow(Icons.Rounded.Celebration, "Events", "Deepavali, a wedding or a trip, each with its own budget", onEvents)
            ActionRow(Icons.Rounded.Handshake, "Lent & borrowed", "Who owes you, whom you owe, part repayments and due-date reminders", onLoans)
            ActionRow(Icons.Rounded.VerifiedUser, "Warranties", "Purchases still under warranty, with their bills", onWarranties)
            SectionLabel("Your data")
            AutoBackupCard(vm)
            ActionRow(Icons.Rounded.PictureAsPdf, "Report (PDF)", "A month or a whole year (April–March or Jan–Dec): income, spending, savings") {
                pickingReport = true
            }
            ActionRow(Icons.Rounded.Backup, "Back up to a file now", "One .zip with everything, bill photos included. Save it to Google Drive.") {
                backup.launch("KYE-backup-${LocalDate.now()}.zip")
            }
            ActionRow(Icons.Rounded.Restore, "Restore from backup", "Replaces everything on this phone with the backup") {
                restorePicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/json", "application/octet-stream", "text/plain"))
            }
            ActionRow(Icons.Rounded.UploadFile, "Import from CSV", "Add expenses and income from a spreadsheet. Get the blank template there.", onImport)
            ActionRow(Icons.Rounded.FileDownload, "Export to CSV", "Same columns as the import template. Opens in Excel or Google Sheets") {
                exporter.launch("KYE-expenses-${LocalDate.now()}.csv")
            }
            SectionLabel("Preferences")
            SettingsCard("Voice entry") {
                val voiceOn = settings?.voiceEnabled ?: true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show the mic button", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (voiceOn) "On Home, next to +" else "Off · use + to add. Its Quick type box still understands \"swiggy 450 biryani\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = voiceOn, onCheckedChange = { vm.setVoiceEnabled(it) })
                }
                if (voiceOn) {
                    Spacer(Modifier.height(12.dp))
                    Text("Language", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("English", (settings?.voiceLang ?: "en-IN") == "en-IN", { vm.setVoiceLang("en-IN") })
                        ChoiceChip("தமிழ்", settings?.voiceLang == "ta-IN", { vm.setVoiceLang("ta-IN") })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tanglish works best in English mode. For Tamil without internet, download the Tamil offline pack in your phone's Google voice settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            SettingsCard("App lock") {
                val lockOn = settings?.appLock ?: false
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Lock KYE", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Asks for your fingerprint, face or phone PIN when you open KYE, and after a minute away. " +
                                "The home-screen widget hides amounts while this is on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = lockOn, onCheckedChange = { wanted ->
                        val activity = context.findActivity()
                        when {
                            activity == null -> Unit
                            !AppLock.isAvailable(context) ->
                                Toast.makeText(context, "Set a screen lock (PIN, pattern or fingerprint) in your phone's settings first", Toast.LENGTH_LONG).show()
                            // Confirm it's really you both to switch it on and to switch it off.
                            else -> AppLock.authenticate(
                                activity,
                                if (wanted) "Turn on app lock" else "Turn off app lock",
                                onSuccess = { vm.setAppLock(wanted) }
                            )
                        }
                    })
                }
            }
            SettingsCard("Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (v, label) ->
                        ChoiceChip(label, (settings?.theme ?: "system") == v, { vm.setTheme(v) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "KYE · Know Your Expense · v${BuildConfig.VERSION_NAME}\nYour data stays on this phone. Back it up now and then.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    restoreUri?.let { uri ->
        val p = restorePreview
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Replace everything?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p != null) {
                        val made = p.exportedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(BACKUP_TIME) }
                        Text("This backup", style = MaterialTheme.typography.labelLarge)
                        Text(
                            listOfNotNull(
                                made?.let { "Made $it" },
                                "${p.entries} entries" + if (p.photos > 0) " · ${p.photos} bill photos" else "",
                                if (p.firstEntry != null && p.lastEntry != null)
                                    "From ${Dates.short(Dates.toLocalDate(p.firstEntry))} to ${Dates.short(Dates.toLocalDate(p.lastEntry))}" else null
                            ).joinToString("\n"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("On this phone now", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${p.entriesHere} entries" + if (p.photosHere > 0) " · ${p.photosHere} bill photos" else "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (p.entries < p.entriesHere) {
                            Text(
                                "The backup has ${p.entriesHere - p.entries} fewer entries than this phone. They'd be lost.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Text(
                        "Everything on this phone is replaced with the backup. Back up first if you're unsure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null
                    vm.importBackup(uri, toast)
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } }
        )
    }
}

private fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

private val BACKUP_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)

@Composable
private fun AutoBackupCard(vm: KyeViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val b = settings?.backup ?: BackupSettings()
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    var busy by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(b.folderUri) { folderName = vm.backupFolderName(b.folderUri) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            busy = true
            vm.setBackupFolder(uri) {
                busy = false
                toast(it)
            }
        }
    }

    SettingsCard("Automatic backup") {
        // Last backup, the thing people most want to know.
        val last = b.lastAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(BACKUP_TIME) }
        Text(
            if (last != null) "Last backup: $last · ${if (b.lastWasAuto) "automatic" else "saved by you"}"
            else "Never backed up",
            style = MaterialTheme.typography.bodyLarge,
            color = if (last == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        val next = BackupSchedule.nextDue(b.frequency, b.lastAt?.let { Dates.toLocalDate(it) }, LocalDate.now())
        if (next != null && b.folderUri != null) {
            Text(
                "Next: " + Dates.label(maxOf(next, LocalDate.now())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        b.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))
        Text("How often", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackupFrequency.entries.forEach { f ->
                ChoiceChip(f.label, b.frequency == f, {
                    if (f != BackupFrequency.OFF && b.folderUri == null) {
                        toast("Choose a folder for the backups first")
                        folderPicker.launch(null)
                    } else vm.setBackupFrequency(f)
                })
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Saved in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                folderName ?: if (b.folderUri != null) "Chosen folder" else "No folder yet",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { folderPicker.launch(null) }, enabled = !busy) {
                Text(if (b.folderUri == null) "Choose folder" else "Change")
            }
        }
        Text(
            "Keeps the last ${BackupSchedule.KEEP} automatic backups there. Pick a folder outside KYE (e.g. Documents) " +
                "so backups survive uninstalling. For a copy off the phone, use \"Back up to a file now\" and save to Drive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (b.folderUri != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Monthly PDF report", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "On the 1st, saves last month's report here too (${Reports.fileName(YearMonth.now().minusMonths(1))}). Never deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = b.monthlyReport, onCheckedChange = { vm.setMonthlyReport(it) })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                busy = true
                vm.backUpNow {
                    busy = false
                    toast(it)
                }
            }, enabled = !busy) { Text(if (busy) "Backing up…" else "Back up now") }
        }
    }
}

private val REPORT_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

/** Pick one of the last 12 months, or a whole year, for the PDF report. */
@Composable
private fun ReportMonthDialog(onDismiss: () -> Unit, onPickYear: (com.kye.app.domain.ReportYear) -> Unit, onPick: (YearMonth) -> Unit) {
    val months = remember { Reports.recentMonths(LocalDate.now()) }
    val years = remember { com.kye.app.domain.ReportYear.choices(LocalDate.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report for which period?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Whole year", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                years.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { y -> ChoiceChip(y.label, false, { onPickYear(y) }) }
                    }
                }
                Text("Month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                months.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m ->
                            ChoiceChip(
                                if (m == YearMonth.now()) "This month" else m.atDay(1).format(REPORT_MONTH),
                                false, { onPick(m) }
                            )
                        }
                    }
                }
                Text(
                    "Saved as a PDF wherever you choose, e.g. Downloads or Google Drive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A small heading between groups of settings ("Money", "Your data", "Preferences"). */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BudgetsScreen(vm: KyeViewModel, onBack: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val spend by vm.monthByCategory.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val totalSpent = spend.values.sum()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                BudgetRowItem("📊", 0xFF6C5CE7, "Overall monthly budget", totalSpent, budgets[0L]) { editing = 0L to "Overall" }
            }
            item {
                Text(
                    "Per category",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(categories.filter { it.kind == CategoryKind.EXPENSE }, key = { it.id }) { c ->
                BudgetRowItem(c.emoji, c.color, c.name, spend[c.id] ?: 0L, budgets[c.id]) { editing = c.id to c.name }
            }
        }
    }

    editing?.let { (catId, name) ->
        var text by remember(catId) { mutableStateOf(budgets[catId]?.let { Money.plain(it) } ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(name) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("Monthly limit (blank = none)") },
                    prefix = { Text("₹ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setBudget(catId, if (text.isBlank()) null else Money.parseToPaise(text))
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BudgetRowItem(emoji: String, color: Long, name: String, spent: Long, limit: Long?, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(emoji, color, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (limit != null) "${Money.format(spent)} of ${Money.format(limit)}" else "Spent ${Money.format(spent)} · tap to set a limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (limit != null && limit > 0) {
                Spacer(Modifier.height(10.dp))
                val f = spent.toFloat() / limit
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
