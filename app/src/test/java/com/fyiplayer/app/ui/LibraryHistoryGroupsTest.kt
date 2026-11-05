package com.fyiplayer.app.ui

import com.fyiplayer.app.data.db.WatchHistoryEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

private val utc: ZoneId = ZoneOffset.UTC

private fun localAt(date: String, time: String, zone: ZoneId = utc): ZonedDateTime =
    ZonedDateTime.of(LocalDateTime.parse("${date}T$time"), zone)

private fun stampOf(date: String, time: String, zone: ZoneId = utc): Long =
    localAt(date, time, zone).toInstant().toEpochMilli()

/** Runs [block] with the JVM default locale set, because the week start is read from it. */
private fun withDefaultLocale(locale: Locale, block: () -> Unit) {
    val previous = Locale.getDefault()
    Locale.setDefault(locale)
    try {
        block()
    } finally {
        Locale.setDefault(previous)
    }
}

private fun entry(pageUrl: String, watchedAt: Long, isShort: Boolean = false) = WatchHistoryEntity(
    pageUrl = pageUrl,
    sourceId = "yt",
    title = pageUrl,
    uploader = null,
    durationSeconds = null,
    thumbnailUrl = null,
    watchedAt = watchedAt,
    isShort = isShort,
)

class LibraryHistoryGroupsTest {

    @Test fun `same calendar day is today and the day before is yesterday`() {
        val now = localAt("2026-11-05", "15:00")
        assertEquals(HistoryGroup.TODAY, historyGroupOf(stampOf("2026-11-05", "00:00"), now))
        assertEquals(HistoryGroup.YESTERDAY, historyGroupOf(stampOf("2026-11-04", "23:59"), now))
    }

    @Test fun `a stamp after now reads as today, not older`() {
        val now = localAt("2026-11-05", "15:00")
        assertEquals(HistoryGroup.TODAY, historyGroupOf(stampOf("2026-11-05", "18:00"), now))
    }

    @Test fun `Monday the 1st starts the week, so the Sunday before is older`() {
        withDefaultLocale(Locale.UK) {
            // 2026-06-01 is a Monday; under a Monday-start locale the week begins that day.
            val now = localAt("2026-06-03", "10:00")
            assertEquals(HistoryGroup.EARLIER_THIS_WEEK, historyGroupOf(stampOf("2026-06-01", "09:00"), now))
            assertEquals(HistoryGroup.OLDER, historyGroupOf(stampOf("2026-05-31", "20:00"), now))
            assertEquals(HistoryGroup.YESTERDAY, historyGroupOf(stampOf("2026-06-02", "08:00"), now))
        }
    }

    @Test fun `on the Monday the 1st the Saturday before is older, not this week`() {
        withDefaultLocale(Locale.UK) {
            val now = localAt("2026-06-01", "10:00")
            assertEquals(HistoryGroup.YESTERDAY, historyGroupOf(stampOf("2026-05-31", "12:00"), now))
            assertEquals(HistoryGroup.OLDER, historyGroupOf(stampOf("2026-05-30", "12:00"), now))
        }
    }

    @Test fun `Sunday-start locale puts the Sunday in this week, Monday-start puts it in this month`() {
        // 2026-11-01 is a Sunday; the week of Thursday 2026-11-05 starts Sunday 11-01 for US, Monday 11-02 for UK.
        val now = localAt("2026-11-05", "10:00")
        withDefaultLocale(Locale.US) {
            assertEquals(HistoryGroup.EARLIER_THIS_WEEK, historyGroupOf(stampOf("2026-11-01", "12:00"), now))
            assertEquals(HistoryGroup.OLDER, historyGroupOf(stampOf("2026-10-31", "12:00"), now))
        }
        withDefaultLocale(Locale.UK) {
            assertEquals(HistoryGroup.EARLIER_THIS_MONTH, historyGroupOf(stampOf("2026-11-01", "12:00"), now))
        }
    }

    @Test fun `earlier this month covers days outside the week and not the previous month`() {
        withDefaultLocale(Locale.US) {
            // Thursday 2026-11-19: this week starts Sunday 11-15.
            val now = localAt("2026-11-19", "10:00")
            assertEquals(HistoryGroup.EARLIER_THIS_WEEK, historyGroupOf(stampOf("2026-11-15", "08:00"), now))
            assertEquals(HistoryGroup.EARLIER_THIS_MONTH, historyGroupOf(stampOf("2026-11-14", "08:00"), now))
            assertEquals(HistoryGroup.EARLIER_THIS_MONTH, historyGroupOf(stampOf("2026-11-01", "08:00"), now))
            assertEquals(HistoryGroup.OLDER, historyGroupOf(stampOf("2026-10-31", "23:00"), now))
        }
    }

    @Test fun `December is older than January, even on the same weekday`() {
        withDefaultLocale(Locale.US) {
            // 2027-01-03 is a Sunday, so this week starts today and yesterday is 2027-01-02.
            val now = localAt("2027-01-03", "09:00")
            assertEquals(HistoryGroup.OLDER, historyGroupOf(stampOf("2026-12-31", "23:00"), now))
            assertEquals(HistoryGroup.YESTERDAY, historyGroupOf(stampOf("2027-01-02", "23:00"), now))
        }
    }

    @Test fun `the calendar day is taken in the zone of now`() {
        val watched = stampOf("2026-11-04", "23:30", utc)
        // 23:30 UTC on 11-04 is 01:30 on 11-05 in UTC+2: today there, yesterday in UTC.
        assertEquals(HistoryGroup.TODAY, historyGroupOf(watched, localAt("2026-11-05", "09:00", ZoneOffset.ofHours(2))))
        assertEquals(HistoryGroup.YESTERDAY, historyGroupOf(watched, localAt("2026-11-05", "07:00", utc)))
    }

    @Test fun `groups keep newest-first order, drop empty groups and leave out Shorts`() {
        withDefaultLocale(Locale.US) {
            val now = localAt("2026-11-05", "10:00")
            val entries = listOf(
                entry("today-a", stampOf("2026-11-05", "09:00")),
                entry("short", stampOf("2026-11-05", "08:00"), isShort = true),
                entry("older", stampOf("2026-10-02", "08:00")),
                entry("yesterday", stampOf("2026-11-04", "08:00")),
            )
            val sections = groupHistory(entries, now)
            assertEquals(
                listOf(HistoryGroup.TODAY, HistoryGroup.YESTERDAY, HistoryGroup.OLDER),
                sections.map { it.group },
            )
            assertEquals(listOf("today-a"), sections[0].videos.map { it.pageUrl })
            assertEquals(listOf("older"), sections[2].videos.map { it.pageUrl })
        }
    }
}
