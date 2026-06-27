# Chzzk Live -> Playable HLS (.m3u8) for ExoPlayer/Media3

Goal: from a Chzzk **channel id**, obtain a playable HLS `.m3u8` master-playlist URL and feed it to ExoPlayer/Media3.

Status of this doc: **Empirically verified against the live API on 2026-06-28** (curl from Git Bash). The target
channel `fd8516eb8d31a8a5147e94c281ae3f07` (은초이 Choy) was **OFFLINE** at research time, so a second, currently-LIVE
channel (`7ce8032370ac5121dcabce7bad375ced`, 풍월량) was used to capture the real `media[]` / HLS payload. Schema was
cross-checked against streamlink's `chzzk.py` plugin and other open-source clients.

> All endpoints below are **unofficial** (the same internal API the chzzk.naver.com web player uses). Naver can change
> them without notice. There is a separate *official* OpenAPI (`openapi.chzzk.naver.com`) but it does **not** expose the
> HLS playback URL, so it is not usable for this task.

---

## 0. TL;DR pipeline

```
channelId
  -> GET https://api.chzzk.naver.com/service/v3/channels/{channelId}/live-detail
  -> content.status == "OPEN"  (else offline)
  -> content.livePlaybackJson  (a STRING -> JSON.parse AGAIN)
  -> .media[]  where mediaId == "HLS"   (standard latency)  OR  "LLHLS" (low latency)
  -> .path   == fully-formed https://...m3u8?...   <-- this IS the master playlist
  -> hand .path straight to ExoPlayer HlsMediaSource
```

The `path` value is a **complete, ready-to-play multivariant (master) HLS playlist URL** with an embedded auth token
(`hdnts=...`). No URL building, no extra signing, no special request headers, and **no cookies** are required for
normal (non-adult) streams. CORS is wide open (`Access-Control-Allow-Origin: *`).

---

## 1. Endpoints

### 1a. Live detail (this is the one you need — contains `livePlaybackJson`)

```
GET https://api.chzzk.naver.com/service/v3/channels/{channelId}/live-detail
```

- `v3` is what the current web player uses. `v2` and `v1` also still respond and return the **same**
  `livePlaybackJson` payload (verified). Use `v3`; fall back to `v2` if `v3` ever 404s.
- No query params required. No auth required for normal streams.
- Adult/19+ streams: requires Naver login cookies (see section 4).

Required request header (per task instructions; the API also works without it, but send it to look like a browser):

```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
```

Recommended extra header for robustness (some Naver edge nodes prefer it; harmless to always send):

```
Referer: https://chzzk.naver.com/
```

### 1b. Live status (lightweight poll — does NOT contain livePlaybackJson)

```
GET https://api.chzzk.naver.com/polling/v3/channels/{channelId}/live-status
```

Use this for cheap "is the streamer live?" polling (alert app use-case). Key fields:
`content.status` ("OPEN"/"CLOSE"), `content.concurrentUserCount`, `content.liveTitle`, `content.adult`,
`content.userAdultStatus`, and `content.livePollingStatusJson` (a stringified JSON with
`playableStatus: "PLAYABLE"`). **It has no `livePlaybackJson`**, so when the user actually presses Play you must call
`live-detail` (1a) to get the m3u8.

### 1c. Find live channels (optional, for discovery)

```
GET https://api.chzzk.naver.com/service/v1/lives?size=20&sortType=POPULAR
```

Returns `content.data[]` with `liveId`, `liveTitle`, `concurrentUserCount`, `channel.channelId`, `adult`, etc.

---

## 2. `live-detail` response shape (verified, live channel)

Top level:

```jsonc
{
  "code": 200,
  "message": null,
  "content": {
    "liveId": 19681711,
    "liveTitle": "탈출하면 엔딩",
    "status": "OPEN",                 // "OPEN" = live, "CLOSE" = offline
    "concurrentUserCount": 9883,
    "adult": false,
    "userAdultStatus": null,          // e.g. "NOT_LOGIN_USER", "ADULT", "NON_ADULT"
    "chatChannelId": "...",
    "channel": { "channelId": "...", "channelName": "풍월량", "channelImageUrl": "...", "verifiedMark": true },
    "livePollingStatusJson": "{\"status\":\"STARTED\",\"isPublishing\":true,\"playableStatus\":\"PLAYABLE\",...}",
    "livePlaybackJson": "{ ...A JSON STRING — SEE SECTION 3... }",
    "previewPlaybackJson": null,
    "radioModePlaybackJson": null
    // ...many more fields omitted...
  }
}
```

