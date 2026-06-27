# Jamkubot Chzzk Preview Page — Implementation Research

**Target URL:** `https://jamkubot.com/custom/so0420/chzzk_video?uid=fd8516eb8d31a8a5147e94c281ae3f07`
**Research date:** 2026-06-28
**Channel:** 은초이 Choy (`fd8516eb8d31a8a5147e94c281ae3f07`)

---

## TL;DR — How the Preview Renders

The page is **not** a refreshing `<img>` and is **not** a plain iframe.

Primary rendering is a **HLS `<video>` element driven by HLS.js** (CDN: `hls.js@latest` from jsdelivr). The HLS m3u8 URL is retrieved from a server-side proxy that wraps the Chzzk live-detail API. An `<iframe src="https://chzzk.naver.com/embed/live/{uid}">` is used as a fallback when:
- HLS.js fails to load, or
- A fatal HLS network/media error is unrecoverable, or
- The native browser cannot play `application/vnd.apple.mpegurl` and HLS.js is not supported.

`liveImageUrl` is **never rendered as an `<img>`** by this page.

---

## Step-by-Step Flow

```
DOMContentLoaded
  └─ loadLiveStream()
       └─ GET /api/chzzk/live/{uid}           ← server-side proxy
            ├─ status !== "OPEN"  → show error overlay (offline)
            └─ status === "OPEN"
                 └─ parse livePlaybackJson (inner JSON string)
                      ├─ find media[].mediaId === "LLHLS" → streamUrl = media[].path
                      └─ fallback: live.path (if LLHLS absent)
                           ├─ streamUrl found → loadHlsLibrary() + setupVideoPlayer(streamUrl)
                           │     uses Hls.js: hls.loadSource(streamUrl); hls.attachMedia(video)
                           └─ no streamUrl → fallbackToIframe()
                                 iframe.src = "https://chzzk.naver.com/embed/live/{uid}"
```

---

## API Call: Jamkubot Proxy

### Request

```
GET https://jamkubot.com/api/chzzk/live/{channelId}
User-Agent: Mozilla/5.0 ...
```

No authentication headers required (public, same-origin proxy).

**Example:**
```
GET https://jamkubot.com/api/chzzk/live/fd8516eb8d31a8a5147e94c281ae3f07
```

### Relationship to the Real Chzzk API

Verified by comparing responses byte-for-byte: the jamkubot proxy returns **identical JSON** to:

```
GET https://api.chzzk.naver.com/service/v3/channels/{channelId}/live-detail
GET https://api.chzzk.naver.com/service/v2/channels/{channelId}/live-detail
```

Both v2 and v3 Chzzk endpoints return the same payload for this channel. The jamkubot server acts as a CORS-free relay; an Android app can call the Chzzk API directly.

### Response Shape (verified, channel offline)

```jsonc
{
  "code": 200,
  "message": null,
  "content": {
    "liveId": 19663179,
    "liveTitle": "이리가 좋은 청년",
    "status": "CLOSE",                    // "OPEN" when live
    "liveImageUrl": null,                 // thumbnail URL when live (e.g. "https://...jpg?type=f...")
    "defaultThumbnailImageUrl": null,
    "concurrentUserCount": 14,
    "accumulateCount": 369,
    "openDate": "2026-06-26 20:35:31",
    "closeDate": "2026-06-27 00:57:26",
    "adult": false,
    "krOnlyViewing": false,
    "clipActive": true,
    "tags": ["버튜버", "버츄얼", "신입", "노래"],
    "chatChannelId": "N2a5oY",
    "categoryType": "ETC",
    "liveCategory": "music",
    "liveCategoryValue": "이터널 리턴",
    "chatActive": true,
    "chatAvailableGroup": "ALL",
    "paidPromotion": false,
    "chatAvailableCondition": "NONE",
    "minFollowerMinute": 0,
    "allowSubscriberInFollowerMode": true,

    // ---- KEY FIELD: inner JSON string, must be parsed separately ----
    "livePlaybackJson": "{...}",          // see below

    "p2pQuality": [],
    "timeMachineActive": false,
    "timeMachinePlayback": false,
    "previewPlaybackJson": null,
    "radioModePlaybackJson": null,

    "channel": {
      "channelId": "fd8516eb8d31a8a5147e94c281ae3f07",
      "channelName": "은초이 Choy",
      "channelImageUrl": "https://nng-phinf.pstatic.net/MjAyNjA1MTVfMTkw/MDAxNzc4ODUzMDk4MzU5.qpvW1qbai07ByErLJGHCI_TIMVfG_8M1i2IUqRnCASIg.KEbyolK-rdCoWR97G-T9fyj_O4eC_c0TreiqIQDAJa0g.PNG/image.png",
      "verifiedMark": false,
      "activatedChannelBadgeIds": []
    },

    // ---- KEY FIELD: polling interval ----
    "livePollingStatusJson": "{\"status\": \"STARTED\", \"isPublishing\": true, \"playableStatus\": \"PLAYABLE\", \"trafficThrottling\": -1, \"callPeriodMilliSecond\": 10000}",

    "userAdultStatus": null,
    "blindType": null,
    "liveConnecting": false,
    "skipPreRollAd": false,
    "paidProduct": null,
    "tvAppViewingPolicyType": "ALLOWED",
    "party": null,
    "membershipBenefitType": "NONE"
  }
}
```

