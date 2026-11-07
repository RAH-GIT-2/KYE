package com.kye.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kye.app.domain.Dates
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecurringAndBudgetTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: KyeDatabase
    private lateinit var repo: Repository
    private val today = LocalDate.of(2026, 11, 7)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, KyeDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, Prefs(context))
    }

    @After fun tearDown() = db.close()

    private fun rule(
        anchor: LocalDate,
        unit: RepeatUnit = RepeatUnit.MONTH,
        mode: RecurringMode = RecurringMode.AUTO_ADD,
        end: LocalDate? = null,
        remindBefore: Int = 0
    ) = RecurringRule(
        name = "Rent", amountPaise = 20_000_00, categoryId = null, subcategoryId = null,
        unit = unit, anchorDate = anchor.toEpochDay(), nextDueDate = anchor.toEpochDay(),
        endDate = end?.toEpochDay(), mode = mode, remindDaysBefore = remindBefore
    )

    private suspend fun entries() = db.txnDao().allRows().map { Dates.toLocalDate(it.txn.dateTime) }.sorted()

    @Test fun autoAddCatchesUpAfterThePhoneWasOffAndDoesNotRepeat() = runBlocking {
        val id = repo.saveRule(rule(today.minusMonths(3)))
        repo.processRecurring(today)
        assertEquals((3 downTo 0).map { today.minusMonths(it.toLong()) }, entries())
        assertEquals(today.plusMonths(1).toEpochDay(), repo.getRule(id)!!.nextDueDate)

        // Opening the app again the same day adds nothing more.
        repo.processRecurring(today)
        assertEquals(4, entries().size)
        assertTrue(db.txnDao().allRows().all { it.txn.source == Source.RECURRING && it.txn.recurringRuleId == id })
    }

    @Test fun nothingIsAddedBeforeTheDueDate() = runBlocking {
        repo.saveRule(rule(today.plusDays(1)))
        repo.processRecurring(today)
        assertTrue(entries().isEmpty())
    }

    @Test fun ruleStopsAfterItsEndDate() = runBlocking {
        val id = repo.saveRule(rule(today.minusMonths(5), end = today.minusMonths(4)))
        repo.processRecurring(today)
        assertEquals(listOf(today.minusMonths(5), today.minusMonths(4)), entries())
        assertFalse(repo.getRule(id)!!.isActive)
    }

    @Test fun remindOnlyNotifiesOnceAndAddsNothingByItself() = runBlocking {
        val id = repo.saveRule(rule(today.plusDays(2), mode = RecurringMode.REMIND_ONLY, remindBefore = 2))
        assertEquals(1, repo.processRecurring(today).size)
        assertTrue("same bill isn't reminded twice", repo.processRecurring(today).isEmpty())
        assertTrue(entries().isEmpty())

        // Paid: adds the entry with the amount typed in, moves to next month.
        repo.markRulePaid(repo.getRule(id)!!, 1_234_00, today)
        assertEquals(1_234_00L, db.txnDao().allRows().single().txn.amountPaise)
        val r = repo.getRule(id)!!
        assertEquals(today.plusDays(2).plusMonths(1).toEpochDay(), r.nextDueDate)
        assertNull(r.lastRemindedFor)
    }

    @Test fun remindOnlyWaitsUntilTheReminderDay() = runBlocking {
        repo.saveRule(rule(today.plusDays(5), mode = RecurringMode.REMIND_ONLY, remindBefore = 2))
        assertTrue(repo.processRecurring(today).isEmpty())
        assertEquals(1, repo.processRecurring(today.plusDays(3)).size)
    }

    @Test fun skipMovesOnWithoutAnEntry() = runBlocking {
        val id = repo.saveRule(rule(today, mode = RecurringMode.REMIND_ONLY))
        repo.skipRule(repo.getRule(id)!!)
        assertTrue(entries().isEmpty())
        assertEquals(today.plusMonths(1).toEpochDay(), repo.getRule(id)!!.nextDueDate)
    }

    private fun spend(rupees: Long, day: LocalDate = today, categoryId: Long? = 1) = Txn(
        amountPaise = rupees * 100, type = TxnType.EXPENSE, categoryId = categoryId, subcategoryId = null,
        dateTime = Dates.atMillis(day, LocalTime.NOON), paymentMode = PaymentMode.UPI
    )

    @Test fun budgetAlertsAt80And100Percent() = runBlocking {
        repo.setBudget(1, 1_000_00)
        assertNull(repo.saveWithBudgetCheck(spend(700), "Food"))
        assertEquals("Food budget 80% used", repo.saveWithBudgetCheck(spend(150), "Food"))
        assertNull("no repeat while still between 80 and 100", repo.saveWithBudgetCheck(spend(100), "Food"))
        assertEquals("Food budget exceeded", repo.saveWithBudgetCheck(spend(60), "Food"))
        assertNull("no repeat once over", repo.saveWithBudgetCheck(spend(500), "Food"))
    }

    @Test fun overallBudgetAndOtherMonths() = runBlocking {
        repo.setBudget(0, 1_000_00)
        repo.saveWithBudgetCheck(spend(5_000, today.minusMonths(1)), null) // October, against October's budget
        assertNull("October's spending doesn't count towards November", repo.saveWithBudgetCheck(spend(700, categoryId = null), null))
        assertEquals("Monthly budget exceeded", repo.saveWithBudgetCheck(spend(500, categoryId = null), null))
        // Income never triggers a budget alert.
        assertNull(repo.saveWithBudgetCheck(spend(50_000).copy(type = TxnType.INCOME), null))
    }

    @Test fun removingABudget() = runBlocking {
        repo.setBudget(1, 1_000_00)
        repo.setBudget(1, null)
        assertNull(repo.saveWithBudgetCheck(spend(5_000), "Food"))
    }
}
