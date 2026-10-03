package com.tubetv.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.tubetv.app.data.library.BrowsedChannel
import com.tubetv.app.data.library.WatchRecord
import com.tubetv.app.data.model.ChannelSummary
import com.tubetv.app.data.model.VideoSummary
import com.tubetv.app.ui.common.AddChannelCard
import com.tubetv.app.ui.common.AppIcons
import com.tubetv.app.ui.common.ChannelCard
import com.tubetv.app.ui.common.ChannelCardWidth
import com.tubetv.app.ui.common.Choice
import com.tubetv.app.ui.common.ChoiceDialog
import com.tubetv.app.ui.common.GridState
import com.tubetv.app.ui.common.Message
import com.tubetv.app.ui.common.VideoCard
import com.tubetv.app.ui.common.VideoCardWidth
import com.tubetv.app.ui.common.VideoGrid
import com.tubetv.app.ui.common.VideoRow
import com.tubetv.app.ui.common.formatTime

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onResume: (WatchRecord) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val selected by vm.selectedTab.collectAsState()
    // Coming back from a video or channel, focus returns to the tab that was open, so it stays selected.
    val tabFocus = remember { FocusRequester() }

    Column(Modifier.fillMaxSize()) {
        // One row: 继续观看 as an icon tab, the channel tabs, then search and settings.
        Row(
            Modifier.fillMaxWidth().padding(start = 40.dp, end = 48.dp, top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabRow(selectedTabIndex = selected.ordinal) {
                HomeTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == selected,
                        onFocus = { vm.selectTab(tab) },
                        onClick = { vm.selectTab(tab) },
                        modifier = if (tab == selected) Modifier.focusRequester(tabFocus) else Modifier,
                    ) {
                        if (tab == HomeTab.Continue) {
                            Icon(
                                AppIcons.History,
                                contentDescription = tab.label,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).size(24.dp),
                            )
                        } else {
                            Text(tab.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    }
                }
            }
            HeaderButton(Icons.Default.Search, "搜索", onSearch)
            HeaderButton(Icons.Default.Settings, "设置", onSettings)
        }
        LaunchedEffect(Unit) { runCatching { tabFocus.requestFocus() } }

        when (selected) {
            HomeTab.Continue -> ContinueWatching(vm, onResume)
            HomeTab.Home -> HomeFeedTab(vm, onOpenVideo)
            HomeTab.Latest -> Latest(vm, onOpenVideo, onSearch)
            HomeTab.Favourites -> Favourites(vm, onOpenChannel, onSearch)
            HomeTab.Browsed -> Browsed(vm, onOpenChannel)
        }
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
}

@Composable
private fun ContinueWatching(vm: HomeViewModel, onResume: (WatchRecord) -> Unit) {
    val recent = vm.continueWatching.collectAsState().value.filterNot { it.isFinished }
    if (recent.isEmpty()) {
        Message("还没有看到一半的视频")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(VideoCardWidth - 4.dp),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(recent, key = { it.videoUrl }) { r ->
            VideoCard(
                r.toSummary(),
                onClick = { onResume(r) },
                progress = r.progress,
                subtitle = listOfNotNull(r.channelName, "看到 ${formatTime(r.positionMs)}").joinToString(" · "),
            )
        }
    }
}

private fun WatchRecord.toSummary() = VideoSummary(videoUrl, title, thumbnailUrl, channelName, channelUrl, durationSec = durationMs / 1000)

