# Chzzk VOD / Replay API — Implementation Reference

> All data verified via live curl calls on 2026-06-28 against channel `fd8516eb8d31a8a5147e94c281ae3f07` (은초이 Choy).

---

## 1. VOD List Endpoint

### Request

```
GET https://api.chzzk.naver.com/service/v1/channels/{channelId}/videos
```

| Parameter    | Type   | Required | Notes                                       |
|--------------|--------|----------|---------------------------------------------|
| `sortType`   | string | yes      | `LATEST` or `POPULAR`                       |
| `pagingType` | string | yes      | Always `PAGE`                               |
| `page`       | int    | yes      | 0-based page index                          |
| `size`       | int    | yes      | Items per page (max observed: 20)           |
| `videoType`  | string | no       | Filter: `REPLAY` or `UPLOAD` (omit for all) |

Required headers:
```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
```

No authentication cookie is required for public channels.

**Example URL:**
```
https://api.chzzk.naver.com/service/v1/channels/fd8516eb8d31a8a5147e94c281ae3f07/videos?sortType=LATEST&pagingType=PAGE&page=0&size=20
```

### Response Shape

```json
{
  "code": 200,
  "message": null,
  "content": {
    "page": 0,
    "size": 20,
    "totalCount": 17,
    "totalPages": 1,
    "data": [ /* array of VideoItem */ ]
  }
}
```

### VideoItem Fields (verified, all present in live response)

| Field                  | Type            | Example value                                         | Notes                                             |
|------------------------|-----------------|-------------------------------------------------------|---------------------------------------------------|
| `videoNo`              | Long            | `13904663`                                            | **Primary key; use in watch URL and detail call** |
| `videoId`              | String (hex/36) | `"2694E3080DBDA2CD3CF3198B8C5F07A0D6FB"`             | Used for NeonPlayer (UPLOAD) or liveRewind        |
| `videoTitle`           | String          | `"이리가 좋은 청년"`                                   | Display title                                     |
| `videoType`            | String (enum)   | `"REPLAY"` or `"UPLOAD"`                             | REPLAY = saved livestream; UPLOAD = manual upload |
| `publishDate`          | String          | `"2026-06-27 01:01:11"`                              | KST, "yyyy-MM-dd HH:mm:ss"                        |
| `publishDateAt`        | Long            | `1782489670557`                                       | Unix epoch milliseconds                           |
| `thumbnailImageUrl`    | String (URL)    | `"https://livecloud-thumb.akamaized.net/..."`        | JPEG, typically 720p width; append `?type=f640_360` for resize |
| `duration`             | Int             | `15702`                                               | Seconds                                           |
| `readCount`            | Int             | `10`                                                  | View count                                        |
| `categoryType`         | String          | `"GAME"` / `"ETC"`                                   |                                                   |
| `videoCategory`        | String          | `"Black_Survival_Eternal_Return"` / `"music"`        | Machine-readable category slug                    |
| `videoCategoryValue`   | String          | `"이터널 리턴"` / `"음악/노래"`                       | Human-readable Korean category name               |
| `exposure`             | Boolean         | `false`                                               | Whether featured/promoted                         |
| `adult`                | Boolean         | `false`                                               | Age-gate flag                                     |
| `clipActive`           | Boolean         | `true`                                                | Clipping enabled                                  |
| `livePv`               | Int             | `369`                                                 | Concurrent viewers at time of broadcast           |
| `tags`                 | String[]        | `["버튜버","버츄얼","신입","노래"]`                   |                                                   |
| `commentActive`        | Boolean         | `true`                                                |                                                   |
| `trailerUrl`           | String?         | `null` (REPLAY) / MP4 URL (UPLOAD)                   | Short preview clip for UPLOAD type                |
| `tvAppViewingPolicyType` | String        | `"ALLOWED"`                                           | `"ALLOWED"` = playable; check before playing      |
| `blindType`            | String?         | `null`                                                | Non-null if content is hidden/blocked             |
| `paidProductId`        | String?         | `null`                                                | Non-null for paid content                         |
| `channel.channelId`    | String          | `"fd8516eb8d31a8a5147e94c281ae3f07"`                 |                                                   |
| `channel.channelName`  | String          | `"은초이 Choy"`                                       |                                                   |
| `channel.channelImageUrl` | String      | `"https://nng-phinf.pstatic.net/..."`                |                                                   |

### Watch URL Pattern

```
https://chzzk.naver.com/video/{videoNo}
```