---

## livePlaybackJson — Parsed Structure

`content.livePlaybackJson` is a **JSON string** embedded inside the outer JSON. It must be parsed with a second `fromJson`/`JSONObject` call.

**When offline (stream ended):**
```jsonc
{
  "meta": {
    "videoId": "2694E3080DBDA2CD3CF3198B8C5F07A0D6FB",
    "streamSeq": 38693044,
    "liveId": "19663179",
    "paidLive": false,
    "cdnInfo": { "cdnType": "GCDN" },
    "p2p": false,
    "cmcdEnabled": false,
    "playbackAuthType": "NONE"
  },
  "serviceMeta": { "contentType": "VIDEO" },
  "live": {
    "start": "2026-06-26T20:35:31",
    "open": "2026-06-26T20:35:32",
    "timeMachine": false,
    "status": "ENDED"           // "STARTED" when live
  },
  "api": [
    { "name": "p2p-config", "path": "https://apis.naver.com/livecloud/livecloud/xray/p2p/v1/config/chzzk" },
    { "name": "qoeConfig",  "path": "https://apis.naver.com/policy/policy/policy" }
  ],
  "media": [],                  // EMPTY when offline; populated when live (see below)
  "thumbnail": {
    "snapshotThumbnailTemplate": "https://livecloud-thumb.akamaized.net/qa/livecloud/KR/stream/38693044/live/19663179/record/55800212/thumbnail/image_{type}.jpg",
    "timeMachineThumbnailTemplate": "https://livecloud-thumb.akamaized.net/qa/livecloud/KR/stream/38693044/live/19663179/record/55800212/thumbnail/image_{type}_{seq}.jpg",
    "types": ["720", "480", "360", "270", "144"]
  },
  "multiview": []
}
```

**When LIVE (expected `media` array — not directly observed, inferred from JS logic):**
```jsonc
"media": [
  {
    "mediaId": "LLHLS",            // Low-Latency HLS — preferred
    "path": "https://...master.m3u8?..."
  },
  {
    "mediaId": "HLS",              // Standard HLS fallback
    "path": "https://...master.m3u8?..."
  }
]
```

The JS selects the first entry where `mediaId === "LLHLS"`. If absent, it tries `live.path`.

**Thumbnail URL construction:**
Replace `{type}` in `snapshotThumbnailTemplate` with one of the `types` values, e.g.:
```
https://livecloud-thumb.akamaized.net/.../thumbnail/image_720.jpg
```

---

## livePollingStatusJson — Parsed Structure

```jsonc
{
  "status": "STARTED",               // or "ENDED"
  "isPublishing": true,
  "playableStatus": "PLAYABLE",      // or "NONE"
  "trafficThrottling": -1,
  "callPeriodMilliSecond": 10000     // poll interval in ms (10 seconds)
}
```

Use `callPeriodMilliSecond` to drive your polling loop when checking live status.

---

## URL Parameters on the Jamkubot Page

| Parameter | Default | Description |
|-----------|---------|-------------|
| `uid` | (required) | Chzzk channel ID |
| `no_ui` | `true` | Hide video controls when `true` |
| `auto_voluem_on` | `false` | Auto-unmute on first user interaction (note: typo in original — `voluem`) |
| `scroll_volume_control` | `false` | Mouse wheel adjusts volume |

---

## What the Page Does NOT Do

- Does **not** display `liveImageUrl` as a refreshing `<img>` tag.
- Does **not** auto-refresh or periodically re-poll while visible (one-shot load only).
- Does **not** use a WebSocket or Server-Sent Events.
- Does **not** require any cookies or authentication tokens for the proxy endpoint.

---

## Mirroring in an Android App

### Option A — ExoPlayer + Direct Chzzk API (Recommended)

