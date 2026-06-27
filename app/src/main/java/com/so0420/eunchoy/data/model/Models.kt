package com.so0420.eunchoy.data.model

import java.time.Instant

/** Resolved Chzzk live status for the hero card. */
data class LiveStatus(
    val isLive: Boolean,
    val title: String?,
    val category: String?,
    val viewerCount: Int?,
    val thumbnailUrl: String?,
    val openDate: Instant?,
    val channelName: String?,
    val channelImageUrl: String?,
    val followerCount: Int?,
    val adult: Boolean,
    val tags: List<String> = emptyList(),
) {
    companion object {
        val UNKNOWN = LiveStatus(
            isLive = false, title = null, category = null, viewerCount = null,
            thumbnailUrl = null, openDate = null, channelName = "은초이 Choy",
            channelImageUrl = null, followerCount = null, adult = false,
        )
    }
}

data class CommunityPost(
    val id: Long,
    val content: String,
    val images: List<String>,
    val createdAt: Instant?,
    val likeCount: Int,
    val commentCount: Int,
    val authorName: String?,
    val authorImage: String?,
)

data class CafePost(
    val id: Long,
    val subject: String,
    val createdAt: Instant?,
    val readCount: Int,
    val commentCount: Int,
    val likeCount: Int,
    val isOpen: Boolean,
    val menuName: String?,
    val hasImage: Boolean,
)

/** Fully resolved cafe article body for the detail view. */
data class CafeArticleContent(
    val id: Long,
    val subject: String,
    val contentHtml: String?,
    val authorNick: String?,
    val authorImage: String?,
    val createdAt: Instant?,
    val needsLogin: Boolean,
    val notFound: Boolean,
)

data class ChzzkVod(
    val videoNo: Long,
    val videoId: String?,
    val title: String,
    val type: String,        // "REPLAY" / "UPLOAD"
    val thumbnailUrl: String?,
    val durationSec: Int,
    val publishedAt: Instant?,
    val readCount: Int,
    val category: String?,
    val isPlayable: Boolean,
)

data class YoutubeVideo(
    val id: String,
    val title: String,
    val url: String,
    val isShort: Boolean,
    val publishedAt: Instant?,
    val thumbnailUrl: String,
    val views: Long,
    val likes: Int,
)

data class Tweet(
    val id: Long,
    val text: String,
    val publishedAt: Instant?,
    val permalink: String,
    val imageUrls: List<String>,
)
