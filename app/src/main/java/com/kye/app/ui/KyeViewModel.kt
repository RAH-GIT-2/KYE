package com.kye.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kye.app.KyeApp
import com.kye.app.data.Category
import com.kye.app.data.CategoryKind
import com.kye.app.data.KeywordRule
import com.kye.app.data.PendingStatus
import com.kye.app.data.PendingTxn
import com.kye.app.data.UserError
import com.kye.app.data.PaymentMode
import com.kye.app.data.RecurringMode
import com.kye.app.data.RecurringRule
import com.kye.app.data.RepeatUnit
import com.kye.app.data.Settings
import com.kye.app.data.Source
import com.kye.app.data.Subcategory
import com.kye.app.data.Txn
import com.kye.app.data.TxnRow
import com.kye.app.data.TxnType
import com.kye.app.domain.AnalyticsState
import com.kye.app.domain.Dates
import com.kye.app.domain.HomeState
import com.kye.app.domain.Period
import com.kye.app.domain.Recurrence
import com.kye.app.domain.Stats
import com.kye.app.voice.KeywordEntry
import com.kye.app.voice.ParsedEntry
import com.kye.app.voice.VoiceParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/** One editable item on the voice confirmation sheet. */
data class VoiceDraft(
    val key: Int,
    val amountText: String,
    val categoryId: Long?,
    val subcategoryId: Long?,
    val date: LocalDate,
    val paymentMode: PaymentMode,
    val note: String,
    val parsedCategoryId: Long?,
    val parsedSubcategoryId: Long?,
    val matchedPhrase: String?,
    val raw: String
)

class KyeViewModel(app: Application) : AndroidViewModel(app) {
    private val kyeApp = app as KyeApp
    private val container = kyeApp.container
    val repo = container.repo
    private val prefs = container.prefs

