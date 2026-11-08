package com.kye.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kye.app.domain.Dates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EventRepositoryTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: KyeDatabase
    private lateinit var repo: Repository
    private val today = LocalDate.of(2026, 11, 1)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, KyeDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, Prefs(context))
    }

    @After fun tearDown() = db.close()

    private fun spend(rupees: Long, eventId: Long?, type: TxnType = TxnType.EXPENSE) = Txn(
        amountPaise = rupees * 100, type = type, categoryId = null, subcategoryId = null,
        dateTime = Dates.atMillis(today, LocalTime.NOON), paymentMode = PaymentMode.UPI, eventId = eventId
    )

    @Test fun spentCountsOnlyTaggedExpensesAndAlerts() = runBlocking {
        val id = repo.saveEvent(Event(name = "  Deepavali   2026 ", emoji = "", budgetPaise = 10_000_00))
        val e = db.eventDao().get(id)!!
        assertEquals("Deepavali 2026", e.name)
        assertEquals("🎉", e.emoji)

        assertNull(repo.saveChecked(spend(5_000, id), null).second)
        assertEquals("Deepavali 2026 budget 80% used", repo.saveChecked(spend(3_500, id), null).second)
        assertEquals("Deepavali 2026 budget exceeded", repo.saveChecked(spend(2_000, id), null).second)
        repo.saveChecked(spend(999, null), null)                      // not tagged
        repo.saveChecked(spend(50_000, id, TxnType.INCOME), null)     // income isn't spending

        val row = repo.events.first().single()
        assertEquals(10_500_00L, row.spentPaise)
        assertEquals(4, row.entries)
        assertEquals(4, repo.eventTxns(id).first().size)
    }

    @Test fun validation() = runBlocking {
        assertTrue(runCatching { repo.saveEvent(Event(name = " ", emoji = "🪔")) }.exceptionOrNull() is UserError)
        assertTrue(runCatching { repo.saveEvent(Event(name = "x", emoji = "🪔", budgetPaise = 0)) }.exceptionOrNull() is UserError)
        assertTrue(runCatching {
            repo.saveEvent(Event(name = "x", emoji = "🪔", startDate = today.toEpochDay(), endDate = today.minusDays(1).toEpochDay()))
        }.exceptionOrNull() is UserError)
    }

    @Test fun deletingKeepsEntriesUntaggedAndTagging() = runBlocking {
        val id = repo.saveEvent(Event(name = "Goa trip", emoji = "✈️"))
        val txnId = repo.saveChecked(spend(1_000, null), null).first
        repo.tagTxn(txnId, id)
        assertEquals(id, repo.getTxn(txnId)!!.eventId)
        repo.deleteEvent(id)
        assertTrue(repo.events.first().isEmpty())
        assertNull(repo.getTxn(txnId)!!.eventId)
        assertEquals(1_000_00L, repo.getTxn(txnId)!!.amountPaise)
    }

    @Test fun eventsSurviveBackupAndRestore() = runBlocking {
        repo.ensureSeeded()
        val id = repo.saveEvent(Event(name = "Deepavali 2026", emoji = "🪔", budgetPaise = 25_000_00, startDate = today.toEpochDay()))
        val txnId = repo.saveChecked(spend(4_500, id), null).first
        repo.setEventClosed(id, true)

        val out = ByteArrayOutputStream()
        repo.exportBackup(out)
        repo.deleteEvent(id)
        repo.importBackup(ByteArrayInputStream(out.toByteArray()))

        val e = db.eventDao().get(id)!!
        assertEquals("Deepavali 2026", e.name)
        assertEquals(25_000_00L, e.budgetPaise)
        assertTrue(e.isClosed)
        assertEquals(id, repo.getTxn(txnId)!!.eventId)
    }

    @Test fun csvExportHasTheEventName() = runBlocking {
        val id = repo.saveEvent(Event(name = "Goa trip", emoji = "✈️"))
        repo.saveChecked(spend(700, id), null)
        val out = ByteArrayOutputStream()
        repo.exportCsv(out)
        val lines = out.toString(Charsets.UTF_8.name()).trim().lines()
        assertTrue(lines[0].endsWith("Note,Event"))
        assertTrue(lines[1], lines[1].endsWith(",Goa trip"))
    }
}