@Composable
private fun Latest(vm: HomeViewModel, onOpenVideo: (String) -> Unit, onSearch: () -> Unit) {
    val favourites by vm.favourites.collectAsState()
    val latest by vm.latest.collectAsState()
    when {
        favourites == null -> return
        favourites!!.isEmpty() -> {
            NoFavourites(onSearch)
            return
        }
    }
    val grid = GridState(latest.videos, loading = latest.loading, hasMore = false, error = latest.error)
    VideoGrid(
        grid,
        onOpen = { onOpenVideo(it.url) },
        onLoadMore = vm::refreshLatest,
        emptyText = "收藏的频道还没有视频",
        header = {
            fullWidth {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::refreshLatest) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(if (latest.loading) "正在刷新…" else "刷新", modifier = Modifier.padding(start = 8.dp))
                    }
                    val note = when {
                        latest.failed.isNotEmpty() -> "这些频道没能加载：${latest.failed.joinToString("、")}"
                        else -> "${favourites!!.size} 个收藏频道的最新视频"
                    }
                    Text(note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    )
}

@Composable
private fun HomeFeedTab(vm: HomeViewModel, onOpenVideo: (String) -> Unit) {
    val feed by vm.home.collectAsState()
    when {
        feed.rows.isEmpty() && feed.loading -> Message("加载中…")
        feed.rows.isEmpty() && feed.error != null -> Message("加载失败：${feed.error}", onRetry = vm::refreshHome)
        feed.rows.isEmpty() -> Message("YouTube 没有返回推荐内容", onRetry = vm::refreshHome)
        else -> LazyColumn(
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            item(key = "__refresh") {
                Row(
                    Modifier.padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = vm::refreshHome) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(if (feed.loading) "正在刷新…" else "刷新", modifier = Modifier.padding(start = 8.dp))
                    }
                    Hint("“为你推荐”来自你看过的视频和收藏、浏览过的频道")
                }
            }
            items(feed.rows, key = { it.title }) { row ->
                VideoRow(row.title, row.videos, onOpen = { onOpenVideo(it.url) })
            }
        }
    }
}

@Composable
private fun NoFavourites(onSearch: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("还没有收藏的频道", style = MaterialTheme.typography.headlineSmall)
        Text(
            "搜索一个频道并打开它，按“收藏”，它的最新视频就会出现在这里。",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        AddChannelCard("搜索频道", onSearch)
    }
}

@Composable
private fun Favourites(vm: HomeViewModel, onOpenChannel: (String) -> Unit, onSearch: () -> Unit) {
    val favourites = vm.favourites.collectAsState().value ?: return
    var removing by remember { mutableStateOf<ChannelSummary?>(null) }
    ChannelGrid {
        fullWidth { Hint("长按频道可取消收藏") }
        item(key = "__add") { AddChannelCard("添加频道", onSearch) }
        items(favourites, key = { it.url }) { c ->
            ChannelCard(c, onClick = { onOpenChannel(c.url) }, onLongClick = { removing = c })
        }
    }
    removing?.let { c ->
        ChoiceDialog(
            title = "取消收藏“${c.name}”？",
            choices = listOf(Choice("取消收藏") { vm.removeFavourite(c.url) }, Choice("返回") {}),
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun Browsed(vm: HomeViewModel, onOpenChannel: (String) -> Unit) {
    val browsed = vm.browsed.collectAsState().value ?: return
    val favouriteUrls = vm.favourites.collectAsState().value.orEmpty().map { it.url }.toSet()
    var editing by remember { mutableStateOf<BrowsedChannel?>(null) }
    var clearing by remember { mutableStateOf(false) }
    if (browsed.isEmpty()) {
        Message("还没有浏览过频道。打开过的频道会记在这里。")
        return
    }
    ChannelGrid {
        fullWidth {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { clearing = true }) {
                    Icon(AppIcons.ClearHistory, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("清除浏览记录", modifier = Modifier.padding(start = 8.dp))
                }
                Hint("长按频道可收藏或删除")
            }
        }
        items(browsed, key = { it.url }) { b ->
            ChannelCard(
                b.summary,
                onClick = { onOpenChannel(b.url) },
                onLongClick = { editing = b },
                subtitle = "看过 ${b.visits} 次",
                favourite = b.url in favouriteUrls,
            )
        }
    }
    editing?.let { b ->
        val isFavourite = b.url in favouriteUrls
        ChoiceDialog(
            title = b.name,
            choices = listOf(
                if (isFavourite) Choice("取消收藏") { vm.removeFavourite(b.url) } else Choice("收藏") { vm.addFavourite(b.summary) },
                Choice("从记录中删除") { vm.forgetBrowsed(b.url) },
                Choice("返回") {},
            ),
            onDismiss = { editing = null },
        )
    }
    if (clearing) {
        ChoiceDialog(
            title = "清除全部浏览记录？",
            message = "收藏的频道不受影响。",
            choices = listOf(Choice("清除") { vm.clearBrowsed() }, Choice("返回") {}),
            onDismiss = { clearing = false },
        )
    }
}

@Composable
private fun ChannelGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(ChannelCardWidth),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        content = content,
    )
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
}
