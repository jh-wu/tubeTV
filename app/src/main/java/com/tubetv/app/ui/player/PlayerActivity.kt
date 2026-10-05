package com.tubetv.app.ui.player

import android.app.AlertDialog
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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import com.tubetv.app.TubeTvApp
import com.tubetv.app.data.library.WatchRecord
import com.tubetv.app.data.model.PlaySource
import com.tubetv.app.data.model.Playback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

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
    /** The audio language picked in the menu (an [AudioOption] label); null plays the original. */
    private var audioChoice: String? = null
    private val prefs by lazy { getSharedPreferences("player", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerView = PlayerView(this).apply {
            useController = true
            keepScreenOn = true
            // Switching to another stream after an error keeps the last picture instead of going black.
            setKeepContentOnPlayerReset(true)
            // Subtitles as outlined white text straight on the picture, with no box behind them.
            subtitleView?.apply {
                setApplyEmbeddedStyles(false)
                setStyle(
                    CaptionStyleCompat(
                        Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null,
                    ),
                )
            }
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

        // The gear in the control bar opens subtitles, audio language and speed.
        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.setOnClickListener { showMenu() }

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
        originalAudioPicked = false
        val exo = player ?: StreamPlayer.create(this, app.playerHttp).also {
            player = it
            playerView.player = it
            it.addListener(listener)
            // Subtitles come on in the language picked last time, when the video has it.
            prefs.getString(KEY_SUBTITLE_LANGUAGE, null)?.let { lang ->
                it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().setPreferredTextLanguage(lang).build()
            }
        }
        errorView.visibility = View.GONE
        var source = p.sources[index]
        val dub = p.audioOptions.firstOrNull { it.label == audioChoice }
        if (source is PlaySource.Merged && dub != null) source = source.copy(audioUrl = dub.url)
        val media = StreamPlayer.mediaSource(this, app.playerHttp, source, p.title, p.subtitles)
        // A live stream starts at the live edge.
        if (p.isLive) exo.setMediaSource(media) else exo.setMediaSource(media, startMs)
        exo.prepare()
        exo.playWhenReady = true
        startSaving()
    }

    /** Whether the original audio has been chosen for this video yet (see [pickOriginalAudio]). */
    private var originalAudioPicked = false

    /**
     * A stream with several audio languages (HLS can carry YouTube's dubs) would otherwise play the
     * one matching the TV's language. Start with the one YouTube names as the original instead.
     */
    private fun pickOriginalAudio(exo: ExoPlayer, tracks: Tracks) {
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        if (audio.isEmpty()) return
        originalAudioPicked = true
        if (audio.size < 2) return
        val original = audio.firstOrNull { it.getTrackFormat(0).label?.contains("original", ignoreCase = true) == true }
            ?: audio.firstOrNull { it.getTrackFormat(0).roleFlags and C.ROLE_FLAG_MAIN != 0 }
            ?: audio.firstOrNull { it.getTrackFormat(0).selectionFlags and C.SELECTION_FLAG_DEFAULT != 0 }
            ?: return
        if (original.isSelected) return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(original.mediaTrackGroup, 0))
            .build()
    }

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            val exo = player ?: return
            if (!originalAudioPicked) pickOriginalAudio(exo, tracks)
        }

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

    private fun showMenu() {
        val exo = player ?: return
        val items = buildList {
            add("字幕：${currentSubtitle(exo) ?: "关闭"}" to ::chooseSubtitle)
            if (audioChoices(exo).size > 1) add("音轨：${currentAudio(exo)}" to ::chooseAudio)
            add("播放速度：${speedLabel(exo.playbackParameters.speed)}" to ::chooseSpeed)
        }
        choose("设置", items.map { it.first }, -1) { items[it].second() }
    }

    private fun textGroups(exo: ExoPlayer) = exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }

    private fun Format.name() = label ?: language?.let { Locale.forLanguageTag(it).getDisplayName(Locale.SIMPLIFIED_CHINESE) } ?: "未知"

    private fun currentSubtitle(exo: ExoPlayer) = textGroups(exo).firstOrNull { it.isSelected }?.let { g ->
        (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it).name() }
    }

    private fun chooseSubtitle() {
        val exo = player ?: return
        val groups = textGroups(exo)
        if (groups.isEmpty()) {
            Toast.makeText(this, "这个视频没有字幕", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = groups.indexOfFirst { it.isSelected }
        choose("字幕", listOf("关闭") + groups.map { it.getTrackFormat(0).name() }, selected + 1) { i ->
            val params = exo.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
            if (i == 0) {
                params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).setPreferredTextLanguage(null)
                prefs.edit().remove(KEY_SUBTITLE_LANGUAGE).apply()
            } else {
                val group = groups[i - 1]
                params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                group.getTrackFormat(0).language?.let { prefs.edit().putString(KEY_SUBTITLE_LANGUAGE, it).apply() }
            }
            exo.trackSelectionParameters = params.build()
        }
    }

    /**
     * The audio languages: YouTube's separate dub files when playing video and audio from separate
     * files, otherwise the audio tracks inside the stream (HLS can carry several).
     */
    private fun audioChoices(exo: ExoPlayer): List<String> {
        val p = playback ?: return emptyList()
        if (p.sources.getOrNull(sourceIndex) is PlaySource.Merged) return p.audioOptions.map { it.label }
        return exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
            .map { it.getTrackFormat(0).name() }
    }

    private fun currentAudio(exo: ExoPlayer): String {
        val p = playback
        if (p != null && p.sources.getOrNull(sourceIndex) is PlaySource.Merged) {
            return audioChoice ?: p.audioOptions.firstOrNull()?.label ?: "默认"
        }
        return exo.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.getTrackFormat(0)?.name() ?: "默认"
    }

    private fun chooseAudio() {
        val exo = player ?: return
        val p = playback ?: return
        val choices = audioChoices(exo)
        choose("音轨", choices, choices.indexOf(currentAudio(exo))) { i ->
            if (p.sources.getOrNull(sourceIndex) is PlaySource.Merged) {
                // A different language is a different file: reload at the same point.
                audioChoice = p.audioOptions[i].label
                play(sourceIndex, exo.currentPosition)
            } else {
                val groups = exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(groups[i].mediaTrackGroup, 0))
                    .build()
            }
        }
    }

    private fun speedLabel(speed: Float) = if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

    private fun chooseSpeed() {
        val exo = player ?: return
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        choose("播放速度", speeds.map(::speedLabel), speeds.indexOf(exo.playbackParameters.speed)) { i ->
            exo.setPlaybackSpeed(speeds[i])
        }
    }

    /** A list to pick from with the remote; [checked] marks the current choice (-1 for none). */
    private fun choose(title: String, labels: List<String>, checked: Int, onChoose: (Int) -> Unit) {
        val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title)
        if (checked >= 0) {
            builder.setSingleChoiceItems(labels.toTypedArray(), checked) { d, i -> d.dismiss(); onChoose(i) }
        } else {
            builder.setItems(labels.toTypedArray()) { d, i -> d.dismiss(); onChoose(i) }
        }
        builder.show()
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
        private const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
        private const val EXTRA_FROM_START = "from_start"

        fun intent(context: Context, videoUrl: String, fromStart: Boolean = false) =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_VIDEO, videoUrl)
                .putExtra(EXTRA_FROM_START, fromStart)
    }
}
