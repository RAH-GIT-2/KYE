package com.kye.app.data

import androidx.room.withTransaction
import com.kye.app.domain.CsvImport
import com.kye.app.domain.Dates
import com.kye.app.domain.Events
import com.kye.app.domain.GoldSchemes
import com.kye.app.domain.MonthlyReport
import com.kye.app.domain.Reports
import com.kye.app.domain.Subscriptions
import com.kye.app.domain.Loans
import com.kye.app.domain.Money
import com.kye.app.domain.Recurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.kye.app.domain.Warranties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** A user-facing problem (duplicate name, category still in use…). The message is shown as-is. */
class UserError(message: String) : Exception(message)

/**
 * @param receiptsDir where receipt photos live (files/receipts). Null in tests that don't use photos.
 */
class Repository(private val db: KyeDatabase, private val prefs: Prefs, private val receiptsDir: File? = null) {
    private val categoryDao = db.categoryDao()
    private val txnDao = db.txnDao()
    private val recurringDao = db.recurringDao()
    private val budgetDao = db.budgetDao()
    private val keywordDao = db.keywordDao()
    private val backupDao = db.backupDao()
    private val recurringLock = Mutex()
    private val seedLock = Mutex()

    val allCategories: Flow<List<Category>> = categoryDao.observeEverything()
    val allSubcategories: Flow<List<Subcategory>> = categoryDao.observeAllSubs()
    val keywordRules: Flow<List<KeywordRule>> = keywordDao.observeAll()
    val rules: Flow<List<RecurringRule>> = recurringDao.observeAll()
    val budgets: Flow<List<Budget>> = budgetDao.observeAll()
    val transactions: Flow<List<TxnRow>> = txnDao.observeAll()

    @Volatile
    private var keywordCache: List<KeywordRule>? = null

    suspend fun keywords(): List<KeywordRule> =
        keywordCache ?: keywordDao.all().also { keywordCache = it }

    // ---------- Seeding ----------

    /**
     * Fresh install: seeds everything. Existing install: adds only built-in categories newer than
     * what this phone already received, skipping any whose name the user already has.
     */
    suspend fun ensureSeeded() = seedLock.withLock {
        if (categoryDao.count() == 0) {
            insertSeed(Seed.categories)
        } else {
            // Installs from before seed versioning got the v1 set.
            val have = prefs.seedVersion().takeIf { it > 0 } ?: 1
            if (have < Seed.VERSION) {
                val existing = categoryDao.allCategories().map { it.name.lowercase() }.toSet()
                insertSeed(Seed.categories.filter { it.since > have && it.name.lowercase() !in existing })
            }
        }
        prefs.setSeedVersion(Seed.VERSION)
    }

    private suspend fun insertSeed(list: List<SeedCategory>) {
        if (list.isEmpty()) return
        db.withTransaction {
            var order = categoryDao.maxSortOrder() + 1
            list.forEach { sc ->
                val catId = categoryDao.insert(
                    Category(name = sc.name, emoji = sc.emoji, color = sc.color, kind = sc.kind, sortOrder = order++)
                )
                val rules = ArrayList<KeywordRule>()
                sc.keywords.forEach { rules += KeywordRule(phrase = it, categoryId = catId, subcategoryId = null, weight = 1) }
                sc.subs.forEach { ss ->
                    val subId = categoryDao.insertSub(
                        Subcategory(categoryId = catId, name = ss.name, isTransfer = ss.transfer)
                    )
                    ss.keywords.forEach {
                        rules += KeywordRule(
                            phrase = it, categoryId = catId, subcategoryId = subId,
                            weight = if (ss.merchant) 3 else 2
                        )
                    }
                }
                categoryDao.insertSub(Subcategory(categoryId = catId, name = OTHERS, isDefault = true))
                keywordDao.insertAll(rules)
            }
        }
        keywordCache = null
    }

    // ---------- Category manager ----------

    private suspend fun requireUniqueCategory(name: String, exceptId: Long = 0) {
        val clash = categoryDao.allCategories().firstOrNull { it.id != exceptId && it.name.equals(name, ignoreCase = true) }
        if (clash != null) {
            throw UserError(
                if (clash.isArchived) "\"${clash.name}\" already exists but is hidden. Unhide it instead."
                else "\"${clash.name}\" already exists"
            )
        }
    }

    private suspend fun requireUniqueSub(categoryId: Long, name: String, exceptId: Long = 0) {
        if (name.equals(OTHERS, ignoreCase = true)) throw UserError("Every category already has \"$OTHERS\"")
        val clash = categoryDao.subsOf(categoryId).firstOrNull { it.id != exceptId && it.name.equals(name, ignoreCase = true) }
        if (clash != null) {
            throw UserError(
                if (clash.isArchived) "\"${clash.name}\" already exists here but is hidden. Unhide it instead."
                else "\"${clash.name}\" already exists in this category"
            )
        }
    }

    private fun cleanName(name: String): String {
        val n = name.trim().replace(Regex("\\s+"), " ")
        if (n.isEmpty()) throw UserError("Enter a name")
        if (n.length > 40) throw UserError("Keep the name under 40 characters")
        return n
    }

    /** Creates a category with its "Others" subcategory, and teaches voice entry its name. */
    suspend fun addCategory(name: String, emoji: String, color: Long, kind: CategoryKind): Long {
        val n = cleanName(name)
        requireUniqueCategory(n)
        val id = db.withTransaction {
            val catId = categoryDao.insert(
                Category(name = n, emoji = emoji.ifBlank { "🏷️" }, color = color, kind = kind, sortOrder = categoryDao.maxSortOrder() + 1)
            )
            categoryDao.insertSub(Subcategory(categoryId = catId, name = OTHERS, isDefault = true))
            keywordDao.insert(KeywordRule(phrase = n.lowercase(), categoryId = catId, subcategoryId = null, weight = USER_WEIGHT))
            catId
        }
        keywordCache = null
        return id
    }