Example: `https://chzzk.naver.com/video/13904663`

---

## 2. VOD Detail Endpoint

### Request

```
GET https://api.chzzk.naver.com/service/v2/videos/{videoNo}
```

Same User-Agent header required. No auth needed for public videos.

**Example URL:**
```
https://api.chzzk.naver.com/service/v2/videos/13904663
```

### Response — Additional Fields vs List

The v2 detail response includes all VideoItem fields from the list plus:

| Field                    | Type    | Example                                    | Notes                                                      |
|--------------------------|---------|--------------------------------------------|------------------------------------------------------------|
| `inKey`                  | String? | `"V127656e0e62594fee..."`                  | Non-null for **UPLOAD** type; null for REPLAY              |
| `vodStatus`              | String  | `"ABR_HLS"` (UPLOAD) / `"NONE"` (REPLAY)  | Indicates encoding completion state                        |
| `liveRewindPlaybackJson` | String? | JSON string (REPLAY) / null (UPLOAD)       | Embedded HLS manifest metadata; parse as JSON              |
| `prevVideo`              | Object? | VideoItem                                  | Previous video in channel's list (may be null)             |
| `nextVideo`              | Object? | VideoItem                                  | Next video in channel's list                               |
| `chapters`               | Array   | `[]`                                       | Chapter markers                                            |
| `chapterActive`          | Boolean | `false`                                    |                                                            |
| `liveOpenDate`           | String? | `"2026-06-26 20:35:31"` (REPLAY) / null   | Broadcast start time (REPLAY only)                         |
| `encryptionType`         | String? | `null`                                     | Non-null for encrypted/DRM content                         |
| `paidPromotion`          | Boolean | `false`                                    |                                                            |
| `videoChatEnabled`       | Boolean | `true` (REPLAY) / `false` (UPLOAD)        |                                                            |
| `videoChatChannelId`     | String? | `"N2a5oY"`                                 | For live chat replay                                       |
| `adParameter`            | Object  | `{"tag":"none"}`                           |                                                            |

---

## 3. HLS Playback Resolution

There are **two separate flows** depending on `videoType` / `vodStatus`.

### 3a. REPLAY type (`vodStatus = "NONE"`, `liveRewindPlaybackJson` is non-null)

The HLS master playlist URL is **directly embedded** in the v2 response — no second API call needed.

1. Call `GET /service/v2/videos/{videoNo}`
2. Parse the string field `content.liveRewindPlaybackJson` as JSON
3. Extract `media[0].path` — this is the signed HLS master playlist URL

```json
// liveRewindPlaybackJson parsed structure (example)
{
  "meta": {
    "videoId": "2694E3080DBDA2CD3CF3198B8C5F07A0D6FB",
    "streamSeq": 38693044,
    "liveId": "19663179",
    "paidLive": false,
    "cdnInfo": { "cdnType": "GCDN" },
    "liveRewind": true,
    "duration": 15703.032,
    "playbackAuthType": "NONE"
  },
  "media": [
    {
      "mediaId": "HLS",
      "protocol": "HLS",
      "path": "https://light-slit.akamaized.net/chzzk/kr/live_rewind/c/live_rewind_kr/.../vod_playlist.m3u8?hdnts=st=...~exp=...~acl=...~hmac=...",
      "encodingTrack": [
        { "encodingTrackId": "1080p", "videoWidth": 1920, "videoHeight": 1080, "videoBitRate": 8192000, "videoFrameRate": "60.0" },
        { "encodingTrackId": "720p",  "videoWidth": 1280, "videoHeight": 720,  "videoBitRate": 3000000, "videoFrameRate": "60.0" },
        { "encodingTrackId": "480p",  "videoWidth": 852,  "videoHeight": 480,  "videoBitRate": 1500000, "videoFrameRate": "30.0" },
        { "encodingTrackId": "360p",  "videoWidth": 640,  "videoHeight": 360,  "videoBitRate": 600000,  "videoFrameRate": "30.0" },
        { "encodingTrackId": "144p",  "videoWidth": 256,  "videoHeight": 144,  "videoBitRate": 128000,  "videoFrameRate": "30.0" }
      ]
    }
  ],
  "thumbnail": {
    "spriteSeekingThumbnail": {
      "spriteFormat": { "rowCount": 3, "columnCount": 4, "intervalType": "millisecond", "interval": 20000, "thumbnailWidth": 160, "thumbnailHeight": 90 },
      "urlTemplate": "https://livecloud-thumb.akamaized.net/.../image_4x3x20000_{spriteIndex}.jpg"
    }
  }
}
```

