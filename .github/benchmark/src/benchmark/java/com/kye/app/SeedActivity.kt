package com.kye.app

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.kye.app.data.CategoryKind
import com.kye.app.data.PaymentMode
import com.kye.app.data.Txn
import com.kye.app.data.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Speed-check builds only (never in the app that is sent out): fills KYE with about 1,500
 * made-up entries over the last 15 months, so scrolling can be measured with real-looking data.
 * Start with: adb shell am start -W -n com.kye.app/.SeedActivity
 */
class SeedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KyeApp
        thread {
            runBlocking {
                val repo = app.container.repo
                repo.ensureSeeded()
                val cats = repo.allCategories.first()
                val spend = cats.filter { it.kind == CategoryKind.EXPENSE }.map { it.id }
                val income = cats.firstOrNull { it.kind == CategoryKind.INCOME }?.id
                val saving = cats.firstOrNull { it.kind == CategoryKind.SAVING }?.id
                val notes = listOf("Swiggy dinner", "Auto to office", "Petrol", "Groceries", "Chai", "Movie", "Medicine", "Zomato lunch", "Electricity bill", "Recharge", "")
                val r = Random(42)
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now()
                val rows = ArrayList<Txn>()
                for (day in 0 until 450) {
                    val d = today.minusDays(day.toLong())
                    repeat(r.nextInt(0, 6)) {
                        val at = d.atTime(8 + r.nextInt(14), r.nextInt(60)).atZone(zone).toInstant().toEpochMilli()
                        rows += Txn(
                            amountPaise = (20 + r.nextInt(1500)) * 100L,
                            type = TxnType.EXPENSE,
                            categoryId = spend.randomOrNull(r),
                            subcategoryId = null,
                            dateTime = at,
                            paymentMode = PaymentMode.entries[r.nextInt(PaymentMode.entries.size)],
                            note = notes[r.nextInt(notes.size)]
                        )
                    }
                    if (d.dayOfMonth == 1) {
                        val at = d.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
                        rows += Txn(amountPaise = 75_000_00, type = TxnType.INCOME, categoryId = income, subcategoryId = null, dateTime = at, paymentMode = PaymentMode.entries[0], note = "Salary")
                        rows += Txn(amountPaise = 10_000_00, type = TxnType.SAVING, categoryId = saving, subcategoryId = null, dateTime = at, paymentMode = PaymentMode.entries[0], note = "SIP")
                    }
                }
                app.container.db.txnDao().insertAll(rows)
                Log.i("KYE-SEED", "added ${rows.size} entries")
            }
            runOnUiThread { finish() }
        }
    }
}
