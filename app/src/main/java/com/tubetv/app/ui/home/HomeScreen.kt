package com.tubetv.app.ui.home

import com.tubetv.app.Strings
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
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
import com.tubetv.app.ui.common.VideoGridColumns
import com.tubetv.app.ui.common.VideoGrid
import com.tubetv.app.ui.common.formatTime

@OptIn(ExperimentalComposeUiApi::class)
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
    // Coming back from a video or channel, focus returns to the tab that was open.
    val tabFocus = remember { FocusRequester() }
    val railFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    // The bar can take focus only while open, so up/down at the content's edges never reach it.
    var railOpen by remember { mutableStateOf(selected != HomeTab.Home) }
    val railOffset by animateDpAsState(if (railOpen) 0.dp else -RailWidth, label = "rail")

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                // The open bar pushes the content right instead of covering it, so right from the bar
                // finds the content (a page lying under the bar doesn't count as being to its right).
                .offset(x = RailWidth + railOffset)
                // The bar closes once focus is back in the content. (Closing when the bar reports losing
                // focus misfired: moving between its icons briefly reports no focus, which closed it mid-move.)
                .onFocusChanged { if (it.hasFocus) railOpen = false }
                .onPreviewKeyEvent { e ->
                    // Left at the screen's left edge brings out the section bar.
                    if (e.key != Key.DirectionLeft || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (!focus.moveFocus(FocusDirection.Left)) railOpen = true
                    true
                },
        ) {
            when (selected) {
                HomeTab.Home -> HomeFeeds(vm, onOpenVideo, tabFocus)
                HomeTab.Continue -> ContinueWatching(vm, onResume)
                HomeTab.Latest -> Latest(vm, onOpenVideo, onSearch)
                HomeTab.Favourites -> Favourites(vm, onOpenChannel, onSearch)
                HomeTab.Browsed -> Browsed(vm, onOpenChannel)
            }
        }
        // The sections, then search and settings, as icons in a bar that slides in over the left edge
        // while it has focus and hides again when focus moves right.
        Column(
            Modifier.offset(x = railOffset).fillMaxHeight().width(RailWidth)
                .background(MaterialTheme.colorScheme.surface)
                .focusRestorer(railFocus)
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeTab.entries.forEach { tab ->
                RailItem(
                    label = tab.label,
                    icon = tab.icon,
                    selected = tab == selected,
                    onClick = { vm.selectTab(tab) },
                    canFocus = railOpen,
                    modifier = if (tab == selected) Modifier.focusRequester(railFocus) else Modifier,
                )
            }
            Spacer(Modifier.weight(1f))
            RailItem(Strings.search, Icons.Default.Search, selected = false, canFocus = railOpen, onClick = onSearch)
            RailItem(Strings.settings, Icons.Default.Settings, selected = false, canFocus = railOpen, onClick = onSettings)
        }
    }
    // Opening the bar moves focus into it, once its items can take focus.
    LaunchedEffect(railOpen) { if (railOpen) runCatching { railFocus.requestFocus() } }
    LaunchedEffect(Unit) { if (selected == HomeTab.Home) runCatching { tabFocus.requestFocus() } }
}

private val RailWidth = 48.dp

private val HomeTab.icon: ImageVector
    get() = when (this) {
        HomeTab.Home -> Icons.Default.Home
        HomeTab.Continue -> AppIcons.History
        HomeTab.Latest -> Icons.Default.Notifications
        HomeTab.Favourites -> Icons.Default.Star
        HomeTab.Browsed -> Icons.Default.AccountBox
    }

/** An icon button that stays highlighted while its section is open; the label is read out, not shown. */
@Composable
private fun RailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    canFocus: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.focusProperties { this.canFocus = canFocus }.size(36.dp),
        shape = SelectableSurfaceDefaults.shape(shape = CircleShape),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.align(Alignment.Center).size(20.dp))
    }
}

