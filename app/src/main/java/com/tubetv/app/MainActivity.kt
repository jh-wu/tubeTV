package com.tubetv.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Surface
import com.tubetv.app.data.library.FeedCache
import com.tubetv.app.ui.channel.ChannelScreen
import com.tubetv.app.ui.channel.ChannelViewModel
import com.tubetv.app.ui.common.LocalPrefetch
import com.tubetv.app.ui.common.TubeTheme
import com.tubetv.app.ui.home.HomeScreen
import com.tubetv.app.ui.home.HomeViewModel
import com.tubetv.app.ui.player.PlayerActivity
import com.tubetv.app.ui.search.SearchScreen
import com.tubetv.app.ui.search.SearchViewModel
import com.tubetv.app.ui.update.UpdateDialog
import com.tubetv.app.ui.update.UpdateViewModel
import com.tubetv.app.ui.video.VideoScreen
import com.tubetv.app.ui.video.VideoViewModel

class MainActivity : ComponentActivity() {

    /** A channel the player's channel button asked for, opened once the screen is back. */
    private var pendingChannel by mutableStateOf<String?>(null)

    private val player = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringExtra(PlayerActivity.RESULT_CHANNEL)?.let { pendingChannel = it }
    }

    private fun openInYouTube(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "没有找到 YouTube 应用", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TubeTvApp
        val play = { url: String, fromStart: Boolean -> player.launch(PlayerActivity.intent(this, url, fromStart)) }

        setContent {
            TubeTheme {
                CompositionLocalProvider(LocalPrefetch provides app::prefetch) {
                    Surface(Modifier.fillMaxSize(), shape = RectangleShape) {
                        val updates = viewModel { UpdateViewModel(app.updates, app) }
                        // A new title language: start the screens over so every list is fetched in it.
                        UpdateDialog(updates, onLanguageChanged = {
                            startActivity(Intent(this@MainActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        })
                        val nav = rememberNavController()
                        val openVideo = { url: String -> nav.navigate("video/${Uri.encode(url)}") }
                        val openChannel = { url: String -> nav.navigate("channel/${Uri.encode(url)}") }
                        LaunchedEffect(pendingChannel) {
                            pendingChannel?.let {
                                pendingChannel = null
                                openChannel(it)
                            }
                        }

                        NavHost(nav, startDestination = "home") {
                            composable("home") {
                                HomeScreen(
                                    vm = viewModel { HomeViewModel(app.source, app.history, app.library, app.getSharedPreferences("home", MODE_PRIVATE), FeedCache(app.filesDir)) },
                                    // Home plays a video straight away; leaving playback comes back here.
                                    onOpenVideo = { url -> play(url, false) },
                                    onOpenChannel = openChannel,
                                    onResume = { play(it.videoUrl, false) },
                                    onSearch = { nav.navigate("search") },
                                    onSettings = updates::showAbout,
                                )
                            }
                            composable("search") {
                                SearchScreen(
                                    vm = viewModel { SearchViewModel(app.source, app.searchHistory, app.library.favourites) },
                                    onOpenVideo = openVideo,
                                    onOpenChannel = openChannel,
                                )
                            }
                            composable("channel/{url}") { entry ->
                                val url = entry.arguments?.getString("url").orEmpty()
                                ChannelScreen(vm = viewModel { ChannelViewModel(app.source, app.library, url) }, onOpenVideo = openVideo)
                            }
                            composable("video/{url}") { entry ->
                                val url = entry.arguments?.getString("url").orEmpty()
                                VideoScreen(
                                    vm = viewModel { VideoViewModel(app.source, app.history, url) },
                                    onPlay = { fromStart -> play(url, fromStart) },
                                    onOpenChannel = openChannel,
                                    onOpenVideo = openVideo,
                                    onOpenInYouTube = { openInYouTube(url) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
