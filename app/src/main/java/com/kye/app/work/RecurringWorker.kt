package com.kye.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kye.app.KyeApp
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Runs daily: posts due auto-add entries and shows bill reminders. Catches up if the phone was off. */
class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Bills and lent/borrowed reminders, same as on app open.
        (applicationContext as KyeApp).runRecurringNow()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "kye-recurring", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