When OFFLINE (verified on the target channel `fd8516eb8d31a8a5147e94c281ae3f07`):

```jsonc
{
  "content": {
    "status": "CLOSE",
    "livePlaybackJson": "{...}"   // STILL PRESENT, but its "media": [] is an EMPTY array, "live":{"status":"ENDED"}
  }
}
```

So: **do not** assume `livePlaybackJson == null` when offline. On the v3 endpoint it is usually a non-null string whose
`media[]` is empty and whose inner `live.status == "ENDED"`. Treat **empty `media[]`** (or outer `status != "OPEN"`) as
"not playable". (`livePlaybackJson` *can* also be literally `null` in some states — handle both.)

---

## 3. `content.livePlaybackJson` — the double-encoded payload (THE IMPORTANT PART)

`content.livePlaybackJson` is a **String containing JSON**. You must parse it a second time. Verified structure of the
parsed object:

```jsonc
{
  "meta": {
    "videoId": "022CD2A03D76B5D03A8401D2991DD77D6E08",
    "streamSeq": 26453996,
    "liveId": "19681711",
    "paidLive": false,
    "cdnInfo": { "cdnType": "GCDN" },
    "p2p": true,
    "cmcdEnabled": false,
    "playbackAuthType": "NONE"        // "NONE" for open streams; differs for paid/auth streams
  },
  "serviceMeta": { "contentType": "VIDEO" },
  "live": {
    "start": "2026-06-27T20:10:39",
    "open":  "2026-06-27T20:10:39",
    "timeMachine": false,
    "status": "STARTED"               // "STARTED" while live, "ENDED" when the broadcast is over
  },
  "api": [
    { "name": "p2p-config", "path": "https://apis.naver.com/.../p2p/v1/config/chzzk" },
    { "name": "qoeConfig",  "path": "https://apis.naver.com/policy/policy/policy" }
  ],
  "media": [ /* SEE BELOW — pick from here */ ],
  "thumbnail": {
    "snapshotThumbnailTemplate": "https://livecloud-thumb.akamaized.net/.../image_{type}.jpg",
    "types": ["720","480","360","270","144"]
  },
  "multiview": []
}
```

### 3a. `media[]` entries (verified)

Two entries are present for a normal live stream. **Both** have `protocol == "HLS"`; they differ by `mediaId`:

| index | `mediaId` | `protocol` | `latency`      | `path` ends with                | Use for                                   |
|-------|-----------|------------|----------------|---------------------------------|-------------------------------------------|
| 0     | `HLS`     | `HLS`      | (absent)       | `..._hls_playlist.m3u8?hdnts=`  | Standard latency. **Most compatible.**    |
| 1     | `LLHLS`   | `HLS`      | `lowLatency`   | `..._playlist.m3u8?hdnts=`      | Low-latency HLS (LL-HLS partial segments) |

Each `media[]` entry:

```jsonc
{
  "mediaId": "HLS",                 // selector key
  "protocol": "HLS",
  "path": "https://ex-nlive-streaming.navercdn.com/chzzk/lip2_kr/.../..._hls_playlist.m3u8?hdnts=st=1782589050~exp=1782650260~acl=*/<sessionid>/*~hmac=<hex>&vp=<token>",
  "p2pPath": "/chzzk/.../...m3u8?channel_id=...&cdn_url=<base64>&...",   // for Naver's P2P SDK — IGNORE for ExoPlayer
  "p2pPathUrlEncoding": "...",      // url-encoded variant of p2pPath — IGNORE
  "encodingTrack": [ /* per-quality metadata, SEE 3b */ ]
}
```

