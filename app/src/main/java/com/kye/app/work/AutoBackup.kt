package com.kye.app.work

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kye.app.KyeApp
import com.kye.app.domain.BackupFrequency
import com.kye.app.domain.BackupSchedule
import com.kye.app.domain.Dates
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Automatic backups into a folder the user picked once. Runs from a twice-daily background job
 * and whenever the app opens; it only writes when a backup is due (see [BackupSchedule]).
 * Keeps the newest [BackupSchedule.KEEP] automatic backups and never touches other files.
 */
object AutoBackup {
    private val lock = Mutex()

    sealed interface Outcome {
        data object NotDue : Outcome
        data class Saved(val fileName: String) : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun runIfDue(context: Context, force: Boolean = false): Outcome = lock.withLock {
        val app = context.applicationContext as KyeApp
        val prefs = app.container.prefs
        val s = prefs.backupSettings()
        if (!force) {
            val last = s.lastAt?.let { Dates.toLocalDate(it) }
            if (!BackupSchedule.isDue(s.frequency, last, LocalDate.now())) return@withLock Outcome.NotDue
        }
        val folder = s.folderUri
        if (folder == null) {
            val msg = "No backup folder chosen"
            prefs.recordBackupError(msg)
            return@withLock Outcome.Failed(msg)
        }
        try {
            val name = BackupSchedule.fileName(LocalDateTime.now())
            write(context, Uri.parse(folder), name) { out -> app.container.repo.exportBackup(out) }
            prune(context, Uri.parse(folder))
            prefs.recordBackup(System.currentTimeMillis(), auto = true)
            Outcome.Saved(name)
        } catch (e: SecurityException) {
            val msg = "KYE can no longer open the backup folder. Choose it again."
            prefs.recordBackupError(msg)
            Outcome.Failed(msg)
        } catch (e: Exception) {
            val msg = "Couldn't save the backup (${e.message ?: e.javaClass.simpleName}). Is the folder still there?"
            prefs.recordBackupError(msg)
            Outcome.Failed(msg)
        }
    }

    private fun folderDoc(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private suspend fun write(context: Context, tree: Uri, name: String, body: suspend (java.io.OutputStream) -> Unit) {
        val resolver = context.contentResolver
        val doc = DocumentsContract.createDocument(resolver, folderDoc(tree), "application/json", name)
            ?: error("folder not writable")
        try {
            resolver.openOutputStream(doc, "wt")?.use { body(it) } ?: error("no stream")
        } catch (e: Exception) {
            // Don't leave a half-written file behind.
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw e
        }
    }

    private fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = HashMap<String, String>() // name → document id
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) found[c.getString(1) ?: continue] = c.getString(0)
        }
        BackupSchedule.toDelete(found.keys.toList()).forEach { name ->
            runCatching {
                DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, found.getValue(name)))
            }
        }
    }

    /** The folder's name for display, e.g. "Download" or "KYE backups". */
    fun folderName(context: Context, folder: String?): String? {
        if (folder == null) return null
        return runCatching {
            context.contentResolver.query(
                folderDoc(Uri.parse(folder)), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(12, java.util.concurrent.TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("kye-backup", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val s = (applicationContext as KyeApp).container.prefs.backupSettings()
        if (s.frequency != BackupFrequency.OFF) AutoBackup.runIfDue(applicationContext)
        // A failure is shown in Settings; retrying every few minutes wouldn't fix a missing folder.
        return Result.success()
    }
}
