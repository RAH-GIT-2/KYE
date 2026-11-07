package com.kye.app.work

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.kye.app.KyeApp
import com.kye.app.data.PendingTxn
import com.kye.app.domain.PaymentParser
import kotlinx.coroutines.launch

/**
 * Reads payment notifications from UPI apps and SMS apps (for bank SMS) and queues them on the
 * Home screen for the user to confirm. Only the listed apps are read; everything else is ignored
 * without being looked at. Nothing is saved as an entry until the user taps Add.
 */
class PaymentListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = SOURCES[sbn.packageName] ?: return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = if (big.length > text.length) big else text
        val full = "$title. $body".take(600)

        val payment = PaymentParser.parse(full) ?: return
        val posted = sbn.postTime
        val pending = PendingTxn(
            amountPaise = payment.amountPaise,
            isCredit = payment.isCredit,
            merchant = payment.merchant,
            sourceApp = app,
            text = body.take(300),
            postedAt = posted,
            fingerprint = payment.ref?.let { "ref:$it" }
                ?: "${sbn.packageName}|${payment.amountPaise}|${payment.isCredit}|${payment.merchant}|${posted / 60_000}"
        )
        val container = (applicationContext as KyeApp).container
        container.scope.launch { container.repo.addPending(pending) }
    }

    companion object {
        /** Package → name shown to the user. */
        private val SOURCES = mapOf(
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "com.phonepe.app" to "PhonePe",
            "net.one97.paytm" to "Paytm",
            "in.org.npci.upiapp" to "BHIM",
            "com.dreamplug.androidapp" to "CRED",
            "in.amazon.mShop.android.shopping" to "Amazon Pay",
            "com.mobikwik_new" to "MobiKwik",
            "money.super.payments" to "super.money",
            "com.google.android.apps.messaging" to "SMS",
            "com.samsung.android.messaging" to "SMS",
            "com.android.mms" to "SMS",
            "com.oneplus.mms" to "SMS",
            "com.miui.smsextra" to "SMS",
            "com.truecaller" to "SMS"
        )

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
