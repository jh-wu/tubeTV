package com.tubetv.app

import java.util.Locale

/** The languages the app's own text comes in. */
enum class UiLanguage(val locale: Locale) {
    SimplifiedChinese(Locale.SIMPLIFIED_CHINESE),
    TraditionalChinese(Locale.TRADITIONAL_CHINESE),
    English(Locale.ENGLISH),
    Japanese(Locale.JAPANESE),
    Korean(Locale.KOREAN);

    companion object {
        /** The app language for [locale]: its Chinese script, Japanese, Korean or English (for anything else). */
        fun of(locale: Locale): UiLanguage = when (locale.language) {
            "zh" -> if (locale.script == "Hant" || (locale.script.isEmpty() && locale.country in setOf("TW", "HK", "MO"))) {
                TraditionalChinese
            } else {
                SimplifiedChinese
            }
            "ja" -> Japanese
            "ko" -> Korean
            "en" -> English
            else -> English
        }
    }
}

/**
 * Every piece of text the app shows, in the language picked in 设置 (see [TubeTvApp.setLanguage]).
 * Changing the language starts the screens over, so each screen reads these afresh.
 */
object Strings {
    @Volatile var language = UiLanguage.SimplifiedChinese

    val locale: Locale get() = language.locale

    private fun t(sc: String, tc: String, en: String, ja: String, ko: String) = when (language) {
        UiLanguage.SimplifiedChinese -> sc
        UiLanguage.TraditionalChinese -> tc
        UiLanguage.English -> en
        UiLanguage.Japanese -> ja
        UiLanguage.Korean -> ko
    }

    // Sections and tabs
    val home get() = t("首页", "首頁", "Home", "ホーム", "홈")
    val latest get() = t("最新视频", "最新影片", "Latest", "新着動画", "최신 동영상")
    val continueWatching get() = t("继续观看", "繼續觀看", "Continue watching", "続きを見る", "이어서 보기")
    val favouriteChannels get() = t("收藏频道", "收藏頻道", "Favourite channels", "お気に入りチャンネル", "즐겨찾는 채널")
    val browsedChannels get() = t("浏览过的频道", "瀏覽過的頻道", "Channels you've visited", "見たチャンネル", "방문한 채널")
    val search get() = t("搜索", "搜尋", "Search", "検索", "검색")
    val settings get() = t("设置", "設定", "Settings", "設定", "설정")
    val forYou get() = t("为你推荐", "為你推薦", "For you", "おすすめ", "추천")
    val live get() = t("直播", "直播", "Live", "ライブ", "라이브")
    val music get() = t("音乐", "音樂", "Music", "音楽", "음악")
    val gaming get() = t("游戏", "遊戲", "Gaming", "ゲーム", "게임")
    val movies get() = t("电影", "電影", "Movies", "映画", "영화")
    val podcasts get() = t("播客", "Podcast", "Podcasts", "ポッドキャスト", "팟캐스트")

