package com.tubetv.app

import android.app.Application
import com.tubetv.app.data.YouTubeSource
import com.tubetv.app.data.library.AppDatabase
import com.tubetv.app.data.library.ChannelLibrary
import com.tubetv.app.data.library.SearchHistory
import com.tubetv.app.data.library.WatchHistoryDao
import com.tubetv.app.data.update.UpdateChecker
import com.tubetv.app.data.youtube.NewPipeSource
import com.tubetv.app.data.youtube.StreamHeaders
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class TubeTvApp : Application() {

    lateinit var http: OkHttpClient
        private set
    /** For the video player: sends each stream request with the user agent YouTube expects for it. */
    lateinit var playerHttp: OkHttpClient
        private set
    lateinit var source: YouTubeSource
        private set
    lateinit var history: WatchHistoryDao
        private set
    lateinit var library: ChannelLibrary
        private set
    lateinit var searchHistory: SearchHistory
        private set
    lateinit var updates: UpdateChecker
        private set

    override fun onCreate() {
        super.onCreate()
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        playerHttp = http.newBuilder().addInterceptor(StreamHeaders).build()
        source = NewPipeSource(http)
        val db = AppDatabase.create(this)
        history = db.watchHistory()
        library = ChannelLibrary(db.channels())
        searchHistory = SearchHistory(this)
        updates = UpdateChecker(http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build(), BuildConfig.VERSION_CODE)
    }
}