- **The field you want is `path`.** It is an absolute `https://...m3u8` URL on host
  `ex-nlive-streaming.navercdn.com` and is itself a **master playlist** (multivariant: it lists 144p/360p/480p/720p60/
  1080p60 sub-playlists). ExoPlayer will do adaptive bitrate selection automatically.
- Ignore `p2pPath` / `p2pPathUrlEncoding` (these target Naver's proprietary P2P offload SDK, not a plain player).

### 3b. `encodingTrack[]` (resolution ladder — informational; you normally don't need it)

Each `media` entry carries an `encodingTrack[]` describing the available renditions. Verified entries (HLS media):

| encodingTrackId | width x height | fps  | videoCodec | videoBitRate | audioBitRate | notes               |
|-----------------|----------------|------|------------|--------------|--------------|---------------------|
| `1080p`         | 1920 x 1080    | 60   | H264       | 8192000      | 192000       | `avoidReencoding:true` (source) |
| `720p`          | 1280 x 720     | 60   | H264       | 3000000      | 192000       |                     |
| `480p`          | 852 x 480      | 30   | H264       | 1500000      | 192000       |                     |
| `360p`          | 640 x 360      | 30   | H264       | 600000       | 96000        |                     |
| `144p`          | 256 x 144      | 30   | H264       | 128000       | 64000        |                     |
| `audioOnly`     | -              | -    | AAC        | -            | 96000        | has its own `path` m3u8, `audioOnly:true` |

Other per-track fields: `videoProfile`, `audioProfile` ("LC"), `videoFrameRate` ("60.0"), `audioSamplingRate` (48000),
`audioChannel` (2), `videoDynamicRange` ("SDR"), `videoCodec` ("H264"). Most tracks only expose `p2pPath` (not a plain
`path`) — another reason to just use the top-level `media.path` master playlist and let ExoPlayer pick renditions.

> "Live edge": there is no explicit numeric live-edge field in `livePlaybackJson`. Live-edge handling is done by the HLS
> playlists themselves. The standard `HLS` variant is a normal sliding-window live playlist; the `LLHLS` variant adds
> `#EXT-X-SERVER-CONTROL` / partial segments. Configure ExoPlayer's `MediaItem.LiveConfiguration` if you want to pin the
> target offset (see section 6).

### 3c. Verified master playlist body (what `media.path` actually returns)

`GET`ting the HLS `path` returns `Content-Type: application/vnd.apple.mpegurl`, HTTP 200, no Set-Cookie:

```
#EXTM3U
#EXT-X-VERSION:7
#EXT-X-INDEPENDENT-SEGMENTS
#EXT-X-STREAM-INF:BANDWIDTH=3192000,CODECS="avc1.640028,mp4a.40.2",RESOLUTION=1280x720,FRAME-RATE=60.00
720p/hdntl=.../..._hls_chunklist.m3u8?vp=...
#EXT-X-STREAM-INF:BANDWIDTH=1692000,CODECS="avc1.4D001F,mp4a.40.2",RESOLUTION=852x480,FRAME-RATE=30.00
480p/...chunklist.m3u8?vp=...
#EXT-X-STREAM-INF:BANDWIDTH=696000,...,RESOLUTION=640x360,...
360p/...
#EXT-X-STREAM-INF:BANDWIDTH=192000,...,RESOLUTION=256x144,...
144p/...
#EXT-X-STREAM-INF:BANDWIDTH=8384000,...,RESOLUTION=1920x1080,FRAME-RATE=60.00
1080p/...
```

Note the per-variant child playlists carry their own token (`hdntl=...`), issued by the master playlist response — the
player follows them automatically. You only ever store/pass the **master** URL (`media.path`).

---

## 4. Adult / login-gated / paid streams

- **Detection:** `content.adult == true` (in both `live-status` and `live-detail`). Also watch
  `content.userAdultStatus`: `"NOT_LOGIN_USER"` (not logged in), `"NON_ADULT"` (logged in but not age-verified / under
  19), `"ADULT"` (allowed). For paid/subscriber streams see `meta.playbackAuthType != "NONE"` and `meta.paidLive`.
- **Behavior when not authorized:** the API returns the detail object but `content.livePlaybackJson` is **null** (or its
  `media[]` is empty), so no m3u8 is obtainable. streamlink's plugin reports `"This stream is for adults only"` in this
  case.
- **How clients unlock it:** send the logged-in Naver cookies on the `live-detail` request:

  ```
  Cookie: NID_AUT=<value>; NID_SES=<value>
  ```

  These come from a `chzzk.naver.com` browser session (DevTools > Application > Cookies). With valid adult cookies the
  same `live-detail` call returns a populated `livePlaybackJson`. (Confirmed by kimcore/chzzk, chzzkpy, streamlink docs.)
- For an alert app you will typically just **skip/disable playback** for adult streams unless you implement a Naver
  login/cookie flow. Surface a "19+ login required" message based on `adult==true` + missing `livePlaybackJson`.

---

## 5. Kotlin — parse `livePlaybackJson` and play with ExoPlayer/Media3

Dependencies (Media3 1.x):

```kotlin
implementation("androidx.media3:media3-exoplayer:1.4.1")
implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
implementation("androidx.media3:media3-ui:1.4.1")
// JSON: either kotlinx.serialization or org.json (below uses kotlinx.serialization)
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
```

### 5a. Retrofit service + models

```kotlin
// --- Retrofit API ---
interface ChzzkApi {
    @GET("service/v3/channels/{channelId}/live-detail")
    suspend fun liveDetail(@Path("channelId") channelId: String): LiveDetailResponse

    @GET("polling/v3/channels/{channelId}/live-status")
    suspend fun liveStatus(@Path("channelId") channelId: String): LiveStatusResponse
}

// Base URL: https://api.chzzk.naver.com/
// Add an OkHttp interceptor that sets:
//   User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
//   Referer:    https://chzzk.naver.com/
// (and, only for adult streams, Cookie: NID_AUT=...; NID_SES=...)

@Serializable
data class LiveDetailResponse(val code: Int, val message: String? = null, val content: LiveDetailContent? = null)

@Serializable
data class LiveDetailContent(
    val status: String? = null,           // "OPEN" / "CLOSE"
    val liveTitle: String? = null,
    val adult: Boolean = false,
    val userAdultStatus: String? = null,
    val livePlaybackJson: String? = null  // <-- STRING of JSON, parse again
)
```

Use a lenient Json: `val json = Json { ignoreUnknownKeys = true }` and register a Kotlinx
`JsonConverterFactory` (`json.asConverterFactory("application/json".toMediaType())`).

### 5b. Inner `livePlaybackJson` models + extraction

```kotlin
@Serializable
data class LivePlayback(
    val media: List<Media> = emptyList(),
    val live: LiveBlock? = null
)

@Serializable
data class LiveBlock(val status: String? = null)   // "STARTED" / "ENDED"

@Serializable
data class Media(
    val mediaId: String,        // "HLS" | "LLHLS"
    val protocol: String,       // "HLS"
    val path: String            // the master .m3u8 URL  <-- what we play
)

private val lenientJson = Json { ignoreUnknownKeys = true }

/** Returns a playable HLS master-playlist URL, or null if offline/not available. */
fun extractHlsUrl(content: LiveDetailContent, preferLowLatency: Boolean = false): String? {
    if (content.status != "OPEN") return null
    val raw = content.livePlaybackJson ?: return null          // may be null for adult/offline
    val playback = lenientJson.decodeFromString<LivePlayback>(raw)   // SECOND parse
    if (playback.live?.status == "ENDED") return null
    val media = playback.media
    if (media.isEmpty()) return null                            // offline: media[] is []
    val wantedId = if (preferLowLatency) "LLHLS" else "HLS"
    return media.firstOrNull { it.mediaId == wantedId && it.protocol == "HLS" }?.path
        ?: media.firstOrNull { it.protocol == "HLS" }?.path     // fallback to any HLS
}
```

### 5c. Feed to ExoPlayer via HlsMediaSource

```kotlin
@OptIn(androidx.media3.common.util.UnstableApi::class)
fun buildPlayer(context: Context, hlsUrl: String): ExoPlayer {
    // The CDN needs the same browser-ish UA. Set it on the HTTP data source.
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                      "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
        .setDefaultRequestProperties(mapOf("Referer" to "https://chzzk.naver.com/"))
        .setAllowCrossProtocolRedirects(true)

    val mediaItem = MediaItem.Builder()
        .setUri(hlsUrl)
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        // Optional: tune live edge (LL-HLS). Defaults are fine for standard HLS.
        .setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(3000)   // ~3s behind edge for standard HLS; lower for LLHLS
                .build()
        )
        .build()

    val source = HlsMediaSource.Factory(httpFactory)
        .setAllowChunklessPreparation(true)
        .createMediaSource(mediaItem)

    return ExoPlayer.Builder(context).build().apply {
        setMediaSource(source)
        prepare()
        playWhenReady = true
    }
}
```

Glue:

```kotlin
val resp = api.liveDetail(channelId)
val url = resp.content?.let { extractHlsUrl(it, preferLowLatency = false) }
if (url != null) buildPlayer(context, url) else showOfflineOrAdultMessage(resp.content)
```

---

## 6. Practical notes / gotchas

- **Token lifetime:** the `path` URL carries `hdnts=st=<start>~exp=<expiry>~...~hmac=<sig>`. Observed window
  `st=1782589050 exp=1782650260` ≈ **17 hours**. It is signed and time-limited, so **fetch `live-detail` fresh right
  before playback**; do not cache the m3u8 URL long-term. If the stream restarts (`streamSeq` changes) the old URL dies.
- **Master vs media playlist:** `media.path` is the *master* (multivariant) playlist. Don't try to read
  `encodingTrack[].path` per-quality (most only have `p2pPath`); let ExoPlayer's adaptive selection use the master.
- **HLS vs LLHLS:** start with `mediaId == "HLS"` for maximum device compatibility. `LLHLS` (low latency) works in
  Media3 too but is more sensitive to buffering on poor networks; offer it as an opt-in.
- **Headers:** for the *API* call send `User-Agent` (+ `Referer`). For the *CDN* m3u8/segment fetches, set the same
  `User-Agent` on `DefaultHttpDataSource.Factory`. No cookies needed for normal streams (CDN sent `Access-Control-Allow-
  Origin: *`, no `Set-Cookie`).
- **Offline handling:** poll `polling/v3/.../live-status` (cheap) for the alert; only call `live-detail` when the user
  opens the player. Offline => `status:"CLOSE"` and/or `media:[]` / `live.status:"ENDED"` / `livePlaybackJson:null`.
- **Adult:** `adult:true` + null `livePlaybackJson` => needs `NID_AUT`/`NID_SES` cookies; otherwise show a 19+ notice.
- **Unofficial API risk:** these `api.chzzk.naver.com` paths are reverse-engineered; wrap calls defensively and degrade
  gracefully if the schema changes.

---

## 7. Sources

- Live empirical capture via curl, 2026-06-28 (channels `fd8516eb8d31a8a5147e94c281ae3f07` offline,
  `7ce8032370ac5121dcabce7bad375ced` live). Endpoints: `service/v3/.../live-detail`, `polling/v3/.../live-status`,
  `service/v1/lives`.
- streamlink chzzk plugin (endpoint, `mediaId=="HLS" && protocol=="HLS"` selection, adult/offline handling):
  https://github.com/streamlink/streamlink/blob/master/src/streamlink/plugins/chzzk.py
- kimcore/chzzk (NID_AUT/NID_SES cookie auth for v1/adult): https://github.com/kimcore/chzzk
- chzzkpy (Python): https://pypi.org/project/chzzkpy/ ; jonghwanhyeon/python-chzzk: https://github.com/jonghwanhyeon/python-chzzk
- jaesung9507/chzzk (Go: GetLiveDetail/GetLivePlayback -> HLS path): https://pkg.go.dev/github.com/jaesung9507/chzzk
- R2turnTrue/chzzk4j (Java): https://github.com/R2turnTrue/chzzk4j
- ChzzkAPI reference gist: https://gist.github.com/zeroday0619/2d03e11bd9e0a76e39915ade887058d5
