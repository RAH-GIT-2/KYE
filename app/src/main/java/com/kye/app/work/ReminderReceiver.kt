package com.kye.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.kye.app.KyeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles "Paid" and "Skip" on bill reminder notifications. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
        if (ruleId < 0) return
        val app = context.applicationContext as KyeApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = app.container.repo
                val rule = repo.getRule(ruleId)
                if (rule != null) {
                    when (intent.action) {
                        ACTION_PAID -> repo.markRulePaid(rule, rule.amountPaise)
                        ACTION_SKIP -> repo.skipRule(rule)
                    }
                }
                NotificationManagerCompat.from(context).cancel(ruleId.toInt())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAID = "com.kye.app.action.PAID"
        const val ACTION_SKIP = "com.kye.app.action.SKIP"
        const val EXTRA_RULE_ID = "rule_id"
    }
}