    // Loading and errors
    val loading get() = t("加载中…", "載入中…", "Loading…", "読み込み中…", "불러오는 중…")
    fun loadFailed(message: String) = t("加载失败：$message", "載入失敗：$message", "Couldn't load: $message", "読み込めませんでした：$message", "불러오지 못했습니다: $message")
    val retry get() = t("重试", "重試", "Retry", "再試行", "다시 시도")
    val reconnecting get() = t("网络连接不稳定，正在重新连接…", "網路連線不穩定，正在重新連線…", "Connection lost, reconnecting…", "接続が不安定です。再接続しています…", "연결이 불안정합니다. 다시 연결하는 중…")
    val noYouTubeApp get() = t("没有找到 YouTube 应用", "找不到 YouTube 應用程式", "The YouTube app isn't installed", "YouTube アプリが見つかりません", "YouTube 앱을 찾을 수 없습니다")
    val notABot get() = t(
        "YouTube 暂时要求这个网络登录验证（“确认你不是机器人”）。请稍后再试，或用 YouTube 应用打开。",
        "YouTube 暫時要求這個網路登入驗證（「確認你不是機器人」）。請稍後再試，或用 YouTube 應用程式開啟。",
        "YouTube is asking this network to sign in (\"confirm you're not a bot\"). Try again later, or open it in the YouTube app.",
        "YouTube がこのネットワークにログインを求めています（「ボットでないことの確認」）。後でもう一度試すか、YouTube アプリで開いてください。",
        "YouTube에서 이 네트워크에 로그인을 요구하고 있습니다(\"봇이 아닌지 확인\"). 나중에 다시 시도하거나 YouTube 앱에서 여세요.",
    )
    val noStreams get() = t("YouTube 没有提供可播放的视频流", "YouTube 沒有提供可播放的影片串流", "YouTube offered no stream that can be played", "YouTube に再生できるストリームがありません", "YouTube에 재생할 수 있는 스트림이 없습니다")
    val noList get() = t("YouTube 没有返回这个列表", "YouTube 沒有傳回這個列表", "YouTube didn't return this list", "YouTube からこのリストが返されませんでした", "YouTube에서 이 목록을 받지 못했습니다")

    // Counts and times
    fun views(count: String) = t("${count}次观看", "${count}次觀看", "$count views", "${count}回視聴", "조회수 ${count}회")
    fun subscribers(count: String) = t("${count}位订阅者", "${count}位訂閱者", "$count subscribers", "登録者${count}人", "구독자 ${count}명")
    val justNow get() = t("刚刚", "剛剛", "just now", "たった今", "방금")
    fun minutesAgo(n: Long) = t("${n}分钟前", "${n}分鐘前", plural(n, "minute") + " ago", "${n}分前", "${n}분 전")
    fun hoursAgo(n: Long) = t("${n}小时前", "${n}小時前", plural(n, "hour") + " ago", "${n}時間前", "${n}시간 전")
    fun daysAgo(n: Long) = t("${n}天前", "${n}天前", plural(n, "day") + " ago", "${n}日前", "${n}일 전")
    fun weeksAgo(n: Long) = t("${n}周前", "${n}週前", plural(n, "week") + " ago", "${n}週間前", "${n}주 전")
    fun monthsAgo(n: Long) = t("${n}个月前", "${n}個月前", plural(n, "month") + " ago", "${n}か月前", "${n}개월 전")
    fun yearsAgo(n: Long) = t("${n}年前", "${n}年前", plural(n, "year") + " ago", "${n}年前", "${n}년 전")
    private fun plural(n: Long, word: String) = if (n == 1L) "1 $word" else "$n ${word}s"

    // Videos and channels
    val liveBadge get() = live
    val liveNow get() = t("直播中", "直播中", "Live now", "ライブ配信中", "실시간")
    val newBadge get() = t("新", "新", "NEW", "新着", "NEW")
    val favourited get() = t("已收藏", "已收藏", "Favourite", "お気に入り", "즐겨찾기")
    val noVideos get() = t("没有视频", "沒有影片", "No videos", "動画がありません", "동영상 없음")
    val channelHasNoVideos get() = t("这个频道还没有视频", "這個頻道還沒有影片", "This channel has no videos yet", "このチャンネルにはまだ動画がありません", "이 채널에는 아직 동영상이 없습니다")
    val favourite get() = t("收藏", "收藏", "Add to favourites", "お気に入りに追加", "즐겨찾기에 추가")
    val unfavouriteToggle get() = t("已收藏（再按取消）", "已收藏（再按取消）", "In favourites (press to remove)", "お気に入り済み（押すと解除）", "즐겨찾기됨 (누르면 해제)")
    val unfavourite get() = t("取消收藏", "取消收藏", "Remove from favourites", "お気に入りから削除", "즐겨찾기 해제")
    fun unfavouriteQuestion(name: String) = t("取消收藏“$name”？", "取消收藏「$name」？", "Remove \"$name\" from favourites?", "「$name」をお気に入りから削除しますか？", "\"$name\"을(를) 즐겨찾기에서 해제할까요?")
    val back get() = t("返回", "返回", "Back", "戻る", "뒤로")
    val play get() = t("播放", "播放", "Play", "再生", "재생")
    fun resumeAt(time: String) = t("继续播放 $time", "繼續播放 $time", "Resume from $time", "$time から再生", "$time 부터 이어서 재생")
    val playFromStart get() = t("从头播放", "從頭播放", "Play from start", "最初から再生", "처음부터 재생")
    val comments get() = t("评论", "留言", "Comments", "コメント", "댓글")
    val related get() = t("相关视频", "相關影片", "Related videos", "関連動画", "관련 동영상")
    val openInYouTube get() = t("用 YouTube 应用打开", "用 YouTube 應用程式開啟", "Open in the YouTube app", "YouTube アプリで開く", "YouTube 앱에서 열기")

