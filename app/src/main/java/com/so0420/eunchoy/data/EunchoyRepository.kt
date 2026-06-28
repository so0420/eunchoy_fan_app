package com.so0420.eunchoy.data

import com.so0420.eunchoy.data.model.CafeArticleContent
import com.so0420.eunchoy.data.model.CafeComment
import com.so0420.eunchoy.data.model.CafePost
import com.so0420.eunchoy.data.model.ChzzkVod
import com.so0420.eunchoy.data.model.CommunityPost
import com.so0420.eunchoy.data.model.LiveStatus
import com.so0420.eunchoy.data.model.Tweet
import com.so0420.eunchoy.data.model.YoutubeVideo
import com.so0420.eunchoy.data.net.LiveDetail
import com.so0420.eunchoy.data.net.LivePlayback
import com.so0420.eunchoy.data.net.Net
import com.so0420.eunchoy.data.xml.TwitterRss
import com.so0420.eunchoy.data.xml.YoutubeFeed
import com.so0420.eunchoy.util.DateUtil

/** Single source of truth for all of 은초이's platform data. Stateless; one shared instance. */
class EunchoyRepository {

    private val chzzk get() = Net.chzzk
    private val naver get() = Net.naver

    // ---- Chzzk live ----

    suspend fun liveStatus(): LiveStatus {
        val detail = chzzk.liveDetail(Config.CHZZK_CHANNEL_ID).content
        val profile = runCatching { chzzk.channel(Config.CHZZK_CHANNEL_ID).content }.getOrNull()
        return mapLive(detail, profile?.followerCount, profile?.channelImageUrl)
    }

    /** Lightweight live-detail fetch for the background poller. */
    suspend fun liveDetail(): LiveDetail? = chzzk.liveDetail(Config.CHZZK_CHANNEL_ID).content

    /** Fresh live-detail -> playable HLS master URL, or null if offline / adult-gated. */
    suspend fun resolveLiveHls(preferLowLatency: Boolean = true): String? {
        val content = chzzk.liveDetail(Config.CHZZK_CHANNEL_ID).content ?: return null
        if (content.status != "OPEN") return null
        return extractHls(content.livePlaybackJson, preferLowLatency)
    }

    private fun mapLive(c: LiveDetail?, followers: Int?, profileImg: String?): LiveStatus {
        if (c == null) return LiveStatus.UNKNOWN
        val thumb = c.liveImageUrl?.replace("{type}", "720")
            ?: c.defaultThumbnailImageUrl
            ?: c.channel?.channelImageUrl
        return LiveStatus(
            isLive = c.isLive,
            title = c.liveTitle,
            category = c.liveCategoryValue ?: c.liveCategory,
            viewerCount = if (c.isLive) c.concurrentUserCount else null,
            thumbnailUrl = if (c.isLive) thumb else (profileImg ?: c.channel?.channelImageUrl),
            openDate = DateUtil.parseKstSlash(c.openDate),
            channelName = c.channel?.channelName ?: "은초이 Choy",
            channelImageUrl = c.channel?.channelImageUrl ?: profileImg,
            followerCount = followers,
            adult = c.adult,
            tags = c.tags,
        )
    }

    // ---- Chzzk community ----

    suspend fun communityPosts(limit: Int = 10): List<CommunityPost> {
        val block = naver.communityComments(Config.CHZZK_CHANNEL_ID, limit = limit).content?.comments
            ?: return emptyList()
        return block.data
            .filterNot { it.comment.deleted || it.comment.secret }
            .map { item ->
                val images = item.comment.attaches.orEmpty()
                    .filter { it.attachType == "PHOTO" }
                    .sortedBy { it.order }
                    .map { it.attachValue }
                CommunityPost(
                    id = item.comment.commentId,
                    content = item.comment.content,
                    images = images,
                    createdAt = DateUtil.parseKstCompact(item.comment.createdDate),
                    likeCount = item.buffNerf?.buffCount ?: 0,
                    commentCount = item.comment.childObjectCount,
                    authorName = item.user?.userNickname,
                    authorImage = item.user?.profileImageUrl,
                )
            }
    }

    // ---- Chzzk VOD ----

    suspend fun chzzkVods(size: Int = 20): List<ChzzkVod> {
        val data = chzzk.videos(Config.CHZZK_CHANNEL_ID, size = size).content?.data ?: return emptyList()
        return data.map { v ->
            ChzzkVod(
                videoNo = v.videoNo,
                videoId = v.videoId,
                title = v.videoTitle,
                type = v.videoType,
                thumbnailUrl = v.thumbnailImageUrl?.replace("{type}", "640"),
                durationSec = v.duration,
                publishedAt = DateUtil.fromEpochMillis(v.publishDateAt)
                    ?: DateUtil.parseKstSlash(v.publishDate),
                readCount = v.readCount,
                category = v.videoCategoryValue,
                isPlayable = v.isPlayable,
            )
        }
    }

