package com.kye.app.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kye.app.MainActivity
import com.kye.app.R
import com.kye.app.data.GoldSchemeRow
import com.kye.app.data.LoanDirection
import com.kye.app.data.LoanRow
import com.kye.app.data.RecurringRule
import com.kye.app.data.Txn
import com.kye.app.data.Warranty
import com.kye.app.domain.Dates
import com.kye.app.domain.GoldSchemes
import com.kye.app.domain.Money
import java.time.LocalDate

object Notifier {
    const val CHANNEL_BILLS = "bills"

    const val CHANNEL_LOANS = "loans"
    const val CHANNEL_WARRANTY = "warranty"
    const val CHANNEL_SCHEMES = "schemes"

    /** "Samsung TV: warranty ends on 7 Nov 2027" (30 days before), then "ends today". */
    @SuppressLint("MissingPermission")
    fun showWarrantyReminder(context: Context, txn: Txn, w: Warranty) {
        if (!canNotify(context)) return
        val name = txn.note.ifBlank { "A purchase" }
        val until = LocalDate.ofEpochDay(w.untilDate)
        val title = if (w.stage >= 2) {
            if (until == LocalDate.now()) "$name: warranty ends today" else "$name: warranty has ended"
        } else "$name: warranty ends ${Dates.label(until)}"
        val id = 200_000 + txn.id.toInt()
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(context).notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_WARRANTY)
                .setSmallIcon(R.drawable.ic_stat_kye)
                .setContentTitle(title)
                .setContentText("Bought ${Dates.label(Dates.toLocalDate(txn.dateTime))} · Settings → Warranties has the bill")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    /** "Gold scheme: ₹5,000 due today · instalment 4 of 11". Amounts hidden while app lock is on. */
    @SuppressLint("MissingPermission")
    fun showSchemeReminder(context: Context, row: GoldSchemeRow, hideAmounts: Boolean) {
        if (!canNotify(context)) return
        val due = GoldSchemes.nextDue(row) ?: return
        val s = row.scheme
        val amount = if (hideAmounts) "Instalment" else Money.format(s.monthlyPaise)
        val whenText = if (due == LocalDate.now()) "due today" else "was due ${Dates.label(due)}"
        val id = 300_000 + s.id.toInt()
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(context).notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_SCHEMES)
                .setSmallIcon(R.drawable.ic_stat_kye)
                .setContentTitle("🪙 ${s.name}: $amount $whenText")
                .setContentText("Instalment ${GoldSchemes.instalmentsPaid(row) + 1} of ${s.months} · Settings → Gold schemes → Pay")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SCHEMES, "Gold schemes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a gold scheme instalment is due"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BILLS, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders for recurring bills like rent, EB and recharges"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LOANS, "Lent & borrowed", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When money you lent or borrowed is due"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WARRANTY, "Warranties", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "30 days before a warranty ends, and on the day"
            }
        )
    }

    /** "Ramesh owes you ₹1,500 · due 30 Nov". Tapping opens KYE. Amounts are hidden while app lock is on. */
    @SuppressLint("MissingPermission")
    fun showLoanReminder(context: Context, row: LoanRow, hideAmounts: Boolean) {
        if (!canNotify(context)) return
        val due = row.loan.dueDate?.let { LocalDate.ofEpochDay(it) } ?: return
        val amount = if (hideAmounts) "money" else Money.format(row.remaining)
        val title = when (row.loan.direction) {
            LoanDirection.LENT -> "${row.loan.person} owes you $amount"
            LoanDirection.BORROWED -> "You owe ${row.loan.person} $amount"
        }
        val whenText = if (due == LocalDate.now()) "Due today" else "Was due ${Dates.label(due)}"
        val id = 100_000 + row.loan.id.toInt()
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(context).notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_LOANS)
                .setSmallIcon(R.drawable.ic_stat_kye)
                .setContentTitle(title)
                .setContentText("$whenText · open KYE → Lent & borrowed")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun showReminder(context: Context, rule: RecurringRule) {
        if (!canNotify(context)) return
        val due = LocalDate.ofEpochDay(rule.nextDueDate)
        val whenText = Dates.label(due).let { if (it == "Today" || it == "Tomorrow") it.lowercase() else "on $it" }
        val amountText = if (rule.amountPaise > 0) " · ${Money.format(rule.amountPaise)}" else ""
        val id = rule.id.toInt()

        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_BILLS)
            .setSmallIcon(R.drawable.ic_stat_kye)
            .setContentTitle("${rule.name} due $whenText")
            .setContentText("Tap Paid once it's done$amountText")
            .setContentIntent(open)
            .setAutoCancel(true)

        if (rule.amountPaise > 0) {
            builder.addAction(0, "Paid", actionIntent(context, rule.id, ReminderReceiver.ACTION_PAID))
        }
        builder.addAction(0, "Skip", actionIntent(context, rule.id, ReminderReceiver.ACTION_SKIP))

        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun actionIntent(context: Context, ruleId: Long, action: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderReceiver.EXTRA_RULE_ID, ruleId)
        val code = (ruleId * 10 + if (action == ReminderReceiver.ACTION_PAID) 1 else 2).toInt()
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
