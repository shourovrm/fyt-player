package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.core.SearchPage
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.core.Topic
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.Period
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeLockupStreamInfoItemExtractor
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamInfoItemExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector

/** Official YouTube topic-channel browseIds -- verified live 2026-08-24: each `browse` response
 *  returns dozens of `videoRenderer`s under a `richShelfRenderer` on the channel's first tab. */
private val BROWSE_IDS = mapOf(
    Topic.NEWS to "UCYfdidRxbB8Qhf0Nx7ioOYw",
    Topic.SPORTS to "UCEgdi0XIXXZ-qJOFPf4JSKw",
    Topic.LIVE to "UC4R8DWoMoI7CAwX8_LjQHig",
)

/**
 * One-shot `browse` fetch for a [Topic]'s official channel feed. Mirrors
 * YoutubeTrendingExtractor's richShelfRenderer walk (PipePipeExtractor, same file the Live topic's
 * browseId came from) but parameterised over [BROWSE_IDS] instead of hardcoding the trending
 * channel. Single page only -- this browse shape carries no continuation token.
 */
internal object YoutubeTopicFeed {
    fun fetch(topic: Topic): SearchPage = guarded {
        // Charts, not topic channels, for these two: the "Music" topic channel is an
        // undocumented editorial feed; charts.youtube.com is the real, stable list.
        if (topic == Topic.MOVIES) return@guarded chart("TRENDING_MOVIES")
        if (topic == Topic.MUSIC) return@guarded chart("VIDEOS", period = "DAILY", country = "global")
        val browseId = BROWSE_IDS.getValue(topic)
        val localization = NewPipe.getPreferredLocalization()
        val country = NewPipe.getPreferredContentCountry()
        val body = JsonWriter.string(
            YoutubeParsingHelper.prepareDesktopJsonBuilder(localization, country)
                .value("browseId", browseId)
                .done(),
        ).toByteArray(StandardCharsets.UTF_8)
        val response = YoutubeParsingHelper.getJsonPostResponse("browse", body, localization)

        val timeAgoParser = ServiceList.YouTube.getTimeAgoParser(localization)
        val collector = StreamInfoItemsCollector(ServiceList.YouTube.serviceId)
        // Music's shelves use the newer lockupViewModel (album/playlist/video); News/Sports still
        // ship videoRenderer. Only video lockups are streams -- albums/playlists are containers.
        itemContents(response).forEach { content ->
            when {
                content.has("videoRenderer") ->
                    collector.commit(YoutubeStreamInfoItemExtractor(content.getObject("videoRenderer"), timeAgoParser))
                content.getObject("lockupViewModel").getString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO" ->
                    collector.commit(YoutubeLockupStreamInfoItemExtractor(content.getObject("lockupViewModel"), timeAgoParser))
            }
        }

        val seen = HashSet<String>()
        val items = collector.items.mapNotNull { it.toStreamVideoRef() }.filter { seen.add(it.pageUrl) }
        SearchPage(items = items, nextPage = null)
    }

    /** Walks tabs[0]'s richGridRenderer and returns each richItemRenderer's `content` object: a
     *  bare `richItemRenderer` entry sits directly on the grid; a `richSectionRenderer` wraps a
     *  shelf -- only `richShelfRenderer` shelves hold items, `reelShelfRenderer` (Shorts) is
     *  deliberately never matched, so those rows are skipped. */
    private fun itemContents(response: JsonObject): List<JsonObject> {
        val tab0 = response.getObject("contents").getObject("twoColumnBrowseResultsRenderer")
            .getArray("tabs").filterIsInstance<JsonObject>().firstOrNull() ?: return emptyList()
        val gridContents = tab0.getObject("tabRenderer").getObject("content")
            .getObject("richGridRenderer").getArray("contents").filterIsInstance<JsonObject>()

        val out = mutableListOf<JsonObject>()
        for (content in gridContents) {
            if (content.has("richItemRenderer")) {
                out += content.getObject("richItemRenderer").getObject("content")
                continue
            }
            val shelf = content.getObject("richSectionRenderer").getObject("content").getObject("richShelfRenderer")
            shelf.getArray("contents").filterIsInstance<JsonObject>()
                .filter { it.has("richItemRenderer") }
                .forEach { out += it.getObject("richItemRenderer").getObject("content") }
        }
        return out
    }