    // Home sections
    val nothingHalfWatched get() = t("还没有看到一半的视频", "還沒有看到一半的影片", "No unfinished videos yet", "途中まで見た動画はまだありません", "보다 만 동영상이 없습니다")
    fun watchedTo(time: String) = t("看到 $time", "看到 $time", "Watched to $time", "$time まで視聴", "$time 까지 시청")
    val favouritesHaveNoVideos get() = t("收藏的频道还没有视频", "收藏的頻道還沒有影片", "Your favourite channels have no videos yet", "お気に入りチャンネルにはまだ動画がありません", "즐겨찾는 채널에 아직 동영상이 없습니다")
    val refreshing get() = t("正在刷新…", "正在重新整理…", "Refreshing…", "更新中…", "새로고침 중…")
    val refresh get() = t("刷新", "重新整理", "Refresh", "更新", "새로고침")
    fun channelsFailed(names: List<String>) = t(
        "这些频道没能加载：${names.joinToString("、")}", "這些頻道沒能載入：${names.joinToString("、")}",
        "Couldn't load: ${names.joinToString(", ")}", "読み込めなかったチャンネル：${names.joinToString("、")}", "불러오지 못한 채널: ${names.joinToString(", ")}",
    )
    fun latestFrom(count: Int) = t("$count 个收藏频道的最新视频", "$count 個收藏頻道的最新影片", "Latest from $count favourite channels", "お気に入り $count チャンネルの新着動画", "즐겨찾는 채널 ${count}개의 최신 동영상")
    val noFavourites get() = t("还没有收藏的频道", "還沒有收藏的頻道", "No favourite channels yet", "お気に入りチャンネルはまだありません", "즐겨찾는 채널이 없습니다")
    val noFavouritesHint get() = t(
        "搜索一个频道并打开它，按“收藏”，它的最新视频就会出现在这里。",
        "搜尋一個頻道並打開它，按「收藏」，它的最新影片就會出現在這裡。",
        "Search for a channel, open it and press \"Add to favourites\"; its newest videos will show up here.",
        "チャンネルを検索して開き、「お気に入りに追加」を押すと、新着動画がここに表示されます。",
        "채널을 검색해 열고 \"즐겨찾기에 추가\"를 누르면 최신 동영상이 여기에 표시됩니다.",
    )
    val searchChannels get() = t("搜索频道", "搜尋頻道", "Search channels", "チャンネルを検索", "채널 검색")
    val favouritesHint get() = t(
        "标“新”的频道在你上次打开后有新视频 · 长按频道可取消收藏",
        "標「新」的頻道在你上次打開後有新影片 · 長按頻道可取消收藏",
        "NEW: uploaded since you last opened it · Long-press a channel to remove it",
        "「新着」はあなたが最後に開いてから新しい動画があるチャンネル · 長押しで削除",
        "NEW: 마지막으로 연 뒤 새 동영상이 있는 채널 · 길게 눌러 해제",
    )
    val addChannel get() = t("添加频道", "新增頻道", "Add channel", "チャンネルを追加", "채널 추가")
    val noBrowsed get() = t("还没有浏览过频道。打开过的频道会记在这里。", "還沒有瀏覽過頻道。打開過的頻道會記在這裡。", "No channels yet. Channels you open are listed here.", "まだチャンネルを見ていません。開いたチャンネルがここに表示されます。", "아직 방문한 채널이 없습니다. 연 채널이 여기에 표시됩니다.")
    val clearBrowsed get() = t("清除浏览记录", "清除瀏覽記錄", "Clear history", "履歴を消去", "기록 지우기")
    val browsedHint get() = t(
        "标“新”的频道在你上次打开后有新视频 · 长按频道可收藏或删除",
        "標「新」的頻道在你上次打開後有新影片 · 長按頻道可收藏或刪除",
        "NEW: uploaded since you last opened it · Long-press a channel to favourite or remove it",
        "「新着」はあなたが最後に開いてから新しい動画があるチャンネル · 長押しでお気に入り追加・削除",
        "NEW: 마지막으로 연 뒤 새 동영상이 있는 채널 · 길게 눌러 즐겨찾기 또는 삭제",
    )
    fun visits(n: Int) = t("看过 $n 次", "看過 $n 次", if (n == 1) "Visited once" else "Visited $n times", "$n 回表示", "${n}번 방문")
    val removeFromHistory get() = t("从记录中删除", "從記錄中刪除", "Remove from history", "履歴から削除", "기록에서 삭제")
    val clearAllQuestion get() = t("清除全部浏览记录？", "清除全部瀏覽記錄？", "Clear all channel history?", "チャンネル履歴をすべて消去しますか？", "채널 기록을 모두 지울까요?")
    val favouritesKept get() = t("收藏的频道不受影响。", "收藏的頻道不受影響。", "Your favourite channels stay.", "お気に入りチャンネルはそのまま残ります。", "즐겨찾는 채널은 유지됩니다.")
    val clear get() = t("清除", "清除", "Clear", "消去", "지우기")

