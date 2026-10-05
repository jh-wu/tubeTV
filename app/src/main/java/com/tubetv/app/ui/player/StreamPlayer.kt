package com.tubetv.app.ui.player

import android.content.Context
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
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.tubetv.app.data.model.PlaySource
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

    fun mediaSource(context: Context, http: OkHttpClient, source: PlaySource, title: String?): MediaSource {
        val data = OkHttpDataSource.Factory(http)
        val metadata = MediaMetadata.Builder().setTitle(title).build()
        fun item(url: String, mime: String? = null) =
            MediaItem.Builder().setUri(url).setMimeType(mime).setMediaMetadata(metadata).build()
        val factory = DefaultMediaSourceFactory(context).setDataSourceFactory(data)
        return when (source) {
            is PlaySource.Hls -> factory.createMediaSource(item(source.url, MimeTypes.APPLICATION_M3U8))
            is PlaySource.Dash -> factory.createMediaSource(item(source.url, MimeTypes.APPLICATION_MPD))
            is PlaySource.Progressive -> ProgressiveMediaSource.Factory(data).createMediaSource(item(source.url))
            is PlaySource.Merged -> MergingMediaSource(
                ProgressiveMediaSource.Factory(data).createMediaSource(item(source.videoUrl)),
                ProgressiveMediaSource.Factory(data).createMediaSource(item(source.audioUrl)),
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