The `path` URL contains a `hdnts` query parameter for CDN authentication (time-limited). Feed this URL directly to ExoPlayer or any HLS player.

> **Warning:** The `hdnts` token is time-limited (exp field in the token). Always fetch a fresh v2 detail response immediately before playback — do not cache `liveRewindPlaybackJson`.

### 3b. UPLOAD type (`vodStatus = "ABR_HLS"`, `inKey` is non-null)

UPLOAD videos use Naver's NeonPlayer service and return **MPEG-DASH** (not HLS), with per-quality direct MP4 URLs.

1. Call `GET /service/v2/videos/{videoNo}` — extract `content.videoId` and `content.inKey`
2. Call NeonPlayer DASH endpoint:

```
GET https://apis.naver.com/neonplayer/vodplay/v2/playback/{videoId}?key={inKey}
Accept: application/dash+xml
User-Agent: Mozilla/5.0 ...
Referer: https://chzzk.naver.com/
```

3. The response is an MPEG-DASH MPD (XML / JSON depending on Accept header). Each `representation` contains a `baseURL` with a direct signed MP4 URL.

Example quality levels observed from a real UPLOAD response:
- `1080P_1920_8000_192` → 1920×1080, ~7.4 Mbps, H.264 avc1.640028
- `720P_1280_5000_192`  → 1280×720
- `480P_852_2000_192`   → 852×480
- `360P_640_1000_128`   → 640×360
- `144P_256_128_64`     → 256×144

**For in-app playback of UPLOAD videos**, the simplest approach for an Android app is to open the web URL in a WebView or Custom Tab:
```
https://chzzk.naver.com/video/{videoNo}
```
The NeonPlayer DASH/MP4 path is feasible but requires parsing the MPEG-DASH MPD and managing signed URLs. ExoPlayer's DASH support can handle this if the MP4 baseURL is passed directly.

---

## 4. Playability Check Fields

Before attempting to play a video, check these fields from the list or detail response:

| Field                      | Safe value   | Block condition                                    |
|----------------------------|--------------|----------------------------------------------------|
| `tvAppViewingPolicyType`   | `"ALLOWED"`  | Any other value (e.g., `"BLOCKED"`)                |
| `blindType`                | `null`       | Non-null (content was hidden by moderation)        |
| `adult`                    | `false`      | `true` requires login + age verification           |
| `paidProductId`            | `null`       | Non-null = paid content requiring purchase         |
| `encryptionType`           | `null`       | Non-null = DRM; may not be playable natively       |
| `vodStatus` (detail only)  | `"ABR_HLS"` or `"NONE"` | Other values may indicate processing/unavailable |

---

## 5. Kotlin / Retrofit Implementation Notes

### Data Classes

```kotlin
// --- List response ---
data class ChzzkVideoListResponse(
    val code: Int,
    val content: VideoListContent
)

data class VideoListContent(
    val page: Int,
    val size: Int,
    val totalCount: Int,
    val totalPages: Int,
    val data: List<VideoItem>
)

data class VideoItem(
    val videoNo: Long,
    val videoId: String,
    val videoTitle: String,
    val videoType: String,           // "REPLAY" or "UPLOAD"
    val publishDate: String,         // "yyyy-MM-dd HH:mm:ss" KST
    val publishDateAt: Long,         // epoch ms
    val thumbnailImageUrl: String?,
    val trailerUrl: String?,
    val duration: Int,               // seconds
    val readCount: Int,
    val categoryType: String?,
    val videoCategory: String?,
    val videoCategoryValue: String?,
    val exposure: Boolean,
    val adult: Boolean,
    val clipActive: Boolean,
    val livePv: Int,
    val tags: List<String>,
    val commentActive: Boolean,
    val blindType: String?,
    val paidProductId: String?,
    val tvAppViewingPolicyType: String,  // check == "ALLOWED"
    val channel: ChannelInfo
) {
    val watchUrl: String get() = "https://chzzk.naver.com/video/$videoNo"
    val isPlayable: Boolean get() =
        tvAppViewingPolicyType == "ALLOWED" &&
        blindType == null &&
        paidProductId == null &&
        !adult
}

// --- Detail response (v2) ---
data class ChzzkVideoDetailResponse(
    val code: Int,
    val content: VideoDetail
)

data class VideoDetail(
    // All VideoItem fields, plus:
    val videoNo: Long,
    val videoId: String,
    val videoTitle: String,
    val videoType: String,
    val inKey: String?,                       // Non-null for UPLOAD
    val vodStatus: String?,                   // "ABR_HLS" | "NONE" | null
    val liveRewindPlaybackJson: String?,      // Non-null for REPLAY — parse as JSON
    val encryptionType: String?,
    val tvAppViewingPolicyType: String,
    val blindType: String?,
    val adult: Boolean,
    val paidProductId: String?,
    val prevVideo: VideoItem?,
    val nextVideo: VideoItem?,
    val chapters: List<Any>
)
```

