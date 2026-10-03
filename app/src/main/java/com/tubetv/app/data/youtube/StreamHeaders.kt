package com.tubetv.app.data.youtube

import okhttp3.Interceptor
import okhttp3.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * YouTube's video servers expect the user agent of the app a stream link was issued to
 * (the link names it in its `c` parameter), so the player's requests carry that one.
 */
object StreamHeaders : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val agent = userAgentFor(request.url.toString()) ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("User-Agent", agent).build())
    }

    /** The user agent a googlevideo.com link expects, or null for other links. */
    fun userAgentFor(url: String): String? {
        if (!url.contains("googlevideo.com")) return null
        return when (clientOf(url)) {
            "VISIONOS" -> YoutubeParsingHelper.getVisionOsUserAgent(null)
            "IOS" -> YoutubeParsingHelper.getIosUserAgent(null)
            "ANDROID" -> YoutubeParsingHelper.getAndroidUserAgent(null)
            else -> null
        }
    }

    /** The `c` value, written as `c=NAME` in a query or `/c/NAME/` in a manifest path. */
    fun clientOf(url: String): String? =
        CLIENT.find(url)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

    private val CLIENT = Regex("[?&]c=([A-Z_]+)(?:&|$)|/c/([A-Z_]+)(?:/|$)")
}
