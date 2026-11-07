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
        "s.isDefault AS subIsDefault " +
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

/** Raw table access for JSON backup and restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM category") suspend fun categories(): List<Category>
    @Query("SELECT * FROM subcategory") suspend fun subcategories(): List<Subcategory>
    @Query("SELECT * FROM txn") suspend fun txns(): List<Txn>
    @Query("SELECT * FROM recurring_rule") suspend fun rules(): List<RecurringRule>
    @Query("SELECT * FROM budget") suspend fun budgets(): List<Budget>
    @Query("SELECT * FROM keyword_rule") suspend fun keywords(): List<KeywordRule>

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
