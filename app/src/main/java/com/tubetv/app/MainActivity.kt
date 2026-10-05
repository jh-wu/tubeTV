package com.tubetv.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
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
        val play = { url: String, fromStart: Boolean -> startActivity(PlayerActivity.intent(this, url, fromStart)) }

        setContent {
            TubeTheme {
                Surface(Modifier.fillMaxSize(), shape = RectangleShape) {
                    val updates = viewModel { UpdateViewModel(app.updates, app) }
                    UpdateDialog(updates)
                    val nav = rememberNavController()
                    val openVideo = { url: String -> nav.navigate("video/${Uri.encode(url)}") }
                    val openChannel = { url: String -> nav.navigate("channel/${Uri.encode(url)}") }

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
