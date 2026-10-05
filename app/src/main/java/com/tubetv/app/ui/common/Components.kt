package com.tubetv.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tubetv.app.data.model.ChannelSummary
import com.tubetv.app.data.model.VideoSummary

/** Width of a video card in a row; grids fit as many of about this width as the screen allows. */
val VideoCardWidth = 200.dp
val ChannelCardWidth = 136.dp

private val Dim @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

@Composable
fun VideoCard(
    video: VideoSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    subtitle: String? = null,
    showChannel: Boolean = true,
) {
    Column(modifier) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                val badge = when {
                    video.isLive -> "直播"
                    video.durationSec > 0 -> formatTime(video.durationSec * 1000)
                    else -> null
                }
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                            .background(if (video.isLive) MaterialTheme.colorScheme.primary else Color(0xCC000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                if (progress != null && progress > 0f) {
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(4.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Text(
            video.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        val sub = subtitle ?: videoSubtitle(video.channelName, video.viewCount, video.uploaded, showChannel)
        if (sub.isNotBlank()) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ChannelCard(
    channel: ChannelSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    subtitle: String? = formatSubscribers(channel.subscriberCount),
    favourite: Boolean = false,
    /** Marks a channel that uploaded since the viewer last opened it. */
    hasNew: Boolean = false,
) {
    Column(modifier.width(ChannelCardWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Card(
                onClick = onClick,
                onLongClick = onLongClick,
                shape = CardDefaults.shape(CircleShape),
                modifier = Modifier.size(112.dp),
            ) {
                Avatar(channel.avatarUrl, channel.name, Modifier.fillMaxSize())
            }
            if (favourite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "已收藏",
                    tint = FavouriteColor,
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                        .background(Color(0xCC000000), CircleShape).padding(4.dp),
                )
            }
            if (hasNew) {
                Text(
                    "新",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart)
                        .background(NewColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            channel.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A round card with a plus sign, for adding a channel. */
@Composable
fun AddChannelCard(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(ChannelCardWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(onClick = onClick, shape = CardDefaults.shape(CircleShape), modifier = Modifier.size(112.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = label, modifier = Modifier.size(48.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun Avatar(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
        if (url == null) {
            Icon(Icons.Filled.Person, contentDescription = name, modifier = Modifier.fillMaxSize(0.5f))
        } else {
            AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun VideoRow(title: String, videos: List<VideoSummary>, onOpen: (VideoSummary) -> Unit, edgePadding: Dp = 48.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = edgePadding))
        LazyRow(
            contentPadding = PaddingValues(horizontal = edgePadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(videos, key = { it.key }) { v ->
                VideoCard(v, onClick = { onOpen(v) }, modifier = Modifier.width(VideoCardWidth))
            }
        }
    }
}

@Composable
fun ChannelRow(
    title: String,
    channels: List<ChannelSummary>,
    onOpen: (ChannelSummary) -> Unit,
    favourites: Set<String> = emptySet(),
    edgePadding: Dp = 48.dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = edgePadding))
        LazyRow(
            contentPadding = PaddingValues(horizontal = edgePadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(channels, key = { it.key }) { c ->
                ChannelCard(c, onClick = { onOpen(c) }, favourite = c.url in favourites)
            }
        }
    }
}

@Composable
fun Message(text: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(
        modifier.padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("重试") }
        }
    }
}

/** Gold, for the favourite star. */
val FavouriteColor = Color(0xFFFFC107)

/** YouTube red, for the "新" mark on channels with new uploads. */
val NewColor = Color(0xFFE53935)
