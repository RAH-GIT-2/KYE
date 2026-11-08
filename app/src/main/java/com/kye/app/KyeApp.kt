package com.kye.app

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.kye.app.data.KyeDatabase
import com.kye.app.data.Prefs
import com.kye.app.data.Receipts
import com.kye.app.data.Repository
import com.kye.app.work.AutoBackup
import com.kye.app.work.KyeWidget
import com.kye.app.work.Notifier
import com.kye.app.work.RecurringWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AppContainer(context: Context) {
    val db: KyeDatabase = KyeDatabase.build(context)
    val prefs = Prefs(context)
    val repo = Repository(db, prefs, Receipts.dir(context))

    /** Background work. A failure in one job (widget, backup…) is logged, never crashes the app. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> android.util.Log.e("KYE", "background job failed", e) }
    )
}

open class KyeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashNote()
        container = AppContainer(this)
        Notifier.createChannels(this)
        RecurringWorker.schedule(this)
        AutoBackup.schedule(this)
        container.scope.launch {
            container.repo.ensureSeeded()
            runRecurringNow()
            // Also on every app open, in case the phone delayed the background job.
            AutoBackup.runIfDue(this@KyeApp)
        }
        // Keep the home-screen widget in step with entries and the app-lock setting.
        container.scope.launch {
            combine(container.repo.transactions, container.prefs.settings) { _, s -> s.appLock }
                .collectLatest {
                    delay(300) // several saves in a row (voice, import) → one redraw
                    runCatching { if (KyeWidget.hasWidgets(this@KyeApp)) KyeWidget.refresh(this@KyeApp) }
                }
        }
    }

    /** Also run on every app open, in case the phone delayed the background job. */
    suspend fun runRecurringNow() {
        val today = LocalDate.now()
        container.repo.processRecurring(today).forEach { Notifier.showReminder(this, it) }
        val hide = container.prefs.settings.first().appLock
        container.repo.loansToRemind(today).forEach { Notifier.showLoanReminder(this, it, hide) }
        container.repo.warrantiesToRemind(today).forEach { (txn, w) -> Notifier.showWarrantyReminder(this, txn, w) }
        container.repo.cleanUpReceipts()
    }

    /**
     * If KYE crashes, write what happened to Downloads/KYE-crash-<time>.txt before closing, so the
     * user can send the file without any cable or developer tools. Only the error and phone model
     * are written, never entries or amounts. Android 10+ (older versions need a storage permission).
     */
    private fun installCrashNote() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeCrashNote(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun writeCrashNote(thread: Thread, error: Throwable) {
        if (Build.VERSION.SDK_INT < 29) return
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        val text = buildString {
            appendLine("KYE $version crashed at ${LocalDateTime.now()}")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }
        val name = "KYE-crash-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) + ".txt"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    }
}
