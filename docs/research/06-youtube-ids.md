# YouTube Channel IDs & RSS Feed Reference

**Streamer:** 은초이 (Eunchoy)  
**Verified:** 2026-06-28  
**Method:** Live curl of channel pages; canonical `<link rel="canonical">` tag + `externalId` JSON field extracted; RSS feeds confirmed HTTP 200.

---

## Resolved Channel IDs

| Role | Handle (URL-decoded) | Display Name | Channel ID |
|------|----------------------|--------------|------------|
| Main (covers/clips) | `@은초이Eunchoy` | 은초이 choy | **`UCLj64SVR6s5cu0IEozBEphg`** |
| VOD / Replay | `@금초이` | 금초이 | **`UCchU7_y7AMPj03CgBjtsRDA`** |

### Source evidence

**Main channel (`@은초이Eunchoy`)**
```
<link rel="canonical" href="https://www.youtube.com/channel/UCLj64SVR6s5cu0IEozBEphg">
<link rel="alternate" type="application/rss+xml" href="https://www.youtube.com/feeds/videos.xml?channel_id=UCLj64SVR6s5cu0IEozBEphg">
```
JSON in page data: `"externalId":"UCLj64SVR6s5cu0IEozBEphg"`

**VOD channel (`@금초이`)**
```
<link rel="canonical" href="https://www.youtube.com/channel/UCchU7_y7AMPj03CgBjtsRDA">
<link rel="alternate" type="application/rss+xml" href="https://www.youtube.com/feeds/videos.xml?channel_id=UCchU7_y7AMPj03CgBjtsRDA">
```
JSON in page data: `"externalId":"UCchU7_y7AMPj03CgBjtsRDA"`

---

## RSS Feed Endpoints

Both feeds return HTTP 200 with `Content-Type: text/xml; charset=UTF-8`. No API key required.

| Channel | RSS URL |
|---------|---------|
| Main (은초이 choy) | `https://www.youtube.com/feeds/videos.xml?channel_id=UCLj64SVR6s5cu0IEozBEphg` |
| VOD (금초이) | `https://www.youtube.com/feeds/videos.xml?channel_id=UCchU7_y7AMPj03CgBjtsRDA` |

### Polling recommendation
- Poll interval: every 5–15 minutes (YouTube does not publish webhook/PubSubHubbub reliably for small channels)
- No `If-Modified-Since` caching header support confirmed; use ETag or compare `<updated>` on entries

---

