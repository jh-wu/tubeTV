package com.tubetv.app.ui.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubetv.app.data.Kiosks
import com.tubetv.app.data.YouTubeSource
import com.tubetv.app.data.library.BrowsedChannel
import com.tubetv.app.data.library.CachedFeed
import com.tubetv.app.data.library.ChannelLibrary
import com.tubetv.app.data.library.FeedCache
import com.tubetv.app.data.library.WatchHistoryDao
import com.tubetv.app.data.library.WatchRecord
import com.tubetv.app.data.model.ChannelSummary
import com.tubetv.app.data.model.VideoSummary
import com.tubetv.app.ui.common.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One list from several channels' uploads, newest first; videos without a date go last. */
fun mergeLatest(perChannel: List<List<VideoSummary>>, max: Int = 150): List<VideoSummary> =
    perChannel.flatten()
        .distinctBy { it.url }
        .sortedByDescending { it.uploadedAtMs ?: Long.MIN_VALUE }
        .take(max)

/** Takes one from each list in turn, so no single source fills the start; drops repeats. */
fun interleave(lists: List<List<VideoSummary>>): List<VideoSummary> {
    val out = LinkedHashMap<String, VideoSummary>()
    val longest = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until longest) for (list in lists) list.getOrNull(i)?.let { out.putIfAbsent(it.url, it) }
    return out.values.toList()
}

/** One of the 首页 tabs' video lists. */
data class FeedState(
    val videos: List<VideoSummary> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    /** False when 为你推荐 has nothing of the viewer's yet and shows YouTube's lists instead. */
    val personal: Boolean = true,
)

