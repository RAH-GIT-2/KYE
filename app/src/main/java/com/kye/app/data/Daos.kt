package com.kye.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

const val ROW_SELECT =
    "SELECT t.*, c.name AS catName, c.emoji AS catEmoji, c.color AS catColor, s.name AS subName " +
        "FROM txn t LEFT JOIN category c ON c.id = t.categoryId " +
        "LEFT JOIN subcategory s ON s.id = t.subcategoryId"

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category WHERE isArchived = 0 ORDER BY sortOrder")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM subcategory WHERE isArchived = 0 ORDER BY id")
    fun observeSubs(): Flow<List<Subcategory>>

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertSub(sub: Subcategory): Long
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

    @Insert
    suspend fun insert(rule: KeywordRule): Long

    @Insert
    suspend fun insertAll(rules: List<KeywordRule>)

    @Query("DELETE FROM keyword_rule WHERE phrase = :phrase AND isUserLearned = 1")
    suspend fun deleteLearned(phrase: String)
}