    // Search
    val searchHint get() = t("搜索频道或视频", "搜尋頻道或影片", "Search channels or videos", "チャンネルや動画を検索", "채널 또는 동영상 검색")
    val recentSearches get() = t("搜索记录", "搜尋記錄", "Recent searches", "検索履歴", "최근 검색")
    val clearSearches get() = t("清除搜索记录", "清除搜尋記錄", "Clear searches", "検索履歴を消去", "검색 기록 지우기")
    val typeToSearch get() = t("输入频道名或视频标题后按搜索", "輸入頻道名稱或影片標題後按搜尋", "Type a channel name or video title, then press Search", "チャンネル名か動画タイトルを入力して検索を押してください", "채널 이름이나 동영상 제목을 입력한 뒤 검색을 누르세요")
    val noVideosFound get() = t("没有找到视频", "沒有找到影片", "No videos found", "動画が見つかりません", "동영상을 찾지 못했습니다")
    val searchingChannels get() = t("正在搜索频道…", "正在搜尋頻道…", "Searching channels…", "チャンネルを検索中…", "채널 검색 중…")
    fun channelSearchFailed(message: String) = t("频道搜索失败：$message", "頻道搜尋失敗：$message", "Channel search failed: $message", "チャンネル検索に失敗しました：$message", "채널 검색 실패: $message")
    val noChannelsFound get() = t("没有找到频道", "沒有找到頻道", "No channels found", "チャンネルが見つかりません", "채널을 찾지 못했습니다")
    val channels get() = t("频道", "頻道", "Channels", "チャンネル", "채널")
    val videos get() = t("视频", "影片", "Videos", "動画", "동영상")

