package com.tubetv.app.data.library

import com.tubetv.app.data.model.VideoSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CachedFeed(val savedAtMs: Long, val videos: List<VideoSummary>, val personal: Boolean = true)

/** The 首页 tabs' last lists, kept on disk so the app opens with them instead of a loading screen. */
class FeedCache(dir: File) {
    /** v2: lists saved before titles were asked for in the chosen language (they came in English). */
    private val file = File(dir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    companion object {
        const val FILE_NAME = "feeds_v2.json"
    }

    suspend fun load(): Map<String, CachedFeed> = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching { json.decodeFromString<Map<String, CachedFeed>>(file.readText()) }.getOrDefault(emptyMap())
        }
    }

    suspend fun save(feeds: Map<String, CachedFeed>) = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(json.encodeToString(feeds))
                tmp.renameTo(file)
            }
        }
    }
}
