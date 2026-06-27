package com.so0420.eunchoy.data.net

import kotlinx.serialization.Serializable

/** Common Chzzk/Naver response envelope: { code, message, content }. */
@Serializable
data class Envelope<T>(
    val code: Int = 0,
    val message: String? = null,
    val content: T? = null,
)

// ---- Live detail (service/v3/channels/{id}/live-detail) ----

@Serializable
data class LiveDetail(
    val status: String? = null,            // "OPEN" / "CLOSE"
    val liveId: Long? = null,
    val liveTitle: String? = null,
    val liveImageUrl: String? = null,      // contains literal "{type}"; null when offline
    val defaultThumbnailImageUrl: String? = null,
    val concurrentUserCount: Int? = null,
    val accumulateCount: Int? = null,
    val openDate: String? = null,          // "yyyy-MM-dd HH:mm:ss" KST
    val closeDate: String? = null,
    val categoryType: String? = null,
    val liveCategory: String? = null,
    val liveCategoryValue: String? = null,
    val tags: List<String> = emptyList(),
    val adult: Boolean = false,
    val livePlaybackJson: String? = null,  // stringified JSON (parse again for HLS)
    val channel: ChannelSummary? = null,
) {
    val isLive: Boolean get() = status == "OPEN"
}

@Serializable
data class ChannelSummary(
    val channelId: String? = null,
    val channelName: String? = null,
    val channelImageUrl: String? = null,
    val verifiedMark: Boolean = false,
)

// ---- Channel profile (service/v1/channels/{id}) ----

@Serializable
data class ChannelProfile(
    val channelId: String? = null,         // null => channel doesn't exist
    val channelName: String? = null,
    val channelImageUrl: String? = null,
    val channelDescription: String? = null,
    val followerCount: Int = 0,
    val openLive: Boolean = false,
    val verifiedMark: Boolean = false,
)

// ---- livePlaybackJson / liveRewindPlaybackJson inner payload (parsed from the string) ----

@Serializable
data class LivePlayback(
    val media: List<PlaybackMedia> = emptyList(),
    val live: LiveBlock? = null,
)

@Serializable
data class LiveBlock(val status: String? = null) // "STARTED" / "ENDED"

@Serializable
data class PlaybackMedia(
    val mediaId: String = "",   // "HLS" | "LLHLS"
    val protocol: String = "",  // "HLS"
    val path: String = "",      // master .m3u8 URL
)

// ---- Community feed (CHANNEL_POST comments) ----

@Serializable
data class CommentContent(
    val comments: CommentsBlock = CommentsBlock(),
    val commentActive: Boolean = true,
)

@Serializable
data class CommentsBlock(
    val data: List<FeedItem> = emptyList(),
    val totalCount: Int = 0,
)

@Serializable
data class FeedItem(
    val comment: Comment = Comment(),
    val user: CommentUser? = null,
    val buffNerf: BuffNerf? = null,
)

@Serializable
data class Comment(
    val commentId: Long = 0,
    val content: String = "",
    val createdDate: String = "",          // "yyyyMMddHHmmss" KST
    val attaches: List<Attach>? = null,    // nullable for text-only posts
    val childObjectCount: Int = 0,         // comment count
    val deleted: Boolean = false,
    val hideByCleanBot: Boolean = false,
    val secret: Boolean = false,
)

@Serializable
data class Attach(
    val attachType: String = "",           // "PHOTO"
    val attachValue: String = "",          // image URL
    val extraJson: String? = null,
    val order: Int = 0,
)

@Serializable
data class CommentUser(
    val userNickname: String = "",
    val profileImageUrl: String? = null,
    val userRoleCode: String? = null,      // "streamer", ...
)

@Serializable
data class BuffNerf(val buffCount: Int = 0, val nerfCount: Int = 0) // buffCount == likes

// ---- VOD list (service/v1/channels/{id}/videos) ----

@Serializable
data class VideoListContent(
    val page: Int = 0,
    val size: Int = 0,
    val totalCount: Int = 0,
    val totalPages: Int = 0,
    val data: List<VideoItem> = emptyList(),
)

@Serializable
data class VideoItem(
    val videoNo: Long = 0,
    val videoId: String? = null,
    val videoTitle: String = "",
    val videoType: String = "",            // "REPLAY" / "UPLOAD"
    val publishDate: String? = null,       // "yyyy-MM-dd HH:mm:ss" KST
    val publishDateAt: Long? = null,       // epoch ms
    val thumbnailImageUrl: String? = null,
    val trailerUrl: String? = null,
    val duration: Int = 0,                 // seconds
    val readCount: Int = 0,
    val videoCategoryValue: String? = null,
    val adult: Boolean = false,
    val blindType: String? = null,
    val paidProductId: String? = null,
    val tvAppViewingPolicyType: String? = null,
) {
    val isPlayable: Boolean
        get() = (tvAppViewingPolicyType == null || tvAppViewingPolicyType == "ALLOWED") &&
            blindType == null && paidProductId == null && !adult
}

// ---- VOD detail (service/v2/videos/{videoNo}) ----

@Serializable
data class VideoDetail(
    val videoNo: Long = 0,
    val videoId: String? = null,
    val videoTitle: String = "",
    val videoType: String = "",
    val inKey: String? = null,             // non-null for UPLOAD
    val vodStatus: String? = null,
    val liveRewindPlaybackJson: String? = null, // non-null for REPLAY
    val adult: Boolean = false,
    val blindType: String? = null,
    val paidProductId: String? = null,
    val tvAppViewingPolicyType: String? = null,
)
