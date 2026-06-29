package com.so0420.eunchoy.work

import com.so0420.eunchoy.AppContainer
import com.so0420.eunchoy.data.Config
import com.so0420.eunchoy.data.SourceKey
import com.so0420.eunchoy.data.settings.AppSettings
import com.so0420.eunchoy.data.settings.SourcePrefs
import com.so0420.eunchoy.notif.AlertPayload
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Polls every enabled source once, diffs against persisted "seen" state, and emits notifications
 * for genuinely new items. First-ever run per source only *seeds* state (no backlog notification storm).
 */
class Poller(private val container: AppContainer) {

    private val repo get() = container.repo
    private val settings get() = container.settings
    private val notifier get() = container.notifier

    suspend fun runOnce() = mutex.withLock {
        // Serialize across the periodic worker + fast foreground service (same process) so the
        // getSeen -> setSeen read-modify-write can't interleave and double-notify.
        val s = settings.current()
        safe { checkLive(s) }
        safe { checkCommunity(s) }
        safe { checkVod(s) }
        safe { checkCafe(s) }
        safe { checkYoutube(s, SourceKey.YOUTUBE_MAIN) }
        safe { checkYoutube(s, SourceKey.YOUTUBE_VOD) }
        safe { checkX(s) }
    }

    private inline fun safe(block: () -> Unit) = runCatching { block() }

    private suspend fun emit(prefs: SourcePrefs, payload: AlertPayload) {
        if (!prefs.notify) return
        notifier.post(payload, alarm = prefs.alarm)
    }

    // ---- Chzzk live: notify on CLOSE->OPEN (or a new broadcast openDate) ----
    private suspend fun checkLive(s: AppSettings) {
        val prefs = s.prefs(SourceKey.CHZZK_LIVE)
        if (!prefs.notify && !s.autoOpenLive) return
        val detail = repo.liveDetail() ?: return
        val prev = settings.getSeen(SourceKey.CHZZK_LIVE)
        if (!detail.isLive || detail.openDate == null) {
            // Record "observed offline" so the next CLOSE->OPEN is treated as new even right after install.
            if (prev == null) settings.setSeen(SourceKey.CHZZK_LIVE, OFFLINE_SENTINEL)
            return
        }
        if (prev == detail.openDate) return
        settings.setSeen(SourceKey.CHZZK_LIVE, detail.openDate)
        if (prev == null) return // first poll caught an already-running broadcast -> seed only

        if (s.autoOpenLive) notifier.autoOpenLive(Config.chzzkLiveUrl())
        if (prefs.notify) {
            val cat = detail.liveCategoryValue?.let { " · $it" }.orEmpty()
            emit(
                prefs,
                AlertPayload(
                    source = SourceKey.CHZZK_LIVE,
                    title = "🔴 은초이 방송 시작!",
                    body = (detail.liveTitle ?: "방송이 시작됐어요") + cat,
                    url = Config.chzzkLiveUrl(),
                    imageUrl = detail.liveImageUrl?.replace("{type}", "720"),
                ),
            )
        }
    }

    // ---- Chzzk community ----
    private suspend fun checkCommunity(s: AppSettings) {
        val prefs = s.prefs(SourceKey.CHZZK_COMMUNITY)
        if (!prefs.notify) return
        val posts = repo.communityPosts(limit = 10)
        if (posts.isEmpty()) return
        val maxId = posts.maxOf { it.id }
        val prev = settings.getSeen(SourceKey.CHZZK_COMMUNITY)?.toLongOrNull()
        settings.setSeen(SourceKey.CHZZK_COMMUNITY, maxId.toString())
        if (prev == null) return
        val fresh = posts.filter { it.id > prev }
        if (fresh.isEmpty()) return
        val newest = fresh.maxByOrNull { it.id }!!
        emit(
            prefs,
            AlertPayload(
                source = SourceKey.CHZZK_COMMUNITY,
                title = "📝 은초이 커뮤니티" + countSuffix(fresh.size),
                body = newest.content.ifBlank { "새 글이 올라왔어요" },
                url = Config.chzzkCommunityUrl(),
                imageUrl = newest.images.firstOrNull(),
            ),
        )
    }

