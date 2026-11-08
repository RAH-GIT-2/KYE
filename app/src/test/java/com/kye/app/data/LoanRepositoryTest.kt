package com.kye.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LoanRepositoryTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: KyeDatabase
    private lateinit var repo: Repository
    private val today = LocalDate.of(2026, 11, 15)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, KyeDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, Prefs(context))
    }

    @After fun tearDown() = db.close()

    private fun loan(rupees: Long, due: LocalDate? = null, person: String = "  Ramesh   K ") = Loan(
        person = person, direction = LoanDirection.LENT, amountPaise = rupees * 100,
        date = today.minusDays(8).toEpochDay(), dueDate = due?.toEpochDay()
    )

    @Test fun partRepaymentsThenClosesWhenFullyRepaid() = runBlocking {
        val id = repo.saveLoan(loan(2_000))
        assertEquals("name tidied", "Ramesh K", repo.loanRow(id)!!.loan.person)

        repo.addLoanPayment(id, 500_00, today, "GPay")
        assertEquals(1_500_00L, repo.loanRow(id)!!.remaining)
        assertFalse(repo.loanRow(id)!!.loan.isClosed)

        assertTrue("can't repay more than is left",
            runCatching { repo.addLoanPayment(id, 1_600_00, today, "") }.exceptionOrNull() is UserError)

        repo.addLoanPayment(id, 1_500_00, today, "")
        assertTrue(repo.loanRow(id)!!.loan.isClosed)
        assertTrue(runCatching { repo.setLoanClosed(id, false) }.exceptionOrNull() is UserError)
    }

    @Test fun deletingARepaymentReopens() = runBlocking {
        val id = repo.saveLoan(loan(1_000))
        repo.addLoanPayment(id, 1_000_00, today, "")
        assertTrue(repo.loanRow(id)!!.loan.isClosed)
        val paymentId = db.backupDao().loanPayments().single().id
        repo.deleteLoanPayment(paymentId)
        assertFalse(repo.loanRow(id)!!.loan.isClosed)
        assertEquals(1_000_00L, repo.loanRow(id)!!.remaining)
    }

    @Test fun markSettledWithMoneyLeftAndReopen() = runBlocking {
        val id = repo.saveLoan(loan(1_000))
        repo.setLoanClosed(id, true)
        assertTrue(repo.loanRow(id)!!.loan.isClosed)
        repo.setLoanClosed(id, false)
        assertFalse(repo.loanRow(id)!!.loan.isClosed)
    }

    @Test fun validation() = runBlocking {
        assertTrue(runCatching { repo.saveLoan(loan(100, person = " ")) }.exceptionOrNull() is UserError)
        assertTrue(runCatching { repo.saveLoan(loan(0)) }.exceptionOrNull() is UserError)
        assertTrue("due before given", runCatching { repo.saveLoan(loan(100, due = today.minusDays(30))) }.exceptionOrNull() is UserError)
        // Editing the amount below what's been repaid is refused.
        val id = repo.saveLoan(loan(1_000))
        repo.addLoanPayment(id, 600_00, today, "")
        val l = repo.loanRow(id)!!.loan
        assertTrue(runCatching { repo.saveLoan(l.copy(amountPaise = 500_00)) }.exceptionOrNull() is UserError)
    }

    @Test fun remindersOnceAWeek() = runBlocking {
        repo.saveLoan(loan(1_000, due = today))
        repo.saveLoan(loan(500, due = today.plusDays(3)))
        assertEquals(1, repo.loansToRemind(today).size)
        assertTrue("not again tomorrow", repo.loansToRemind(today.plusDays(1)).isEmpty())
        // A week later both are due/overdue: the first again, the second for the first time.
        assertEquals(2, repo.loansToRemind(today.plusDays(7)).size)
    }

    @Test fun newDueDateGetsItsOwnReminder() = runBlocking {
        val id = repo.saveLoan(loan(1_000, due = today))
        assertEquals(1, repo.loansToRemind(today).size)
        repo.saveLoan(repo.loanRow(id)!!.loan.copy(dueDate = today.plusDays(2).toEpochDay()))
        assertEquals(1, repo.loansToRemind(today.plusDays(2)).size)
    }

    @Test fun loansSurviveBackupAndRestore() = runBlocking {
        repo.ensureSeeded()
        val id = repo.saveLoan(loan(2_000, due = today))
        repo.addLoanPayment(id, 500_00, today, "cash")
        val out = ByteArrayOutputStream()
        repo.exportBackup(out)
        repo.deleteLoan(id)
        assertTrue(db.backupDao().loans().isEmpty())

        repo.importBackup(ByteArrayInputStream(out.toByteArray()))
        val back = repo.loanRow(id)!!
        assertEquals("Ramesh K", back.loan.person)
        assertEquals(1_500_00L, back.remaining)
        assertEquals("cash", db.backupDao().loanPayments().single().note)
    }
}
