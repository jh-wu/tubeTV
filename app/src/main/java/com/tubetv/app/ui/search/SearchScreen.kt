package com.tubetv.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.tubetv.app.data.YouTubeSource
import com.tubetv.app.data.library.SearchHistory
import com.tubetv.app.data.model.ChannelSummary
import com.tubetv.app.data.model.VideoSummary
import com.tubetv.app.ui.common.AppIcons
import com.tubetv.app.ui.common.ChannelRow
import com.tubetv.app.ui.common.Load
import com.tubetv.app.ui.common.PagedGridViewModel
import com.tubetv.app.ui.common.VideoGrid
import com.tubetv.app.ui.common.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Searches videos (the paged grid) and channels (one row above it) at the same time. */
class SearchViewModel(
    private val source: YouTubeSource,
    private val history: SearchHistory,
    favourites: Flow<List<ChannelSummary>>,
) : PagedGridViewModel<VideoSummary>(null) {
    var lastQuery = ""
        private set

    val recent = history.terms

    val favouriteUrls: StateFlow<Set<String>> =
        favourites.map { list -> list.map { it.url }.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Null before the first search. */
    private val _channels = MutableStateFlow<Load<List<ChannelSummary>>?>(null)
    val channels: StateFlow<Load<List<ChannelSummary>>?> = _channels.asStateFlow()
    private var channelJob: Job? = null

    fun search(query: String) {
        val q = query.trim()
        if (q.isNotEmpty()) history.add(q)
        if (q == lastQuery) return
        lastQuery = q
        reset(if (q.isEmpty()) null else { page -> source.searchVideos(q, page) })
        channelJob?.cancel()
        if (q.isEmpty()) {
            _channels.value = null
            return
        }
        _channels.value = Load.Loading
        channelJob = viewModelScope.launch {
            _channels.value = runCatching { source.searchChannels(q, null).items }
                .fold({ Load.Ready(it) }, { Load.Failed(it.userMessage()) })
        }
    }

    fun clearHistory() = history.clear()
}

@Composable
fun SearchScreen(vm: SearchViewModel, onOpenVideo: (String) -> Unit, onOpenChannel: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val recent by vm.recent.collectAsState()
    val channels by vm.channels.collectAsState()
    val favouriteUrls by vm.favouriteUrls.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
            decorationBox = { field ->
                if (query.isEmpty()) {
                    Text("搜索频道或视频", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
                field()
            },
            modifier = Modifier
                .padding(start = 48.dp, top = 32.dp)
                .width(600.dp)
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused }
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .border(
                    2.dp,
                    if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (recent.isNotEmpty()) {
            Row(
                Modifier.padding(start = 48.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("搜索记录", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = vm::clearHistory) { Icon(AppIcons.ClearHistory, contentDescription = "清除搜索记录") }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 48.dp)) {
                    items(recent, key = { it }) { term ->
                        OutlinedButton(onClick = { query = term; vm.search(term) }) { Text(term) }
                    }
                }
            }
        }
        val channelResults = channels
        VideoGrid(
            state,
            onOpen = { onOpenVideo(it.url) },
            onLoadMore = vm::loadMore,
            emptyText = if (vm.lastQuery.isEmpty()) "输入频道名或视频标题后按搜索" else "没有找到视频",
            header = {
                if (channelResults != null) item(span = { GridItemSpan(maxLineSpan) }) {
                    when (channelResults) {
                        is Load.Loading -> Text("正在搜索频道…", style = MaterialTheme.typography.bodyMedium)
                        is Load.Failed -> Text("频道搜索失败：${channelResults.message}", style = MaterialTheme.typography.bodyMedium)
                        is Load.Ready -> {
                            if (channelResults.value.isEmpty()) Text("没有找到频道", style = MaterialTheme.typography.bodyMedium)
                            else ChannelRow("频道", channelResults.value, onOpen = { onOpenChannel(it.url) }, favourites = favouriteUrls, edgePadding = 0.dp)
                        }
                    }
                }
                if (state.items.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("视频", style = MaterialTheme.typography.titleLarge) }
                }
            },
        )
    }
}
