package com.kye.app.data

import androidx.room.withTransaction
import com.kye.app.domain.Dates
import com.kye.app.domain.Recurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/** A user-facing problem (duplicate name, category still in use…). The message is shown as-is. */
class UserError(message: String) : Exception(message)

class Repository(private val db: KyeDatabase, private val prefs: Prefs) {
    private val categoryDao = db.categoryDao()
    private val txnDao = db.txnDao()
    private val recurringDao = db.recurringDao()
    private val budgetDao = db.budgetDao()
    private val keywordDao = db.keywordDao()
    private val backupDao = db.backupDao()
    private val recurringLock = Mutex()
    private val seedLock = Mutex()

    val categories: Flow<List<Category>> = categoryDao.observeAll()
    val allCategories: Flow<List<Category>> = categoryDao.observeEverything()
    val subcategories: Flow<List<Subcategory>> = categoryDao.observeSubs()
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

    suspend fun deleteTxn(txn: Txn) = txnDao.delete(txn)

    /** Saves and returns a budget alert message if this entry crossed 80% or 100% of a budget. */
    suspend fun saveWithBudgetCheck(txn: Txn, categoryName: String?): String? {
        if (txn.type != TxnType.EXPENSE) {
            saveTxn(txn)
            return null
        }
        val day = Dates.toLocalDate(txn.dateTime)
        val from = Dates.startOfDay(day.withDayOfMonth(1))
        val to = Dates.endOfDay(day.withDayOfMonth(day.lengthOfMonth()))
        val limits = budgetDao.all().associate { it.categoryId to it.monthlyLimitPaise }
        val catLimit = txn.categoryId?.let { limits[it] }
        val overallLimit = limits[0L]

        val beforeCat = if (catLimit != null) txnDao.sumExpense(from, to, txn.categoryId) else 0L
        val beforeAll = if (overallLimit != null) txnDao.sumExpense(from, to, null) else 0L
        saveTxn(txn)
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
        return messages.joinToString("\n").ifBlank { null }
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

    // ---------- Budgets ----------

    suspend fun setBudget(categoryId: Long, limitPaise: Long?) {
        if (limitPaise == null || limitPaise <= 0) budgetDao.delete(categoryId)
        else budgetDao.upsert(Budget(categoryId, limitPaise))
    }

    // ---------- Export ----------

    suspend fun exportCsv(out: OutputStream) {
        val rows = txnDao.allRows()
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("Date,Time,Type,Category,Subcategory,Amount (INR),Payment,Note,Source\n")
            rows.forEach { r ->
                val t = r.txn
                val cells = listOf(
                    Dates.toLocalDate(t.dateTime).toString(),
                    Dates.toLocalTime(t.dateTime).withNano(0).toString(),
                    t.type.name,
                    r.catName ?: "Uncategorised",
                    r.subName ?: "",
                    BigDecimal(t.amountPaise).movePointLeft(2).toPlainString(),
                    t.paymentMode.label,
                    t.note,
                    t.source.name
                )
                w.write(cells.joinToString(",") { csv(it) })
                w.write("\n")
            }
        }
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

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
                keywords = backupDao.keywords()
            )
        }
        out.bufferedWriter(Charsets.UTF_8).use { it.write(BackupJson.encode(data)) }
    }

    /** Parses first, so a bad file never touches the database. Replaces everything on success. */
    suspend fun importBackup(input: InputStream): BackupData {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val data = BackupJson.decode(text)
        db.withTransaction {
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
        }
        keywordCache = null
        // A backup from an older app version gets any newer built-in categories on top.
        prefs.setSeedVersion(data.seedVersion.coerceAtLeast(1))
        ensureSeeded()
        return data
    }
}

const val OTHERS = "Others"

/** Keywords the user adds or names they type outrank the built-in ones (1–3), but not learned corrections (5). */
private const val USER_WEIGHT = 4
