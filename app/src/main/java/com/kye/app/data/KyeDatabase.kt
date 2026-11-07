package com.kye.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Category::class,
        Subcategory::class,
        Txn::class,
        RecurringRule::class,
        Budget::class,
        KeywordRule::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KyeDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun recurringDao(): RecurringDao
    abstract fun budgetDao(): BudgetDao
    abstract fun keywordDao(): KeywordDao

    companion object {
        fun build(context: Context): KyeDatabase =
            Room.databaseBuilder(context, KyeDatabase::class.java, "kye.db").build()
    }
}
