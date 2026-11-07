package com.kye.app.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Runs on the JVM against real SQLite (Robolectric). Plain Application so KyeApp's
 * WorkManager setup doesn't run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DatabaseUpgradeTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    /** Builds a database exactly as app version 1.0 left it, with some user data in it. */
    private fun createV1(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `category` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `color` INTEGER NOT NULL, `kind` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `subcategory` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER NOT NULL, `name` TEXT NOT NULL, `isTransfer` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subcategory_categoryId` ON `subcategory` (`categoryId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `txn` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amountPaise` INTEGER NOT NULL, `type` TEXT NOT NULL, `categoryId` INTEGER, `subcategoryId` INTEGER, `dateTime` INTEGER NOT NULL, `paymentMode` TEXT NOT NULL, `note` TEXT NOT NULL, `source` TEXT NOT NULL, `recurringRuleId` INTEGER, `rawVoiceText` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_dateTime` ON `txn` (`dateTime`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_txn_categoryId` ON `txn` (`categoryId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_rule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `type` TEXT NOT NULL, `categoryId` INTEGER, `subcategoryId` INTEGER, `paymentMode` TEXT NOT NULL, `unit` TEXT NOT NULL, `interval` INTEGER NOT NULL, `anchorDate` INTEGER NOT NULL, `occurrenceIndex` INTEGER NOT NULL, `nextDueDate` INTEGER NOT NULL, `endDate` INTEGER, `mode` TEXT NOT NULL, `remindDaysBefore` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `lastRemindedFor` INTEGER)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `budget` (`categoryId` INTEGER NOT NULL, `monthlyLimitPaise` INTEGER NOT NULL, PRIMARY KEY(`categoryId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `keyword_rule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `phrase` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, `subcategoryId` INTEGER, `weight` INTEGER NOT NULL, `isUserLearned` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_keyword_rule_phrase` ON `keyword_rule` (`phrase`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'v1-hash')")

            db.execSQL("INSERT INTO category VALUES (1, 'Food & Dining', '🍛', 4294933061, 'EXPENSE', 0, 0)")
            db.execSQL("INSERT INTO subcategory VALUES (1, 1, 'Swiggy', 0, 0)")
            db.execSQL("INSERT INTO subcategory VALUES (2, 1, 'Others', 0, 0)")
            db.execSQL("INSERT INTO txn VALUES (1, 45000, 'EXPENSE', 1, 1, 1730000000000, 'UPI', 'biryani', 'VOICE', NULL, 'swiggy 450', 1, 1)")
            db.execSQL("INSERT INTO txn VALUES (2, 12000, 'EXPENSE', 1, 2, 1730000000000, 'CASH', '', 'MANUAL', NULL, NULL, 1, 1)")
            db.execSQL("INSERT INTO budget VALUES (1, 500000)")
            db.execSQL("INSERT INTO keyword_rule VALUES (1, 'swiggy', 1, 1, 3, 0)")
            db.version = 1
        }
    }

    @Test
    fun upgradeFromV1ToLatestKeepsEverything() = runBlocking {
        val name = "upgrade-test.db"
        createV1(name)

        val db = Room.databaseBuilder(context, KyeDatabase::class.java, name)
            .addMigrations(KyeDatabase.MIGRATION_1_2, KyeDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        // Room validates the migrated schema against the entities when it opens; a mismatch throws here.
        val rows = db.txnDao().allRows()
        assertEquals(2, rows.size)
        assertEquals("biryani", rows.first { it.txn.id == 1L }.txn.note)
        assertEquals("Swiggy", rows.first { it.txn.id == 1L }.title)
        // The "Others" entry is now recognised by flag, so its title falls back to the category.
        assertEquals("Food & Dining", rows.first { it.txn.id == 2L }.title)

        val subs = db.categoryDao().observeAllSubs().first()
        assertTrue(subs.first { it.name == "Others" }.isDefault)
        assertFalse(subs.first { it.name == "Swiggy" }.isDefault)
        assertEquals(500000L, db.budgetDao().all().single().monthlyLimitPaise)

        // v2 created the detected-payments table; v3 removed it again.
        db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM sqlite_master WHERE name LIKE '%pending_txn%'")
            .use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        db.close()
    }

    /** Add screen and voice entry read the "visible subcategories" list; it must match Settings' full list. */
    @Test
    fun visibleSubcategoriesAreReturned() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, KyeDatabase::class.java).allowMainThreadQueries().build()
        Repository(db, Prefs(context)).ensureSeeded()
        val home = db.categoryDao().allCategories().first { it.name == "Home & Help" }
        val all = db.categoryDao().observeAllSubs().first().filter { it.categoryId == home.id }
        val visible = db.categoryDao().observeSubs().first().filter { it.categoryId == home.id }
        assertTrue("Rent is visible", visible.any { it.name == "Rent" })
        assertEquals(all.size, visible.size)
        db.close()
    }

    @Test
    fun categoryManagerAndBackupRoundTrip() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, KyeDatabase::class.java).allowMainThreadQueries().build()
        val repo = Repository(db, Prefs(context))
        repo.ensureSeeded()

        val cats = db.categoryDao().allCategories()
        assertTrue("income categories seeded", cats.any { it.kind == CategoryKind.INCOME && it.name == "Salary" })
        val food = cats.first { it.name == "Food & Dining" }
        val foodSubs = db.categoryDao().subsOf(food.id)
        assertEquals(1, foodSubs.count { it.isDefault })

        // Add a category and subcategory; duplicates are refused.
        val gym = repo.addCategory("Fitness", "🏋️", 0xFF00B894, CategoryKind.EXPENSE)
        val cult = repo.addSubcategory(gym, "Cult")
        assertTrue(runCatching { repo.addCategory("fitness", "x", 0, CategoryKind.EXPENSE) }.exceptionOrNull() is UserError)
        assertTrue(runCatching { repo.addSubcategory(gym, "others") }.exceptionOrNull() is UserError)
        assertTrue("name becomes a voice keyword", repo.keywords().any { it.phrase == "fitness" && it.categoryId == gym })

        // Entries in the new category, then merge it into Personal Care.
        val txnId = db.txnDao().insert(Txn(amountPaise = 150000, type = TxnType.EXPENSE, categoryId = gym, subcategoryId = cult, dateTime = 1, paymentMode = PaymentMode.UPI))
        assertTrue(runCatching { repo.deleteCategory(gym) }.exceptionOrNull() is UserError)
        repo.setBudget(gym, 200000)

        val care = cats.first { it.name == "Personal Care" }
        repo.mergeCategory(gym, care.id)
        val moved = db.txnDao().get(txnId)!!
        assertEquals(care.id, moved.categoryId)
        assertEquals("Cult moved under Personal Care", care.id, db.categoryDao().getSub(cult)!!.categoryId)
        assertEquals(null, db.categoryDao().get(gym))
        assertEquals(200000L, db.budgetDao().all().first { it.categoryId == care.id }.monthlyLimitPaise)

        // Deleting a subcategory moves its entries to "Others".
        repo.deleteSubcategory(cult)
        val others = db.categoryDao().subsOf(care.id).first { it.isDefault }
        assertEquals(others.id, db.txnDao().get(txnId)!!.subcategoryId)

        // Backup → wipe → restore gives back the same data.
        val out = ByteArrayOutputStream()
        repo.exportBackup(out)
        val before = db.backupDao().run { listOf(categories().size, subcategories().size, txns().size, keywords().size, budgets().size) }
        repo.deleteTxn(db.txnDao().get(txnId)!!)
        repo.importBackup(ByteArrayInputStream(out.toByteArray()))
        val after = db.backupDao().run { listOf(categories().size, subcategories().size, txns().size, keywords().size, budgets().size) }
        assertEquals(before, after)
        assertEquals(150000L, db.txnDao().get(txnId)!!.amountPaise)

        // CSV import: creates a new category and subcategory, adds entries, and Undo removes exactly those.
        val countBefore = db.txnDao().allRows().size
        val salary = cats.first { it.name == "Salary" }
        val (batch, n) = repo.importEntries(
            newCategories = listOf(NewCategory("pets", "Pets", CategoryKind.EXPENSE)),
            entries = listOf(
                ImportEntry(Txn(amountPaise = 50000, type = TxnType.EXPENSE, categoryId = null, subcategoryId = null, dateTime = 2, paymentMode = PaymentMode.UPI, note = "vet"), newCategoryKey = "pets", newSubName = "Vet"),
                ImportEntry(Txn(amountPaise = 50000, type = TxnType.EXPENSE, categoryId = null, subcategoryId = null, dateTime = 3, paymentMode = PaymentMode.UPI, note = "vet again"), newCategoryKey = "pets", newSubName = "vet"),
                ImportEntry(Txn(amountPaise = 6500000, type = TxnType.INCOME, categoryId = salary.id, subcategoryId = null, dateTime = 4, paymentMode = PaymentMode.NET_BANKING))
            )
        )
        assertEquals(3, n)
        val pets = db.categoryDao().allCategories().first { it.name == "Pets" }
        val petSubs = db.categoryDao().subsOf(pets.id)
        assertEquals("one Vet subcategory, not two", 1, petSubs.count { it.name.equals("vet", ignoreCase = true) })
        assertEquals(1, petSubs.count { it.isDefault })
        val imported = db.txnDao().allRows().filter { it.txn.source == Source.IMPORT }
        assertEquals(3, imported.size)
        assertTrue(imported.filter { it.txn.categoryId == pets.id }.all { it.subName == "Vet" })
        assertEquals("blank subcategory goes to Others", true, imported.first { it.txn.categoryId == salary.id }.subIsDefault)
        assertEquals(3, repo.undoImport(batch))
        assertEquals(countBefore, db.txnDao().allRows().size)

        // A file that isn't a backup is refused before anything is touched.
        assertTrue(runCatching { repo.importBackup(ByteArrayInputStream("{\"hello\":1}".toByteArray())) }.exceptionOrNull() is UserError)
        assertEquals(1, db.txnDao().allRows().size)
        db.close()
    }
}
