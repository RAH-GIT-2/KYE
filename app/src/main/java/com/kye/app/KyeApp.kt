package com.kye.app

import android.app.Application
import android.content.Context
import com.kye.app.data.KyeDatabase
import com.kye.app.data.Prefs
import com.kye.app.data.Repository
import com.kye.app.work.Notifier
import com.kye.app.work.RecurringWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppContainer(context: Context) {
    val db: KyeDatabase = KyeDatabase.build(context)
    val repo = Repository(db)
    val prefs = Prefs(context)
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
        container.scope.launch {
            container.repo.ensureSeeded()
            runRecurringNow()
        }
    }

    /** Also run on every app open, in case the phone delayed the background job. */
    suspend fun runRecurringNow() {
        container.repo.processRecurring(LocalDate.now()).forEach { Notifier.showReminder(this, it) }
    }
}
