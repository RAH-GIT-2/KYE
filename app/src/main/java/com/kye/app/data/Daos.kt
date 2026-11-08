package com.kye.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

const val ROW_SELECT =
    "SELECT t.*, c.name AS catName, c.emoji AS catEmoji, c.color AS catColor, s.name AS subName, " +
        "s.isDefault AS subIsDefault, " +
        "(SELECT COUNT(*) FROM attachment a WHERE a.txnId = t.id) AS attachments, " +
        "(SELECT w.untilDate FROM warranty w WHERE w.txnId = t.id) AS warrantyUntil " +
        "FROM txn t LEFT JOIN category c ON c.id = t.categoryId " +
        "LEFT JOIN subcategory s ON s.id = t.subcategoryId"

@Dao
interface CategoryDao {
    /** Every category including hidden ones, for the manager and for labelling old entries. */
    @Query("SELECT * FROM category ORDER BY isArchived, sortOrder")
    fun observeEverything(): Flow<List<Category>>

    @Query("SELECT * FROM subcategory ORDER BY isDefault, id")
    fun observeAllSubs(): Flow<List<Subcategory>>

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun get(id: Long): Category?

    @Query("SELECT * FROM category")
    suspend fun allCategories(): List<Category>

    @Query("SELECT * FROM subcategory WHERE id = :id")
    suspend fun getSub(id: Long): Subcategory?

    @Query("SELECT * FROM subcategory WHERE categoryId = :categoryId")
    suspend fun subsOf(categoryId: Long): List<Subcategory>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM category")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertSub(sub: Subcategory): Long

    @Update
    suspend fun update(category: Category)

    @Update
    suspend fun updateSub(sub: Subcategory)

    @Update
    suspend fun updateAll(categories: List<Category>)

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("DELETE FROM subcategory WHERE id = :id")
    suspend fun deleteSub(id: Long)

    // ---------- Usage (decides whether a real delete is safe) ----------

    @Query(
        "SELECT (SELECT COUNT(*) FROM txn WHERE categoryId = :id) + " +
            "(SELECT COUNT(*) FROM recurring_rule WHERE categoryId = :id)"
    )
    suspend fun categoryUsage(id: Long): Int

    @Query(
        "SELECT (SELECT COUNT(*) FROM txn WHERE subcategoryId = :id) + " +
            "(SELECT COUNT(*) FROM recurring_rule WHERE subcategoryId = :id)"
    )
    suspend fun subUsage(id: Long): Int

    // ---------- Reassignment, used by merge and delete ----------

    @Query("UPDATE txn SET categoryId = :toCat, subcategoryId = :toSub WHERE subcategoryId = :fromSub")
    suspend fun moveSubTxns(fromSub: Long, toCat: Long, toSub: Long)

    @Query("UPDATE recurring_rule SET categoryId = :toCat, subcategoryId = :toSub WHERE subcategoryId = :fromSub")
    suspend fun moveSubRules(fromSub: Long, toCat: Long, toSub: Long)

    @Query("UPDATE keyword_rule SET categoryId = :toCat, subcategoryId = :toSub WHERE subcategoryId = :fromSub")
    suspend fun moveSubKeywords(fromSub: Long, toCat: Long, toSub: Long)

    @Query("UPDATE txn SET categoryId = :toCat WHERE subcategoryId = :subId")
    suspend fun retagSubTxns(subId: Long, toCat: Long)

    @Query("UPDATE recurring_rule SET categoryId = :toCat WHERE subcategoryId = :subId")
    suspend fun retagSubRules(subId: Long, toCat: Long)

    @Query("UPDATE keyword_rule SET categoryId = :toCat WHERE subcategoryId = :subId")
    suspend fun retagSubKeywords(subId: Long, toCat: Long)

    @Query("UPDATE txn SET categoryId = :toCat, subcategoryId = :toSub WHERE categoryId = :fromCat")
    suspend fun moveCatTxns(fromCat: Long, toCat: Long, toSub: Long)

    @Query("UPDATE recurring_rule SET categoryId = :toCat, subcategoryId = :toSub WHERE categoryId = :fromCat")
    suspend fun moveCatRules(fromCat: Long, toCat: Long, toSub: Long)

    @Query("UPDATE keyword_rule SET categoryId = :toCat WHERE categoryId = :fromCat AND subcategoryId IS NULL")
    suspend fun moveCatKeywords(fromCat: Long, toCat: Long)

    @Query("UPDATE subcategory SET categoryId = :toCat WHERE id = :subId")
    suspend fun reparentSub(subId: Long, toCat: Long)
}

@Dao
interface TxnDao {
    @Query(ROW_SELECT + " ORDER BY t.dateTime DESC, t.id DESC")
    fun observeAll(): Flow<List<TxnRow>>