    /** Kind can't change: existing entries were saved as that type. */
    suspend fun updateCategory(id: Long, name: String, emoji: String, color: Long) {
        val old = categoryDao.get(id) ?: return
        val n = cleanName(name)
        requireUniqueCategory(n, exceptId = id)
        db.withTransaction {
            categoryDao.update(old.copy(name = n, emoji = emoji.ifBlank { old.emoji }, color = color))
            if (!old.name.equals(n, ignoreCase = true)) {
                keywordDao.deleteExact(old.name.lowercase(), id, null)
                keywordDao.insert(KeywordRule(phrase = n.lowercase(), categoryId = id, subcategoryId = null, weight = USER_WEIGHT))
            }
        }
        keywordCache = null
    }

    suspend fun setCategoryHidden(id: Long, hidden: Boolean) {
        val c = categoryDao.get(id) ?: return
        categoryDao.update(c.copy(isArchived = hidden))
    }

    suspend fun reorderCategories(orderedIds: List<Long>) {
        val byId = categoryDao.allCategories().associateBy { it.id }
        categoryDao.updateAll(orderedIds.mapIndexedNotNull { i, id -> byId[id]?.copy(sortOrder = i) })
    }

    /** Real delete, only allowed when nothing uses the category. Otherwise hide or merge. */
    suspend fun deleteCategory(id: Long) {
        if (categoryDao.categoryUsage(id) > 0) throw UserError("This category has entries. Hide it or merge it instead.")
        db.withTransaction {
            keywordDao.deleteForCategory(id)
            budgetDao.delete(id)
            categoryDao.deleteCategory(id) // subcategories cascade
        }
        keywordCache = null
    }

    suspend fun categoryUsage(id: Long): Int = categoryDao.categoryUsage(id)

    private suspend fun defaultSubOf(categoryId: Long): Long =
        categoryDao.subsOf(categoryId).firstOrNull { it.isDefault }?.id
            ?: categoryDao.insertSub(Subcategory(categoryId = categoryId, name = OTHERS, isDefault = true))

    /**
     * Moves everything in [fromId] into [toId], then deletes [fromId]. Subcategories come along;
     * one with the same name as a target subcategory is folded into it. Budgets, recurring rules
     * and voice keywords follow the entries.
     */
    suspend fun mergeCategory(fromId: Long, toId: Long) {
        if (fromId == toId) return
        val from = categoryDao.get(fromId) ?: return
        val to = categoryDao.get(toId) ?: return
        if (from.kind != to.kind) throw UserError("Can only merge into a category of the same type")
        db.withTransaction {
            val toDefault = defaultSubOf(toId)
            val toSubs = categoryDao.subsOf(toId)
            for (s in categoryDao.subsOf(fromId)) {
                val target = if (s.isDefault) toDefault
                else toSubs.firstOrNull { !it.isDefault && it.name.equals(s.name, ignoreCase = true) }?.id
                if (target != null) {
                    categoryDao.moveSubTxns(s.id, toId, target)
                    categoryDao.moveSubRules(s.id, toId, target)
                    categoryDao.moveSubKeywords(s.id, toId, target)
                    categoryDao.deleteSub(s.id)
                } else {
                    categoryDao.reparentSub(s.id, toId)
                    categoryDao.retagSubTxns(s.id, toId)
                    categoryDao.retagSubRules(s.id, toId)
                    categoryDao.retagSubKeywords(s.id, toId)
                }
            }
            categoryDao.moveCatTxns(fromId, toId, toDefault)
            categoryDao.moveCatRules(fromId, toId, toDefault)
            categoryDao.moveCatKeywords(fromId, toId)
            val budgets = budgetDao.all().associate { it.categoryId to it.monthlyLimitPaise }
            val fromBudget = budgets[fromId]
            if (fromBudget != null && budgets[toId] == null) budgetDao.upsert(Budget(toId, fromBudget))
            budgetDao.delete(fromId)
            categoryDao.deleteCategory(fromId)
        }
        keywordCache = null
    }

    suspend fun addSubcategory(categoryId: Long, name: String): Long {
        val n = cleanName(name)
        requireUniqueSub(categoryId, n)
        val id = db.withTransaction {
            val subId = categoryDao.insertSub(Subcategory(categoryId = categoryId, name = n))
            keywordDao.insert(KeywordRule(phrase = n.lowercase(), categoryId = categoryId, subcategoryId = subId, weight = USER_WEIGHT))
            subId
        }
        keywordCache = null
        return id
    }

    suspend fun renameSubcategory(id: Long, name: String) {
        val old = categoryDao.getSub(id) ?: return
        if (old.isDefault) throw UserError("\"$OTHERS\" can't be renamed")
        val n = cleanName(name)
        requireUniqueSub(old.categoryId, n, exceptId = id)
        db.withTransaction {
            categoryDao.updateSub(old.copy(name = n))
            if (!old.name.equals(n, ignoreCase = true)) {
                keywordDao.deleteExact(old.name.lowercase(), old.categoryId, id)
                keywordDao.insert(KeywordRule(phrase = n.lowercase(), categoryId = old.categoryId, subcategoryId = id, weight = USER_WEIGHT))
            }
        }
        keywordCache = null
    }

