package com.fyiplayer.app.ui

import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.db.WatchHistoryEntity
import com.fyiplayer.app.data.repo.toVideoRef
import java.time.Instant
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** History date headers. Declaration order is display order, newest first. */
enum class HistoryGroup { TODAY, YESTERDAY, EARLIER_THIS_WEEK, EARLIER_THIS_MONTH, OLDER }

/** One date header and the videos under it, newest watch first. */
data class HistorySection(val group: HistoryGroup, val videos: List<VideoRef>)

/**
 * Buckets a watch instant by calendar day in the zone of [now] (the device's zone when [now] comes
 * from ZonedDateTime.now()). "This week" starts on the first day of the week for the device locale,
 * so a Sunday-start locale and a Monday-start locale split the same days differently.
 */
internal fun historyGroupOf(watchedAt: Long, now: ZonedDateTime): HistoryGroup {
    val watched = Instant.ofEpochMilli(watchedAt).atZone(now.zone).toLocalDate()
    val today = now.toLocalDate()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
    // A stamp after now (the clock moved back) reads as today; it must not fall through to Older.
    if (!watched.isBefore(today)) return HistoryGroup.TODAY
    if (watched == today.minusDays(1)) return HistoryGroup.YESTERDAY
    if (!watched.isBefore(weekStart)) return HistoryGroup.EARLIER_THIS_WEEK
    if (YearMonth.from(watched) == YearMonth.from(today)) return HistoryGroup.EARLIER_THIS_MONTH
    return HistoryGroup.OLDER
}

/** Non-Shorts history rows under their date headers, newest first. Groups with no rows are dropped. */
internal fun groupHistory(entries: List<WatchHistoryEntity>, now: ZonedDateTime): List<HistorySection> {
    val byGroup = entries.filterNot { it.isShort }.groupBy { historyGroupOf(it.watchedAt, now) }
    return HistoryGroup.entries
        .filter { byGroup.containsKey(it) }
        .map { group -> HistorySection(group, byGroup.getValue(group).map { it.toVideoRef() }) }
}
