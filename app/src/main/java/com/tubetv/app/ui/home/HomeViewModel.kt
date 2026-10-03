package com.tubetv.app.ui.home

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubetv.app.data.YouTubeSource
import com.tubetv.app.data.library.BrowsedChannel
import com.tubetv.app.data.library.ChannelLibrary
import com.tubetv.app.data.library.WatchHistoryDao
import com.tubetv.app.data.library.WatchRecord
import com.tubetv.app.data.model.ChannelSummary
import com.tubetv.app.data.model.VideoSummary
import com.tubetv.app.ui.common.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One list from several channels' uploads, newest first; videos without a date go last. */
fun mergeLatest(perChannel: List<List<VideoSummary>>, max: Int = 150): List<VideoSummary> =
    perChannel.flatten()
        .distinctBy { it.url }
        .sortedByDescending { it.uploadedAtMs ?: Long.MIN_VALUE }
        .take(max)

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
    history: WatchHistoryDao,
    private val library: ChannelLibrary,
    private val prefs: SharedPreferences,
) : ViewModel() {

    /** The open tab (see [HomeTab]), kept across visits to a video and app restarts. */
    private val _selectedTab = MutableStateFlow(
        HomeTab.entries.getOrNull(prefs.getInt(KEY_TAB, HomeTab.Latest.ordinal)) ?: HomeTab.Latest,
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

    fun removeFavourite(url: String) = viewModelScope.launch { library.removeFavourite(url) }
    fun addFavourite(channel: ChannelSummary) = viewModelScope.launch { library.addFavourite(channel) }
    fun forgetBrowsed(url: String) = viewModelScope.launch { library.forget(url) }
    fun clearBrowsed() = viewModelScope.launch { library.clearBrowsed() }

    companion object {
        private const val KEY_TAB = "selected_tab"
        private const val CONCURRENT_CHANNELS = 4

        private fun VideoSummary.withChannel(c: ChannelSummary) =
            copy(channelName = channelName ?: c.name, channelUrl = channelUrl ?: c.url)
    }
}

enum class HomeTab(val label: String) {
    Continue("继续观看"),
    Latest("最新视频"),
    Favourites("收藏频道"),
    Browsed("浏览过的频道"),
}
