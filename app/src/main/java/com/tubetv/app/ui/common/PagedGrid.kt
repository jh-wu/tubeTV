package com.tubetv.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tubetv.app.data.model.Keyed
import com.tubetv.app.data.model.Page
import com.tubetv.app.data.model.VideoSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GridState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

/** Loads pages of items on demand as the grid scrolls. A null page position asks for the first page. */
open class PagedGridViewModel<T : Keyed>(
    private var loader: (suspend (page: Any?) -> Page<T>)?,
) : ViewModel() {

    private val _state = MutableStateFlow(GridState<T>())
    val state: StateFlow<GridState<T>> = _state.asStateFlow()
    private var nextPage: Any? = null
    private var job: Job? = null

    init { if (loader != null) loadMore() }

    protected fun reset(newLoader: (suspend (Any?) -> Page<T>)?) {
        job?.cancel()
        loader = newLoader
        nextPage = null
        _state.value = GridState(hasMore = newLoader != null)
        if (newLoader != null) loadMore()
    }

    fun loadMore() {
        val load = loader ?: return
        val s = _state.value
        if (s.loading || !s.hasMore) return
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            runCatching { load(nextPage) }
                .onSuccess { page ->
                    nextPage = page.next
                    _state.update { st ->
                        st.copy(
                            items = (st.items + page.items).distinctBy { it.key },
                            loading = false,
                            hasMore = page.hasMore && page.items.isNotEmpty(),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }
}

@Composable
fun VideoGrid(
    state: GridState<VideoSummary>,
    onOpen: (VideoSummary) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "没有视频",
    showChannel: Boolean = true,
    header: (LazyGridScope.() -> Unit)? = null,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    if (state.items.isEmpty() && header == null) {
        EmptyGrid(state, onLoadMore, emptyText)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(VideoCardWidth - 4.dp),
        modifier = modifier,
        state = gridState,
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        header?.invoke(this)
        if (state.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyGrid(state, onLoadMore, emptyText) }
        }
        itemsIndexed(state.items, key = { _, v -> v.key }) { index, v ->
            if (index >= state.items.size - 12) {
                LaunchedEffect(state.items.size) { onLoadMore() }
            }
            VideoCard(v, onClick = { onOpen(v) }, showChannel = showChannel)
        }
        if (state.error != null && state.items.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Message("加载失败：${state.error}", onRetry = onLoadMore) }
        }
    }
}

@Composable
private fun EmptyGrid(state: GridState<*>, onRetry: () -> Unit, emptyText: String) = when {
    state.error != null -> Message("加载失败：${state.error}", onRetry = onRetry)
    state.loading -> Message("加载中…")
    else -> Message(emptyText)
}