/** The newest uploads across the favourite channels. */
data class LatestState(
    val videos: List<VideoSummary> = emptyList(),
    val loading: Boolean = false,
    /** Channels whose videos could not be loaded. */
    val failed: List<String> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    private val source: YouTubeSource,
    private val history: WatchHistoryDao,
    private val library: ChannelLibrary,
    private val prefs: SharedPreferences,
    private val cache: FeedCache,
) : ViewModel() {

    /** The open tab (see [HomeTab]), kept across visits to a video and app restarts. */
    private val _selectedTab = MutableStateFlow(
        HomeTab.entries.getOrNull(prefs.getInt(KEY_TAB, HomeTab.Home.ordinal)) ?: HomeTab.Home,
    )
    val selectedTab: StateFlow<HomeTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: HomeTab) {
        if (_selectedTab.value == tab) return
        _selectedTab.value = tab
        prefs.edit().putInt(KEY_TAB, tab.ordinal).apply()
    }

    val continueWatching: StateFlow<List<WatchRecord>> =
        history.recent(30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null until the database has answered, so the tabs don't flash "no favourites". */
    val favourites: StateFlow<List<ChannelSummary>?> =
        library.favourites.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val browsed: StateFlow<List<BrowsedChannel>?> =
        library.browsed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _latest = MutableStateFlow(LatestState())
    val latest: StateFlow<LatestState> = _latest.asStateFlow()
    private var latestJob: Job? = null
    private var latestChannels: List<ChannelSummary> = emptyList()

    init {
        // Reload the newest videos whenever the set of favourites changes.
        viewModelScope.launch {
            library.favourites.distinctUntilChangedBy { list -> list.map { it.url }.toSet() }.collect {
                latestChannels = it
                refreshLatest()
            }
        }
    }

    /** Each channel's newest upload time we know of, by channel URL, for the "新" marks. */
    private val _newestUpload = MutableStateFlow<Map<String, Long>>(emptyMap())
    val newestUpload: StateFlow<Map<String, Long>> = _newestUpload.asStateFlow()
    private var browsedUpdatesJob: Job? = null

    private fun noteUploads(channelUrl: String, videos: List<VideoSummary>) {
        val newest = videos.mapNotNull { it.uploadedAtMs }.maxOrNull() ?: return
        _newestUpload.update { it + (channelUrl to newest) }
    }

    /** Whether a channel uploaded in the last week and since it was last opened ([lastVisitedAt]). */
    fun hasNew(channelUrl: String, lastVisitedAt: Long?, newest: Map<String, Long>): Boolean {
        val upload = newest[channelUrl] ?: return false
        return upload > System.currentTimeMillis() - NEW_WINDOW_MS && (lastVisitedAt == null || upload > lastVisitedAt)
    }

    /** Looks up the newest uploads of browsed channels (favourites come with 最新视频), once per app run. */
    fun loadBrowsedUpdates() {
        if (browsedUpdatesJob != null) return
        browsedUpdatesJob = viewModelScope.launch {
            val channels = library.browsed.first().take(BROWSED_UPDATES_MAX)
            val gate = Semaphore(CONCURRENT_CHANNELS)
            coroutineScope {
                channels.filter { it.url !in _newestUpload.value }.forEach { c ->
                    launch { gate.withPermit { runCatching { source.latestVideos(c.url) }.getOrNull()?.let { noteUploads(c.url, it) } } }
                }
            }
        }
    }

    fun refreshLatest() {
        latestJob?.cancel()
        latestJob = viewModelScope.launch {
            val channels = latestChannels
            if (channels.isEmpty()) {
                _latest.value = LatestState()
                return@launch
            }
            _latest.value = _latest.value.copy(loading = true, error = null)
            val gate = Semaphore(CONCURRENT_CHANNELS)
            val results = coroutineScope {
                channels.map { c ->
                    async { gate.withPermit { c to runCatching { source.latestVideos(c.url) } } }
                }.awaitAll()
            }
            results.forEach { (c, r) -> r.getOrNull()?.let { noteUploads(c.url, it) } }
            val failed = results.filter { it.second.isFailure }
            val videos = mergeLatest(results.mapNotNull { (c, r) -> r.getOrNull()?.map { v -> v.withChannel(c) } })
            _latest.value = LatestState(
                videos = videos,
                failed = failed.map { it.first.name },
                error = failed.takeIf { videos.isEmpty() && it.isNotEmpty() }
                    ?.first()?.second?.exceptionOrNull()?.userMessage(),
            )
        }
    }

    /** The open tab across the top of 首页, kept like [selectedTab]. */
    private val _selectedFeed = MutableStateFlow(
        FeedTab.entries.getOrNull(prefs.getInt(KEY_FEED, 0)) ?: FeedTab.ForYou,
    )
    val selectedFeed: StateFlow<FeedTab> = _selectedFeed.asStateFlow()

    private val _feeds = MutableStateFlow<Map<FeedTab, FeedState>>(emptyMap())
    val feeds: StateFlow<Map<FeedTab, FeedState>> = _feeds.asStateFlow()
    private val feedJobs = mutableMapOf<FeedTab, Job>()

    init {
        viewModelScope.launch {
            // Show the lists from last time straight away; reload only those that have gone stale.
            val now = System.currentTimeMillis()
            val cached = cache.load()
            _feeds.update { feeds ->
                feeds + FeedTab.entries.mapNotNull { tab ->
                    val c = cached[tab.name] ?: return@mapNotNull null
                    if (feeds[tab] != null) return@mapNotNull null
                    tab to FeedState(c.videos, loaded = now - c.savedAtMs < FEED_FRESH_MS, personal = c.personal)
                }
            }
            loadFeed(_selectedFeed.value)?.join()
            // Then the other tabs, one at a time, so switching to them is instant.
            for (tab in FeedTab.entries) loadFeed(tab)?.join()
        }
    }

    fun selectFeed(tab: FeedTab) {
        loadFeed(tab)
        if (_selectedFeed.value == tab) return
        _selectedFeed.value = tab
        prefs.edit().putInt(KEY_FEED, tab.ordinal).apply()
    }

    /**
     * Loads a 首页 tab the first time it opens, when its saved list is stale, or when [force]d (OK on
     * the tab, 重试). Returns the load, or null when the tab is already loaded or loading.
     */
    fun loadFeed(tab: FeedTab, force: Boolean = false): Job? {
        val current = _feeds.value[tab]
        if (!force && current != null && current.loading) return feedJobs[tab]
        if (!force && current != null && current.loaded && current.error == null) return null
        feedJobs[tab]?.cancel()
        return viewModelScope.launch {
            updateFeed(tab) { it.copy(loading = true, error = null) }
            try {
                val (videos, personal) = if (tab.kioskId != null) source.kiosk(tab.kioskId) to true else forYou()
                updateFeed(tab) { FeedState(videos, loaded = true, personal = personal) }
                saveFeeds()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed reload keeps the list it already had on screen.
                updateFeed(tab) { it.copy(loading = false, loaded = true, error = e.userMessage().takeIf { _ -> it.videos.isEmpty() }) }
            }
        }.also { feedJobs[tab] = it }
    }

    private suspend fun saveFeeds() {
        val now = System.currentTimeMillis()
        cache.save(
            _feeds.value.filterValues { it.loaded && it.error == null && it.videos.isNotEmpty() }
                .map { (tab, f) -> tab.name to CachedFeed(now, f.videos, f.personal) }.toMap(),
        )
    }

    private fun updateFeed(tab: FeedTab, change: (FeedState) -> FeedState) =
        _feeds.update { it + (tab to change(it[tab] ?: FeedState())) }

    /**
     * 为你推荐: videos related to what the viewer watched, mixed with new uploads from the
     * channels they follow or browse. With none of those yet, YouTube's own lists mixed together.
     */
    private suspend fun forYou(): Pair<List<VideoSummary>, Boolean> = coroutineScope {
        val watched = history.recent(RECOMMEND_FROM_WATCHED * 3).first()
        val channels = (library.favourites.first() + library.browsed.first().map { it.summary })
            .distinctBy { it.url }.take(RECOMMEND_FROM_CHANNELS)
        val gate = Semaphore(CONCURRENT_CHANNELS)
        val fromWatched = watched.take(RECOMMEND_FROM_WATCHED).map { r ->
            async { gate.withPermit { runCatching { source.related(r.videoUrl) }.getOrDefault(emptyList()) } }
        }
        val fromChannels = channels.map { c ->
            async { gate.withPermit { runCatching { source.latestVideos(c.url).take(6) }.getOrDefault(emptyList()) } }
        }
        val seen = watched.filter { it.isFinished }.map { it.videoUrl }.toSet()
        val picks = interleave(fromWatched.awaitAll() + fromChannels.awaitAll())
            .filterNot { it.url in seen }
            .take(RECOMMEND_MAX)
        if (picks.isNotEmpty()) return@coroutineScope picks to true

        val kiosks = FeedTab.entries.filter { it.kioskId != null }
            .map { tab -> async { tab to runCatching { source.kiosk(tab.kioskId!!) } } }
            .awaitAll()
        // The other tabs can show what was just fetched instead of asking again.
        kiosks.forEach { (tab, r) ->
            r.getOrNull()?.let { list -> if (_feeds.value[tab]?.loaded != true) updateFeed(tab) { FeedState(list, loaded = true) } }
        }
        val mixed = interleave(kiosks.mapNotNull { it.second.getOrNull() })
        if (mixed.isEmpty()) kiosks.firstNotNullOfOrNull { it.second.exceptionOrNull() }?.let { throw it }
        mixed.take(RECOMMEND_MAX) to false
    }

    fun removeFavourite(url: String) = viewModelScope.launch { library.removeFavourite(url) }
    fun addFavourite(channel: ChannelSummary) = viewModelScope.launch { library.addFavourite(channel) }
    fun forgetBrowsed(url: String) = viewModelScope.launch { library.forget(url) }
    fun clearBrowsed() = viewModelScope.launch { library.clearBrowsed() }

    companion object {
        private const val KEY_TAB = "selected_tab_v3"
        private const val KEY_FEED = "selected_feed"
        private const val CONCURRENT_CHANNELS = 4
        private const val RECOMMEND_FROM_WATCHED = 3
        private const val RECOMMEND_FROM_CHANNELS = 6
        private const val RECOMMEND_MAX = 40
        /** A saved list younger than this is shown without asking YouTube again. */
        private const val FEED_FRESH_MS = 30 * 60_000L
        private const val NEW_WINDOW_MS = 7 * 24 * 3_600_000L
        private const val BROWSED_UPDATES_MAX = 40

        private fun VideoSummary.withChannel(c: ChannelSummary) =
            copy(channelName = channelName ?: c.name, channelUrl = channelUrl ?: c.url)
    }
}

/** The sections in the bar down the right of the home screen. */
enum class HomeTab(val label: String) {
    Home("首页"),
    Continue("继续观看"),
    Latest("最新视频"),
    Favourites("收藏频道"),
    Browsed("浏览过的频道"),
}

/** The tabs across the top of 首页: picks for the viewer, then YouTube's public lists. */
enum class FeedTab(val label: String, val kioskId: String?) {
    ForYou("为你推荐", null),
    Live(Kiosks.title(Kiosks.LIVE), Kiosks.LIVE),
    Music(Kiosks.title(Kiosks.MUSIC), Kiosks.MUSIC),
    Gaming(Kiosks.title(Kiosks.GAMING), Kiosks.GAMING),
    Movies(Kiosks.title(Kiosks.MOVIES), Kiosks.MOVIES),
    Podcasts(Kiosks.title(Kiosks.PODCASTS), Kiosks.PODCASTS),
}