    // Comments
    val noComments get() = t("这个视频没有评论", "這部影片沒有留言", "No comments on this video", "この動画にはコメントがありません", "이 동영상에는 댓글이 없습니다")
    val pinned get() = t("置顶", "置頂", "Pinned", "固定", "고정됨")
    fun likes(count: String) = t("赞 $count", "讚 $count", "$count likes", "高評価 $count", "좋아요 $count")
    fun replies(n: Int) = t("$n 条回复", "$n 則回覆", if (n == 1) "1 reply" else "$n replies", "返信 $n 件", "답글 ${n}개")
    val heartedByCreator get() = t("作者点了赞", "作者按了愛心", "Hearted by the creator", "クリエイターがハート", "크리에이터가 하트를 누름")

    // Player
    val channel get() = t("频道", "頻道", "Channel", "チャンネル", "채널")
    val thisVideo get() = t("这个视频", "這部影片", "This video", "この動画", "이 동영상")
    val channelMenuMessage get() = t(
        "打开频道页会停止播放；看完评论按返回键继续播放。",
        "打開頻道頁會停止播放；看完留言按返回鍵繼續播放。",
        "Opening the channel stops the video. After reading comments, press Back to keep watching.",
        "チャンネルを開くと再生が止まります。コメントを読んだら戻るボタンで再生に戻ります。",
        "채널을 열면 재생이 멈춥니다. 댓글을 본 뒤 뒤로 버튼을 누르면 계속 재생됩니다.",
    )
    val openChannel get() = t("打开频道", "打開頻道", "Open channel", "チャンネルを開く", "채널 열기")
    val viewComments get() = t("查看评论", "查看留言", "View comments", "コメントを見る", "댓글 보기")
    val keepWatching get() = t("继续播放", "繼續播放", "Keep watching", "再生を続ける", "계속 보기")
    val quality get() = t("画质", "畫質", "Quality", "画質", "화질")
    val subtitles get() = t("字幕", "字幕", "Subtitles", "字幕", "자막")
    val audioTrack get() = t("音轨", "音軌", "Audio", "音声", "오디오")
    val speed get() = t("播放速度", "播放速度", "Speed", "再生速度", "재생 속도")
    fun setting(name: String, value: String) = t("$name：$value", "$name：$value", "$name: $value", "$name：$value", "$name: $value")
    val off get() = t("关闭", "關閉", "Off", "オフ", "끄기")
    val unknown get() = t("未知", "未知", "Unknown", "不明", "알 수 없음")
    val noSubtitles get() = t("这个视频没有字幕", "這部影片沒有字幕", "This video has no subtitles", "この動画には字幕がありません", "이 동영상에는 자막이 없습니다")
    val default get() = t("默认", "預設", "Default", "デフォルト", "기본")
    val auto get() = t("自动", "自動", "Auto", "自動", "자동")
    fun autoGenerated(name: String) = t("$name（自动生成）", "$name（自動產生）", "$name (auto-generated)", "$name（自動生成）", "$name (자동 생성)")
    fun originalAudio(name: String) = t("$name（原声）", "$name（原音）", "$name (original)", "$name（オリジナル）", "$name (원본)")
    val tried get() = t("尝试过：", "嘗試過：", "Tried:", "試したもの：", "시도한 것:")
    fun playbackFailed(reason: String) = t("播放失败：$reason", "播放失敗：$reason", "Couldn't play: $reason", "再生できませんでした：$reason", "재생하지 못했습니다: $reason")
    val failedHelp get() = t("按确认键用 YouTube 应用打开，按返回键退出", "按確認鍵用 YouTube 應用程式開啟，按返回鍵退出", "Press OK to open it in the YouTube app, or Back to leave", "決定ボタンで YouTube アプリで開く、戻るボタンで終了", "확인을 눌러 YouTube 앱에서 열거나 뒤로를 눌러 나가세요")

