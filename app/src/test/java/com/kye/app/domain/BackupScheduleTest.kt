package com.kye.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BackupScheduleTest {
    private val today = LocalDate.of(2026, 11, 7)

    @Test fun offIsNeverDue() {
        assertFalse(BackupSchedule.isDue(BackupFrequency.OFF, null, today))
        assertNull(BackupSchedule.nextDue(BackupFrequency.OFF, today.minusYears(1), today))
    }

    @Test fun neverBackedUpIsDueToday() {
        BackupFrequency.entries.filter { it != BackupFrequency.OFF }.forEach {
            assertTrue(it.name, BackupSchedule.isDue(it, null, today))
        }
    }

    @Test fun dailyGoesByCalendarDayNotHours() {
        // Backed up late yesterday: due this morning even though less than 24 hours passed.
        assertTrue(BackupSchedule.isDue(BackupFrequency.DAILY, today.minusDays(1), today))
        assertFalse(BackupSchedule.isDue(BackupFrequency.DAILY, today, today))
    }

    @Test fun weeklyAndMonthly() {
        assertFalse(BackupSchedule.isDue(BackupFrequency.WEEKLY, today.minusDays(6), today))
        assertTrue(BackupSchedule.isDue(BackupFrequency.WEEKLY, today.minusDays(7), today))
        assertEquals(today.plusWeeks(1), BackupSchedule.nextDue(BackupFrequency.WEEKLY, today, today))

        assertFalse(BackupSchedule.isDue(BackupFrequency.MONTHLY, LocalDate.of(2026, 10, 8), today))
        assertTrue(BackupSchedule.isDue(BackupFrequency.MONTHLY, LocalDate.of(2026, 10, 7), today))
        // Month-end: backed up 31 Jan → due 28 Feb.
        assertEquals(LocalDate.of(2026, 2, 28), BackupSchedule.nextDue(BackupFrequency.MONTHLY, LocalDate.of(2026, 1, 31), today))
    }

    @Test fun overdueStaysDue() {
        assertTrue(BackupSchedule.isDue(BackupFrequency.WEEKLY, today.minusMonths(3), today))
    }

    @Test fun fileNamesSortByTime() {
        val a = BackupSchedule.fileName(LocalDateTime.of(2026, 11, 7, 9, 5))
        val b = BackupSchedule.fileName(LocalDateTime.of(2026, 11, 7, 21, 15))
        assertEquals("KYE-auto-backup-2026-11-07-0905.json", a)
        assertTrue(a < b)
    }

    @Test fun keepsTheNewestFiveAndLeavesOtherFilesAlone() {
        val autos = (1..8).map { BackupSchedule.fileName(LocalDateTime.of(2026, 11, it, 9, 0)) }
        val names = autos.shuffled() + listOf("KYE-backup-2026-11-01.json", "notes.txt", "KYE-auto-backup-broken.txt")
        val deleted = BackupSchedule.toDelete(names)
        assertEquals(autos.take(3).toSet(), deleted.toSet())
        assertTrue(BackupSchedule.toDelete(autos.take(4)).isEmpty())
    }
}