Best for full control, no third-party dependency.

```kotlin
// 1. Fetch live detail
// GET https://api.chzzk.naver.com/service/v3/channels/{channelId}/live-detail

// 2. Check status
if (response.content.status != "OPEN") {
    showOfflineState()
    return
}

// 3. Parse the inner JSON string
val playbackJson = JSONObject(response.content.livePlaybackJson)
val mediaArray = playbackJson.getJSONArray("media")

var hlsUrl: String? = null
for (i in 0 until mediaArray.length()) {
    val item = mediaArray.getJSONObject(i)
    if (item.getString("mediaId") == "LLHLS") {
        hlsUrl = item.getString("path")
        break
    }
}
if (hlsUrl == null && playbackJson.has("live")) {
    hlsUrl = playbackJson.getJSONObject("live").optString("path")
}

// 4. Play with ExoPlayer (HLS supported natively)
val mediaItem = MediaItem.fromUri(hlsUrl!!)
val player = ExoPlayer.Builder(context).build()
player.setMediaItem(mediaItem)
player.prepare()
player.play()
```

### Option B — WebView embedding Chzzk embed (Simple, less control)

```kotlin
webView.settings.javaScriptEnabled = true
webView.settings.mediaPlaybackRequiresUserGesture = false
webView.loadUrl("https://chzzk.naver.com/embed/live/$channelId")
```

Requires `android.permission.INTERNET`. The Chzzk embed is a full SPA that handles HLS internally; no additional setup needed.

### Option C — WebView embedding Jamkubot page

```kotlin
webView.loadUrl("https://jamkubot.com/custom/so0420/chzzk_video?uid=$channelId&no_ui=false")
```

Works but introduces a third-party dependency.

---

## Retrofit Interface (Option A)

```kotlin
// Chzzk live-detail API
interface ChzzkApi {
    @GET("service/v3/channels/{channelId}/live-detail")
    suspend fun getLiveDetail(
        @Path("channelId") channelId: String
    ): ChzzkLiveDetailResponse
}

// Base URL: https://api.chzzk.naver.com/

data class ChzzkLiveDetailResponse(
    val code: Int,
    val message: String?,
    val content: LiveDetail?
)

data class LiveDetail(
    val liveId: Long,
    val liveTitle: String?,
    val status: String,               // "OPEN" | "CLOSE"
    val liveImageUrl: String?,
    val concurrentUserCount: Int,
    val accumulateCount: Int,
    val openDate: String?,
    val closeDate: String?,
    val livePlaybackJson: String,     // inner JSON string — parse manually
    val livePollingStatusJson: String, // inner JSON string — parse manually
    val channel: ChannelInfo?
)

data class ChannelInfo(
    val channelId: String,
    val channelName: String,
    val channelImageUrl: String?,
    val verifiedMark: Boolean
)
```

`livePlaybackJson` and `livePollingStatusJson` are double-encoded JSON strings; deserialize them separately with `Gson().fromJson(...)` or `JSONObject(...)` after the outer response is parsed.

---

## Polling Strategy

The `livePollingStatusJson.callPeriodMilliSecond` field in the live-detail response tells the client how often to poll. Currently observed as **10000 ms (10 seconds)**. Implement a `CoroutineScope` loop in your ViewModel:

```kotlin
viewModelScope.launch {
    while (isActive) {
        val detail = chzzkApi.getLiveDetail(channelId)
        updateUi(detail)
        val pollMs = parsePollInterval(detail.content?.livePollingStatusJson) ?: 10_000L
        delay(pollMs)
    }
}
```

---

## Verified Facts vs. Inferences

| Claim | Status |
|-------|--------|
| Page uses HLS.js `<video>` as primary renderer | **Verified** (source HTML) |
| `<iframe>` used as fallback only | **Verified** (source HTML) |
| `liveImageUrl` is never shown as `<img>` by this page | **Verified** (source HTML) |
| Proxy endpoint `/api/chzzk/live/{uid}` proxies Chzzk API | **Verified** (identical responses) |
| Chzzk API base: `https://api.chzzk.naver.com/service/v3/channels/{uid}/live-detail` | **Verified** (curl) |
| `livePlaybackJson.media[].mediaId === "LLHLS"` contains m3u8 URL when live | **Inferred** from JS + offline response showing empty `media: []` |
| Polling interval from `callPeriodMilliSecond` = 10000 ms | **Verified** (API response) |
| No auth headers required for proxy or Chzzk API | **Verified** (unauthenticated curl succeeds) |
