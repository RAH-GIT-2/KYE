package com.kye.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class BackupFrequency(val label: String) {
    OFF("Off"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

/**
 * When automatic backups are due and which old ones to delete. Pure Kotlin, unit-tested.
 * Due-ness is by calendar date, not exact hours, so a job that runs a little early or late
 * never skips a day.
 */
object BackupSchedule {
    const val PREFIX = "KYE-auto-backup-"
    const val KEEP = 5

    private val NAME_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

    /** The next date a backup is due, or null when automatic backup is off. Never backed up = today. */
    fun nextDue(frequency: BackupFrequency, lastBackup: LocalDate?, today: LocalDate): LocalDate? {
        if (frequency == BackupFrequency.OFF) return null
        val last = lastBackup ?: return today
        return when (frequency) {
            BackupFrequency.DAILY -> last.plusDays(1)
            BackupFrequency.WEEKLY -> last.plusWeeks(1)
            BackupFrequency.MONTHLY -> last.plusMonths(1)
            BackupFrequency.OFF -> null
        }
    }

    fun isDue(frequency: BackupFrequency, lastBackup: LocalDate?, today: LocalDate): Boolean =
        nextDue(frequency, lastBackup, today)?.let { !it.isAfter(today) } ?: false

    /** "KYE-auto-backup-2026-11-07-2115.json": sorts oldest → newest by name. */
    fun fileName(at: LocalDateTime): String = PREFIX + at.format(NAME_TIME) + ".json"

    /** Automatic backups beyond the newest [keep]. Files the user saved themselves are never touched. */
    fun toDelete(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter { it.startsWith(PREFIX) && it.endsWith(".json") }
            .sortedDescending()
            .drop(keep)
}