    @Query(ROW_SELECT + " ORDER BY t.dateTime DESC, t.id DESC")
    suspend fun allRows(): List<TxnRow>

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun get(id: Long): Txn?

    @Insert
    suspend fun insert(txn: Txn): Long

    @Update
    suspend fun update(txn: Txn)

    @Delete
    suspend fun delete(txn: Txn)

    @Query(
        "SELECT COALESCE(SUM(amountPaise), 0) FROM txn WHERE type = 'EXPENSE' " +
            "AND dateTime BETWEEN :from AND :to AND (:categoryId IS NULL OR categoryId = :categoryId)"
    )
    suspend fun sumExpense(from: Long, to: Long, categoryId: Long?): Long

    @Insert
    suspend fun insertAll(txns: List<Txn>)

    /** Undo for one CSV import: every entry it added carries the import's time in createdAt. */
    @Query("DELETE FROM txn WHERE source = 'IMPORT' AND createdAt = :batch")
    suspend fun deleteImport(batch: Long): Int
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_rule ORDER BY isActive DESC, nextDueDate ASC")
    fun observeAll(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rule WHERE isActive = 1")
    suspend fun active(): List<RecurringRule>

    @Query("SELECT * FROM recurring_rule WHERE id = :id")
    suspend fun get(id: Long): RecurringRule?

    @Upsert
    suspend fun upsert(rule: RecurringRule): Long

    @Delete
    suspend fun delete(rule: RecurringRule)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budget")
    suspend fun all(): List<Budget>

    @Upsert
    suspend fun upsert(budget: Budget)

    @Query("DELETE FROM budget WHERE categoryId = :categoryId")
    suspend fun delete(categoryId: Long)
}

@Dao
interface KeywordDao {
    @Query("SELECT * FROM keyword_rule")
    suspend fun all(): List<KeywordRule>

    @Query("SELECT * FROM keyword_rule ORDER BY phrase")
    fun observeAll(): Flow<List<KeywordRule>>

    @Insert
    suspend fun insert(rule: KeywordRule): Long

    @Insert
    suspend fun insertAll(rules: List<KeywordRule>)

    @Query("DELETE FROM keyword_rule WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM keyword_rule WHERE phrase = :phrase AND isUserLearned = 1")
    suspend fun deleteLearned(phrase: String)

    @Query("DELETE FROM keyword_rule WHERE phrase = :phrase AND categoryId = :categoryId AND subcategoryId IS :subcategoryId")
    suspend fun deleteExact(phrase: String, categoryId: Long, subcategoryId: Long?)

    @Query("DELETE FROM keyword_rule WHERE categoryId = :categoryId")
    suspend fun deleteForCategory(categoryId: Long)
}

@Dao
interface LoanDao {
    @Query(
        "SELECT l.*, COALESCE((SELECT SUM(p.amountPaise) FROM loan_payment p WHERE p.loanId = l.id), 0) AS paidPaise " +
            "FROM loan l ORDER BY l.isClosed, COALESCE(l.dueDate, 9999999), l.date DESC"
    )
    fun observeAll(): Flow<List<LoanRow>>

    @Query(
        "SELECT l.*, COALESCE((SELECT SUM(p.amountPaise) FROM loan_payment p WHERE p.loanId = l.id), 0) AS paidPaise " +
            "FROM loan l WHERE l.id = :id"
    )
    suspend fun getRow(id: Long): LoanRow?

    @Query(
        "SELECT l.*, COALESCE((SELECT SUM(p.amountPaise) FROM loan_payment p WHERE p.loanId = l.id), 0) AS paidPaise " +
            "FROM loan l WHERE l.isClosed = 0"
    )
    suspend fun open(): List<LoanRow>

    @Query("SELECT * FROM loan_payment WHERE loanId = :loanId ORDER BY date DESC, id DESC")
    fun observePayments(loanId: Long): Flow<List<LoanPayment>>

    @Query("SELECT DISTINCT person FROM loan ORDER BY person COLLATE NOCASE")
    fun observePeople(): Flow<List<String>>

    @Insert suspend fun insert(loan: Loan): Long
    @Update suspend fun update(loan: Loan)
    @Query("DELETE FROM loan WHERE id = :id") suspend fun delete(id: Long) // payments cascade

    @Insert suspend fun insertPayment(p: LoanPayment): Long
    @Query("SELECT * FROM loan_payment WHERE id = :id") suspend fun getPayment(id: Long): LoanPayment?
    @Query("DELETE FROM loan_payment WHERE id = :id") suspend fun deletePayment(id: Long)
}

@Dao
interface EventDao {
    @Query(
        "SELECT e.*, " +
            "COALESCE((SELECT SUM(t.amountPaise) FROM txn t WHERE t.eventId = e.id AND t.type = 'EXPENSE'), 0) AS spentPaise, " +
            "(SELECT COUNT(*) FROM txn t WHERE t.eventId = e.id) AS entries " +
            "FROM event e ORDER BY e.isClosed, e.createdAt DESC"
    )
    fun observeAll(): Flow<List<EventRow>>

