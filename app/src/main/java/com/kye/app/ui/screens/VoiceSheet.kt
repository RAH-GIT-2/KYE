@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kye.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.domain.Dates
import com.kye.app.domain.UNCATEGORISED_COLOR
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.VoiceDraft
import com.kye.app.ui.components.CategoryAvatar
import com.kye.app.ui.components.CategoryGrid
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.ui.components.DatePickerSheet
import com.kye.app.ui.components.PaymentChips
import com.kye.app.ui.components.SubcategoryChips
import com.kye.app.voice.SpeechController
import kotlinx.coroutines.launch

@Composable
fun VoiceSheet(vm: KyeViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val speech = remember { SpeechController(context) }
    var lang by remember { mutableStateOf(settings?.voiceLang ?: "en-IN") }
    var drafts by remember { mutableStateOf<List<VoiceDraft>>(emptyList()) }
    var typed by remember { mutableStateOf("") }
    var editingCategoryFor by remember { mutableStateOf<Int?>(null) }
    var editingDateFor by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) { onDispose { speech.release() } }

    fun handleText(text: String) {
        scope.launch { drafts = vm.parseVoice(text) }
    }

    fun listen() {
        drafts = emptyList()
        speech.start(lang) { handleText(it) }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen()
        else Toast.makeText(context, "Microphone permission is needed for voice entry", Toast.LENGTH_LONG).show()
    }

    fun startWithPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) { startWithPermission() }

    fun update(key: Int, change: (VoiceDraft) -> VoiceDraft) {
        drafts = drafts.map { if (it.key == key) change(it) else it }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("English", lang == "en-IN", {
                    lang = "en-IN"; vm.setVoiceLang("en-IN"); startWithPermission()
                })
                ChoiceChip("தமிழ்", lang == "ta-IN", {
                    lang = "ta-IN"; vm.setVoiceLang("ta-IN"); startWithPermission()
                })
            }
            Spacer(Modifier.height(20.dp))

            MicButton(listening = speech.listening, level = speech.level) {
                if (speech.listening) speech.finish() else startWithPermission()
            }
            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = when {
                    speech.error != null && drafts.isEmpty() -> speech.error ?: ""
                    speech.partial.isNotBlank() -> "“${speech.partial}”"
                    speech.listening -> "Listening… say \"Swiggy 450\" or \"auto 120 nethu\""
                    else -> "Tap the mic to speak"
                },
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "transcript"
            ) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (speech.error != null && drafts.isEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                )
            }

            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("…or type it: petrol 500 cash") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) handleText(typed) }),
                trailingIcon = {
                    IconButton(onClick = { if (typed.isNotBlank()) handleText(typed) }) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Parse")
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            drafts.forEach { d ->
                DraftCard(
                    vm = vm,
                    draft = d,
                    onAmount = { v -> update(d.key) { it.copy(amountText = v) } },
                    onNote = { v -> update(d.key) { it.copy(note = v) } },
                    onPayment = { m -> update(d.key) { it.copy(paymentMode = m) } },
                    onPickCategory = { editingCategoryFor = d.key },
                    onPickDate = { editingDateFor = d.key },
                    onRemove = { drafts = drafts.filter { it.key != d.key } }
                )
                Spacer(Modifier.height(12.dp))
            }

            if (drafts.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { startWithPermission() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Try again") }
                    Button(
                        onClick = {
                            vm.saveVoice(drafts) { count, msg ->
                                val text = if (count == 0) "Nothing saved: add an amount" else "Saved $count ${if (count == 1) "entry" else "entries"}"
                                Toast.makeText(context, if (msg != null) "$text\n$msg" else text, Toast.LENGTH_LONG).show()
                                if (count > 0) onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(if (drafts.size == 1) "Save" else "Save ${drafts.size}") }
                }
            } else {
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    editingCategoryFor?.let { key ->
        val d = drafts.firstOrNull { it.key == key }
        if (d != null) {
            CategoryDialog(
                vm = vm,
                categoryId = d.categoryId,
                subcategoryId = d.subcategoryId,
                onDismiss = { editingCategoryFor = null },
                onPick = { c, s -> update(key) { it.copy(categoryId = c, subcategoryId = s) } }
            )
        } else editingCategoryFor = null
    }

    editingDateFor?.let { key ->
        val d = drafts.firstOrNull { it.key == key }
        if (d != null) {
            DatePickerSheet(d.date, onDismiss = { editingDateFor = null }) { picked -> update(key) { it.copy(date = picked) } }
        } else editingDateFor = null
    }
}

@Composable
private fun MicButton(listening: Boolean, level: Float, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "ring"
    )
    val voice by animateFloatAsState(if (listening) 1f + level * 0.25f else 1f, spring(stiffness = 400f), label = "voice")
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size(96.dp)
                    .scale(ring)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.15f))
            )
        }
        Box(
            Modifier
                .size(96.dp)
                .scale(voice)
                .clip(CircleShape)
                .background(primary)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = if (listening) "Stop" else "Speak",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

@Composable
private fun DraftCard(
    vm: KyeViewModel,
    draft: VoiceDraft,
    onAmount: (String) -> Unit,
    onNote: (String) -> Unit,
    onPayment: (com.kye.app.data.PaymentMode) -> Unit,
    onPickCategory: () -> Unit,
    onPickDate: () -> Unit,
    onRemove: () -> Unit
) {
    val cats by vm.categoryMap.collectAsStateWithLifecycle()
    val subs by vm.subcategories.collectAsStateWithLifecycle()
    val cat = draft.categoryId?.let { cats[it] }
    val sub = subs.firstOrNull { it.id == draft.subcategoryId }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onPickCategory)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryAvatar(cat?.emoji ?: "❔", cat?.color ?: UNCATEGORISED_COLOR, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(cat?.name ?: "Pick a category", style = MaterialTheme.typography.titleSmall)
                        Text(
                            sub?.name ?: "Tap to change",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Remove") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.amountText,
                    onValueChange = onAmount,
                    modifier = Modifier.weight(1f),
                    prefix = { Text("₹ ") },
                    label = { Text("Amount") },
                    singleLine = true,
                    isError = draft.amountText.isBlank(),
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clickable(onClick = onPickDate)
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(Dates.label(draft.date), style = MaterialTheme.typography.labelLarge)
                }
            }
            PaymentChips(draft.paymentMode, onPayment)
            OutlinedTextField(
                value = draft.note,
                onValueChange = onNote,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

@Composable
private fun CategoryDialog(
    vm: KyeViewModel,
    categoryId: Long?,
    subcategoryId: Long?,
    onDismiss: () -> Unit,
    onPick: (Long?, Long?) -> Unit
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val subs by vm.subcategories.collectAsStateWithLifecycle()
    var cat by remember { mutableStateOf(categoryId) }
    var sub by remember { mutableStateOf(subcategoryId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CategoryGrid(categories, cat) { c ->
                    if (cat != c.id) {
                        cat = c.id
                        sub = null
                    }
                }
                if (cat != null) {
                    Text("Subcategory", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SubcategoryChips(subs.filter { it.categoryId == cat }, sub) { sub = it.id }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(cat, sub)
                onDismiss()
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    )
}
