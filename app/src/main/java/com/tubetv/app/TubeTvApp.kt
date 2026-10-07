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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var prefetchJob: Job? = null

    /**
     * Gets a video ready to play once its card has kept focus for a moment, so scrolling past
     * cards doesn't fetch every one of them.
     */
    fun prefetch(url: String) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            delay(PREFETCH_DELAY_MS)
            source.prefetch(url)
        }
    }

    override fun onCreate() {
        super.onCreate()
        http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        playerHttp = http.newBuilder().addInterceptor(StreamHeaders).build()
        source = NewPipeSource(http, titleLanguage?.let(Locale::forLanguageTag) ?: Locale.getDefault())
        val db = AppDatabase.create(this)
        history = db.watchHistory()
        library = ChannelLibrary(db.channels())
        searchHistory = SearchHistory(this)
        updates = UpdateChecker(http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build(), BuildConfig.VERSION_CODE)
    }

    private val settings by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    /** The language tag titles are asked for in (see [TitleLanguages]); null follows the TV's language. */
    val titleLanguage: String? get() = settings.getString(KEY_TITLE_LANGUAGE, null)

    /** Switches the title language and drops the saved lists, which have titles in the old one. */
    fun setTitleLanguage(tag: String?) {
        settings.edit().putString(KEY_TITLE_LANGUAGE, tag).apply()
        source.setLanguage(tag?.let(Locale::forLanguageTag) ?: Locale.getDefault())
        File(filesDir, "feeds.json").delete()
    }

    private companion object {
        const val PREFETCH_DELAY_MS = 600L
        const val KEY_TITLE_LANGUAGE = "title_language"
    }
}
