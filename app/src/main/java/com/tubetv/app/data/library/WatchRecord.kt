package com.tubetv.app.data.library

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Where the viewer left off in one video. One row per video. */
@Entity(tableName = "watch_history")
data class WatchRecord(
    @PrimaryKey val videoUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val channelName: String?,
    val channelUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    /** Near the end counts as finished, so resume restarts instead. */
    val isFinished: Boolean
        get() = durationMs > 0 && positionMs >= durationMs - FINISHED_MARGIN_MS

    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    companion object {
        const val FINISHED_MARGIN_MS = 20_000L
    }
}
