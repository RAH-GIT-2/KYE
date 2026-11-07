package com.kye.app.data

import androidx.room.withTransaction
import com.kye.app.domain.Dates
import com.kye.app.domain.Recurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class Repository(private val db: KyeDatabase) {
    private val categoryDao = db.categoryDao()
    private val txnDao = db.txnDao()
    private val recurringDao = db.recurringDao()
    private val budgetDao = db.budgetDao()
    private val keywordDao = db.keywordDao()
    private val recurringLock = Mutex()
    private val seedLock = Mutex()

    val categories: Flow<List<Category>> = categoryDao.observeAll()
    val subcategories: Flow<List<Subcategory>> = categoryDao.observeSubs()
    val rules: Flow<List<RecurringRule>> = recurringDao.observeAll()
    val budgets: Flow<List<Budget>> = budgetDao.observeAll()
    val transactions: Flow<List<TxnRow>> = txnDao.observeAll()

    @Volatile
    private var keywordCache: List<KeywordRule>? = null

    suspend fun keywords(): List<KeywordRule> =
        keywordCache ?: keywordDao.all().also { keywordCache = it }

    // ---------- Seeding ----------

    suspend fun ensureSeeded() = seedLock.withLock {
        if (categoryDao.count() > 0) return@withLock
        db.withTransaction {
            Seed.categories.forEachIndexed { index, sc ->
                val catId = categoryDao.insert(
                    Category(name = sc.name, emoji = sc.emoji, color = sc.color, kind = sc.kind, sortOrder = index)
                )
                val rules = ArrayList<KeywordRule>()
                sc.keywords.forEach { rules += KeywordRule(phrase = it, categoryId = catId, subcategoryId = null, weight = 1) }
                (sc.subs + SeedSub("Others")).forEach { ss ->
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
                keywordDao.insertAll(rules)
            }
        }
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
}
