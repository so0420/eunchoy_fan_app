package com.so0420.eunchoy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.so0420.eunchoy.data.model.CafePost
import com.so0420.eunchoy.data.model.ChzzkVod
import com.so0420.eunchoy.data.model.CommunityPost
import com.so0420.eunchoy.data.model.Tweet
import com.so0420.eunchoy.data.model.YoutubeVideo
import com.so0420.eunchoy.ui.theme.SkySurface
import com.so0420.eunchoy.ui.theme.SkySurfaceVariant
import com.so0420.eunchoy.util.DateUtil

private val cardShape = RoundedCornerShape(20.dp)

@Composable
private fun BaseCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = SkySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier,
    ) { content() }
}

@Composable
fun CommunityCard(post: CommunityPost, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BaseCard(onClick, modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(post.authorImage, 32)
                Spacer(Modifier.width(8.dp))
                Text(
                    post.authorName ?: "은초이 Choy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    DateUtil.relative(post.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (post.content.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    post.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (post.images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ImageStrip(post.images)
            }
            Spacer(Modifier.height(10.dp))
            Row {
                StatLabel(Icons.Outlined.FavoriteBorder, post.likeCount)
                Spacer(Modifier.width(14.dp))
                StatLabel(Icons.Outlined.ChatBubbleOutline, post.commentCount)
            }
        }
    }
}

@Composable
private fun ImageStrip(images: List<String>) {
    if (images.size == 1) {
        AsyncImage(
            model = images[0],
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SkySurfaceVariant),
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            images.take(3).forEach { url ->
                Box(Modifier.weight(1f)) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SkySurfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
fun VodCard(vod: ChzzkVod, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BaseCard(onClick, modifier.width(240.dp)) {
        Column {
            Thumbnail(vod.thumbnailUrl, DateUtil.duration(vod.durationSec))
            Column(Modifier.padding(12.dp)) {
                Text(
                    vod.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${DateUtil.relative(vod.publishedAt)} · 조회 ${formatCount(vod.readCount)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun YoutubeCard(video: YoutubeVideo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BaseCard(onClick, modifier.fillMaxWidth()) {
        Column {
            Thumbnail(video.thumbnailUrl, badge = if (video.isShort) "Shorts" else null)
            Column(Modifier.padding(14.dp)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${DateUtil.relative(video.publishedAt)} · 조회 ${formatCount(video.views)} · ♥ ${formatCount(video.likes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CafeCard(post: CafePost, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BaseCard(onClick, modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                post.menuName?.let { Pill(it); Spacer(Modifier.width(8.dp)) }
                if (!post.isOpen) {
                    Icon(
                        Icons.Outlined.Lock, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    DateUtil.relative(post.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                post.subject,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                StatLabel(Icons.Outlined.Visibility, post.readCount)
                Spacer(Modifier.width(14.dp))
                StatLabel(Icons.Outlined.ChatBubbleOutline, post.commentCount)
                Spacer(Modifier.width(14.dp))
                StatLabel(Icons.Outlined.FavoriteBorder, post.likeCount)
            }
        }
    }
}

@Composable
fun TweetCard(tweet: Tweet, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BaseCard(onClick, modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🐦 @Eun_choy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    DateUtil.relative(tweet.publishedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tweet.text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(tweet.text, style = MaterialTheme.typography.bodyMedium)
            }
            if (tweet.imageUrls.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ImageStrip(tweet.imageUrls)
            }
        }
    }
}

@Composable
private fun Thumbnail(url: String?, durationBadge: String? = null, badge: String? = null) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(SkySurfaceVariant),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Icon(
            Icons.Filled.PlayCircleFilled,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.Center).size(46.dp),
        )
        val label = durationBadge?.takeIf { it.isNotBlank() } ?: badge
        if (label != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