    suspend fun setSubcategoryHidden(id: Long, hidden: Boolean) {
        val s = categoryDao.getSub(id) ?: return
        if (s.isDefault) throw UserError("\"$OTHERS\" can't be hidden")
        categoryDao.updateSub(s.copy(isArchived = hidden))
    }

    suspend fun subUsage(id: Long): Int = categoryDao.subUsage(id)

    /** Moves the subcategory's entries into [toSubId] (any category of the same type) and deletes it. */
    suspend fun mergeSubcategory(fromId: Long, toSubId: Long) {
        if (fromId == toSubId) return
        val from = categoryDao.getSub(fromId) ?: return
        val to = categoryDao.getSub(toSubId) ?: return
        if (from.isDefault) throw UserError("\"$OTHERS\" can't be merged away")
        val fromKind = categoryDao.get(from.categoryId)?.kind
        val toKind = categoryDao.get(to.categoryId)?.kind
        if (fromKind != toKind) throw UserError("Can only merge into a category of the same type")
        db.withTransaction {
            categoryDao.moveSubTxns(fromId, to.categoryId, toSubId)
            categoryDao.moveSubRules(fromId, to.categoryId, toSubId)
            categoryDao.moveSubKeywords(fromId, to.categoryId, toSubId)
            categoryDao.deleteSub(fromId)
        }
        keywordCache = null
    }

    /** Deletes a subcategory; its entries move to the category's "Others". */
    suspend fun deleteSubcategory(id: Long) {
        val s = categoryDao.getSub(id) ?: return
        if (s.isDefault) throw UserError("\"$OTHERS\" can't be deleted")
        mergeSubcategory(id, defaultSubOf(s.categoryId))
    }

    // ---------- Voice keywords ----------

    suspend fun addKeyword(phrase: String, categoryId: Long, subcategoryId: Long?) {
        val p = phrase.trim().lowercase().replace(Regex("\\s+"), " ")
        if (p.length < 2) throw UserError("Keyword is too short")
        keywordDao.deleteExact(p, categoryId, subcategoryId)
        keywordDao.insert(KeywordRule(phrase = p, categoryId = categoryId, subcategoryId = subcategoryId, weight = USER_WEIGHT))
        keywordCache = null
    }

    suspend fun deleteKeyword(id: Long) {
        keywordDao.delete(id)
        keywordCache = null
    }

    // ---------- Transactions ----------

    suspend fun getTxn(id: Long): Txn? = txnDao.get(id)

    suspend fun saveTxn(txn: Txn): Long =
        if (txn.id == 0L) txnDao.insert(txn)
        else {
            txnDao.update(txn.copy(updatedAt = System.currentTimeMillis()))
            txn.id
        }

    /** Deletes the entry, its photos (rows cascade; files removed here) and its warranty. */
    suspend fun deleteTxn(txn: Txn) {
        val files = db.extrasDao().attachmentsOf(txn.id).map { it.fileName }
        txnDao.delete(txn)
        files.forEach { receiptFile(it)?.delete() }
    }

    private fun receiptFile(name: String): File? = receiptsDir?.let { File(it, name) }

    /** Saves several edited entries together (History: change category, payment or event). */
    suspend fun updateTxns(txns: List<Txn>) {
        val now = System.currentTimeMillis()
        db.withTransaction { txns.forEach { txnDao.update(it.copy(updatedAt = now)) } }
    }

    /** Deletes several entries with their photos and warranties. */
    suspend fun deleteTxns(txns: List<Txn>) {
        val files = txns.flatMap { t -> db.extrasDao().attachmentsOf(t.id).map { it.fileName } }
        db.withTransaction { txns.forEach { txnDao.delete(it) } }
        files.forEach { receiptFile(it)?.delete() }
    }

    /** Saves and returns a budget alert message if this entry crossed 80% or 100% of a budget. */
    suspend fun saveWithBudgetCheck(txn: Txn, categoryName: String?): String? = saveChecked(txn, categoryName).second

    /** Like [saveWithBudgetCheck], also returning the entry's id (for Undo). */
    suspend fun saveChecked(txn: Txn, categoryName: String?): Pair<Long, String?> {
        if (txn.type != TxnType.EXPENSE) return saveTxn(txn) to null
        val day = Dates.toLocalDate(txn.dateTime)
        val from = Dates.startOfDay(day.withDayOfMonth(1))
        val to = Dates.endOfDay(day.withDayOfMonth(day.lengthOfMonth()))
        val limits = budgetDao.all().associate { it.categoryId to it.monthlyLimitPaise }
        val catLimit = txn.categoryId?.let { limits[it] }
        val overallLimit = limits[0L]

        val beforeCat = if (catLimit != null) txnDao.sumExpense(from, to, txn.categoryId) else 0L
        val beforeAll = if (overallLimit != null) txnDao.sumExpense(from, to, null) else 0L
        val event = txn.eventId?.let { db.eventDao().get(it) }
        val eventBefore = event?.takeIf { it.budgetPaise != null }?.let { db.eventDao().spent(it.id) }
        val id = saveTxn(txn)
        val afterCat = if (catLimit != null) txnDao.sumExpense(from, to, txn.categoryId) else 0L
        val afterAll = if (overallLimit != null) txnDao.sumExpense(from, to, null) else 0L

        val messages = ArrayList<String>()
        if (catLimit != null && catLimit > 0) {
            crossed(beforeCat, afterCat, catLimit)?.let {
                messages += if (it == 100) "${categoryName ?: "Category"} budget exceeded"
                else "${categoryName ?: "Category"} budget 80% used"
            }
        }
        if (overallLimit != null && overallLimit > 0) {
            crossed(beforeAll, afterAll, overallLimit)?.let {
                messages += if (it == 100) "Monthly budget exceeded" else "Monthly budget 80% used"
            }
        }
        // Event budget, if the entry is tagged to one.
        if (eventBefore != null && event != null) {
            Events.alert(event.name, eventBefore, db.eventDao().spent(event.id), event.budgetPaise)?.let { messages += it }
        }
        return id to messages.joinToString("\n").ifBlank { null }
    }

