package com.tubetv.app.ui.comments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tubetv.app.TubeTvApp
import com.tubetv.app.data.YouTubeSource
import com.tubetv.app.data.model.Comment
import com.tubetv.app.ui.common.Avatar
import com.tubetv.app.ui.common.Message
import com.tubetv.app.ui.common.PagedGridViewModel
import com.tubetv.app.ui.common.TubeTheme
import com.tubetv.app.ui.common.uploadedText

class CommentsViewModel(source: YouTubeSource, url: String) :
    PagedGridViewModel<Comment>({ page -> source.comments(url, page) })

/** A video's comments, newest pages loading as the list is scrolled down. */
@Composable
fun CommentsScreen(vm: CommentsViewModel, title: String?) {
    val state by vm.state.collectAsState()
    if (state.items.isEmpty()) {
        when {
            state.error != null -> Message("加载失败：${state.error}", onRetry = vm::loadMore)
            state.loading || state.hasMore -> Message("加载中…")
            else -> Message("这个视频没有评论")
        }
        return
    }
    val first = remember { FocusRequester() }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 96.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                listOfNotNull("评论", title?.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        itemsIndexed(state.items, key = { _, c -> c.key }) { index, c ->
            if (index >= state.items.size - 5) LaunchedEffect(state.items.size) { vm.loadMore() }
            CommentItem(c, if (index == 0) Modifier.focusRequester(first) else Modifier)
        }
        if (state.loading) item { Text("加载中…", modifier = Modifier.padding(16.dp)) }
        if (state.error != null) {
            item { Message("加载失败：${state.error}", onRetry = vm::loadMore, modifier = Modifier.fillMaxWidth()) }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun CommentItem(c: Comment, modifier: Modifier = Modifier) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    // Each comment takes focus so the remote can move through them; focused ones get a quiet grey, not white.
    Surface(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(c.avatarUrl, c.author, Modifier.size(48.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val head = listOfNotNull(
                    "置顶".takeIf { c.pinned },
                    c.author.ifBlank { null },
                    uploadedText(c.uploadedAtMs, c.uploaded),
                ).joinToString(" · ")
                Text(head, style = MaterialTheme.typography.bodySmall, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.text, style = MaterialTheme.typography.bodyLarge)
                val foot = listOfNotNull(
                    c.likes?.let { "赞 $it" },
                    "${c.replies} 条回复".takeIf { c.replies > 0 },
                    "作者点了赞".takeIf { c.hearted },
                ).joinToString("    ")
                if (foot.isNotEmpty()) Text(foot, style = MaterialTheme.typography.bodySmall, color = dim)
            }
        }
    }
}

/** The 评论 page over the player, so Back goes straight back to the video. */
class CommentsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TubeTvApp
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE)
        setContent {
            TubeTheme {
                Surface(Modifier.fillMaxSize(), shape = RectangleShape) {
                    CommentsScreen(viewModel { CommentsViewModel(app.source, url) }, title)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, url: String, title: String?) =
            Intent(context, CommentsActivity::class.java).putExtra(EXTRA_URL, url).putExtra(EXTRA_TITLE, title)
    }
}
