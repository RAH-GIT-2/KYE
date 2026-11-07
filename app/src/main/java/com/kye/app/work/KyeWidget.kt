package com.kye.app.work

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kye.app.KyeApp
import com.kye.app.MainActivity
import com.kye.app.R
import com.kye.app.domain.Dates
import com.kye.app.domain.Money
import com.kye.app.domain.Stats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Home-screen widget: today's and this month's spend, + and mic buttons. */
class KyeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Called by the launcher (first placed, every 30 min, after reboot): read fresh numbers.
        val pending = goAsync()
        val app = context.applicationContext as KyeApp
        app.container.scope.launch {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun hasWidgets(context: Context): Boolean =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, KyeWidget::class.java)).isNotEmpty()

        /** Redraws every KYE widget. Cheap; does nothing when none is placed. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, KyeWidget::class.java))
            if (ids.isEmpty()) return
            val container = (context.applicationContext as KyeApp).container
            val settings = container.prefs.settings.first()
            val today = LocalDate.now()

            val views = RemoteViews(context.packageName, R.layout.widget_kye)
            if (settings.appLock) {
                // App lock is on: amounts stay private on the home screen.
                views.setTextViewText(R.id.widget_title, "KYE")
                views.setTextViewText(R.id.widget_today, "Locked")
                views.setTextViewText(R.id.widget_month, "Tap + or the mic to add")
            } else {
                val home = Stats.home(container.repo.transactions.first(), emptyList(), emptyMap(), emptyMap(), today)
                views.setTextViewText(R.id.widget_title, "KYE · Today")
                views.setTextViewText(R.id.widget_today, Money.format(home.today))
                views.setTextViewText(R.id.widget_month, "${Dates.monthName(today)} ${Money.format(home.spent)}")
            }
            views.setOnClickPendingIntent(R.id.widget_root, open(context, null, 10))
            views.setOnClickPendingIntent(R.id.widget_add, open(context, MainActivity.ACTION_ADD, 11))
            views.setOnClickPendingIntent(R.id.widget_mic, open(context, MainActivity.ACTION_VOICE, 12))
            manager.updateAppWidget(ids, views)
        }

        fun open(context: Context, action: String?, code: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (action != null) intent.putExtra(MainActivity.EXTRA_ACTION, action)
            return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
