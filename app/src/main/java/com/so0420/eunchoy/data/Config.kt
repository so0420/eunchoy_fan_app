package com.so0420.eunchoy.data

/** Static identifiers + URL builders for 은초이's accounts (verified 2026-06-28). */
object Config {
    const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // Chzzk
    const val CHZZK_CHANNEL_ID = "fd8516eb8d31a8a5147e94c281ae3f07"

    // Naver Cafe (은초이 공식 카페)
    const val CAFE_ID = 31466153L
    const val CAFE_URL_NAME = "eunchoy"
    const val CAFE_MEMBER_KEY = "eD2ZYpSl1N5IUVBze12Ni2qk3fbd9TnEpAP8-RvcKD8"

    // YouTube
    const val YT_MAIN_CHANNEL_ID = "UCLj64SVR6s5cu0IEozBEphg" // @은초이Eunchoy
    const val YT_VOD_CHANNEL_ID = "UCchU7_y7AMPj03CgBjtsRDA"  // @금초이 (다시보기)

    // X / Twitter
    const val X_HANDLE = "Eun_choy"
    const val DEFAULT_X_BRIDGE = "https://nitter.net/$X_HANDLE/rss"

    fun chzzkLiveUrl() = "https://chzzk.naver.com/live/$CHZZK_CHANNEL_ID"
    fun chzzkChannelUrl() = "https://chzzk.naver.com/$CHZZK_CHANNEL_ID"
    fun chzzkCommunityUrl() = "https://chzzk.naver.com/$CHZZK_CHANNEL_ID/community"
    fun chzzkVideoUrl(videoNo: Long) = "https://chzzk.naver.com/video/$videoNo"

    fun cafeMemberUrl() = "https://cafe.naver.com/f-e/cafes/$CAFE_ID/members/$CAFE_MEMBER_KEY"
    fun cafeArticleUrl(articleId: Long) =
        "https://m.cafe.naver.com/ca-fe/web/cafes/$CAFE_URL_NAME/articles/$articleId"

    fun youtubeMainUrl() = "https://www.youtube.com/channel/$YT_MAIN_CHANNEL_ID"
    fun youtubeVodUrl() = "https://www.youtube.com/channel/$YT_VOD_CHANNEL_ID"
    fun youtubeWatchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"
    fun youtubeRssUrl(channelId: String) =
        "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

    fun xProfileUrl() = "https://x.com/$X_HANDLE"
    fun tweetUrl(id: Long) = "https://x.com/$X_HANDLE/status/$id"
}
