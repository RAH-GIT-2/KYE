package com.kye.app

import android.app.Application
import android.content.Context
import com.kye.app.data.KyeDatabase
import com.kye.app.data.Prefs
import com.kye.app.data.Repository
import com.kye.app.work.Notifier
import com.kye.app.work.AutoBackup
import com.kye.app.work.KyeWidget
import com.kye.app.work.RecurringWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppContainer(context: Context) {
    val db: KyeDatabase = KyeDatabase.build(context)
    val prefs = Prefs(context)
    val repo = Repository(db, prefs)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class KyeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
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
                    if (KyeWidget.hasWidgets(this@KyeApp)) KyeWidget.refresh(this@KyeApp)
                }
        }
    }

    /** Also run on every app open, in case the phone delayed the background job. */
    suspend fun runRecurringNow() {
        container.repo.processRecurring(LocalDate.now()).forEach { Notifier.showReminder(this, it) }
    }
}
