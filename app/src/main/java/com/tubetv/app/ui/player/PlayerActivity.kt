package com.tubetv.app.ui.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import com.tubetv.app.TubeTvApp
import com.tubetv.app.data.library.WatchRecord
import com.tubetv.app.data.model.Playback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen playback of one video. Resumes from the saved position and saves progress
 * while playing. If a stream fails it tries the video's other streams, then fresh links,
 * and finally offers to open the video in the YouTube app.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private val app get() = application as TubeTvApp
    private lateinit var playerView: PlayerView
    private lateinit var errorView: TextView
    private var player: ExoPlayer? = null
    private var playback: Playback? = null
    private var sourceIndex = 0
    private var refetched = false
    private var failed = false
    private var saveJob: Job? = null
    private val attempts = mutableListOf<String>()
    private lateinit var videoUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView = PlayerView(this).apply {
            useController = true
            keepScreenOn = true
            // Switching to another stream after an error keeps the last picture instead of going black.
            setKeepContentOnPlayerReset(true)
        }
        styleControls(playerView)
        // The control bar opens with the progress bar focused, so left/right seek straight away.
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (visibility == View.VISIBLE) focusProgress()
        })
        errorView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setBackgroundColor(0xE0000000.toInt())
            setPadding(48, 32, 48, 32)
            visibility = View.GONE
        }
        setContentView(FrameLayout(this).apply {
            addView(playerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(errorView, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        })

        videoUrl = intent.getStringExtra(EXTRA_VIDEO) ?: return finish()
        val fromStart = intent.getBooleanExtra(EXTRA_FROM_START, false)

        lifecycleScope.launch {
            try {
                val p = app.source.playback(videoUrl)
                playback = p
                val record = app.history.get(videoUrl)
                val start = record?.takeIf { !fromStart && !it.isFinished && !p.isLive }?.positionMs ?: 0L
                play(0, start)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private fun play(index: Int, startMs: Long) {
        val p = playback ?: return
        sourceIndex = index
        val exo = player ?: StreamPlayer.create(this, app.playerHttp).also {
            player = it
            playerView.player = it
            it.addListener(listener)
        }
        errorView.visibility = View.GONE
        val media = StreamPlayer.mediaSource(this, app.playerHttp, p.sources[index], p.title)
        // A live stream starts at the live edge.
        if (p.isLive) exo.setMediaSource(media) else exo.setMediaSource(media, startMs)
        exo.prepare()
        exo.playWhenReady = true
        startSaving()
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                save()
                finish()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = playback ?: return fail(error)
            Log.w("PlayerActivity", "playback error ${StreamPlayer.describe(error)}")
            val position = player?.currentPosition ?: 0L
            val status = StreamPlayer.httpStatus(error)
            attempts += "${p.sources[sourceIndex].label}: ${status?.let { "HTTP $it" } ?: error.errorCodeName}"
            // The video's other streams first, then freshly requested links (they expire), then give up.
            if (sourceIndex + 1 < p.sources.size) {
                play(sourceIndex + 1, position)
                return
            }
            if (!refetched) {
                refetched = true
                app.source.forget(videoUrl)
                lifecycleScope.launch {
                    try {
                        playback = app.source.playback(videoUrl)
                        play(0, position)
                    } catch (e: Exception) {
                        fail(e)
                    }
                }
                return
            }
            fail(error)
        }
    }

    private var openingBar = false

    /**
     * A remote key while the control bar is hidden opens it on the progress bar (and does nothing else).
     * After a failure, OK opens the video in the YouTube app.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val ok = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        if (failed && ok) {
            if (event.action == KeyEvent.ACTION_UP) openInYouTube()
            return true
        }
        val opensBar = ok || event.keyCode in setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        )
        if (opensBar && player != null && (!playerView.isControllerFullyVisible || openingBar)) {
            // A key that leaves touch mode reaches here only as its key-up, so either half opens the bar.
            if (event.action == KeyEvent.ACTION_DOWN || !openingBar) {
                playerView.showController()
                focusProgress()
            }
            openingBar = event.action == KeyEvent.ACTION_DOWN
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun openInYouTube() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
        try {
            startActivity(intent)
            finish()
        } catch (_: ActivityNotFoundException) {
            errorView.text = "${errorView.text}\n\n没有找到 YouTube 应用"
        }
    }

    /** Focuses the progress bar now and again once the bar has laid out (the controller may refocus play/pause). */
    private fun focusProgress() {
        val bar = progressBar() ?: return
        bar.requestFocus()
        bar.post { bar.requestFocus() }
        bar.postDelayed({ if (playerView.isControllerFullyVisible && !bar.isFocused && playerView.findFocus() !is DefaultTimeBar) bar.requestFocus() }, 300)
    }

    private fun progressBar(): DefaultTimeBar? = playerView.findViewById(androidx.media3.ui.R.id.exo_progress)

    /**
     * Makes the focused control obvious from across the room: a white frame and a larger
     * size on buttons, and a brighter, framed bar while the progress bar has focus.
     */
    private fun styleControls(root: View) {
        val density = resources.displayMetrics.density
        fun frame() = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(0x1AFFFFFF)
                setStroke((3 * density).toInt(), FRAME_COLOR)
            })
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        when (root) {
            is DefaultTimeBar -> {
                root.background = frame()
                root.setKeyTimeIncrement(10_000)
                root.setPlayedColor(DIM_COLOR)
                root.setScrubberColor(DIM_COLOR)
                root.setOnFocusChangeListener { _, focused ->
                    val c = if (focused) FOCUS_COLOR else DIM_COLOR
                    root.setPlayedColor(c)
                    root.setScrubberColor(c)
                }
            }
            is ImageButton, is android.widget.Button -> {
                root.background = frame()
                root.setOnFocusChangeListener { v, focused ->
                    val scale = if (focused) 1.3f else 1f
                    v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                }
            }
            is ViewGroup -> for (i in 0 until root.childCount) styleControls(root.getChildAt(i))
        }
    }

    private fun startSaving() {
        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                if (player?.isPlaying == true) save()
            }
        }
    }

    private fun save() {
        val exo = player ?: return
        val p = playback ?: return
        if (p.isLive) return
        val duration = exo.duration.takeIf { it > 0 } ?: 0L
        val position = if (exo.playbackState == Player.STATE_ENDED) duration else exo.currentPosition
        if (position <= 0 && duration <= 0) return
        val record = WatchRecord(
            videoUrl = videoUrl,
            title = p.title,
            thumbnailUrl = p.thumbnailUrl,
            channelName = p.channelName,
            channelUrl = p.channelUrl,
            positionMs = position,
            durationMs = duration,
            updatedAt = System.currentTimeMillis(),
        )
        lifecycleScope.launch { app.history.upsert(record) }
    }

    private fun fail(e: Throwable) {
        Log.w("PlayerActivity", "playback failed", e)
        failed = true
        val tried = attempts.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "尝试过：\n")
        errorView.text = listOfNotNull(
            "播放失败：${StreamPlayer.describe(e)}",
            tried,
            "按确认键用 YouTube 应用打开，按返回键退出",
        ).joinToString("\n\n")
        errorView.visibility = View.VISIBLE
    }

    override fun onStart() {
        super.onStart()
        player?.playWhenReady = true
    }

    override fun onStop() {
        save()
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        saveJob?.cancel()
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val FOCUS_COLOR = Color.WHITE
        private const val DIM_COLOR = 0xFFB0B0B0.toInt()
        /** White at 50% opacity: visible on any picture without being glaring. */
        private const val FRAME_COLOR = 0x80FFFFFF.toInt()
        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_FROM_START = "from_start"

        fun intent(context: Context, videoUrl: String, fromStart: Boolean = false) =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_VIDEO, videoUrl)
                .putExtra(EXTRA_FROM_START, fromStart)
    }
}
