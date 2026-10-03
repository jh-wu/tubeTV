package com.tubetv.app.ui.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tubetv.app.data.YouTubeSource
import com.tubetv.app.data.library.ChannelLibrary
import com.tubetv.app.data.model.ChannelDetail
import com.tubetv.app.data.model.VideoSummary
import com.tubetv.app.ui.common.AppIcons
import com.tubetv.app.ui.common.Avatar
import com.tubetv.app.ui.common.FavouriteColor
import com.tubetv.app.ui.common.Load
import com.tubetv.app.ui.common.Message
import com.tubetv.app.ui.common.PagedGridViewModel
import com.tubetv.app.ui.common.VideoGrid
import com.tubetv.app.ui.common.formatSubscribers
import com.tubetv.app.ui.common.userMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One channel: its header and its uploads. Opening it adds it to the browsing history. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelViewModel(
    private val source: YouTubeSource,
    private val library: ChannelLibrary,
    private val url: String,
) : PagedGridViewModel<VideoSummary>({ page -> source.channelVideos(url, page) }) {

    private val _detail = MutableStateFlow<Load<ChannelDetail>>(Load.Loading)
    val detail: StateFlow<Load<ChannelDetail>> = _detail.asStateFlow()

    /** YouTube's canonical address for the channel once known; favourites are stored under it. */
    private val canonicalUrl = MutableStateFlow(url)

    val isFavourite: StateFlow<Boolean> = canonicalUrl.flatMapLatest { library.isFavourite(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init { loadDetail() }

    fun loadDetail() {
        _detail.value = Load.Loading
        viewModelScope.launch {
            runCatching { source.channel(url) }
                .onSuccess { d ->
                    canonicalUrl.value = d.url
                    _detail.value = Load.Ready(d)
                    library.visited(d.summary)
                }
                .onFailure { _detail.value = Load.Failed(it.userMessage()) }
        }
    }

    fun toggleFavourite() {
        val d = (detail.value as? Load.Ready)?.value ?: return
        viewModelScope.launch {
            if (isFavourite.value) library.removeFavourite(d.url) else library.addFavourite(d.summary)
        }
    }
}

@Composable
fun ChannelScreen(vm: ChannelViewModel, onOpenVideo: (String) -> Unit) {
    val detail by vm.detail.collectAsState()
    val videos by vm.state.collectAsState()
    val favourite by vm.isFavourite.collectAsState()

    when (val d = detail) {
        is Load.Loading -> Message("加载中…")
        is Load.Failed -> Message("加载失败：${d.message}", onRetry = vm::loadDetail)
        is Load.Ready -> VideoGrid(
            videos,
            onOpen = { onOpenVideo(it.url) },
            onLoadMore = vm::loadMore,
            emptyText = "这个频道还没有视频",
            showChannel = false,
            header = {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ChannelHeader(d.value, favourite, vm::toggleFavourite)
                }
            },
        )
    }
}

@Composable
private fun ChannelHeader(d: ChannelDetail, favourite: Boolean, onToggleFavourite: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(d.url) { runCatching { focus.requestFocus() } }
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(d.avatarUrl, d.name, Modifier.size(120.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(d.name, style = MaterialTheme.typography.headlineMedium)
            formatSubscribers(d.subscriberCount)?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            d.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            // One button for both states, so it keeps the focus when pressed.
            Button(onClick = onToggleFavourite, modifier = Modifier.padding(top = 8.dp).focusRequester(focus)) {
                Icon(
                    if (favourite) Icons.Filled.Star else AppIcons.StarBorder,
                    contentDescription = null,
                    tint = if (favourite) FavouriteColor else LocalContentColor.current,
                    modifier = Modifier.size(20.dp),
                )
                Text(if (favourite) "已收藏（再按取消）" else "收藏", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
