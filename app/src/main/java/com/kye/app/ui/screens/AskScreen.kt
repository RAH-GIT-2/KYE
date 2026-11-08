@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kye.app.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.domain.AskAnswer
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.ChoiceChip
import com.kye.app.ui.components.TxnItem
import kotlinx.coroutines.launch

private val EXAMPLES = listOf(
    "Swiggy this month",
    "Petrol last 3 months",
    "What did I spend yesterday",
    "Biggest expense this month",
    "Food last month",
    "Income this month",
    "Cash spending this week",
    "Who owes me",
    "nethu evlo selavu"
)

@Composable
fun AskScreen(vm: KyeViewModel, startWithMic: Boolean, onOpenTxn: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by remember { mutableStateOf<AskAnswer?>(null) }

    fun ask(q: String) {
        val text = q.trim()
        if (text.isEmpty()) return
        question = text
        keyboard?.hide()
        scope.launch { answer = vm.ask(text) }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { ask(it) }
        }
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, settings?.voiceLang ?: "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask about your money")
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Voice input isn't available on this phone. Type your question instead.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (startWithMic) listen() else runCatching { focus.requestFocus() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask KYE") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "input") {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("How much on Swiggy this month?") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { ask(question) }),
                    trailingIcon = {
                        if (question.isBlank()) {
                            IconButton(onClick = { listen() }) { Icon(Icons.Rounded.Mic, contentDescription = "Ask by voice") }
                        } else {
                            IconButton(onClick = { ask(question) }) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Ask") }
                        }
                    }
                )
            }

            val a = answer
            if (a == null) {
                item(key = "examples") {
                    Column {
                        Text("Try", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EXAMPLES.forEach { e -> ChoiceChip(e, false, { ask(e) }) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Ask about any category, shop, word in your notes, payment mode (cash, UPI, card) or period " +
                                "(today, yesterday, this week, last month, in October, last 3 months, this year). " +
                                "Tanglish works too: \"intha maasam swiggy evlo\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item(key = "answer") {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text(a.headline, style = MaterialTheme.typography.titleLarge)
                            a.details.forEach {
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (a.understood.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Looked at: ${a.understood}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (a.rows.isNotEmpty()) {
                    item(key = "rows-title") {
                        Text("Entries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    items(a.rows, key = { it.txn.id }) { row -> TxnItem(row) { onOpenTxn(row.txn.id) } }
                }
                item(key = "more") {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EXAMPLES.take(5).forEach { e -> ChoiceChip(e, false, { ask(e) }) }
                        }
                    }
                }
            }
        }
    }
}