    private fun crossed(before: Long, after: Long, limit: Long): Int? {
        val eighty = limit * 8 / 10
        return when {
            before < limit && after >= limit -> 100
            before < eighty && after >= eighty -> 80
            else -> null
        }
    }

    // ---------- Voice learning ----------

    suspend fun learn(phrase: String, categoryId: Long, subcategoryId: Long?) {
        val p = phrase.trim().lowercase()
        if (p.length < 2) return
        keywordDao.deleteLearned(p)
        keywordDao.insert(
            KeywordRule(phrase = p, categoryId = categoryId, subcategoryId = subcategoryId, weight = 5, isUserLearned = true)
        )
        keywordCache = null
    }

    // ---------- Recurring ----------

    suspend fun getRule(id: Long): RecurringRule? = recurringDao.get(id)
    suspend fun saveRule(rule: RecurringRule): Long = recurringDao.upsert(rule)
    suspend fun deleteRule(rule: RecurringRule) = recurringDao.delete(rule)

    /**
     * Saves a rule and adds an entry for each chosen past due date ([backfill]), all at once. The
     * rule itself carries on from its next due date as usual. Returns the rule's id.
     */
    suspend fun saveRuleWithBackfill(rule: RecurringRule, backfill: List<LocalDate>): Long = recurringLock.withLock {
        db.withTransaction {
            val upserted = recurringDao.upsert(rule)
            val id = if (rule.id != 0L) rule.id else upserted
            val saved = rule.copy(id = id)
            backfill.distinct().sorted().forEach { txnDao.insert(txnFromRule(saved, saved.amountPaise, it)) }
            id
        }
    }

    fun advance(rule: RecurringRule): RecurringRule {
        val i = rule.occurrenceIndex + 1
        val next = Recurrence.occurrence(LocalDate.ofEpochDay(rule.anchorDate), rule.unit, rule.interval, i)
        val ended = rule.endDate != null && next.toEpochDay() > rule.endDate
        return rule.copy(
            occurrenceIndex = i,
            nextDueDate = next.toEpochDay(),
            isActive = rule.isActive && !ended,
            lastRemindedFor = null
        )
    }

    private fun txnFromRule(rule: RecurringRule, amount: Long, date: LocalDate) = Txn(
        amountPaise = amount,
        type = rule.type,
        categoryId = rule.categoryId,
        subcategoryId = rule.subcategoryId,
        dateTime = Dates.atMillis(date, LocalTime.of(9, 0)),
        paymentMode = rule.paymentMode,
        note = rule.name,
        source = Source.RECURRING,
        recurringRuleId = rule.id
    )

    suspend fun markRulePaid(rule: RecurringRule, amount: Long, date: LocalDate = LocalDate.now()) {
        recurringLock.withLock {
            val fresh = recurringDao.get(rule.id)
            if (fresh != null) {
                if (amount > 0) txnDao.insert(txnFromRule(fresh, amount, date))
                recurringDao.upsert(advance(fresh))
            }
        }
    }

    suspend fun skipRule(rule: RecurringRule) {
        recurringLock.withLock {
            val fresh = recurringDao.get(rule.id)
            if (fresh != null) recurringDao.upsert(advance(fresh))
        }
    }

    /**
     * Posts every auto-add entry that is due (catching up if the phone was off) and returns the
     * remind-only rules that need a notification now.
     */
    suspend fun processRecurring(today: LocalDate): List<RecurringRule> = recurringLock.withLock {
        val reminders = ArrayList<RecurringRule>()
        for (original in recurringDao.active()) {
            var rule = original
            var guard = 0
            while (guard++ < 1000 && rule.isActive) {
                val due = LocalDate.ofEpochDay(rule.nextDueDate)
                if (rule.endDate != null && rule.nextDueDate > rule.endDate) {
                    rule = rule.copy(isActive = false)
                    break
                }
                if (rule.mode == RecurringMode.AUTO_ADD) {
                    if (due.isAfter(today)) break
                    txnDao.insert(txnFromRule(rule, rule.amountPaise, due))
                    rule = advance(rule)
                } else {
                    val remindOn = due.minusDays(rule.remindDaysBefore.toLong())
                    if (!today.isBefore(remindOn) && rule.lastRemindedFor != rule.nextDueDate) {
                        reminders += rule
                        rule = rule.copy(lastRemindedFor = rule.nextDueDate)
                    }
                    break
                }
            }
            if (rule != original) recurringDao.upsert(rule)
        }
        reminders
    }

    /**
     * Yearly (or 3-monthly+) subscriptions that renew within a week; each is marked so it's mentioned
     * once per renewal. The mark is cleared when the renewal is added.
     */
    suspend fun renewalsToRemind(today: LocalDate): List<RecurringRule> = recurringLock.withLock {
        val cats = categoryDao.allCategories().associateBy { it.id }
        recurringDao.active()
            .filter { Subscriptions.isSubscription(it, cats) && Subscriptions.needsRenewalNotice(it, today) }
            .onEach { recurringDao.upsert(it.copy(lastRemindedFor = it.nextDueDate)) }
    }

