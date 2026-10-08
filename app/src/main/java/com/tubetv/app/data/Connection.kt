package com.tubetv.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.io.IOException

/**
 * Whether the TV is online, and how many requests are waiting to reconnect, so a dropped
 * Wi-Fi shows "reconnecting" and carries on instead of stopping at an error.
 */
class Connection(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(onlineNow())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _waiting = MutableStateFlow(0)
    /** Requests waiting for the network to come back; the screens say "reconnecting" while above 0. */
    val waiting: StateFlow<Int> = _waiting.asStateFlow()

    init {
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        runCatching {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _online.value = onlineNow() }
                override fun onLost(network: Network) { _online.value = onlineNow() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { _online.value = onlineNow() }
            })
        }
    }

    private fun onlineNow(): Boolean = runCatching {
        cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    /** Whether [e] means YouTube couldn't be reached, as opposed to YouTube answering with an error. */
    fun isNetworkError(e: Throwable): Boolean =
        !_online.value || generateSequence(e) { it.cause }.any { it is IOException }

    /** Waits before reconnect attempt [attempt] (from 0): until the network is back, then a growing pause. */
    suspend fun pause(attempt: Int) {
        if (!_online.value) online.first { it }
        delay((1_000L shl attempt.coerceAtMost(4)).coerceAtMost(MAX_PAUSE_MS))
    }

    /** Runs [block], trying again for as long as it fails for want of a network (until cancelled). */
    suspend fun <T> retrying(block: suspend () -> T): T {
        var attempt = 0
        try {
            while (true) {
                try {
                    return block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!isNetworkError(e)) throw e
                    if (attempt == 0) _waiting.update { it + 1 }
                    pause(attempt++)
                }
            }
        } finally {
            if (attempt > 0) _waiting.update { it - 1 }
        }
    }

    private companion object {
        const val MAX_PAUSE_MS = 15_000L
    }
}

/** [inner] with every request retried through a network drop (see [Connection.retrying]). */
class RetryingSource(private val inner: YouTubeSource, private val connection: Connection) : YouTubeSource by inner {
    override suspend fun searchVideos(query: String, page: Any?) = connection.retrying { inner.searchVideos(query, page) }
    override suspend fun searchChannels(query: String, page: Any?) = connection.retrying { inner.searchChannels(query, page) }
    override suspend fun channel(url: String) = connection.retrying { inner.channel(url) }
    override suspend fun channelVideos(url: String, page: Any?) = connection.retrying { inner.channelVideos(url, page) }
    override suspend fun latestVideos(channelUrl: String) = connection.retrying { inner.latestVideos(channelUrl) }
    override suspend fun kiosk(id: String) = connection.retrying { inner.kiosk(id) }
    override suspend fun video(url: String) = connection.retrying { inner.video(url) }
    override suspend fun related(url: String) = connection.retrying { inner.related(url) }
    override suspend fun playback(url: String, full: Boolean) = connection.retrying { inner.playback(url, full) }
}
