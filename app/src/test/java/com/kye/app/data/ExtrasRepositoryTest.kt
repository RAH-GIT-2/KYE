package com.kye.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kye.app.domain.Dates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Receipt photos, warranties, savings goals, and .zip backups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExtrasRepositoryTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: KyeDatabase
    private lateinit var repo: Repository
    private lateinit var dir: File
    private val today = LocalDate.of(2026, 11, 15)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, KyeDatabase::class.java).allowMainThreadQueries().build()
        dir = File(context.cacheDir, "receipts-test-${System.nanoTime()}").apply { mkdirs() }
        repo = Repository(db, Prefs(context), dir)
    }

    @After fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun buy(rupees: Long, note: String = "Samsung TV") = repo.saveChecked(
        Txn(amountPaise = rupees * 100, type = TxnType.EXPENSE, categoryId = null, subcategoryId = null,
            dateTime = Dates.atMillis(today, LocalTime.NOON), paymentMode = PaymentMode.CREDIT_CARD, note = note),
        null
    ).first

    private fun photo(name: String, bytes: ByteArray = byteArrayOf(1, 2, 3)): String {
        File(dir, name).writeBytes(bytes)
        return name
    }

    @Test fun photosShowOnTheEntryAndDeletingRemovesTheFile() = runBlocking {
        val id = buy(42_000)
        repo.addAttachment(id, photo("r-a.jpg"))
        repo.addAttachment(id, photo("r-b.jpg"))
        assertEquals(2, repo.transactions.first().single().attachments)

        val a = repo.attachments(id).first().first()
        repo.deleteAttachment(a.id)
        assertFalse(File(dir, "r-a.jpg").exists())
        assertEquals(1, repo.transactions.first().single().attachments)

        // Deleting the entry removes its remaining photos too.
        repo.deleteTxn(repo.getTxn(id)!!)
        assertFalse(File(dir, "r-b.jpg").exists())
        assertTrue(db.backupDao().attachments().isEmpty())
    }

    @Test fun cleanUpKeepsRecentAndUsedFiles() = runBlocking {
        val id = buy(100)
        repo.addAttachment(id, photo("r-used.jpg"))
        val old = File(dir, photo("r-orphan-old.jpg")).apply { setLastModified(System.currentTimeMillis() - 3 * 24 * 3600 * 1000L) }
        val fresh = File(dir, photo("r-orphan-new.jpg"))
        repo.cleanUpReceipts()
        assertFalse(old.exists())
        assertTrue("may belong to an entry being added right now", fresh.exists())
        assertTrue(File(dir, "r-used.jpg").exists())
    }

    @Test fun warrantyRemindersAndList() = runBlocking {
        val id = buy(42_000)
        repo.setWarranty(id, today.plusYears(1))
        val row = repo.transactions.first().single()
        assertEquals(today.plusYears(1).toEpochDay(), row.warrantyUntil)
        assertEquals(1, repo.warranties.first().size)

        assertTrue(repo.warrantiesToRemind(today).isEmpty())
        val first = repo.warrantiesToRemind(today.plusYears(1).minusDays(30))
        assertEquals(1, first.single().second.stage)
        assertTrue("not again", repo.warrantiesToRemind(today.plusYears(1).minusDays(10)).isEmpty())
        assertEquals(2, repo.warrantiesToRemind(today.plusYears(1)).single().second.stage)

        // A new date starts over; clearing removes it.
        repo.setWarranty(id, today.plusYears(2))
        assertEquals(0, repo.warrantyOf(id)!!.stage)
        repo.setWarranty(id, null)
        assertNull(repo.warrantyOf(id))
        assertNull(repo.transactions.first().single().warrantyUntil)
    }

    @Test fun goalsAddAndTakeOut() = runBlocking {
        val goalId = repo.saveGoal(Goal(name = "  Goa   trip ", emoji = "", targetPaise = 50_000_00))
        val g = repo.goals.first().single()
        assertEquals("Goa trip", g.goal.name)
        assertEquals("🎯", g.goal.emoji)

        repo.addToGoal(goalId, 20_000_00, today, PaymentMode.UPI, null, null)
        repo.addToGoal(goalId, 15_000_00, today, PaymentMode.UPI, null, null)
        assertTrue(runCatching { repo.withdrawFromGoal(goalId, 40_000_00, today, 35_000_00) }.exceptionOrNull() is UserError)
        repo.withdrawFromGoal(goalId, 3_000_00, today, 35_000_00)

        val r = repo.goals.first().single()
        assertEquals(35_000_00L, r.savedPaise)
        assertEquals(3_000_00L, r.withdrawnPaise)
        assertEquals(32_000_00L, r.progress)
        val txns = repo.goalTxns(goalId).first()
        assertEquals(3, txns.size)
        assertEquals(2, txns.count { it.txn.type == TxnType.SAVING })   // counts as Saving
        assertEquals(1, txns.count { it.txn.type == TxnType.TRANSFER }) // not spending, not income

        // Deleting the goal keeps the savings entries.
        repo.deleteGoal(goalId)
        assertTrue(repo.goals.first().isEmpty())
        assertEquals(3, repo.transactions.first().size)
        assertTrue(repo.transactions.first().all { it.txn.goalId == null })
    }

    @Test fun goalValidation() = runBlocking {
        assertTrue(runCatching { repo.saveGoal(Goal(name = " ", emoji = "🎯", targetPaise = 100)) }.exceptionOrNull() is UserError)
        assertTrue(runCatching { repo.saveGoal(Goal(name = "x", emoji = "🎯", targetPaise = 0)) }.exceptionOrNull() is UserError)
    }

    @Test fun zipBackupKeepsPhotosWarrantiesAndGoals() = runBlocking {
        repo.ensureSeeded()
        val id = buy(42_000)
        val bytes = ByteArray(5_000) { (it % 251).toByte() }
        repo.addAttachment(id, photo("r-tv.jpg", bytes))
        repo.setWarranty(id, today.plusYears(2))
        val goalId = repo.saveGoal(Goal(name = "Bike", emoji = "🏍️", targetPaise = 1_00_000_00))
        repo.addToGoal(goalId, 10_000_00, today, PaymentMode.UPI, null, null)

        val out = ByteArrayOutputStream()
        repo.exportBackup(out)
        val zip = out.toByteArray()
        assertEquals("a .zip", 'P'.code.toByte(), zip[0])

        // Lose everything, photo file included…
        repo.deleteTxn(repo.getTxn(id)!!)
        repo.deleteGoal(goalId)
        assertFalse(File(dir, "r-tv.jpg").exists())

        // …and get it all back.
        repo.importBackup(ByteArrayInputStream(zip))
        assertArrayEquals(bytes, File(dir, "r-tv.jpg").readBytes())
        val row = repo.transactions.first().first { it.txn.id == id }
        assertEquals(1, row.attachments)
        assertEquals(today.plusYears(2).toEpochDay(), row.warrantyUntil)
        assertEquals(10_000_00L, repo.goals.first().single().progress)
    }

    @Test fun oldJsonBackupsStillRestore() = runBlocking {
        repo.ensureSeeded()
        val catId = db.categoryDao().allCategories().first().id
        val json = """
            {"app":"KYE","format":1,"seedVersion":2,
             "categories":[{"id":$catId,"name":"Food & Dining","emoji":"🍛","color":1,"kind":"EXPENSE","sortOrder":0,"isArchived":false}],
             "subcategories":[],"recurringRules":[],"budgets":[],"keywords":[],
             "transactions":[{"id":7,"amountPaise":45000,"type":"EXPENSE","categoryId":$catId,"dateTime":1,"paymentMode":"UPI","note":"old","source":"MANUAL"}]}
        """.trimIndent()
        repo.importBackup(ByteArrayInputStream(json.toByteArray()))
        assertEquals("old", repo.getTxn(7)!!.note)
    }

    @Test fun zipCannotWriteOutsideTheReceiptsFolder() = runBlocking {
        repo.ensureSeeded()
        val out = ByteArrayOutputStream()
        repo.exportBackup(out)
        // Re-pack the backup with a sneaky "../" entry.
        val evil = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(evil).use { z ->
            java.util.zip.ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { src ->
                while (true) {
                    val e = src.nextEntry ?: break
                    z.putNextEntry(java.util.zip.ZipEntry(e.name)); z.write(src.readBytes()); z.closeEntry()
                }
            }
            z.putNextEntry(java.util.zip.ZipEntry("receipts/../../evil.txt")); z.write(byteArrayOf(9)); z.closeEntry()
        }
        repo.importBackup(ByteArrayInputStream(evil.toByteArray()))
        assertFalse(File(dir.parentFile, "evil.txt").exists())
        assertFalse(File(dir.parentFile?.parentFile, "evil.txt").exists())
    }

    @Test fun goldSchemeInstalmentsRemindersAndBackup() = runBlocking {
        repo.ensureSeeded()
        val start = today.minusMonths(2) // instalments due 2 months ago, last month, today
        val id = repo.saveScheme(GoldScheme(name = "  GRT  Golden ", monthlyPaise = 5_000_00, months = 11, startDate = start.toEpochDay(), bonusPaise = 5_000_00, createdAt = Dates.atMillis(start, LocalTime.NOON)))
        assertTrue(runCatching { repo.saveScheme(GoldScheme(name = "x", monthlyPaise = 0, months = 11, startDate = 1)) }.exceptionOrNull() is UserError)

        // An instalment is a Saving entry under Savings → Gold Scheme.
        repo.paySchemeInstalment(id, 10_000_00, today, PaymentMode.UPI)
        val entry = repo.transactions.first().single { it.txn.schemeId == id }
        assertEquals(TxnType.SAVING, entry.txn.type)
        assertEquals("Gold Scheme", entry.subName)
        val r = repo.schemes.first().single()
        assertEquals("GRT Golden", r.scheme.name)
        assertEquals(2, com.kye.app.domain.GoldSchemes.instalmentsPaid(r))

        // Third instalment is due today: reminded once.
        assertEquals(1, repo.schemesToRemind(today).size)
        assertTrue(repo.schemesToRemind(today).isEmpty())

        // Backup round trip keeps the scheme and the tag.
        val out = ByteArrayOutputStream()
        repo.exportBackup(out)
        repo.deleteScheme(id)
        assertNull(repo.getTxn(entry.txn.id)!!.schemeId)
        repo.importBackup(ByteArrayInputStream(out.toByteArray()))
        assertEquals(id, repo.getTxn(entry.txn.id)!!.schemeId)
        assertEquals(10_000_00L, repo.schemes.first().single().paidPaise)

        // Closing stops reminders.
        repo.setSchemeClosed(id, true, today)
        assertTrue(repo.schemesToRemind(today.plusMonths(1)).isEmpty())

        // Monthly report sees the instalment as saving.
        val report = repo.monthlyReport(java.time.YearMonth.from(today), today)
        assertEquals(10_000_00L, report.saving)
    }

    @Test fun restorePreviewDescribesTheBackupWithoutChangingAnything() = runBlocking {
        repo.ensureSeeded()
        buy(100, "First")
        val out = ByteArrayOutputStream()
        repo.exportBackup(out)
        buy(200, "Second")

        val p = repo.previewBackup(ByteArrayInputStream(out.toByteArray()))
        assertEquals(1, p.entries)
        assertEquals(2, p.entriesHere)
        assertTrue(p.exportedAt != null)
        assertEquals(p.firstEntry, p.lastEntry)
        assertEquals("nothing replaced by a preview", 2, db.txnDao().allRows().size)

        // A damaged zip is refused with a clear message.
        val broken = out.toByteArray().copyOf(40)
        assertTrue(runCatching { repo.previewBackup(ByteArrayInputStream(broken)) }.exceptionOrNull() is UserError)
    }

    @Test fun bulkUpdateAndDeleteEntries() = runBlocking {
        val a = buy(100, "A")
        val b = buy(200, "B")
        val c = buy(300, "C")
        repo.addAttachment(b, photo("r-b.jpg"))
        repo.updateTxns(listOf(repo.getTxn(a)!!, repo.getTxn(b)!!).map { it.copy(paymentMode = PaymentMode.CASH) })
        assertEquals(PaymentMode.CASH, repo.getTxn(a)!!.paymentMode)
        assertEquals(PaymentMode.CASH, repo.getTxn(b)!!.paymentMode)
        assertEquals(PaymentMode.CREDIT_CARD, repo.getTxn(c)!!.paymentMode)

        repo.deleteTxns(listOf(repo.getTxn(a)!!, repo.getTxn(b)!!))
        assertNull(repo.getTxn(a))
        assertNull(repo.getTxn(b))
        assertFalse("photo file removed", File(dir, "r-b.jpg").exists())
        assertEquals(300_00L, repo.getTxn(c)!!.amountPaise)
    }
}
