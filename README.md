# tubeTV

tubeTV is a YouTube app for Google TV and Android TV, made to be used with the TV remote.
You don't need a Google account: open the app and start watching. You can keep a list of
favourite channels, see their newest videos in one place, and pick up videos where you left off.

The app's menus come in 简体中文, 繁體中文, English, 日本語 and 한국어. They follow the TV's
language unless you pick one in **设置** (see [Settings](#settings)). This guide uses the
Simplified Chinese names.

## Install

1. On the TV, turn on developer options: **Settings → System → About**, then select
   **Android TV OS build** seven times.
2. Download the app: <https://github.com/jh-wu/tubeTV/releases/latest/download/tubetv.apk>
3. Get the file onto the TV. The easiest way is the **Send files to TV** app, on your phone and
   on the TV.
4. When the TV asks, allow that app to install unknown apps
   (**Settings → Apps → Security & restrictions → Unknown sources**), then open the file and install it.

You only do this once. After that, tubeTV checks for a new version each time it starts and offers
**更新**. Your favourites and history are kept when you update.

## Getting around

Press **Left** at the left edge of the screen to open the side bar. From top to bottom:

| Icon | Section | What's there |
| --- | --- | --- |
| House | **首页** | Videos to watch (see below) |
| Bell | **最新视频** | The newest videos from all your favourite channels, newest first |
| Clock | **继续观看** | Videos you started but didn't finish |
| Star | **收藏频道** | Your favourite channels |
| Person | **浏览过的频道** | Every channel you've opened, most recent first |
| Magnifier | **搜索** | Search for videos and channels |
| Gear | **设置** | Settings and updates |

Each video shows its channel, how long ago it came out (for example 3天前) and how many times it
has been watched. Scroll down and more videos load.

### 首页

Tabs run across the top:

- **为你推荐**: suggestions based on what you've watched lately. Press **OK** on the tab to get
  a fresh mix you haven't seen yet.
- **直播, 音乐, 游戏, 电影, 播客**: what's popular on YouTube right now.

Press **OK** on any open tab to reload it. Pressing **Down** past the last video takes you back
up to the tabs.

### Favourite channels

- To add a favourite, open the channel and press **收藏**.
- To remove one, long-press it in 收藏频道.
- A red **新** on a channel means it posted a video in the past week that's newer than your last visit.
- 最新视频 shows 24 videos at a time; scroll down for more. Press **刷新** to check again.

In 浏览过的频道, long-press a channel to add it to your favourites or remove it from the list.
**清除浏览记录** clears the whole list; your favourites stay.

## Watching

Videos in the side-bar sections (首页, 最新视频, 继续观看) play right away. From search or a
channel page, a video opens its page first, where you can play it,
continue from where you stopped, start from the beginning, open the channel, read the **评论**
(comments), or pick a related video.

While a video plays, press any button on the remote to show the controls:

- **Left / Right** on the progress bar skips back or forward 10 seconds.
- The **gear** picks 画质 (quality), 字幕 (subtitles), 音轨 (audio language, on videos that offer
  more than one) and 播放速度 (speed). Videos start in their original language, and the app
  remembers your subtitle choice for the next video.
- The **person icon** at the bottom left offers **打开频道** (go to the channel) and **查看评论**
  (read comments). Press **Back** in the comments to return to the video.

Where you stopped is saved as you watch, so the video shows up in 继续观看.

## Settings

Open **设置** at the bottom of the side bar.

- **检查更新** checks for a new version.
- **语言** picks the language of the app's menus and of video titles: 跟随系统 (the TV's language),
  简体中文, 繁體中文, English, 日本語 or 한국어. The app restarts in the new language. A title only
  changes when the uploader has translated it.
  最新视频 always shows a video's original title.

## When something goes wrong

- **No internet, or the Wi-Fi drops**: the app shows 网络连接不稳定，正在重新连接… and keeps
  trying. Once you're back online, lists finish loading and the video continues where it stopped.
- **A video won't play**: the app tries the video's other streams first. If none of them work,
  press **OK** to open it in the YouTube app.
- **YouTube asks you to confirm you're not a bot**: YouTube does this to some networks from time
  to time. Try again later, or open the video in the YouTube app.

## Privacy

There's no sign-in. Your favourites, history, where you stopped in each video and your recent
searches are stored only on your TV. Uninstalling the app deletes them.

## Things it can't do

- Your personal YouTube home page, subscriptions, likes and playlists. These need a Google
  account, which the app doesn't use.
- Posting comments or opening a comment's replies. You can read comments but not reply.

---

## For developers

tubeTV is written in Kotlin with Compose for TV, Media3 (ExoPlayer) and Room. YouTube data
comes from [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), with no API key.
Every push to `main` builds and publishes a release.

```sh
./gradlew assembleRelease     # needs JDK 17+ and the Android SDK (platform 35)
./gradlew testDebugUnitTest   # unit tests
TUBETV_LIVE=1 ./gradlew testDebugUnitTest --tests '*LiveYouTubeTest*'   # against real YouTube
```

When YouTube changes and something breaks, update `newpipeExtractor` in
`gradle/libs.versions.toml`. All builds are signed with the checked-in `app/debug.keystore`, so
new versions install over old ones.