    val settings: StateFlow<Settings?> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val categories: StateFlow<List<Category>> = repo.categories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val subcategories: StateFlow<List<Subcategory>> = repo.subcategories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Everything, hidden included, for the category manager. */
    val allCategories: StateFlow<List<Category>> = repo.allCategories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allSubcategories: StateFlow<List<Subcategory>> = repo.allSubcategories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val keywordRules: StateFlow<List<KeywordRule>> = repo.keywordRules
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pending: StateFlow<List<PendingTxn>> = repo.pending
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Includes hidden categories, so old entries and stats still show their real name. */
    val categoryMap: StateFlow<Map<Long, Category>> = repo.allCategories
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val txns: StateFlow<List<TxnRow>> = repo.transactions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rules: StateFlow<List<RecurringRule>> = repo.rules
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val budgets: StateFlow<Map<Long, Long>> = repo.budgets
        .map { list -> list.associate { it.categoryId to it.monthlyLimitPaise } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val home: StateFlow<HomeState> = combine(txns, rules, budgets, categoryMap) { t, r, b, c ->
        Stats.home(t, r, b, c, LocalDate.now())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    val period = MutableStateFlow(Period.THIS_MONTH)

    val analytics: StateFlow<AnalyticsState?> = combine(txns, period, rules, budgets, categoryMap) { t, p, r, b, c ->
        Stats.analytics(t, p, r, b, c, LocalDate.now())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val monthByCategory: StateFlow<Map<Long?, Long>> = txns
        .map { Stats.monthSpendByCategory(it, LocalDate.now()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    var voiceOpen by mutableStateOf(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repo.ensureSeeded()
            kyeApp.runRecurringNow()
        }
    }

    // ---------- Settings ----------

    fun setTheme(value: String) = viewModelScope.launch { prefs.setTheme(value) }
    fun setVoiceLang(value: String) = viewModelScope.launch { prefs.setVoiceLang(value) }
    fun setVoiceEnabled(value: Boolean) = viewModelScope.launch { prefs.setVoiceEnabled(value) }
    fun markAskedNotifications() = viewModelScope.launch { prefs.setAskedNotifications() }

    // ---------- Helpers ----------

    fun subsOf(categoryId: Long?): List<Subcategory> =
        if (categoryId == null) emptyList() else subcategories.value.filter { it.categoryId == categoryId }

    fun typeFor(categoryId: Long?, subcategoryId: Long?, fallback: TxnType = TxnType.EXPENSE): TxnType {
        val sub = subcategories.value.firstOrNull { it.id == subcategoryId }
        if (sub?.isTransfer == true) return TxnType.TRANSFER
        val cat = categoryId?.let { categoryMap.value[it] }
        return when (cat?.kind) {
            CategoryKind.SAVING -> TxnType.SAVING
            CategoryKind.INCOME -> TxnType.INCOME
            CategoryKind.EXPENSE ->
                if (fallback == TxnType.SAVING || fallback == TxnType.INCOME) TxnType.EXPENSE else fallback
            null -> fallback
        }
    }

    // ---------- Transactions ----------

    suspend fun loadTxn(id: Long): Txn? = withContext(Dispatchers.IO) { repo.getTxn(id) }

    fun saveTxn(txn: Txn, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val name = txn.categoryId?.let { categoryMap.value[it]?.name }
            val msg = withContext(Dispatchers.IO) { repo.saveWithBudgetCheck(txn, name) }
            onDone(msg)
        }
    }

    fun deleteTxn(txn: Txn, onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteTxn(txn) }
            onDone()
        }
    }

    // ---------- Voice ----------

    suspend fun parseVoice(text: String): List<VoiceDraft> = withContext(Dispatchers.Default) {
        val subs = subcategories.value
        // Hidden categories and subcategories shouldn't be guessed.
        val visibleCats = categories.value.map { it.id }.toSet()
        val visibleSubs = subs.map { it.id }.toSet()
        val keywords = repo.keywords()
            .filter { it.categoryId in visibleCats && (it.subcategoryId == null || it.subcategoryId in visibleSubs) }
            .map { KeywordEntry(it.phrase, it.categoryId, it.subcategoryId, it.weight) }
        val family: (Long) -> Long? = { catId ->
            subs.firstOrNull { it.categoryId == catId && it.name.contains("Family") }?.id
        }
        VoiceParser.parse(text, keywords, family, LocalDate.now()).mapIndexed { i, p -> p.toDraft(i) }
    }

    private fun ParsedEntry.toDraft(key: Int) = VoiceDraft(
        key = key,
        amountText = amountPaise?.let { com.kye.app.domain.Money.plain(it) } ?: "",
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        date = date,
        paymentMode = paymentMode ?: PaymentMode.UPI,
        note = note,
        parsedCategoryId = categoryId,
        parsedSubcategoryId = subcategoryId,
        matchedPhrase = matchedPhrase,
        raw = raw
    )

    /** Saves confirmed voice drafts and learns from any category corrections. Returns the count saved. */
    fun saveVoice(drafts: List<VoiceDraft>, onDone: (Int, String?) -> Unit) {
        viewModelScope.launch {
            var saved = 0
            val messages = ArrayList<String>()
            withContext(Dispatchers.IO) {
                for (d in drafts) {
                    val paise = com.kye.app.domain.Money.parseToPaise(d.amountText) ?: continue
                    if (paise <= 0) continue
                    val time = if (d.date == LocalDate.now()) LocalTime.now() else LocalTime.of(12, 0)
                    val txn = Txn(
                        amountPaise = paise,
                        type = typeFor(d.categoryId, d.subcategoryId),
                        categoryId = d.categoryId,
                        subcategoryId = d.subcategoryId,
                        dateTime = Dates.atMillis(d.date, time),
                        paymentMode = d.paymentMode,
                        note = d.note,
                        source = Source.VOICE,
                        rawVoiceText = d.raw
                    )
                    val name = d.categoryId?.let { categoryMap.value[it]?.name }
                    repo.saveWithBudgetCheck(txn, name)?.let { messages += it }
                    saved++

                    val changed = d.categoryId != d.parsedCategoryId || d.subcategoryId != d.parsedSubcategoryId
                    if (changed && d.categoryId != null) {
                        val phrase = d.matchedPhrase
                            ?: d.note.split(' ').firstOrNull { it.length >= 3 }
                        if (phrase != null) repo.learn(phrase, d.categoryId, d.subcategoryId)
                    }
                }
            }
            onDone(saved, messages.distinct().joinToString("\n").ifBlank { null })
        }
    }

    // ---------- Recurring ----------

    suspend fun loadRule(id: Long): RecurringRule? = withContext(Dispatchers.IO) { repo.getRule(id) }

    fun saveRule(rule: RecurringRule, onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.saveRule(rule)
                kyeApp.runRecurringNow()
            }
            onDone()
        }
    }

    fun deleteRule(rule: RecurringRule, onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteRule(rule) }
            onDone()
        }
    }

    fun markPaid(rule: RecurringRule, amount: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.markRulePaid(rule, amount) }
    }

    fun skip(rule: RecurringRule) {
        viewModelScope.launch(Dispatchers.IO) { repo.skipRule(rule) }
    }

    /** Builds a rule whose first due date is the first occurrence on/after today (no back-filling). */
    fun newRule(
        base: RecurringRule?,
        name: String,
        amount: Long,
        categoryId: Long?,
        subcategoryId: Long?,
        payment: PaymentMode,
        unit: RepeatUnit,
        interval: Int,
        start: LocalDate,
        mode: RecurringMode,
        remindDaysBefore: Int,
        active: Boolean
    ): RecurringRule {
        val today = LocalDate.now()
        val scheduleChanged = base == null || base.unit != unit || base.interval != interval ||
            base.anchorDate != start.toEpochDay()
        val index = if (scheduleChanged) Recurrence.firstIndexOnOrAfter(start, unit, interval, today)
        else base!!.occurrenceIndex
        val next = Recurrence.occurrence(start, unit, interval, index)
        return RecurringRule(
            id = base?.id ?: 0,
            name = name,
            amountPaise = amount,
            type = typeFor(categoryId, subcategoryId),
            categoryId = categoryId,
            subcategoryId = subcategoryId,
            paymentMode = payment,
            unit = unit,
            interval = interval,
            anchorDate = start.toEpochDay(),
            occurrenceIndex = index,
            nextDueDate = next.toEpochDay(),
            mode = mode,
            remindDaysBefore = remindDaysBefore,
            isActive = active,
            lastRemindedFor = if (scheduleChanged) null else base?.lastRemindedFor
        )
    }

    /** "Make this recurring" from the add screen: this entry is occurrence 0, the rule starts at the next one. */
    fun ruleFromTxn(txn: Txn, name: String, unit: RepeatUnit, interval: Int) {
        val start = Dates.toLocalDate(txn.dateTime)
        val next = Recurrence.occurrence(start, unit, interval, 1)
        val rule = RecurringRule(
            name = name,
            amountPaise = txn.amountPaise,
            type = txn.type,
            categoryId = txn.categoryId,
            subcategoryId = txn.subcategoryId,
            paymentMode = txn.paymentMode,
            unit = unit,
            interval = interval,
            anchorDate = start.toEpochDay(),
            occurrenceIndex = 1,
            nextDueDate = next.toEpochDay(),
            mode = RecurringMode.AUTO_ADD
        )
        viewModelScope.launch(Dispatchers.IO) { repo.saveRule(rule) }
    }

    // ---------- Budgets & export ----------

    fun setBudget(categoryId: Long, limit: Long?) {
        viewModelScope.launch(Dispatchers.IO) { repo.setBudget(categoryId, limit) }
    }

    fun exportCsv(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { repo.exportCsv(it) }
                        ?: error("no stream")
                }.isSuccess
            }
            onDone(ok)
        }
    }

    // ---------- Backup & restore ----------

    fun exportBackup(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { repo.exportBackup(it) }
                        ?: error("no stream")
                }.isSuccess
            }
            onDone(if (ok) "Backup saved" else "Backup failed")
        }
    }

    fun importBackup(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val data = getApplication<Application>().contentResolver.openInputStream(uri)?.use { repo.importBackup(it) }
                        ?: error("no stream")
                    kyeApp.runRecurringNow()
                    "Restored ${data.txns.size} entries and ${data.categories.size} categories"
                } catch (e: UserError) {
                    e.message ?: "Restore failed"
                } catch (e: Exception) {
                    "Restore failed"
                }
            }
            onDone(msg)
        }
    }

    // ---------- Category manager ----------

    /** Runs a repository change; reports a [UserError] message (or a generic one) through [onError]. */
    private fun change(onError: (String) -> Unit, onDone: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                try {
                    block()
                    null
                } catch (e: UserError) {
                    e.message
                } catch (e: Exception) {
                    "Something went wrong"
                }
            }
            if (err != null) onError(err) else onDone()
        }
    }

    fun addCategory(name: String, emoji: String, color: Long, kind: CategoryKind, onError: (String) -> Unit, onDone: () -> Unit) =
        change(onError, onDone) { repo.addCategory(name, emoji, color, kind) }

    fun updateCategory(id: Long, name: String, emoji: String, color: Long, onError: (String) -> Unit, onDone: () -> Unit) =
        change(onError, onDone) { repo.updateCategory(id, name, emoji, color) }

    fun setCategoryHidden(id: Long, hidden: Boolean, onError: (String) -> Unit) =
        change(onError) { repo.setCategoryHidden(id, hidden) }

    fun moveCategory(id: Long, up: Boolean, onError: (String) -> Unit) = change(onError) {
        val ids = allCategories.value.filter { !it.isArchived }.map { it.id }.toMutableList()
        val i = ids.indexOf(id)
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in ids.indices) return@change
        ids[i] = ids[j].also { ids[j] = ids[i] }
        // Hidden ones keep their place after the visible ones.
        repo.reorderCategories(ids + allCategories.value.filter { it.isArchived }.map { it.id })
    }

    fun deleteCategory(id: Long, onError: (String) -> Unit, onDone: () -> Unit) =
        change(onError, onDone) { repo.deleteCategory(id) }

    fun mergeCategory(fromId: Long, toId: Long, onError: (String) -> Unit, onDone: () -> Unit) =
        change(onError, onDone) { repo.mergeCategory(fromId, toId) }

    suspend fun categoryUsage(id: Long): Int = withContext(Dispatchers.IO) { repo.categoryUsage(id) }
    suspend fun subUsage(id: Long): Int = withContext(Dispatchers.IO) { repo.subUsage(id) }

    fun addSubcategory(categoryId: Long, name: String, onError: (String) -> Unit, onDone: () -> Unit) =
        change(onError, onDone) { repo.addSubcategory(categoryId, name) }

    fun renameSubcategory(id: Long, name: String, onError: (String) -> Unit, onDone: () -> Unit) =
        change(onError, onDone) { repo.renameSubcategory(id, name) }

    fun setSubcategoryHidden(id: Long, hidden: Boolean, onError: (String) -> Unit) =
        change(onError) { repo.setSubcategoryHidden(id, hidden) }

    fun deleteSubcategory(id: Long, onError: (String) -> Unit, onDone: () -> Unit) =
        change(onError, onDone) { repo.deleteSubcategory(id) }

    fun mergeSubcategory(fromId: Long, toSubId: Long, onError: (String) -> Unit, onDone: () -> Unit) =
        change(onError, onDone) { repo.mergeSubcategory(fromId, toSubId) }

    fun addKeyword(phrase: String, categoryId: Long, subcategoryId: Long?, onError: (String) -> Unit, onDone: () -> Unit) =
        change(onError, onDone) { repo.addKeyword(phrase, categoryId, subcategoryId) }

    fun deleteKeyword(id: Long, onError: (String) -> Unit) = change(onError) { repo.deleteKeyword(id) }

    // ---------- Detected payments ----------

    /** Best category guess for a merchant name, using the same keywords as voice entry. */
    suspend fun guessCategory(text: String): Pair<Long?, Long?> {
        val d = parseVoice(text).firstOrNull() ?: return null to null
        return d.categoryId to d.subcategoryId
    }

    fun resolvePending(id: Long, status: PendingStatus) {
        viewModelScope.launch(Dispatchers.IO) { repo.setPendingStatus(id, status) }
    }
}