    // ---------- Budgets ----------

    suspend fun setBudget(categoryId: Long, limitPaise: Long?) {
        if (limitPaise == null || limitPaise <= 0) budgetDao.delete(categoryId)
        else budgetDao.upsert(Budget(categoryId, limitPaise))
    }

    // ---------- Export ----------

    /** Same columns as the import template, so an export can be edited and imported again. */
    suspend fun exportCsv(out: OutputStream) {
        val rows = txnDao.allRows()
        val eventNames = backupDao.events().associate { it.id to it.name }
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write(CsvImport.TEMPLATE)
            rows.forEach { r ->
                val t = r.txn
                val cells = listOf(
                    CsvImport.formatDate(Dates.toLocalDate(t.dateTime)),
                    CsvImport.typeLabel(t.type),
                    BigDecimal(t.amountPaise).movePointLeft(2).toPlainString(),
                    r.catName ?: "",
                    r.specificSub ?: "",
                    t.paymentMode.label,
                    t.note,
                    t.eventId?.let { eventNames[it] } ?: ""
                )
                w.write(cells.joinToString(",") { CsvImport.csvCell(it) })
                w.write("\r\n")
            }
        }
    }

    // ---------- CSV import ----------

    /**
     * Saves imported entries in one go. Categories in [newCategories] are created first; an entry
     * points at one through [ImportEntry.newCategoryKey]. Subcategory names not seen before are
     * created under their category. Returns the batch id that [undoImport] takes, and the count.
     */
    suspend fun importEntries(newCategories: List<NewCategory>, entries: List<ImportEntry>): Pair<Long, Int> {
        if (entries.isEmpty()) return 0L to 0
        val batch = System.currentTimeMillis()
        db.withTransaction {
            val createdCats = HashMap<String, Long>()
            newCategories.forEachIndexed { i, nc ->
                createdCats[nc.key] = addCategory(nc.name, "🏷️", IMPORT_COLORS[i % IMPORT_COLORS.size], nc.kind)
            }
            val subCache = HashMap<Pair<Long, String>, Long>()
            val txns = entries.map { e ->
                val catId = e.newCategoryKey?.let { createdCats[it] } ?: e.txn.categoryId
                val subId = when {
                    catId == null -> null
                    e.newSubName != null -> {
                        val key = catId to e.newSubName.lowercase()
                        subCache[key] ?: (categoryDao.subsOf(catId).firstOrNull { it.name.equals(e.newSubName, ignoreCase = true) }?.id
                            ?: addSubcategory(catId, e.newSubName)).also { subCache[key] = it }
                    }
                    e.txn.subcategoryId != null -> e.txn.subcategoryId
                    else -> defaultSubOf(catId)
                }
                e.txn.copy(categoryId = catId, subcategoryId = subId, source = Source.IMPORT, createdAt = batch, updatedAt = batch)
            }
            txnDao.insertAll(txns)
        }
        keywordCache = null
        return batch to entries.size
    }

    suspend fun undoImport(batch: Long): Int = txnDao.deleteImport(batch)

    // ---------- Receipt photos ----------

    fun attachments(txnId: Long): Flow<List<Attachment>> = db.extrasDao().observeAttachments(txnId)

    /** [fileName] is already in receipts (see Receipts.import). */
    suspend fun addAttachment(txnId: Long, fileName: String) {
        db.extrasDao().insertAttachment(Attachment(txnId = txnId, fileName = fileName))
    }

    suspend fun deleteAttachment(id: Long) {
        val a = db.extrasDao().attachment(id) ?: return
        db.extrasDao().deleteAttachment(id)
        receiptFile(a.fileName)?.delete()
    }

    /** Removes photo files nothing points to (e.g. the app closed before an entry was saved). Keeps today's. */
    suspend fun cleanUpReceipts() {
        val dir = receiptsDir ?: return
        val used = db.extrasDao().allFileNames().toSet()
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir.listFiles()?.filter { it.name !in used && it.lastModified() < dayAgo }?.forEach { it.delete() }
    }

    // ---------- Warranties ----------

    val warranties: Flow<List<WarrantyRow>> = db.extrasDao().observeWarranties()

    suspend fun warrantyOf(txnId: Long): Warranty? = db.extrasDao().warranty(txnId)

    /** Sets or clears an entry's warranty. A new date starts its reminders afresh. */
    suspend fun setWarranty(txnId: Long, until: LocalDate?) {
        val dao = db.extrasDao()
        if (until == null) {
            dao.deleteWarranty(txnId)
            return
        }
        val old = dao.warranty(txnId)
        if (old?.untilDate == until.toEpochDay()) return
        dao.upsertWarranty(Warranty(txnId = txnId, untilDate = until.toEpochDay(), stage = 0))
    }

    /** Warranties to remind about today, with the stage reached (1 = in 30 days, 2 = ends today/ended). */
    suspend fun warrantiesToRemind(today: LocalDate): List<Pair<Txn, Warranty>> {
        val dao = db.extrasDao()
        return db.withTransaction {
            dao.allWarranties().mapNotNull { w ->
                val stage = Warranties.dueStage(w.untilDate, w.stage, today) ?: return@mapNotNull null
                val txn = txnDao.get(w.txnId) ?: return@mapNotNull null
                val updated = w.copy(stage = stage)
                dao.upsertWarranty(updated)
                txn to updated
            }
        }
    }

    // ---------- Savings goals ----------

    val goals: Flow<List<GoalRow>> = db.extrasDao().observeGoals()
    fun goalTxns(goalId: Long): Flow<List<TxnRow>> = db.extrasDao().observeGoalTxns(goalId)

    suspend fun saveGoal(g: Goal): Long {
        val name = g.name.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) throw UserError("Enter a name")
        if (g.targetPaise <= 0) throw UserError("Enter a target amount")
        val clean = g.copy(name = name, emoji = g.emoji.ifBlank { "🎯" })
        return if (g.id == 0L) db.extrasDao().insertGoal(clean) else {
            db.extrasDao().updateGoal(clean)
            g.id
        }
    }

    suspend fun setGoalClosed(id: Long, closed: Boolean) {
        val g = db.extrasDao().goal(id) ?: return
        db.extrasDao().updateGoal(g.copy(isClosed = closed))
    }

    /** Deletes the goal only; its savings entries stay, untagged. */
    suspend fun deleteGoal(id: Long) = db.withTransaction {
        db.extrasDao().untagGoal(id)
        db.extrasDao().deleteGoal(id)
    }

    suspend fun tagGoal(txnId: Long, goalId: Long?) = db.extrasDao().tagGoal(txnId, goalId)

    /** "Add ₹" on a goal: a Saving entry tagged to it, so it counts in savings as usual. */
    suspend fun addToGoal(goalId: Long, amount: Long, date: LocalDate, payment: PaymentMode, categoryId: Long?, subcategoryId: Long?): Long {
        if (amount <= 0) throw UserError("Enter an amount")
        val g = db.extrasDao().goal(goalId) ?: throw UserError("Goal not found")
        val time = if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON
        return saveTxn(
            Txn(
                amountPaise = amount, type = TxnType.SAVING, categoryId = categoryId, subcategoryId = subcategoryId,
                dateTime = Dates.atMillis(date, time), paymentMode = payment, note = "${g.emoji} ${g.name}", goalId = goalId
            )
        )
    }

    /** Taking money back out: a Transfer tagged to the goal (not spending, not income). */
    suspend fun withdrawFromGoal(goalId: Long, amount: Long, date: LocalDate, available: Long): Long {
        if (amount <= 0) throw UserError("Enter an amount")
        if (amount > available) throw UserError("Only ${Money.format(available, showPaise = true)} is saved in this goal")
        val g = db.extrasDao().goal(goalId) ?: throw UserError("Goal not found")
        val time = if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON
        return saveTxn(
            Txn(
                amountPaise = amount, type = TxnType.TRANSFER, categoryId = null, subcategoryId = null,
                dateTime = Dates.atMillis(date, time), paymentMode = PaymentMode.UPI,
                note = "Taken out of ${g.emoji} ${g.name}", goalId = goalId
            )
        )
    }

    // ---------- Gold schemes ----------

    val schemes: Flow<List<GoldSchemeRow>> = db.schemeDao().observeAll()
    fun schemeTxns(schemeId: Long): Flow<List<TxnRow>> = db.schemeDao().observeTxns(schemeId)

    suspend fun saveScheme(s: GoldScheme): Long {
        val clean = s.copy(name = s.name.trim().replace(Regex("\\s+"), " "), note = s.note.trim())
        GoldSchemes.check(clean)?.let { throw UserError(it) }
        val dao = db.schemeDao()
        return if (s.id == 0L) dao.insert(clean) else {
            val old = dao.get(s.id)
            // A changed start date re-arms reminders.
            dao.update(if (old?.startDate != clean.startDate) clean.copy(lastRemindedFor = null) else clean)
            s.id
        }
    }

    /** Redeemed / stopped (closed), or reopened. */
    suspend fun setSchemeClosed(id: Long, closed: Boolean, on: LocalDate = LocalDate.now()) {
        val s = db.schemeDao().get(id) ?: return
        db.schemeDao().update(s.copy(isClosed = closed, closedOn = if (closed) on.toEpochDay() else null))
    }

    /** Deletes the scheme only; its instalments stay in history as savings. */
    suspend fun deleteScheme(id: Long) = db.withTransaction {
        db.schemeDao().untagAll(id)
        db.schemeDao().delete(id)
    }

    /**
     * Pays an instalment: a Saving entry tagged to the scheme, filed under Savings → "Gold Scheme"
     * when that subcategory exists (any saving category otherwise).
     */
    suspend fun paySchemeInstalment(schemeId: Long, amount: Long, date: LocalDate, payment: PaymentMode): Long {
        if (amount <= 0) throw UserError("Enter an amount")
        val s = db.schemeDao().get(schemeId) ?: throw UserError("Scheme not found")
        val savingCats = categoryDao.allCategories().filter { it.kind == CategoryKind.SAVING }
        val goldSub = savingCats.flatMap { c -> categoryDao.subsOf(c.id) }
            .firstOrNull { it.name.equals("Gold Scheme", ignoreCase = true) }
        val catId = goldSub?.categoryId ?: savingCats.firstOrNull { !it.isArchived }?.id ?: savingCats.firstOrNull()?.id
        val subId = goldSub?.id ?: catId?.let { defaultSubOf(it) }
        val time = if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON
        return saveTxn(
            Txn(
                amountPaise = amount, type = TxnType.SAVING, categoryId = catId, subcategoryId = subId,
                dateTime = Dates.atMillis(date, time), paymentMode = payment, note = "🪙 ${s.name}", schemeId = schemeId
            )
        )
    }

    /** Schemes with an instalment due today (or missed); each is marked so it isn't reminded twice. */
    suspend fun schemesToRemind(today: LocalDate): List<GoldSchemeRow> {
        val dao = db.schemeDao()
        return db.withTransaction {
            dao.open().filter { GoldSchemes.shouldRemind(it, today, Dates.toLocalDate(it.scheme.createdAt)) }.onEach { r ->
                dao.update(r.scheme.copy(lastRemindedFor = GoldSchemes.nextDue(r)?.toEpochDay()))
            }
        }
    }

    // ---------- Monthly report ----------

    suspend fun monthlyReport(month: YearMonth, today: LocalDate = LocalDate.now()): MonthlyReport {
        val cats = categoryDao.allCategories().associateBy { it.id }
        val budgets = budgetDao.all().associate { it.categoryId to it.monthlyLimitPaise }
        return Reports.build(txnDao.allRows(), month, budgets, cats, today)
    }

    // ---------- Event budgets ----------

    val events: Flow<List<EventRow>> = db.eventDao().observeAll()
    fun eventTxns(eventId: Long): Flow<List<TxnRow>> = db.eventDao().observeTxns(eventId)

    suspend fun saveEvent(e: Event): Long {
        val name = e.name.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) throw UserError("Enter a name")
        if (e.budgetPaise != null && e.budgetPaise <= 0) throw UserError("Budget must be more than zero, or leave it empty")
        if (e.startDate != null && e.endDate != null && e.endDate < e.startDate) throw UserError("The end date is before the start date")
        val clean = e.copy(name = name, emoji = e.emoji.ifBlank { "🎉" })
        return if (e.id == 0L) db.eventDao().insert(clean) else {
            db.eventDao().update(clean)
            e.id
        }
    }

    suspend fun setEventClosed(id: Long, closed: Boolean) {
        val e = db.eventDao().get(id) ?: return
        db.eventDao().update(e.copy(isClosed = closed))
    }

    /** Deletes the event only; its entries stay, untagged. */
    suspend fun deleteEvent(id: Long) = db.withTransaction {
        db.eventDao().untagAll(id)
        db.eventDao().delete(id)
    }

    suspend fun tagTxn(txnId: Long, eventId: Long?) = db.eventDao().tag(txnId, eventId)

    // ---------- Lent / borrowed ----------

    val loans: Flow<List<LoanRow>> = db.loanDao().observeAll()
    val loanPeople: Flow<List<String>> = db.loanDao().observePeople()
    fun loanPayments(loanId: Long): Flow<List<LoanPayment>> = db.loanDao().observePayments(loanId)
    suspend fun loanRow(id: Long): LoanRow? = db.loanDao().getRow(id)

    /** Adds or edits a loan. The amount can't drop below what's already been repaid. */
    suspend fun saveLoan(loan: Loan): Long {
        val person = loan.person.trim().replace(Regex("\\s+"), " ")
        if (person.isEmpty()) throw UserError("Enter a name")
        if (loan.amountPaise <= 0) throw UserError("Enter an amount")
        if (loan.dueDate != null && loan.dueDate < loan.date) throw UserError("The due date is before the date given")
        val dao = db.loanDao()
        return db.withTransaction {
            if (loan.id == 0L) {
                dao.insert(loan.copy(person = person))
            } else {
                val paid = dao.getRow(loan.id)?.paidPaise ?: 0L
                if (loan.amountPaise < paid) throw UserError("${Money.format(paid)} has already been repaid; the amount can't be less")
                // A new due date gets its own reminder.
                val old = dao.getRow(loan.id)?.loan
                val reminded = if (old?.dueDate != loan.dueDate) null else loan.lastRemindedOn
                dao.update(loan.copy(person = person, isClosed = Loans.closedAfter(loan, paid), lastRemindedOn = reminded))
                loan.id
            }
        }
    }

    suspend fun addLoanPayment(loanId: Long, amount: Long, date: LocalDate, note: String) {
        val dao = db.loanDao()
        db.withTransaction {
            val row = dao.getRow(loanId) ?: return@withTransaction
            Loans.checkPayment(row, amount)?.let { throw UserError(it) }
            dao.insertPayment(LoanPayment(loanId = loanId, amountPaise = amount, date = date.toEpochDay(), note = note.trim()))
            if (Loans.closedAfter(row.loan, row.paidPaise + amount)) dao.update(row.loan.copy(isClosed = true))
        }
    }

    suspend fun deleteLoanPayment(paymentId: Long) {
        val dao = db.loanDao()
        db.withTransaction {
            val p = dao.getPayment(paymentId) ?: return@withTransaction
            dao.deletePayment(paymentId)
            val row = dao.getRow(p.loanId) ?: return@withTransaction
            if (row.loan.isClosed && row.remaining > 0) dao.update(row.loan.copy(isClosed = false))
        }
    }

    /** "Mark as settled" (closed with something left, e.g. forgiven) or reopen. */
    suspend fun setLoanClosed(loanId: Long, closed: Boolean) {
        val dao = db.loanDao()
        val row = dao.getRow(loanId) ?: return
        if (!closed && row.remaining <= 0) throw UserError("It's fully repaid. Delete a repayment to reopen it.")
        dao.update(row.loan.copy(isClosed = closed))
    }

    suspend fun deleteLoan(loanId: Long) = db.loanDao().delete(loanId)

    /** Loans to remind about today; each is marked so it isn't reminded again for a week. */
    suspend fun loansToRemind(today: LocalDate): List<LoanRow> {
        val dao = db.loanDao()
        return db.withTransaction {
            dao.open().filter { Loans.shouldRemind(it, today) }.onEach {
                dao.update(it.loan.copy(lastRemindedOn = today.toEpochDay()))
            }
        }
    }

    // ---------- Backup & restore ----------

    suspend fun exportBackup(out: OutputStream) {
        val data = db.withTransaction {
            BackupData(
                seedVersion = prefs.seedVersion(),
                categories = backupDao.categories(),
                subcategories = backupDao.subcategories(),
                txns = backupDao.txns(),
                rules = backupDao.rules(),
                budgets = backupDao.budgets(),
                keywords = backupDao.keywords(),
                loans = backupDao.loans(),
                loanPayments = backupDao.loanPayments(),
                events = backupDao.events(),
                attachments = backupDao.attachments(),
                warranties = backupDao.warranties(),
                goals = backupDao.goals(),
                schemes = backupDao.schemes()
            )
        }
        // A .zip: the data as JSON, plus every receipt photo.
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BACKUP_JSON))
            zip.write(BackupJson.encode(data).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            data.attachments.map { it.fileName }.distinct().forEach { name ->
                val f = receiptFile(name) ?: return@forEach
                if (!f.isFile) return@forEach
                zip.putNextEntry(ZipEntry("receipts/$name"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Restores a .zip backup (data + photos) or an older .json one. Parses first, so a bad file
     * never touches the database. Replaces everything on success.
     */
    suspend fun importBackup(input: InputStream): BackupData {
        val (data, photos) = readBackup(input.use { it.readBytes() })
        return restore(data, photos)
    }

    /** What a backup file holds, compared with this phone, so Restore can say what it will replace. */
    suspend fun previewBackup(input: InputStream): BackupPreview {
        val (data, photos) = readBackup(input.use { it.readBytes() })
        val dates = data.txns.map { it.dateTime }
        return BackupPreview(
            exportedAt = data.exportedAt,
            entries = data.txns.size,
            photos = photos.size,
            categories = data.categories.size,
            firstEntry = dates.minOrNull(),
            lastEntry = dates.maxOrNull(),
            entriesHere = backupDao.txns().size,
            photosHere = backupDao.attachments().size
        )
    }

    /** Parses a .zip (data + photos) or older .json backup. Throws [UserError] for anything else. */
    private fun readBackup(bytes: ByteArray): Pair<BackupData, Map<String, ByteArray>> {
        val isZip = bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        var text: String? = null
        val photos = HashMap<String, ByteArray>()
        if (isZip) {
            try {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        when {
                            e.name == BACKUP_JSON -> text = zip.readBytes().toString(Charsets.UTF_8)
                            // Only plain names inside receipts/, never paths that could escape the folder.
                            e.name.matches(Regex("receipts/[A-Za-z0-9._-]+")) -> photos[e.name.removePrefix("receipts/")] = zip.readBytes()
                        }
                    }
                }
            } catch (e: java.io.IOException) {
                throw UserError("This backup file is damaged and can't be restored")
            }
        } else {
            text = bytes.toString(Charsets.UTF_8)
        }
        return BackupJson.decode(text ?: throw UserError("This isn't a KYE backup file")) to photos
    }

    private suspend fun restore(data: BackupData, photos: Map<String, ByteArray>): BackupData {
        db.withTransaction {
            backupDao.clearAttachments()
            backupDao.clearWarranties()
            backupDao.clearGoals()
            backupDao.clearSchemes()
            backupDao.clearLoanPayments()
            backupDao.clearLoans()
            backupDao.clearEvents()
            backupDao.clearTxns()
            backupDao.clearRules()
            backupDao.clearBudgets()
            backupDao.clearKeywords()
            backupDao.clearSubcategories()
            backupDao.clearCategories()
            backupDao.insertCategories(data.categories)
            backupDao.insertSubcategories(data.subcategories)
            backupDao.insertTxns(data.txns)
            backupDao.insertRules(data.rules)
            backupDao.insertBudgets(data.budgets)
            backupDao.insertKeywords(data.keywords)
            backupDao.insertLoans(data.loans)
            backupDao.insertLoanPayments(data.loanPayments)
            backupDao.insertEvents(data.events)
            backupDao.insertGoals(data.goals)
            backupDao.insertSchemes(data.schemes)
            backupDao.insertAttachments(data.attachments)
            backupDao.insertWarranties(data.warranties)
        }
        receiptsDir?.let { dir ->
            dir.mkdirs()
            photos.forEach { (name, content) -> File(dir, name).writeBytes(content) }
        }
        keywordCache = null
        // A backup from an older app version gets any newer built-in categories on top.
        prefs.setSeedVersion(data.seedVersion.coerceAtLeast(1))
        ensureSeeded()
        return data
    }
}

const val OTHERS = "Others"

/** The data file inside a .zip backup. */
private const val BACKUP_JSON = "kye-backup.json"

/** Keywords the user adds or names they type outrank the built-in ones (1–3), but not learned corrections (5). */
private const val USER_WEIGHT = 4

/** An entry from a CSV file, ready to save. Category/subcategory that still need creating are named here. */
data class ImportEntry(val txn: Txn, val newCategoryKey: String? = null, val newSubName: String? = null)

/** A category the user chose to create during an import. */
data class NewCategory(val key: String, val name: String, val kind: CategoryKind)

private val IMPORT_COLORS = listOf(0xFF6C5CE7, 0xFF00B894, 0xFFE17055, 0xFF0984E3, 0xFFFDCB6E, 0xFFE84393)

/** Shown before a restore: what the backup holds and what's on the phone now. Times in millis. */
data class BackupPreview(
    val exportedAt: Long?,
    val entries: Int,
    val photos: Int,
    val categories: Int,
    val firstEntry: Long?,
    val lastEntry: Long?,
    val entriesHere: Int,
    val photosHere: Int
)
