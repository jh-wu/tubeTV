package com.tubetv.app.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tubetv.app.data.YouTubeSource
import com.tubetv.app.data.library.WatchHistoryDao
import com.tubetv.app.data.library.WatchRecord
import com.tubetv.app.data.model.VideoDetail
import com.tubetv.app.ui.common.Avatar
import com.tubetv.app.ui.common.Load
import com.tubetv.app.ui.common.Message
import com.tubetv.app.ui.common.VideoRow
import com.tubetv.app.ui.common.formatTime
import com.tubetv.app.ui.common.formatViews
import com.tubetv.app.ui.common.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VideoViewModel(
    private val source: YouTubeSource,
    history: WatchHistoryDao,
    private val url: String,
) : ViewModel() {
    private val _detail = MutableStateFlow<Load<VideoDetail>>(Load.Loading)
    val detail: StateFlow<Load<VideoDetail>> = _detail.asStateFlow()

    val record: StateFlow<WatchRecord?> =
        history.observe(url).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { load() }

    fun load() {
        _detail.value = Load.Loading
        viewModelScope.launch {
            _detail.value = runCatching { source.video(url) }
                .fold({ Load.Ready(it) }, { Load.Failed(it.userMessage()) })
        }
    }
}

@Composable
fun VideoScreen(
    vm: VideoViewModel,
    onPlay: (fromStart: Boolean) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenInYouTube: () -> Unit,
) {
    val state by vm.detail.collectAsState()
    val record by vm.record.collectAsState()
    when (val s = state) {
        is Load.Loading -> Message("加载中…")
        is Load.Failed -> LoadFailed(s.message, vm::load, onOpenInYouTube)
        is Load.Ready -> VideoContent(s.value, record, onPlay, onOpenChannel, onOpenVideo)
    }
}

/** The error, with retry and a way out to the YouTube app, which can play what this app could not load. */
@Composable
private fun LoadFailed(message: String, onRetry: () -> Unit, onOpenInYouTube: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(message) { runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("加载失败：$message", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry, modifier = Modifier.focusRequester(focus)) { Text("重试") }
            OutlinedButton(onClick = onOpenInYouTube) { Text("用 YouTube 应用打开") }
        }
    }
}

@Composable
private fun VideoContent(
    d: VideoDetail,
    record: WatchRecord?,
    onPlay: (Boolean) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(d.url) { runCatching { playFocus.requestFocus() } }
    val resumable = record?.takeIf { !it.isFinished && it.positionMs > 0 && !d.isLive }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(horizontal = 48.dp)) {
                AsyncImage(
                    model = d.thumbnailUrl,
                    contentDescription = d.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(400.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(d.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(
                        if (d.isLive) "直播中" else d.durationSec.takeIf { it > 0 }?.let { formatTime(it * 1000) },
                        formatViews(d.viewCount),
                        d.uploaded,
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (resumable != null) {
                            Button(onClick = { onPlay(false) }, modifier = Modifier.focusRequester(playFocus)) {
                                Text("继续播放 ${formatTime(resumable.positionMs)}")
                            }
                            OutlinedButton(onClick = { onPlay(true) }) { Text("从头播放") }
                        } else {
                            Button(onClick = { onPlay(true) }, modifier = Modifier.focusRequester(playFocus)) { Text("播放") }
                        }
                        d.channel?.let { c ->
                            OutlinedButton(onClick = { onOpenChannel(c.url) }) {
                                Avatar(c.avatarUrl, c.name, Modifier.size(24.dp))
                                Text(c.name, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    d.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (d.related.isNotEmpty()) {
            item { VideoRow("相关视频", d.related, onOpen = { onOpenVideo(it.url) }) }
        }
    }
}