/** 首页: 为你推荐 and YouTube's lists as tabs across the top, the open one as a grid below. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HomeFeeds(vm: HomeViewModel, onOpenVideo: (String) -> Unit, tabFocus: FocusRequester) {
    val selected by vm.selectedFeed.collectAsState()
    val feeds by vm.feeds.collectAsState()
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        val feed = feeds[selected] ?: FeedState(loading = true)
        val reload = { vm.loadFeed(selected, force = true) }
        // Up from the grid lands on the open tab, not whichever tab is above the focused card.
        TabRow(
            selectedTabIndex = selected.ordinal,
            modifier = Modifier.padding(start = 40.dp, end = 48.dp, top = 24.dp).focusRestorer(tabFocus),
        ) {
            FeedTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selected,
                    onFocus = { vm.selectFeed(tab) },
                    // OK on the open tab reloads it.
                    onClick = { if (tab == selected) reload() else vm.selectFeed(tab) },
                    modifier = if (tab == selected) Modifier.focusRequester(tabFocus) else Modifier,
                ) {
                    val loading = feeds[tab]?.loading == true
                    Text(
                        if (loading) "${tab.label}…" else tab.label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
        // A reloaded (or newly opened) list starts from the top.
        val gridState = rememberLazyGridState()
        LaunchedEffect(selected, feed.generation) { gridState.scrollToItem(0) }
        VideoGrid(
            GridState(feed.videos, loading = feed.loading, hasMore = false, error = feed.error),
            gridState = gridState,
            onOpen = { onOpenVideo(it.url) },
            // Only a failed load is worth asking again; the lists have one page.
            onLoadMore = { if (feed.error != null) reload() },
            emptyText = Strings.noList,
            // Down past the last row of videos goes back up to the open tab, where OK reloads.
            modifier = Modifier.onPreviewKeyEvent { e ->
                if (e.key != Key.DirectionDown || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (!focus.moveFocus(FocusDirection.Down)) runCatching { tabFocus.requestFocus() }
                true
            },
        )
    }
}

@Composable
private fun ContinueWatching(vm: HomeViewModel, onResume: (WatchRecord) -> Unit) {
    val recent = vm.continueWatching.collectAsState().value.filterNot { it.isFinished }
    if (recent.isEmpty()) {
        Message(Strings.nothingHalfWatched)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(VideoGridColumns),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(recent, key = { it.videoUrl }) { r ->
            VideoCard(
                r.toSummary(),
                onClick = { onResume(r) },
                progress = r.progress,
                subtitle = Strings.watchedTo(formatTime(r.positionMs)),
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
    val grid = GridState(latest.visible, loading = latest.loading, hasMore = latest.hasMore, error = latest.error)
    val gridState = rememberLazyGridState()
    LaunchedEffect(latest.generation) { gridState.scrollToItem(0) }
    VideoGrid(
        grid,
        gridState = gridState,
        onOpen = { onOpenVideo(it.url) },
        // Near the bottom, 24 more; after a failed load with nothing shown, 重试 loads again.
        onLoadMore = { if (latest.videos.isEmpty() && latest.error != null) vm.refreshLatest() else vm.showMoreLatest() },
        emptyText = Strings.favouritesHaveNoVideos,
        header = {
            fullWidth {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::refreshLatest) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(if (latest.loading) Strings.refreshing else Strings.refresh, modifier = Modifier.padding(start = 8.dp))
                    }
                    val note = when {
                        latest.failed.isNotEmpty() -> Strings.channelsFailed(latest.failed)
                        else -> Strings.latestFrom(favourites!!.size)
                    }
                    Text(note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    )
}

@Composable
private fun NoFavourites(onSearch: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(Strings.noFavourites, style = MaterialTheme.typography.headlineSmall)
        Text(
            Strings.noFavouritesHint,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        AddChannelCard(Strings.searchChannels, onSearch)
    }
}

@Composable
private fun Favourites(vm: HomeViewModel, onOpenChannel: (String) -> Unit, onSearch: () -> Unit) {
    val favourites = vm.favourites.collectAsState().value ?: return
    val newest by vm.newestUpload.collectAsState()
    val lastVisited = vm.browsed.collectAsState().value.orEmpty().associate { it.url to it.lastVisitedAt }
    var removing by remember { mutableStateOf<ChannelSummary?>(null) }
    ChannelGrid {
        fullWidth { Hint(Strings.favouritesHint) }
        item(key = "__add") { AddChannelCard(Strings.addChannel, onSearch) }
        items(favourites, key = { it.url }) { c ->
            ChannelCard(
                c,
                onClick = { onOpenChannel(c.url) },
                onLongClick = { removing = c },
                hasNew = vm.hasNew(c.url, lastVisited[c.url], newest),
            )
        }
    }
    removing?.let { c ->
        ChoiceDialog(
            title = Strings.unfavouriteQuestion(c.name),
            choices = listOf(Choice(Strings.unfavourite) { vm.removeFavourite(c.url) }, Choice(Strings.back) {}),
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun Browsed(vm: HomeViewModel, onOpenChannel: (String) -> Unit) {
    val browsed = vm.browsed.collectAsState().value ?: return
    val favouriteUrls = vm.favourites.collectAsState().value.orEmpty().map { it.url }.toSet()
    val newest by vm.newestUpload.collectAsState()
    LaunchedEffect(Unit) { vm.loadBrowsedUpdates() }
    var editing by remember { mutableStateOf<BrowsedChannel?>(null) }
    var clearing by remember { mutableStateOf(false) }
    if (browsed.isEmpty()) {
        Message(Strings.noBrowsed)
        return
    }
    ChannelGrid {
        fullWidth {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { clearing = true }) {
                    Icon(AppIcons.ClearHistory, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(Strings.clearBrowsed, modifier = Modifier.padding(start = 8.dp))
                }
                Hint(Strings.browsedHint)
            }
        }
        items(browsed, key = { it.url }) { b ->
            ChannelCard(
                b.summary,
                onClick = { onOpenChannel(b.url) },
                onLongClick = { editing = b },
                subtitle = Strings.visits(b.visits),
                favourite = b.url in favouriteUrls,
                hasNew = vm.hasNew(b.url, b.lastVisitedAt, newest),
            )
        }
    }
    editing?.let { b ->
        val isFavourite = b.url in favouriteUrls
        ChoiceDialog(
            title = b.name,
            choices = listOf(
                if (isFavourite) Choice(Strings.unfavourite) { vm.removeFavourite(b.url) } else Choice(Strings.favourite) { vm.addFavourite(b.summary) },
                Choice(Strings.removeFromHistory) { vm.forgetBrowsed(b.url) },
                Choice(Strings.back) {},
            ),
            onDismiss = { editing = null },
        )
    }
    if (clearing) {
        ChoiceDialog(
            title = Strings.clearAllQuestion,
            message = Strings.favouritesKept,
            choices = listOf(Choice(Strings.clear) { vm.clearBrowsed() }, Choice(Strings.back) {}),
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
