package com.so0420.eunchoy.data

/** The seven notifiable sources. [id] is a stable string used for prefs keys + notification ids. */
enum class SourceKey(val id: String, val label: String, val emoji: String) {
    CHZZK_LIVE("chzzk_live", "치지직 방송", "🔴"),
    CHZZK_COMMUNITY("chzzk_community", "치지직 커뮤니티", "📝"),
    CHZZK_VOD("chzzk_vod", "치지직 다시보기", "🎬"),
    CAFE("cafe", "네이버 카페", "☕"),
    YOUTUBE_MAIN("youtube_main", "유튜브 (메인)", "📺"),
    YOUTUBE_VOD("youtube_vod", "유튜브 (다시보기)", "📼"),
    X("x", "X (트위터)", "🐦");

    companion object {
        fun from(id: String): SourceKey? = entries.firstOrNull { it.id == id }
    }
}
