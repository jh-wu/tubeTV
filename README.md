# tubeTV

A native Android TV / Google TV client for YouTube, written in Kotlin with Jetpack Compose for TV,
Media3 (ExoPlayer) and Room. It follows the design of [iyfTV](https://github.com/jh-wu/iyftv), with
favourite channels in place of iyfTV's fixed categories.

## Features (v1)

- An icon bar that slides in from the left (press left at the screen's edge) holds the sections (首页, 继续观看, 最新视频, 收藏频道, 浏览过的频道),
  then 搜索 and 设置.
- **首页** (the default section) has six tabs across the top. OK on the open tab reloads it;
  down past the last video goes back up to the tab. **为你推荐** mixes videos related to
  what you watched recently with new uploads from your favourite and browsed channels (with none
  yet, it mixes YouTube's lists). Then YouTube's public lists: 直播, 音乐, 游戏, 电影, 播客.
  YouTube's own personal home feed needs a Google sign-in, which the app doesn't do.
- While playing, the gear in the control bar picks subtitles (remembered for the next video),
  the audio language on dubbed videos, and the speed.
- **最新视频**: the newest uploads of all favourite channels, merged into one grid, newest first.
- **收藏频道**: favourite channels. Add one with **收藏** on its channel page; long-press a channel
  here to remove it. The **+** card opens search.
- **浏览过的频道**: every channel you open is remembered here, most recent first, with how often
  you opened it. Long-press to favourite or forget one; **清除浏览记录** empties the list
  (favourites stay).
- **Search**: channels in a row, videos in a paged grid, with recent search terms.
- **Channel page**: avatar, subscribers, description, the favourite button, and its uploads.
- **Video page**: play or resume, open the channel, related videos.
- **Playback**: full-screen with D-pad controls. Progress is saved every 10 seconds; the
  继续观看 tab lists unfinished videos. If none of a video's streams play, OK opens it in the
  YouTube app instead.

Favourites, browsing history, watch progress and search terms are stored on the TV only
(Room database and SharedPreferences); there is no Google sign-in.

## Install on Google TV

Every push to `main` publishes a signed APK to the latest GitHub release:
<https://github.com/jh-wu/tubeTV/releases/latest/download/tubetv.apk>

1. On the TV: Settings → System → About → click **Android TV OS build** 7 times to enable developer options.
2. Get the APK onto the TV, either with the **Send files to TV** app or with `adb install tubetv.apk` over the network.
3. Allow the app you used under Settings → Apps → Security & restrictions → **Unknown sources**, then install.

Later builds install over the old one and keep your favourites and history, because all builds
share the same signing key (`app/debug.keystore`). As in iyfTV, the app checks the latest GitHub
release on start and offers **更新**; this needs the repo to be public.

## Build

Requires Android Studio (or the Android SDK with platform 35) and JDK 17+.

```sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Layout

```
app/src/main/java/com/tubetv/app/
  data/YouTubeSource.kt        interface the UI talks to
  data/youtube/                YouTube client built on NewPipeExtractor
  data/library/                Room database: watch progress, favourite and browsed channels
  ui/                          Compose for TV screens and the Media3 player activity
```

## How the YouTube integration works

[NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) reads the same data the
YouTube website and apps use, without an API key or account. Search, channel pages and video
pages come from it; "最新视频" uses each channel's RSS feed (one small request per channel) and
falls back to the channel's video tab. For playback the app prefers YouTube's HLS manifest, then
DASH, then separate video and audio files, then a single low-resolution file, and sends each
stream request with the user agent of the YouTube app the link was issued for.

When YouTube changes something, extraction breaks until NewPipeExtractor ships a fix; bump
`newpipeExtractor` in `gradle/libs.versions.toml` to pick it up.

- `./gradlew testDebugUnitTest` runs the unit tests.
- `TUBETV_LIVE=1 ./gradlew testDebugUnitTest --tests '*LiveYouTubeTest*'` runs the client against YouTube.
  CI's `probe-youtube` job does this on every push without failing the build.