    @Query(ROW_SELECT + " WHERE t.eventId = :eventId ORDER BY t.dateTime DESC, t.id DESC")
    fun observeTxns(eventId: Long): Flow<List<TxnRow>>

    @Query("SELECT * FROM event WHERE id = :id") suspend fun get(id: Long): Event?

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM txn WHERE eventId = :eventId AND type = 'EXPENSE'")
    suspend fun spent(eventId: Long): Long

    @Insert suspend fun insert(e: Event): Long
    @Update suspend fun update(e: Event)
    @Query("DELETE FROM event WHERE id = :id") suspend fun delete(id: Long)

    /** Deleting an event keeps its entries; they just lose the tag. */
    @Query("UPDATE txn SET eventId = NULL WHERE eventId = :eventId") suspend fun untagAll(eventId: Long)

    @Query("UPDATE txn SET eventId = :eventId WHERE id = :txnId") suspend fun tag(txnId: Long, eventId: Long?)
}

/** Receipt photos, warranties and savings goals (v2.3). */
@Dao
interface ExtrasDao {
    // ---- Photos
    @Query("SELECT * FROM attachment WHERE txnId = :txnId ORDER BY id")
    fun observeAttachments(txnId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachment WHERE txnId = :txnId") suspend fun attachmentsOf(txnId: Long): List<Attachment>
    @Query("SELECT * FROM attachment WHERE id = :id") suspend fun attachment(id: Long): Attachment?
    @Query("SELECT fileName FROM attachment") suspend fun allFileNames(): List<String>
    @Insert suspend fun insertAttachment(a: Attachment): Long
    @Query("DELETE FROM attachment WHERE id = :id") suspend fun deleteAttachment(id: Long)

    // ---- Warranties
    @Query("SELECT * FROM warranty WHERE txnId = :txnId") suspend fun warranty(txnId: Long): Warranty?
    @Upsert suspend fun upsertWarranty(w: Warranty)
    @Query("DELETE FROM warranty WHERE txnId = :txnId") suspend fun deleteWarranty(txnId: Long)
    @Query("SELECT * FROM warranty") suspend fun allWarranties(): List<Warranty>

    @Query(
        "SELECT t.*, w.untilDate AS untilDate, c.name AS catName, c.emoji AS catEmoji, s.name AS subName, " +
            "(SELECT COUNT(*) FROM attachment a WHERE a.txnId = t.id) AS attachments " +
            "FROM warranty w JOIN txn t ON t.id = w.txnId " +
            "LEFT JOIN category c ON c.id = t.categoryId LEFT JOIN subcategory s ON s.id = t.subcategoryId " +
            "ORDER BY w.untilDate"
    )
    fun observeWarranties(): Flow<List<WarrantyRow>>

    // ---- Goals
    @Query(
        "SELECT g.*, " +
            "COALESCE((SELECT SUM(t.amountPaise) FROM txn t WHERE t.goalId = g.id AND t.type = 'SAVING'), 0) AS savedPaise, " +
            "COALESCE((SELECT SUM(t.amountPaise) FROM txn t WHERE t.goalId = g.id AND t.type = 'TRANSFER'), 0) AS withdrawnPaise " +
            "FROM goal g ORDER BY g.isClosed, g.createdAt DESC"
    )
    fun observeGoals(): Flow<List<GoalRow>>

    @Query(ROW_SELECT + " WHERE t.goalId = :goalId ORDER BY t.dateTime DESC, t.id DESC")
    fun observeGoalTxns(goalId: Long): Flow<List<TxnRow>>

    @Query("SELECT * FROM goal WHERE id = :id") suspend fun goal(id: Long): Goal?
    @Insert suspend fun insertGoal(g: Goal): Long
    @Update suspend fun updateGoal(g: Goal)
    @Query("DELETE FROM goal WHERE id = :id") suspend fun deleteGoal(id: Long)
    @Query("UPDATE txn SET goalId = NULL WHERE goalId = :goalId") suspend fun untagGoal(goalId: Long)
    @Query("UPDATE txn SET goalId = :goalId WHERE id = :txnId") suspend fun tagGoal(txnId: Long, goalId: Long?)
}

/** Gold savings schemes (v2.4). Instalments are SAVING entries tagged with schemeId. */
@Dao
interface SchemeDao {
    @Query(
        "SELECT s.*, " +
            "COALESCE((SELECT SUM(t.amountPaise) FROM txn t WHERE t.schemeId = s.id AND t.type = 'SAVING'), 0) AS paidPaise, " +
            "(SELECT COUNT(*) FROM txn t WHERE t.schemeId = s.id AND t.type = 'SAVING') AS payments " +
            "FROM gold_scheme s ORDER BY s.isClosed, s.startDate DESC, s.id DESC"
    )
    fun observeAll(): Flow<List<GoldSchemeRow>>