    // ---- Chzzk VOD ----
    private suspend fun checkVod(s: AppSettings) {
        val prefs = s.prefs(SourceKey.CHZZK_VOD)
        if (!prefs.notify) return
        val vods = repo.chzzkVods(size = 20)
        if (vods.isEmpty()) return
        val maxId = vods.maxOf { it.videoNo }
        val prev = settings.getSeen(SourceKey.CHZZK_VOD)?.toLongOrNull()
        settings.setSeen(SourceKey.CHZZK_VOD, maxId.toString())
        if (prev == null) return
        val fresh = vods.filter { it.videoNo > prev }
        if (fresh.isEmpty()) return
        val newest = fresh.maxByOrNull { it.videoNo }!!
        emit(
            prefs,
            AlertPayload(
                source = SourceKey.CHZZK_VOD,
                title = "🎬 새 다시보기" + countSuffix(fresh.size),
                body = newest.title,
                url = Config.chzzkVideoUrl(newest.videoNo),
                imageUrl = newest.thumbnailUrl,
            ),
        )
    }

    // ---- Naver cafe ----
    private suspend fun checkCafe(s: AppSettings) {
        val prefs = s.prefs(SourceKey.CAFE)
        if (!prefs.notify) return
        val posts = repo.cafePosts(maxPages = 2)
        if (posts.isEmpty()) return
        val maxId = posts.maxOf { it.id }
        val prev = settings.getSeen(SourceKey.CAFE)?.toLongOrNull()
        settings.setSeen(SourceKey.CAFE, maxId.toString())
        if (prev == null) return
        val fresh = posts.filter { it.id > prev }
        if (fresh.isEmpty()) return
        val newest = fresh.maxByOrNull { it.id }!!
        emit(
            prefs,
            AlertPayload(
                source = SourceKey.CAFE,
                title = "☕ 카페 새 글" + countSuffix(fresh.size),
                body = newest.subject,
                url = Config.cafeArticleUrl(newest.id),
            ),
        )
    }

    // ---- YouTube (main / vod) ----
    private suspend fun checkYoutube(s: AppSettings, key: SourceKey) {
        val prefs = s.prefs(key)
        if (!prefs.notify) return
        val videos = if (key == SourceKey.YOUTUBE_MAIN) repo.youtubeMain() else repo.youtubeVod()
        if (videos.isEmpty()) return
        // Track recently-seen video ids (RSS is newest-first) instead of a timestamp watermark,
        // so a re-published/late-surfacing video isn't silently missed.
        val seenRaw = settings.getSeen(key)
        val seenIds = seenRaw?.split(",")?.filter { it.isNotBlank() }?.toSet().orEmpty()
        settings.setSeen(key, videos.map { it.id }.take(30).joinToString(","))
        if (seenRaw == null) return // seed
        val fresh = videos.filter { it.id !in seenIds }
        if (fresh.isEmpty()) return
        val newest = fresh.first()
        val label = if (key == SourceKey.YOUTUBE_MAIN) "📺 새 영상 (메인)" else "📼 새 영상 (다시보기)"
        emit(
            prefs,
            AlertPayload(
                source = key,
                title = label + countSuffix(fresh.size),
                body = newest.title,
                url = newest.url,
                imageUrl = newest.thumbnailUrl,
            ),
        )
    }

    // ---- X / Twitter ----
    private suspend fun checkX(s: AppSettings) {
        val prefs = s.prefs(SourceKey.X)
        if (!prefs.notify) return
        val tweets = repo.tweets(s.xBridgeUrl)
        if (tweets.isEmpty()) return
        val maxId = tweets.maxOf { it.id }
        val prev = settings.getSeen(SourceKey.X)?.toLongOrNull()
        settings.setSeen(SourceKey.X, maxId.toString())
        if (prev == null) return
        val fresh = tweets.filter { it.id > prev }
        if (fresh.isEmpty()) return
        val newest = fresh.maxByOrNull { it.id }!!
        emit(
            prefs,
            AlertPayload(
                source = SourceKey.X,
                title = "🐦 새 트윗" + countSuffix(fresh.size),
                body = newest.text,
                url = newest.permalink,
                imageUrl = newest.imageUrls.firstOrNull(),
            ),
        )
    }

    private fun countSuffix(n: Int): String = if (n > 1) " (외 ${n - 1}개)" else ""

    private companion object {
        const val OFFLINE_SENTINEL = "offline"
        val mutex = Mutex()
    }
}