## RSS Feed Structure (Atom + YouTube + mrss namespaces)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns:yt="http://www.youtube.com/xml/schemas/2015"
      xmlns:media="http://search.yahoo.com/mrss/"
      xmlns="http://www.w3.org/2005/Atom">

  <!-- Feed-level metadata -->
  <link rel="self" href="http://www.youtube.com/feeds/videos.xml?channel_id=UCLj64SVR6s5cu0IEozBEphg"/>
  <id>yt:channel:Lj64SVR6s5cu0IEozBEphg</id>           <!-- NOTE: strips "UC" prefix here -->
  <yt:channelId>Lj64SVR6s5cu0IEozBEphg</yt:channelId>  <!-- also strips "UC" at feed level -->
  <title>은초이 choy</title>
  <link rel="alternate" href="https://www.youtube.com/channel/UCLj64SVR6s5cu0IEozBEphg"/>
  <published>2025-05-05T06:32:13+00:00</published>      <!-- channel creation date -->

  <entry>
    <!-- Identity -->
    <id>yt:video:kUohix_53Xw</id>
    <yt:videoId>kUohix_53Xw</yt:videoId>
    <yt:channelId>UCLj64SVR6s5cu0IEozBEphg</yt:channelId>  <!-- full UC... id in entries -->

    <!-- Basic metadata -->
    <title>팬서비스 (ファンサ) ／ 은초이 COVER</title>
    <link rel="alternate" href="https://www.youtube.com/watch?v=kUohix_53Xw"/>
    <!-- Shorts use /shorts/ path: href="https://www.youtube.com/shorts/1cZuzHWUA18" -->

    <!-- Timestamps (ISO 8601, UTC offset included) -->
    <published>2026-05-15T08:19:27+00:00</published>   <!-- upload date -->
    <updated>2026-06-11T17:48:56+00:00</updated>        <!-- last metadata edit -->

    <!-- Rich media block -->
    <media:group>
      <media:title>팬서비스 (ファンサ) ／ 은초이 COVER</media:title>

      <!-- Legacy Flash embed; url contains videoId -->
      <media:content url="https://www.youtube.com/v/kUohix_53Xw?version=3"
                     type="application/x-shockwave-flash"
                     width="640" height="390"/>

      <!-- Thumbnail: i1/i2/i3/i4 subdomain varies; always hqdefault.jpg for this feed -->
      <media:thumbnail url="https://i4.ytimg.com/vi/kUohix_53Xw/hqdefault.jpg"
                       width="480" height="360"/>

      <!-- Full description text (HTML entities encoded, &#13; for CR) -->
      <media:description>vocal: 은초이 choy
mix&amp;mastering&amp;chorus: woomawang
...
✦ Chzzk : https://chzzk.naver.com/fd8516eb8d31a8a5147e94c281ae3f07</media:description>

      <!-- Engagement (count = like count for YouTube RSS) -->
      <media:community>
        <media:starRating count="33" average="5.00" min="1" max="5"/>
        <media:statistics views="426"/>
      </media:community>
    </media:group>
  </entry>

</feed>
```

### Key field paths (XPath / Retrofit XML notes)

| Field | XPath | Example value | Notes |
|-------|-------|---------------|-------|
| Video ID | `//entry/yt:videoId` | `kUohix_53Xw` | Use to build watch URL |
| Channel ID (in entry) | `//entry/yt:channelId` | `UCLj64SVR6s5cu0IEozBEphg` | Full UC... present in entries |
| Title | `//entry/title` | `팬서비스 (ファンサ) ／ 은초이 COVER` | |
| Watch URL | `//entry/link[@rel='alternate']/@href` | `https://www.youtube.com/watch?v=kUohix_53Xw` | Shorts: `.../shorts/{id}` |
| Published | `//entry/published` | `2026-05-15T08:19:27+00:00` | Parse with `OffsetDateTime` |
| Updated | `//entry/updated` | `2026-06-11T17:48:56+00:00` | Use to detect metadata edits |
| Thumbnail URL | `//entry/media:group/media:thumbnail/@url` | `https://i4.ytimg.com/vi/kUohix_53Xw/hqdefault.jpg` | 480x360 JPEG |
| Description | `//entry/media:group/media:description` | (text) | Unescape `&amp;`, `&#13;` |
| Views | `//entry/media:group/media:community/media:statistics/@views` | `426` | String, parse to Long |
| Likes | `//entry/media:group/media:community/media:starRating/@count` | `33` | String, parse to Int |

### Thumbnail URL alternatives (not in feed, but derivable)

```
https://i.ytimg.com/vi/{videoId}/default.jpg      // 120x90
https://i.ytimg.com/vi/{videoId}/mqdefault.jpg    // 320x180
https://i.ytimg.com/vi/{videoId}/hqdefault.jpg    // 480x360  ← what the feed provides
https://i.ytimg.com/vi/{videoId}/sddefault.jpg    // 640x480
https://i.ytimg.com/vi/{videoId}/maxresdefault.jpg // 1280x720 (may 404 for older videos)
```

---

## Kotlin / Retrofit Implementation Notes

### Dependency
```kotlin
// build.gradle.kts
implementation("com.squareup.retrofit2:converter-simplexml:2.9.0")
// OR use kotlinx-serialization with XML plugin (nlpie/kotlin-xml-serialization)
// OR use Android's built-in XmlPullParser (no extra dependency)
```

### Recommended: XmlPullParser (no extra deps, suitable for simple feed parsing)

```kotlin
data class YtVideoEntry(
    val videoId: String,
    val channelId: String,
    val title: String,
    val watchUrl: String,         // watch?v= or shorts/
    val published: OffsetDateTime,
    val updated: OffsetDateTime,
    val thumbnailUrl: String,     // hqdefault.jpg 480x360
    val description: String,
    val views: Long,
    val likes: Int
)

// Feed URLs
const val MAIN_CHANNEL_ID  = "UCLj64SVR6s5cu0IEozBEphg"
const val VOD_CHANNEL_ID   = "UCchU7_y7AMPj03CgBjtsRDA"

fun rssFeedUrl(channelId: String) =
    "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
```

### Retrofit service (SimpleXML or raw ResponseBody)

```kotlin
interface YouTubeRssService {
    @GET("feeds/videos.xml")
    suspend fun getVideos(
        @Query("channel_id") channelId: String
    ): Response<ResponseBody>  // parse body manually with XmlPullParser
}

val retrofit = Retrofit.Builder()
    .baseUrl("https://www.youtube.com/")
    .addConverterFactory(ScalarsConverterFactory.create())
    .build()
```

### Namespace-aware parsing tip

The feed uses three XML namespaces. When using `XmlPullParser`, check both local name and namespace URI:

```kotlin
// yt namespace: "http://www.youtube.com/xml/schemas/2015"
// media namespace: "http://search.yahoo.com/mrss/"
// atom default: "http://www.w3.org/2005/Atom"

if (parser.name == "videoId" && parser.namespace == "http://www.youtube.com/xml/schemas/2015") {
    videoId = parser.nextText()
}
if (parser.name == "thumbnail" && parser.namespace == "http://search.yahoo.com/mrss/") {
    thumbnailUrl = parser.getAttributeValue(null, "url")
}
```

### Detecting new uploads

Compare the `published` timestamp of the first entry against the last known timestamp stored in SharedPreferences/Room. The feed returns entries in reverse-chronological order (newest first), so only the first entry needs to be checked each poll.

```kotlin
val latestPublished = entries.maxByOrNull { it.published }?.published
if (latestPublished != null && latestPublished > lastKnownPublished) {
    // new video uploaded — trigger notification
}
```

---

## Channel Metadata Summary

| Field | Main channel | VOD channel |
|-------|-------------|-------------|
| Display name | 은초이 choy | 금초이 |
| Handle | `@은초이Eunchoy` | `@금초이` |
| Channel ID | `UCLj64SVR6s5cu0IEozBEphg` | `UCchU7_y7AMPj03CgBjtsRDA` |
| Channel URL | `https://www.youtube.com/channel/UCLj64SVR6s5cu0IEozBEphg` | `https://www.youtube.com/channel/UCchU7_y7AMPj03CgBjtsRDA` |
| RSS feed URL | `https://www.youtube.com/feeds/videos.xml?channel_id=UCLj64SVR6s5cu0IEozBEphg` | `https://www.youtube.com/feeds/videos.xml?channel_id=UCchU7_y7AMPj03CgBjtsRDA` |
| Created | 2025-05-05 | 2025-05-22 |
| Description | 치지직에서 노래/게임 방송하는 은초이입니다 | 은초이 다시보기 채널입니다 (replay VOD archive) |
| Content type | Cover songs, clips, Shorts | Full-length stream VODs |

---

## Extraction Method (for future re-verification)

To re-extract channel IDs from a handle page without the YouTube Data API:

```bash
# Option 1: canonical link tag
curl -s -L "https://www.youtube.com/@HANDLE" \
  -H 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36' \
  | grep -o 'canonical.*channel/UC[^"]*"'

# Option 2: externalId JSON field in ytInitialData
curl -s -L "https://www.youtube.com/@HANDLE" \
  -H 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36' \
  | grep -o 'externalId":"UC[^"]*"'

# Option 3: RSS link tag in page head
curl -s -L "https://www.youtube.com/@HANDLE" \
  -H 'User-Agent: Mozilla/5.0 ...' \
  | grep -o 'feeds/videos.xml?channel_id=UC[^"]*"'
```

All three methods yield the same ID. `externalId` and the canonical `<link>` are the most reliable.

---

## Notes & Caveats

- **No API key needed** for RSS feeds. The Data API v3 is not required for this use case.
- **Feed lag:** YouTube RSS feeds may lag 5–30 minutes behind actual upload time.
- **Max entries per feed:** 15 entries (hard limit, not configurable).
- **Shorts appear in the feed** with a `/shorts/` link path rather than `/watch?v=`. Parse the `<link href>` attribute carefully if you need to distinguish Shorts from regular videos.
- **`<yt:channelId>` in feed header** omits the `UC` prefix (e.g., `Lj64SVR6s5cu0IEozBEphg`). This is a known YouTube quirk. The full `UC...` ID is present inside each `<entry>`.
- **Thumbnail subdomain** (`i1`, `i2`, `i4`) varies and is not meaningful. You can safely replace with `i.ytimg.com` (no number) for all thumbnails.
- **`media:starRating/@count`** maps to the like count (not a 1–5 star rating), since YouTube removed public dislike counts. The `average` is always `5.00`.
