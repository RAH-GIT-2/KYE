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
        Loan::class,
        LoanPayment::class
    ],
    version = 4,
    exportSchema = true
)
abstract class KyeDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun recurringDao(): RecurringDao
    abstract fun budgetDao(): BudgetDao
    abstract fun keywordDao(): KeywordDao
    abstract fun backupDao(): BackupDao
    abstract fun loanDao(): LoanDao

    companion object {
        /** v2: "Others" subcategories get an explicit flag; a detected-payment table (dropped again in v3). Shipped, so never edit. */
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

        /** v3: notification reading was removed; drop its table and everything it stored. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_pending_txn_fingerprint`")
                db.execSQL("DROP TABLE IF EXISTS `pending_txn`")
            }
        }

        /** v4: lent / borrowed tracker. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loan` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`person` TEXT NOT NULL, `direction` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, " +
                        "`date` INTEGER NOT NULL, `dueDate` INTEGER, `note` TEXT NOT NULL, `isClosed` INTEGER NOT NULL, " +
                        "`lastRemindedOn` INTEGER, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_person` ON `loan` (`person`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loan_payment` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`loanId` INTEGER NOT NULL, `amountPaise` INTEGER NOT NULL, `date` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, FOREIGN KEY(`loanId`) REFERENCES `loan`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payment_loanId` ON `loan_payment` (`loanId`)")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun build(context: Context): KyeDatabase =
            Room.databaseBuilder(context, KyeDatabase::class.java, "kye.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