    @Query(
        "SELECT s.*, " +
            "COALESCE((SELECT SUM(t.amountPaise) FROM txn t WHERE t.schemeId = s.id AND t.type = 'SAVING'), 0) AS paidPaise, " +
            "(SELECT COUNT(*) FROM txn t WHERE t.schemeId = s.id AND t.type = 'SAVING') AS payments " +
            "FROM gold_scheme s WHERE s.isClosed = 0"
    )
    suspend fun open(): List<GoldSchemeRow>

    @Query(ROW_SELECT + " WHERE t.schemeId = :schemeId ORDER BY t.dateTime DESC, t.id DESC")
    fun observeTxns(schemeId: Long): Flow<List<TxnRow>>

    @Query("SELECT * FROM gold_scheme WHERE id = :id") suspend fun get(id: Long): GoldScheme?
    @Insert suspend fun insert(s: GoldScheme): Long
    @Update suspend fun update(s: GoldScheme)
    @Query("DELETE FROM gold_scheme WHERE id = :id") suspend fun delete(id: Long)

    /** Deleting a scheme keeps its instalments as ordinary savings. */
    @Query("UPDATE txn SET schemeId = NULL WHERE schemeId = :schemeId") suspend fun untagAll(schemeId: Long)
}

/** Raw table access for JSON backup and restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM category") suspend fun categories(): List<Category>
    @Query("SELECT * FROM subcategory") suspend fun subcategories(): List<Subcategory>
    @Query("SELECT * FROM txn") suspend fun txns(): List<Txn>
    @Query("SELECT * FROM recurring_rule") suspend fun rules(): List<RecurringRule>
    @Query("SELECT * FROM budget") suspend fun budgets(): List<Budget>
    @Query("SELECT * FROM keyword_rule") suspend fun keywords(): List<KeywordRule>
    @Query("SELECT * FROM loan") suspend fun loans(): List<Loan>
    @Query("SELECT * FROM loan_payment") suspend fun loanPayments(): List<LoanPayment>
    @Insert suspend fun insertLoans(v: List<Loan>)
    @Insert suspend fun insertLoanPayments(v: List<LoanPayment>)
    @Query("DELETE FROM loan_payment") suspend fun clearLoanPayments()
    @Query("SELECT * FROM event") suspend fun events(): List<Event>
    @Insert suspend fun insertEvents(v: List<Event>)
    @Query("DELETE FROM event") suspend fun clearEvents()
    @Query("SELECT * FROM attachment") suspend fun attachments(): List<Attachment>
    @Query("SELECT * FROM warranty") suspend fun warranties(): List<Warranty>
    @Query("SELECT * FROM goal") suspend fun goals(): List<Goal>
    @Insert suspend fun insertAttachments(v: List<Attachment>)
    @Insert suspend fun insertWarranties(v: List<Warranty>)
    @Insert suspend fun insertGoals(v: List<Goal>)
    @Query("DELETE FROM attachment") suspend fun clearAttachments()
    @Query("DELETE FROM warranty") suspend fun clearWarranties()
    @Query("DELETE FROM goal") suspend fun clearGoals()
    @Query("SELECT * FROM gold_scheme") suspend fun schemes(): List<GoldScheme>
    @Insert suspend fun insertSchemes(v: List<GoldScheme>)
    @Query("DELETE FROM gold_scheme") suspend fun clearSchemes()
    @Query("DELETE FROM loan") suspend fun clearLoans()

    @Insert suspend fun insertCategories(v: List<Category>)
    @Insert suspend fun insertSubcategories(v: List<Subcategory>)
    @Insert suspend fun insertTxns(v: List<Txn>)
    @Insert suspend fun insertRules(v: List<RecurringRule>)
    @Insert suspend fun insertBudgets(v: List<Budget>)
    @Insert suspend fun insertKeywords(v: List<KeywordRule>)

    @Query("DELETE FROM txn") suspend fun clearTxns()
    @Query("DELETE FROM recurring_rule") suspend fun clearRules()
    @Query("DELETE FROM budget") suspend fun clearBudgets()
    @Query("DELETE FROM keyword_rule") suspend fun clearKeywords()
    @Query("DELETE FROM subcategory") suspend fun clearSubcategories()
    @Query("DELETE FROM category") suspend fun clearCategories()
}
