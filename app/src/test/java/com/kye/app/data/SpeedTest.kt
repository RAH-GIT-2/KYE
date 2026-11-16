package com.kye.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kye.app.domain.Dates
import com.kye.app.domain.Money
import com.kye.app.domain.Period
import com.kye.app.domain.Stats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/** Five years of heavy use (about 20,000 entries): how long the main screens' work takes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SpeedTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var db: KyeDatabase
    private lateinit var repo: Repository
    private val today = LocalDate.of(2026, 11, 15)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, KyeDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(db, Prefs(context))
        repo.ensureSeeded()
        val cats = db.categoryDao().allCategories().filter { !it.isArchived }
        val rnd = java.util.Random(7)
        val notes = listOf("Swiggy dinner", "Petrol", "Big basket", "Auto", "Chai", "Medical", "Movie", "EB bill")
        val txns = (0 until 5 * 365).flatMap { d ->
            val day = today.minusDays(d.toLong())
            (0 until 11).map {
                val c = cats[rnd.nextInt(cats.size)]
                Txn(
                    amountPaise = (50 + rnd.nextInt(5000)) * 100L,
                    type = if (rnd.nextInt(20) == 0) TxnType.INCOME else TxnType.EXPENSE,
                    categoryId = c.id, subcategoryId = null,
                    dateTime = Dates.atMillis(day, LocalTime.of(8 + it, 0)),
                    paymentMode = PaymentMode.UPI, note = notes[rnd.nextInt(notes.size)]
                )
            }
        }
        db.txnDao().insertAll(txns)
    }

    @After fun tearDown() = db.close()

    private inline fun <T> time(label: String, block: () -> T): T {
        block() // warm up
        val start = System.nanoTime()
        val out = block()
        println("SPEED $label: ${(System.nanoTime() - start) / 1_000_000} ms")
        return out
    }

    @Test fun timings() = runBlocking {
        val rows = time("load all entries") { db.txnDao().allRows() }
        val cats = db.categoryDao().allCategories().associateBy { it.id }
        val rules = emptyList<RecurringRule>()
        println("SPEED entries: ${rows.size}")
        time("home") { Stats.home(rows, rules, emptyMap(), cats, today) }
        time("analytics this month") { Stats.analytics(rows, Period.THIS_MONTH, rules, emptyMap(), cats, today) }
        time("month by category") { Stats.monthSpendByCategory(rows, today) }
        time("history group") { rows.groupBy { Dates.toLocalDate(it.txn.dateTime) } }
        time("history search") {
            rows.filter {
                it.title.lowercase().contains("swig") || it.txn.note.lowercase().contains("swig") ||
                    (it.catName?.lowercase()?.contains("swig") == true) || Money.plain(it.txn.amountPaise).contains("swig")
            }
        }
        time("first flow emit") { repo.transactions.first() }
        Unit
    }
}