### Retrofit Interface

```kotlin
interface ChzzkApiService {
    @GET("service/v1/channels/{channelId}/videos")
    suspend fun getChannelVideos(
        @Path("channelId") channelId: String,
        @Query("sortType") sortType: String = "LATEST",
        @Query("pagingType") pagingType: String = "PAGE",
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
        @Query("videoType") videoType: String? = null  // "REPLAY" | "UPLOAD" | null
    ): ChzzkVideoListResponse

    @GET("service/v2/videos/{videoNo}")
    suspend fun getVideoDetail(
        @Path("videoNo") videoNo: Long
    ): ChzzkVideoDetailResponse
}

// Base URL: "https://api.chzzk.naver.com/"
```

### Retrofit / OkHttp Setup (header interceptor)

```kotlin
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .build()
        chain.proceed(request)
    }
    .build()
```

### Playback Resolution Logic

```kotlin
suspend fun resolvePlaybackUrl(videoNo: Long): String? {
    val detail = api.getVideoDetail(videoNo).content

    // Playability gate
    if (detail.tvAppViewingPolicyType != "ALLOWED" ||
        detail.blindType != null ||
        detail.adult ||
        detail.paidProductId != null) {
        return null  // open browser fallback
    }

    return when (detail.videoType) {
        "REPLAY" -> {
            // Parse embedded JSON string
            detail.liveRewindPlaybackJson?.let { json ->
                val parsed = Gson().fromJson(json, LiveRewindPlayback::class.java)
                parsed.media.firstOrNull { it.protocol == "HLS" }?.path
                // Feed directly to ExoPlayer HlsMediaSource
            }
        }
        "UPLOAD" -> {
            // Option A: Open in browser (simplest, no DASH parsing needed)
            "https://chzzk.naver.com/video/${detail.videoNo}"
            // Option B: Call NeonPlayer and parse DASH MPD for direct MP4 URL
            // GET https://apis.naver.com/neonplayer/vodplay/v2/playback/{videoId}?key={inKey}
        }
        else -> "https://chzzk.naver.com/video/${detail.videoNo}"
    }
}

data class LiveRewindPlayback(
    val meta: LiveRewindMeta,
    val media: List<LiveRewindMedia>
)
data class LiveRewindMeta(val videoId: String, val liveRewind: Boolean, val duration: Double)
data class LiveRewindMedia(val mediaId: String, val protocol: String, val path: String)
```

---

## 6. Pagination

The list endpoint is page-based (not cursor-based):

```kotlin
// Total pages: response.content.totalPages
// Has more:    page < totalPages - 1
// Next page:   page + 1
```

For large channels, iterate pages from 0 to `totalPages - 1`.

---

## 7. Additional Notes

- **REPLAY `liveRewindPlaybackJson` is null** when the VOD is still being processed after a live broadcast ends (`vodStatus = "NONE"` but no playback JSON present). In that case, fall back to the browser URL.
- **HLS `hdnts` token expiry:** The `exp` timestamp embedded in the HLS path indicates the CDN authentication expiry. Typical window is ~17 hours from fetch time. Always re-fetch the detail endpoint to get a fresh URL before playback.
- **NeonPlayer DASH for UPLOAD:** The response JSON contains one MPEG-DASH MPD-like structure per quality. `representation[i].baseURL[0].value` is the signed MP4 URL. ExoPlayer's `DashMediaSource` or a direct `ProgressiveMediaSource` per quality works. No DRM observed on public UPLOAD videos.
- **`sortType=POPULAR`** is a valid alternative sort; no other sort types were observed documented.
- **Search by keyword** (not needed for this app but noted): `GET https://api.chzzk.naver.com/service/v1/search/videos?keyword=...&offset=0&size=20`
