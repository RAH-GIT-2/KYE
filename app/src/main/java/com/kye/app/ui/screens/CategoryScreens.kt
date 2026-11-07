@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kye.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kye.app.data.Category
import com.kye.app.data.CategoryKind
import com.kye.app.data.Subcategory
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.components.CategoryAvatar
import com.kye.app.ui.components.ChoiceChip
import kotlinx.coroutines.launch

/** A fixed palette keeps the donut chart readable in both themes. */
private val PALETTE = listOf(
    0xFFFF7A45, 0xFF2ECC71, 0xFF74B9FF, 0xFFFDCB6E, 0xFF00CEC9, 0xFFA29BFE,
    0xFFE84393, 0xFFF39C12, 0xFF6C5CE7, 0xFFFF6B81, 0xFFD63031, 0xFF0984E3,
    0xFF00B894, 0xFF636E72, 0xFFFFA502, 0xFF8854D0
)

private val EMOJIS = listOf(
    "🏷️", "🍛", "☕", "🛒", "🥦", "🍗", "🚗", "🛺", "⛽", "✈️", "🏠", "💡", "📱", "🎬", "🎮", "📚",
    "🎓", "💊", "🏥", "💇", "🏋️", "🐶", "👶", "🧸", "🎁", "🎉", "🛍️", "👗", "💻", "🔧", "🙏", "🏦",
    "💳", "💰", "📈", "💼", "🧑‍💻", "↩️", "🎀", "🪙", "🌾", "🚌"
)

private fun kindLabel(k: CategoryKind) = when (k) {
    CategoryKind.EXPENSE -> "Expense"
    CategoryKind.INCOME -> "Income"
    CategoryKind.SAVING -> "Saving"
}

// ---------------------------------------------------------------------------------------------
// List of categories
// ---------------------------------------------------------------------------------------------

@Composable
fun CategoriesScreen(vm: KyeViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val all by vm.allCategories.collectAsStateWithLifecycle()
    val subs by vm.allSubcategories.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(CategoryKind.EXPENSE) }
    var creating by remember { mutableStateOf(false) }
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    val visible = all.filter { it.kind == kind && !it.isArchived }.sortedBy { it.sortOrder }
    val hidden = all.filter { it.kind == kind && it.isArchived }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New category") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "kinds") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryKind.entries.forEach { k -> ChoiceChip(kindLabel(k), kind == k, { kind = k }) }
                }
            }
            item(key = "hint") {
                Text(
                    "Tap a category to add subcategories and voice keywords. Arrows change the order in pickers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(visible, key = { it.id }) { c ->
                CategoryRow(
                    c,
                    subCount = subs.count { it.categoryId == c.id && !it.isDefault && !it.isArchived },
                    canMoveUp = visible.first().id != c.id,
                    canMoveDown = visible.last().id != c.id,
                    onMove = { up -> vm.moveCategory(c.id, up, toast) },
                    onClick = { onOpen(c.id) }
                )
            }
            if (hidden.isNotEmpty()) {
                item(key = "hidden-title") {
                    Text(
                        "Hidden · not shown when adding entries",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(hidden, key = { it.id }) { c ->
                    CategoryRow(c, subCount = null, canMoveUp = false, canMoveDown = false, onMove = {}, onClick = { onOpen(c.id) })
                }
            }
        }
    }

    if (creating) {
        CategoryEditDialog(
            title = "New ${kindLabel(kind).lowercase()} category",
            initial = null,
            onDismiss = { creating = false },
            onSave = { name, emoji, color ->
                vm.addCategory(name, emoji, color, kind, toast) { creating = false }
            }
        )
    }
}