    /** Public charts.youtube.com browse -- the same request upstream NewPipe's "Trending" kiosks
     *  send, no key, no cookie. [chartType] TRENDING_MOVIES = trailers (Movies has no topic
     *  channel with free videos), VIDEOS + DAILY = charts.youtube.com "Daily top music videos" (user
     *  choice over WEEKLY: more local, moves faster). Country is
     *  the user's content-country setting and is what actually selects the list. VIDEOS is
     *  rejected (HTTP 400) without a [period]; TRENDING_MOVIES takes none. */
    private fun chart(
        chartType: String,
        period: String? = null,
        // "global" = the worldwide chart; the Music chip uses it on purpose (user choice: Music
        // = global trending, the country-flavoured feed is the Local chip's job).
        country: String = NewPipe.getPreferredContentCountry().countryCode,
    ): SearchPage {
        val localization = NewPipe.getPreferredLocalization()
        val body = JsonWriter.string()
            .`object`()
                .`object`("context").`object`("client")
                    .value("clientName", "WEB_MUSIC_ANALYTICS").value("clientVersion", "2.0")
                    .value("hl", localization.localizationCode).value("gl", country)
                .end().end()
                .value("browseId", "FEmusic_analytics_charts_home")
                .value("query", "perspective=CHART_DETAILS&chart_params_country_code=$country&chart_params_chart_type=$chartType" + (period?.let { "&chart_params_period_type=$it" } ?: ""))
            .end().done().toByteArray(StandardCharsets.UTF_8)
        val headers = mapOf(
            "Content-Type" to listOf("application/json"),
            "Origin" to listOf("https://charts.youtube.com"),
            "Referer" to listOf("https://charts.youtube.com/"),
            "X-YouTube-Client-Name" to listOf("67"),
            "X-YouTube-Client-Version" to listOf("2.0"),
        )
        val raw = NewPipe.getDownloader()
            .post("https://charts.youtube.com/youtubei/v1/browse?alt=json&prettyPrint=false", headers, body, localization)
            .responseBody()
        val videos = JsonParser.`object`().from(raw).getObject("contents").getObject("sectionListRenderer")
            .getArray("contents").filterIsInstance<JsonObject>().firstOrNull()
            ?.getObject("musicAnalyticsSectionRenderer")?.getObject("content")
            ?.getArray("videos")?.filterIsInstance<JsonObject>()?.firstOrNull()
            ?.getArray("videoViews")?.filterIsInstance<JsonObject>().orEmpty()
        val items = videos.mapNotNull { v ->
            val id = v.getString("id") ?: return@mapNotNull null
            val channelId = v.getString("externalChannelId")
            VideoRef(
                sourceId = "youtube",
                pageUrl = "https://www.youtube.com/watch?v=$id",
                remoteId = id,
                title = v.getString("title") ?: return@mapNotNull null,
                thumbnailUrl = v.getObject("thumbnail").getArray("thumbnails").filterIsInstance<JsonObject>()
                    .lastOrNull()?.getString("url"),
                durationSeconds = v.getInt("videoDuration", -1).takeIf { it > 0 },
                uploader = v.getString("channelName"),
                uploaderUrl = channelId?.let { "https://www.youtube.com/channel/$it" },
                // Charts gives no view count, only chart position and release date -- viewCountText
                // stays null rather than showing a fake or misleading number.
                uploadedText = releaseAge(v.getObject("releaseDate")),
            )
        }
        return SearchPage(items = items, nextPage = null)
    }

    private fun releaseAge(releaseDate: JsonObject): String? {
        val year = releaseDate.getInt("year", -1).takeIf { it > 0 } ?: return null
        val month = releaseDate.getInt("month", -1).takeIf { it in 1..12 } ?: return null
        val day = releaseDate.getInt("day", -1).takeIf { it in 1..31 } ?: return null
        val then = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        val days = Period.between(then, LocalDate.now()).days.toLong().coerceAtLeast(0)
        val months = Period.between(then, LocalDate.now()).toTotalMonths()
        return when {
            months >= 12 -> "${months / 12} year${if (months / 12 == 1L) "" else "s"} ago"
            months >= 1 -> "$months month${if (months == 1L) "" else "s"} ago"
            days >= 1 -> "$days day${if (days == 1L) "" else "s"} ago"
            else -> "today"
        }
    }
}
