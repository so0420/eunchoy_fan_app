package com.so0420.eunchoy.data.xml

import android.util.Xml
import com.so0420.eunchoy.data.Config
import com.so0420.eunchoy.data.model.Tweet
import com.so0420.eunchoy.data.model.YoutubeVideo
import com.so0420.eunchoy.util.DateUtil
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLDecoder

private const val NS_YT = "http://www.youtube.com/xml/schemas/2015"
private const val NS_MEDIA = "http://search.yahoo.com/mrss/"

/** Parses a YouTube channel Atom feed (videos.xml) into [YoutubeVideo]s. */
object YoutubeFeed {
    fun parse(xml: String): List<YoutubeVideo> {
        if (!looksLikeXml(xml)) return emptyList()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(xml))
        }
        val out = mutableListOf<YoutubeVideo>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "entry") {
                parseEntry(parser)?.let(out::add)
            }
            event = parser.next()
        }
        return out
    }

    private fun parseEntry(parser: XmlPullParser): YoutubeVideo? {
        var videoId = ""
        var title = ""
        var watchUrl = ""
        var published: String? = null
        var thumb = ""
        var views = 0L
        var likes = 0
        while (!(parser.eventType == XmlPullParser.END_TAG && parser.name == "entry")) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                val ns = parser.namespace
                when {
                    parser.name == "videoId" && ns == NS_YT -> videoId = parser.nextText().trim()
                    parser.name == "title" && ns != NS_MEDIA -> title = parser.nextText().trim()
                    parser.name == "link" -> {
                        if (parser.getAttributeValue(null, "rel") == "alternate") {
                            watchUrl = parser.getAttributeValue(null, "href").orEmpty()
                        }
                    }
                    parser.name == "published" -> published = parser.nextText().trim()
                    parser.name == "thumbnail" && ns == NS_MEDIA ->
                        thumb = parser.getAttributeValue(null, "url").orEmpty()
                    parser.name == "statistics" && ns == NS_MEDIA ->
                        views = parser.getAttributeValue(null, "views")?.toLongOrNull() ?: 0L
                    parser.name == "starRating" && ns == NS_MEDIA ->
                        likes = parser.getAttributeValue(null, "count")?.toIntOrNull() ?: 0
                }
            }
            parser.next()
        }
        if (videoId.isBlank()) return null
        val isShort = watchUrl.contains("/shorts/")
        if (thumb.isBlank()) thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        return YoutubeVideo(
            id = videoId,
            title = title,
            url = watchUrl.ifBlank { Config.youtubeWatchUrl(videoId) },
            isShort = isShort,
            publishedAt = DateUtil.parseIso(published),
            thumbnailUrl = thumb,
            views = views,
            likes = likes,
        )
    }
}

/** Parses a Nitter RSS feed into [Tweet]s. Returns empty list if the body is a bot-challenge page. */
object TwitterRss {
    fun parse(xml: String): List<Tweet> {
        if (!looksLikeXml(xml)) return emptyList()
        if (xml.contains("not a bot", ignoreCase = true) ||
            xml.contains("not yet whitelisted", ignoreCase = true)
        ) return emptyList()

        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
        val out = mutableListOf<Tweet>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                parseItem(parser)?.let(out::add)
            }
            event = parser.next()
        }
        return out
    }

    private fun parseItem(parser: XmlPullParser): Tweet? {
        var title = ""
        var guid = ""
        var pubDate: String? = null
        var link = ""
        var description = ""
        while (!(parser.eventType == XmlPullParser.END_TAG && parser.name == "item")) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> title = parser.nextText().trim()
                    "guid" -> guid = parser.nextText().trim()
                    "pubDate" -> pubDate = parser.nextText().trim()
                    "link" -> link = parser.nextText().trim()
                    "description" -> description = parser.nextText()
                }
            }
            parser.next()
        }
        // guid is the numeric snowflake id (may have a trailing #m or be a permalink).
        val id = Regex("(\\d{6,})").find(guid)?.value?.toLongOrNull()
            ?: Regex("status/(\\d+)").find(link)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: return null
        return Tweet(
            id = id,
            text = title,
            publishedAt = DateUtil.parseRfc1123(pubDate),
            permalink = Config.tweetUrl(id),
            imageUrls = extractImages(description),
        )
    }

    private val IMG = Regex("<img[^>]+src=\"([^\"]+)\"")

    private fun extractImages(html: String): List<String> =
        IMG.findAll(html).map { it.groupValues[1] }.map(::deNitterize).distinct().toList()

    /**
     * Nitter proxies images as /pic/<urlencoded original>. Decode back to the twimg CDN url.
     * Two shapes: avatars embed the host (pbs.twimg.com%2F...) while tweet media is host-less
     * (media%2F...jpg) and must be prefixed with the pbs.twimg.com CDN.
     */
    private fun deNitterize(url: String): String {
        val idx = url.indexOf("/pic/")
        if (idx < 0) return url
        val encoded = url.substring(idx + 5)
        return runCatching {
            val decoded = URLDecoder.decode(encoded, "UTF-8").trimStart('/')
            when {
                decoded.startsWith("http") -> decoded
                decoded.startsWith("pbs.twimg.com") ||
                    decoded.startsWith("video.twimg.com") ||
                    decoded.startsWith("abs.twimg.com") -> "https://$decoded"
                else -> "https://pbs.twimg.com/$decoded" // host-less media path
            }
        }.getOrDefault(url)
    }
}

private fun looksLikeXml(body: String): Boolean {
    val head = body.trimStart().take(64).lowercase()
    return head.startsWith("<?xml") || head.startsWith("<rss") || head.startsWith("<feed")
}