@Composable
private fun CategoryRow(
    c: Category,
    subCount: Int?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryAvatar(c.emoji, c.color, 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subCount != null) {
                    Text(
                        if (subCount == 0) "No subcategories" else "$subCount subcategor${if (subCount == 1) "y" else "ies"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (subCount != null) {
                IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
                }
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                    modifier = Modifier.padding(end = 10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// One category: subcategories, keywords, hide / merge / delete
// ---------------------------------------------------------------------------------------------

private sealed interface Dialog {
    data object EditCategory : Dialog
    data object AddSub : Dialog
    data object MergeCategory : Dialog
    data class DeleteCategory(val usage: Int) : Dialog
    data class RenameSub(val sub: Subcategory) : Dialog
    data class MergeSub(val sub: Subcategory) : Dialog
    data class DeleteSub(val sub: Subcategory, val usage: Int) : Dialog
    data object AddKeyword : Dialog
}

@Composable
fun CategoryDetailScreen(vm: KyeViewModel, categoryId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by vm.allCategories.collectAsStateWithLifecycle()
    val allSubs by vm.allSubcategories.collectAsStateWithLifecycle()
    val keywords by vm.keywordRules.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var menu by remember { mutableStateOf(false) }
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    val c = all.firstOrNull { it.id == categoryId }
    if (c == null) {
        // Deleted or merged away.
        Box(Modifier.fillMaxSize())
        return
    }
    val subs = allSubs.filter { it.categoryId == c.id }
    val catKeywords = keywords.filter { it.categoryId == c.id }
    val subNames = subs.associate { it.id to it.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit name, icon, colour") }, onClick = {
                            menu = false; dialog = Dialog.EditCategory
                        })
                        DropdownMenuItem(text = { Text(if (c.isArchived) "Unhide" else "Hide") }, onClick = {
                            menu = false
                            vm.setCategoryHidden(c.id, !c.isArchived, toast)
                            toast(if (c.isArchived) "${c.name} is visible again" else "${c.name} hidden. Old entries keep it.")
                        })
                        DropdownMenuItem(text = { Text("Merge into another…") }, onClick = {
                            menu = false; dialog = Dialog.MergeCategory
                        })
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = {
                            menu = false
                            scope.launch { dialog = Dialog.DeleteCategory(vm.categoryUsage(c.id)) }
                        })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(c.emoji, c.color, 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(c.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            kindLabel(c.kind) + if (c.isArchived) " · hidden" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "subs") {
                ManagerCard("Subcategories", action = "Add", onAction = { dialog = Dialog.AddSub }) {
                    subs.sortedWith(compareBy<Subcategory> { it.isDefault }.thenBy { it.isArchived }).forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        SubRow(
                            s,
                            onRename = { dialog = Dialog.RenameSub(s) },
                            onToggleHidden = { vm.setSubcategoryHidden(s.id, !s.isArchived, toast) },
                            onMerge = { dialog = Dialog.MergeSub(s) },
                            onDelete = { scope.launch { dialog = Dialog.DeleteSub(s, vm.subUsage(s.id)) } }
                        )
                    }
                }
            }

            item(key = "keywords") {
                ManagerCard("Voice keywords", action = "Add", onAction = { dialog = Dialog.AddKeyword }) {
                    Text(
                        "Saying one of these picks this category. Words you correct on the voice screen are learned here too.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    if (catKeywords.isEmpty()) {
                        Text("No keywords yet", style = MaterialTheme.typography.bodyMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        catKeywords.forEach { k ->
                            val target = k.subcategoryId?.let { subNames[it] }
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = {
                                    Text(
                                        if (target != null) "${k.phrase} → $target" else k.phrase,
                                        maxLines = 1
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Close, contentDescription = "Remove ${k.phrase}",
                                        modifier = Modifier.size(18.dp).clickable { vm.deleteKeyword(k.id, toast) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        Dialog.EditCategory -> CategoryEditDialog(
            title = "Edit category",
            initial = c,
            onDismiss = { dialog = null },
            onSave = { name, emoji, color -> vm.updateCategory(c.id, name, emoji, color, toast) { dialog = null } }
        )
        Dialog.AddSub -> NameDialog("New subcategory in ${c.name}", "", onDismiss = { dialog = null }) { name ->
            vm.addSubcategory(c.id, name, toast) { dialog = null }
        }
        is Dialog.RenameSub -> NameDialog("Rename subcategory", d.sub.name, onDismiss = { dialog = null }) { name ->
            vm.renameSubcategory(d.sub.id, name, toast) { dialog = null }
        }
        Dialog.MergeCategory -> PickDialog(
            title = "Merge ${c.name} into…",
            note = "All entries, subcategories, recurring bills, budget and keywords move over. ${c.name} is then deleted.",
            options = all.filter { it.kind == c.kind && it.id != c.id }.map { it.id to "${it.emoji}  ${it.name}" },
            onDismiss = { dialog = null }
        ) { target ->
            vm.mergeCategory(c.id, target, toast) {
                dialog = null
                toast("Merged")
                onBack()
            }
        }
        is Dialog.MergeSub -> PickDialog(
            title = "Merge ${d.sub.name} into…",
            note = "Its entries and keywords move over, then ${d.sub.name} is deleted.",
            options = subs.filter { it.id != d.sub.id }.map { it.id to it.name },
            onDismiss = { dialog = null }
        ) { target ->
            vm.mergeSubcategory(d.sub.id, target, toast) { dialog = null }
        }
        is Dialog.DeleteCategory -> if (d.usage == 0) {
            ConfirmDialog(
                "Delete ${c.name}?",
                "It isn't used by any entry or recurring bill. Its subcategories and keywords are deleted too.",
                "Delete",
                onDismiss = { dialog = null }
            ) {
                vm.deleteCategory(c.id, toast) {
                    dialog = null
                    onBack()
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("${c.name} is in use") },
                text = {
                    Text(
                        "${d.usage} entr${if (d.usage == 1) "y or bill uses" else "ies and bills use"} it. Deleting would " +
                            "leave them uncategorised.\n\nHide it to stop seeing it when adding entries, or merge it into another category."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { dialog = Dialog.MergeCategory }) { Text("Merge…") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { dialog = null }) { Text("Cancel") }
                        if (!c.isArchived) {
                            TextButton(onClick = {
                                vm.setCategoryHidden(c.id, true, toast)
                                dialog = null
                            }) { Text("Hide") }
                        }
                    }
                }
            )
        }
        is Dialog.DeleteSub -> ConfirmDialog(
            "Delete ${d.sub.name}?",
            if (d.usage == 0) "Nothing uses it."
            else "${d.usage} entr${if (d.usage == 1) "y moves" else "ies move"} to \"Others\" in ${c.name}.",
            "Delete",
            onDismiss = { dialog = null }
        ) {
            vm.deleteSubcategory(d.sub.id, toast) { dialog = null }
        }
        Dialog.AddKeyword -> KeywordDialog(
            categoryName = c.name,
            subs = subs.filter { !it.isArchived },
            onDismiss = { dialog = null }
        ) { phrase, subId ->
            vm.addKeyword(phrase, c.id, subId, toast) { dialog = null }
        }
    }
}

@Composable
private fun ManagerCard(title: String, action: String, onAction: () -> Unit, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onAction) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(action)
                }
            }
            Column(Modifier.padding(end = 8.dp)) { content() }
        }
    }
}

@Composable
private fun SubRow(
    s: Subcategory,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                s.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (s.isArchived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            val tag = when {
                s.isDefault -> "Catch-all · always here"
                s.isArchived -> "Hidden"
                s.isTransfer -> "Counts as a transfer"
                else -> null
            }
            if (tag != null) {
                Text(tag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!s.isDefault) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${s.name}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(if (s.isArchived) "Unhide" else "Hide") }, onClick = { menu = false; onToggleHidden() })
                    DropdownMenuItem(text = { Text("Merge into…") }, onClick = { menu = false; onMerge() })
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

@Composable
private fun CategoryEditDialog(
    title: String,
    initial: Category?,
    onDismiss: () -> Unit,
    onSave: (String, String, Long) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: EMOJIS.first()) }
    var color by remember { mutableStateOf(initial?.color ?: PALETTE.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryAvatar(emoji, color, 48.dp)
                            Spacer(Modifier.width(12.dp))
                            OutlinedTextField(
                                value = name, onValueChange = { name = it.take(40) },
                                label = { Text("Name") }, singleLine = true
                            )
                        }
                    }
                    item { Text("Icon", style = MaterialTheme.typography.labelLarge) }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            EMOJIS.forEach { e ->
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (e == emoji) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable { emoji = e },
                                    contentAlignment = Alignment.Center
                                ) { Text(e, fontSize = 20.sp) }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = emoji,
                            onValueChange = { v -> if (v.isNotBlank()) emoji = v.trim().take(8) },
                            label = { Text("Or type any emoji") },
                            singleLine = true
                        )
                    }
                    item { Text("Colour", style = MaterialTheme.typography.labelLarge) }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PALETTE.forEach { p ->
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(p))
                                        .then(
                                            if (p == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            else Modifier
                                        )
                                        .clickable { color = p },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (p == color) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, emoji, color) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PickDialog(
    title: String,
    note: String,
    options: List<Pair<Long, String>>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    var picked by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(note, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                if (options.isEmpty()) Text("Nothing to merge into yet.")
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(options, key = { it.first }) { (id, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (picked == id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { picked = id }
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) { Text(label) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picked?.let(onPick) }, enabled = picked != null) { Text("Merge") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmDialog(title: String, body: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun KeywordDialog(
    categoryName: String,
    subs: List<Subcategory>,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit
) {
    var phrase by remember { mutableStateOf("") }
    var subId by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New voice keyword") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = phrase, onValueChange = { phrase = it.take(40) },
                    label = { Text("Word or phrase") },
                    placeholder = { Text("e.g. gym, ஜிம், cult fit") },
                    singleLine = true
                )
                Text("Goes to", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("$categoryName (any)", subId == null, { subId = null })
                    subs.filter { !it.isDefault }.forEach { s -> ChoiceChip(s.name, subId == s.id, { subId = s.id }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(phrase, subId) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
