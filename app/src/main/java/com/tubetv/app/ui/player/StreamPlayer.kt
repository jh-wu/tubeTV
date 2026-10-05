package com.tubetv.app.ui.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.tubetv.app.data.model.PlaySource
import com.tubetv.app.data.model.SubtitleOption
import okhttp3.OkHttpClient

/** How the app turns a [PlaySource] into something ExoPlayer can play. */
@OptIn(UnstableApi::class)
object StreamPlayer {

    /**
     * A player that picks one video quality, the best the TV can show up to 1080p, and keeps it.
     * Stepping between qualities mid-video showed black for seconds on some TVs.
     */
    fun create(context: Context, http: OkHttpClient): ExoPlayer {
        val tracks = DefaultTrackSelector(
            context,
            DefaultTrackSelector.Parameters.Builder(context)
                .setMaxVideoSize(1920, 1080)
                .setForceHighestSupportedBitrate(true)
                .build(),
        )
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(OkHttpDataSource.Factory(http)))
            .setTrackSelector(tracks)
            .build()
    }

    /** The media for [source], with [subtitles] alongside (off until chosen in the player's menu). */
    fun mediaSource(
        context: Context,
        http: OkHttpClient,
        source: PlaySource,
        title: String?,
        subtitles: List<SubtitleOption> = emptyList(),
    ): MediaSource {
        val metadata = MediaMetadata.Builder().setTitle(title).build()
        val subtitleConfigs = subtitles.map {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(it.language)
                .setLabel(it.label)
                .setId(it.url)
                .build()
        }
        fun item(url: String, mime: String? = null, withSubtitles: Boolean = true) =
            MediaItem.Builder().setUri(url).setMimeType(mime).setMediaMetadata(metadata)
                .setSubtitleConfigurations(if (withSubtitles) subtitleConfigs else emptyList())
                .build()
        val factory = DefaultMediaSourceFactory(context).setDataSourceFactory(OkHttpDataSource.Factory(http))
        return when (source) {
            is PlaySource.Hls -> factory.createMediaSource(item(source.url, MimeTypes.APPLICATION_M3U8))
            is PlaySource.Dash -> factory.createMediaSource(item(source.url, MimeTypes.APPLICATION_MPD))
            is PlaySource.Progressive -> factory.createMediaSource(item(source.url))
            is PlaySource.Merged -> MergingMediaSource(
                factory.createMediaSource(item(source.videoUrl)),
                factory.createMediaSource(item(source.audioUrl, withSubtitles = false)),
            )
        }
    }

    fun httpStatus(e: Throwable): Int? = generateSequence(e) { it.cause }
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode

    /** The error with its cause chain, so a TV message says more than "Source error". */
    fun describe(e: Throwable): String = generateSequence(e) { it.cause }.take(4)
        .joinToString(" ← ") { t ->
            val code = (t as? PlaybackException)?.errorCodeName?.let { "[$it] " }.orEmpty()
            "$code${t.javaClass.simpleName}: ${t.message}"
        }
}