    /** Resolve a REPLAY VOD's HLS url; null for UPLOAD (use browser) or unavailable. */
    suspend fun resolveVodHls(videoNo: Long): String? {
        val d = chzzk.videoDetail(videoNo).content ?: return null
        if (d.blindType != null || d.adult || d.paidProductId != null) return null
        if (d.videoType != "REPLAY") return null
        return extractHls(d.liveRewindPlaybackJson, preferLowLatency = false)
    }

    private fun extractHls(playbackJson: String?, preferLowLatency: Boolean): String? {
        val raw = playbackJson ?: return null
        val playback = runCatching { Net.json.decodeFromString<LivePlayback>(raw) }.getOrNull() ?: return null
        if (playback.live?.status == "ENDED") return null
        val media = playback.media.filter { it.protocol == "HLS" && it.path.isNotBlank() }
        if (media.isEmpty()) return null
        val wanted = if (preferLowLatency) "LLHLS" else "HLS"
        return media.firstOrNull { it.mediaId == wanted }?.path ?: media.first().path
    }

    // ---- Naver Cafe ----

    suspend fun cafePosts(maxPages: Int = 2): List<CafePost> {
        val rows = mutableListOf<com.so0420.eunchoy.data.net.CafeArticleRow>()
        var page = 1
        while (page <= maxPages) {
            val res = naver.cafeArticleList(Config.CAFE_ID, page = page).message.result ?: break
            rows += res.articleList.filter { it.memberKey == Config.CAFE_MEMBER_KEY }
            if (!res.hasNext) break
            page++
        }
        return rows.map { r ->
            CafePost(
                id = r.articleId,
                subject = r.subject,
                createdAt = DateUtil.fromEpochMillis(r.writeDateTimestamp),
                readCount = r.readCount,
                commentCount = r.commentCount,
                likeCount = r.likeItCount,
                isOpen = r.openArticle,
                menuName = r.menuName,
                hasImage = r.attachImage,
            )
        }
    }

    suspend fun cafeArticle(articleId: Long): CafeArticleContent {
        val result = naver.cafeArticle(Config.CAFE_ID, articleId).result
        val err = result.errorCode
        if (err != null) {
            return CafeArticleContent(
                id = articleId, subject = "", contentHtml = null,
                authorNick = null, authorImage = null, createdAt = null,
                needsLogin = err == "0004", notFound = err == "4003",
            )
        }
        val a = result.article
        // All comments (the base call only bundles the first page); fall back to that page on failure.
        val rawComments = runCatching {
            naver.cafeArticleComments(Config.CAFE_ID, articleId).result.comments?.items
        }.getOrNull() ?: result.comments?.items.orEmpty()
        val comments = rawComments.map { c ->
            CafeComment(
                id = c.id,
                isReply = c.refId != 0L && c.refId != c.id,
                authorNick = c.writer?.nick,
                authorImage = c.writer?.image?.url,
                content = c.content,
                createdAt = DateUtil.fromEpochMillis(c.updateDate),
                isArticleWriter = c.isArticleWriter,
                isDeleted = c.isDeleted,
                // Naver sticker CDN 404s without a resize param; ?type=p100_100 returns the image.
                stickerUrl = c.sticker?.url?.takeIf { it.isNotBlank() }?.let { "$it?type=p100_100" },
            )
        }
        return CafeArticleContent(
            id = articleId,
            subject = a?.subject.orEmpty(),
            contentHtml = a?.contentHtml,
            authorNick = a?.writer?.nick,
            authorImage = a?.writer?.image?.url,
            createdAt = DateUtil.fromEpochMillis(a?.writeDate),
            needsLogin = false,
            notFound = false,
            comments = comments,
            commentCount = a?.commentCount ?: comments.size,
        )
    }

    // ---- YouTube ----

    suspend fun youtubeMain(): List<YoutubeVideo> =
        YoutubeFeed.parse(Net.getText(Config.youtubeRssUrl(Config.YT_MAIN_CHANNEL_ID)))

    suspend fun youtubeVod(): List<YoutubeVideo> =
        YoutubeFeed.parse(Net.getText(Config.youtubeRssUrl(Config.YT_VOD_CHANNEL_ID)))

    // ---- X / Twitter ----

    suspend fun tweets(bridgeUrl: String = Config.DEFAULT_X_BRIDGE): List<Tweet> =
        TwitterRss.parse(Net.getText(bridgeUrl))
}
