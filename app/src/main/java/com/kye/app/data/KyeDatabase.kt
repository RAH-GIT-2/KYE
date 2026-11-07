package com.kye.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change needs a version bump and a Migration below. Never use
 * fallbackToDestructiveMigration: all data lives only on the phone, so a wipe loses it for good.
 * Schemas are exported to app/schemas/ so each version's shape is on record.
 */
@Database(
    entities = [
        Category::class,
        Subcategory::class,
        Txn::class,
        RecurringRule::class,
        Budget::class,
        KeywordRule::class,
        PendingTxn::class
    ],
    version = 2,
    exportSchema = true
)
abstract class KyeDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun recurringDao(): RecurringDao
    abstract fun budgetDao(): BudgetDao
    abstract fun keywordDao(): KeywordDao
    abstract fun pendingDao(): PendingDao
    abstract fun backupDao(): BackupDao

    companion object {
        /** v2: "Others" subcategories get an explicit flag; detected-payment inbox table. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `subcategory` ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `subcategory` SET `isDefault` = 1 WHERE `name` = 'Others'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_txn` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`amountPaise` INTEGER NOT NULL, `isCredit` INTEGER NOT NULL, `merchant` TEXT NOT NULL, " +
                        "`sourceApp` TEXT NOT NULL, `text` TEXT NOT NULL, `postedAt` INTEGER NOT NULL, " +
                        "`fingerprint` TEXT NOT NULL, `status` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_txn_fingerprint` ON `pending_txn` (`fingerprint`)"
                )
            }
        }

        fun build(context: Context): KyeDatabase =
            Room.databaseBuilder(context, KyeDatabase::class.java, "kye.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
