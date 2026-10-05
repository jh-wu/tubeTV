package com.tubetv.app.data.youtube

import com.tubetv.app.data.YouTubeSource
import com.tubetv.app.data.model.ChannelDetail
import com.tubetv.app.data.model.ChannelSummary
import com.tubetv.app.data.model.Page
import com.tubetv.app.data.model.Playback
import com.tubetv.app.data.model.VideoDetail
import com.tubetv.app.data.model.VideoSummary
import com.tubetv.app.data.youtube.Conversions.absolute
import com.tubetv.app.data.youtube.Conversions.isLive
import com.tubetv.app.data.youtube.Conversions.pick
import com.tubetv.app.data.youtube.Conversions.toSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import org.schabi.newpipe.extractor.Page as NpPage

/**
 * YouTube through NewPipeExtractor, which reads the same data the YouTube website and apps
 * use, without an API key or a Google account.
 */
class NewPipeSource(http: OkHttpClient, locale: Locale = Locale.getDefault()) : YouTubeSource {

    private val service: StreamingService = ServiceList.YouTube

    init {
        val country = locale.country.takeIf { it.length == 2 } ?: "US"
        NewPipe.init(OkHttpDownloader(http), Localization.fromLocale(locale), ContentCountry(country))
    }

    /** The video last opened, so going from its page to the player needs one request, not two. */
    private var cached: Pair<Long, StreamInfo>? = null

    override suspend fun searchVideos(query: String, page: Any?): Page<VideoSummary> =
        search(query, YoutubeSearchQueryHandlerFactory.VIDEOS, page) { items ->
            items.filterIsInstance<StreamInfoItem>().map { it.toSummary() }
        }

    override suspend fun searchChannels(query: String, page: Any?): Page<ChannelSummary> =
        search(query, YoutubeSearchQueryHandlerFactory.CHANNELS, page) { items ->
            items.filterIsInstance<ChannelInfoItem>().map { it.toSummary() }
        }

    private suspend fun <T> search(query: String, filter: String, page: Any?, map: (List<InfoItem>) -> List<T>): Page<T> = io {
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        if (page == null) {
            val info = SearchInfo.getInfo(service, handler)
            Page(map(info.relatedItems), info.nextPage.validOrNull())
        } else {
            val more = SearchInfo.getMoreItems(service, handler, page as NpPage)
            Page(map(more.items), more.nextPage.validOrNull())
        }
    }

    override suspend fun channel(url: String): ChannelDetail = io {
        val info = ChannelInfo.getInfo(service, url)
        ChannelDetail(
            url = info.url,
            name = info.name.orEmpty(),
            avatarUrl = info.avatars.pick(176)?.let(::absolute),
            bannerUrl = info.banners.pick(400)?.let(::absolute),
            subscriberCount = info.subscriberCount,
            description = info.description?.trim()?.ifBlank { null },
        )
    }

    override suspend fun channelVideos(url: String, page: Any?): Page<VideoSummary> = io {
        val id = service.channelLHFactory.fromUrl(url).id
        val tab = service.channelTabLHFactory.fromQuery(id, listOf(ChannelTabs.VIDEOS), "")
        if (page == null) {
            val info = ChannelTabInfo.getInfo(service, tab)
            Page(info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toSummary() }, info.nextPage.validOrNull())
        } else {
            val more = ChannelTabInfo.getMoreItems(service, tab, page as NpPage)
            Page(more.items.filterIsInstance<StreamInfoItem>().map { it.toSummary() }, more.nextPage.validOrNull())
        }
    }

    override suspend fun latestVideos(channelUrl: String): List<VideoSummary> = io {
        // The channel's RSS feed is one small request with exact upload times;
        // when YouTube doesn't serve it, the channel's video tab does the job.
        runCatching { FeedInfo.getInfo(service, channelUrl).relatedItems.map { it.toSummary() } }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: channelVideos(channelUrl, null).items
    }

    override suspend fun kiosk(id: String): List<VideoSummary> = io {
        val extractor = service.kioskList.getExtractorById(id, null)
        extractor.fetchPage()
        KioskInfo.getInfo(extractor).relatedItems.map { it.toSummary() }
    }

    override suspend fun related(url: String): List<VideoSummary> = io {
        // Only the watch page: StreamInfo would also unscramble every stream link, which is slow.
        val extractor = service.getStreamExtractor(url)
        extractor.fetchPage()
        extractor.relatedItems?.items.orEmpty().filterIsInstance<StreamInfoItem>().map { it.toSummary() }
    }

    override suspend fun video(url: String): VideoDetail = io {
        val info = streamInfo(url)
        VideoDetail(
            url = info.url,
            title = info.name.orEmpty(),
            thumbnailUrl = info.thumbnails.pick(720),
            description = info.description?.content?.let(::plainText)?.ifBlank { null },
            channel = info.uploaderUrl?.ifBlank { null }?.let {
                ChannelSummary(it, info.uploaderName.orEmpty(), info.uploaderAvatars.pick(176)?.let(::absolute), info.uploaderSubscriberCount)
            },
            durationSec = info.duration,
            viewCount = info.viewCount,
            uploaded = info.textualUploadDate?.ifBlank { null },
            isLive = info.streamType.isLive(),
            related = info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toSummary() },
        )
    }

    override suspend fun playback(url: String): Playback = io {
        val info = streamInfo(url)
        val sources = Conversions.playSources(
            hlsUrl = info.hlsUrl,
            dashUrl = info.dashMpdUrl,
            videoOnly = info.videoOnlyStreams.orEmpty(),
            audio = info.audioStreams.orEmpty(),
            muxed = info.videoStreams.orEmpty(),
        )
        if (sources.isEmpty()) error("YouTube 没有提供可播放的视频流")
        Playback(
            url = info.url,
            title = info.name.orEmpty(),
            thumbnailUrl = info.thumbnails.pick(720),
            channelName = info.uploaderName?.ifBlank { null },
            channelUrl = info.uploaderUrl?.ifBlank { null },
            isLive = info.streamType.isLive(),
            sources = sources,
            subtitles = Conversions.subtitleOptions(info.subtitles.orEmpty()),
            audioOptions = Conversions.audioOptions(info.audioStreams.orEmpty()),
            videoOptions = Conversions.videoOptions(info.videoOnlyStreams.orEmpty()),
        )
    }

    override fun forget(url: String) = synchronized(this) {
        cached = null
    }

    private fun streamInfo(url: String): StreamInfo {
        synchronized(this) {
            cached?.let { (at, info) ->
                if (info.url == url && System.currentTimeMillis() - at < CACHE_MS) return info
            }
        }
        return StreamInfo.getInfo(service, url).also {
            synchronized(this) { cached = System.currentTimeMillis() to it }
        }
    }

    private fun NpPage?.validOrNull(): NpPage? = this?.takeIf { NpPage.isValid(it) }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        /** Stream links stay valid for hours; a few minutes is plenty to get from a video's page to its player. */
        const val CACHE_MS = 5 * 60_000L

        /** YouTube descriptions arrive as HTML. */
        fun plainText(html: String): String =
            html.replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
                .trim()
    }
}