    // Settings and updates
    fun currentVersion(build: Int) = t("当前版本：build $build", "目前版本：build $build", "Current version: build $build", "現在のバージョン：build $build", "현재 버전: build $build")
    fun about(build: Int, name: String, repo: String) = t(
        "当前版本：build $build（$name）\n更新来源：GitHub $repo", "目前版本：build $build（$name）\n更新來源：GitHub $repo",
        "Current version: build $build ($name)\nUpdates from: GitHub $repo", "現在のバージョン：build $build（$name）\n更新元：GitHub $repo",
        "현재 버전: build $build ($name)\n업데이트 출처: GitHub $repo",
    )
    val checkForUpdates get() = t("检查更新", "檢查更新", "Check for updates", "アップデートを確認", "업데이트 확인")
    val languageSetting get() = t("语言", "語言", "Language", "言語", "언어")
    val languageMessage get() = t(
        "菜单和视频标题都用这个语言显示（视频标题要上传者提供了翻译）。",
        "選單和影片標題都用這個語言顯示（影片標題要上傳者提供了翻譯）。",
        "Menus and video titles use this language (titles only where the uploader translated them).",
        "メニューと動画タイトルをこの言語で表示します（タイトルは投稿者が翻訳している場合のみ）。",
        "메뉴와 동영상 제목을 이 언어로 표시합니다(제목은 업로더가 번역한 경우에만).",
    )
    val followSystem get() = t("跟随系统", "跟隨系統", "Same as the TV", "テレビと同じ", "TV와 같게")
    val close get() = t("关闭", "關閉", "Close", "閉じる", "닫기")
    val checking get() = t("正在检查更新…", "正在檢查更新…", "Checking for updates…", "アップデートを確認中…", "업데이트 확인 중…")
    val cancel get() = t("取消", "取消", "Cancel", "キャンセル", "취소")
    val upToDate get() = t("已是最新版本", "已是最新版本", "You're up to date", "最新バージョンです", "최신 버전입니다")
    val ok get() = t("好", "好", "OK", "OK", "확인")
    fun newVersion(build: Int) = t("发现新版本 build $build", "發現新版本 build $build", "New version: build $build", "新しいバージョン：build $build", "새 버전: build $build")
    val update get() = t("更新", "更新", "Update", "アップデート", "업데이트")
    val later get() = t("以后再说", "以後再說", "Later", "後で", "나중에")
    fun downloading(build: Int, percent: Int) = t("正在下载 build $build… $percent%", "正在下載 build $build… $percent%", "Downloading build $build… $percent%", "build $build をダウンロード中… $percent%", "build $build 다운로드 중… $percent%")
    fun downloaded(build: Int) = t("build $build 已下载", "build $build 已下載", "Build $build downloaded", "build $build をダウンロードしました", "build $build 다운로드 완료")
    val allowInstall get() = t(
        "请在设置里允许 tubeTV 安装未知应用，然后返回按“安装”。", "請在設定裡允許 tubeTV 安裝未知應用程式，然後返回按「安裝」。",
        "Allow tubeTV to install unknown apps in Settings, then come back and press Install.",
        "設定で tubeTV に不明なアプリのインストールを許可してから、戻って「インストール」を押してください。",
        "설정에서 tubeTV의 알 수 없는 앱 설치를 허용한 뒤 돌아와 \"설치\"를 누르세요.",
    )
    val pressInstall get() = t("如果没有出现安装界面，请按“安装”。", "如果沒有出現安裝畫面，請按「安裝」。", "If the installer didn't open, press Install.", "インストール画面が出ない場合は「インストール」を押してください。", "설치 화면이 나타나지 않으면 \"설치\"를 누르세요.")
    val install get() = t("安装", "安裝", "Install", "インストール", "설치")
    val updateFailed get() = t("更新失败", "更新失敗", "Update failed", "アップデートに失敗しました", "업데이트 실패")
    val invalidPackage get() = t("下载的文件不是有效的安装包", "下載的檔案不是有效的安裝套件", "The download isn't a valid app package", "ダウンロードしたファイルは有効なアプリではありません", "다운로드한 파일이 올바른 앱 패키지가 아닙니다")
    val noInstaller get() = t("无法打开系统安装程序", "無法開啟系統安裝程式", "Couldn't open the system installer", "システムのインストーラを開けませんでした", "시스템 설치 프로그램을 열 수 없습니다")
}
