# Chzzk — Live Status + Channel Profile API

Research doc for the Android (Kotlin/Retrofit) Streamer Alert app.
All facts below were **verified live** against `api.chzzk.naver.com` on **2026-06-28** unless explicitly marked *(uncertain)*.

Example channel used throughout:
`fd8516eb8d31a8a5147e94c281ae3f07` (channelName: `은초이 Choy`) — was **offline** at capture time.
A second, **live** channel (`7ce8032370ac5121dcabce7bad375ced`, `풍월량`) was captured to document the `OPEN` shape.

---

## 0. Host, transport, required headers

- Base URL: `https://api.chzzk.naver.com`
- No authentication / API key required for these two read endpoints (public).
- HTTPS only.

### Required request header (VERIFIED)
The `User-Agent` header is **mandatory**. A request with **no** `User-Agent` had the connection reset (curl exit 56, HTTP `000`). Always send a browser UA:

```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
```

Recommended (commonly used by client libraries, not strictly required in our tests but safe to add):
```
Accept: application/json, text/plain, */*
```
*(uncertain)* Some libraries also send `Referer: https://chzzk.naver.com/`. It was **not** needed in our tests, but include it if you ever see 403s.

---

## 1. Response envelope (VERIFIED — applies to BOTH endpoints)

Every response is wrapped:

```json
{ "code": 200, "message": null, "content": { ... } }
```

| Field | Type | Notes |
|---|---|---|
| `code` | Int | `200` = success. Mirrors HTTP status. `404` seen for a non-existent channel on the live-detail endpoint. |
| `message` | String? | `null` on success. Korean error text on failure, e.g. `"채널이 존재하지 않습니다."` ("channel does not exist"). |
| `content` | Object? | Payload. **Absent/omitted** on error responses (e.g. the 404 body was `{"code":404,"message":"채널이 존재하지 않습니다."}` with no `content` key). |

Implementation note: treat `code != 200` (or `content == null`) as an API-level error even though the HTTP layer sometimes still returns 200. Keep `code` as the source of truth.

---

## 2. Endpoint A — Live detail

```
GET https://api.chzzk.naver.com/service/v3/channels/{channelId}/live-detail
```
- Path param `{channelId}` = 32-char hex channel id.
- No query params.
- Full URL for the example:
  `https://api.chzzk.naver.com/service/v3/channels/fd8516eb8d31a8a5147e94c281ae3f07/live-detail`

### 2.1 Fields under `content` (VERIFIED)

JSON path is relative to `content`.

| Field path | Type | OPEN example | CLOSE example | Meaning / notes |
|---|---|---|---|---|
| `status` | String | `"OPEN"` | `"CLOSE"` | **Primary live/offline flag.** `OPEN` = live now, `CLOSE` = offline. |
| `liveId` | Long | `19681711` | `19663179` | Numeric id of the (current or last) broadcast. |
| `liveTitle` | String | `"탈출하면 엔딩"` | `"이리가 좋은 청년"` | Stream title. Can contain emoji/Korean. May be the **last** title when offline. |
| `liveImageUrl` | String? | `https://livecloud-thumb.akamaized.net/chzzk/livecloud/KR/stream/26453996/live/19681711/record/55851550/thumbnail/image_{type}.jpg` | `null` | Thumbnail template. **Contains literal `{type}` placeholder.** **`null` when offline** — fall back to `defaultThumbnailImageUrl`, then `channel.channelImageUrl`. See §2.2. |
| `defaultThumbnailImageUrl` | String? | `null` | `null` | Optional static fallback thumbnail (often null). |
| `concurrentUserCount` | Int | `9883` | `14` | Live viewer count. When offline this holds a **stale/last** value — do not display it unless `status == OPEN`. |
| `accumulateCount` | Int | `0` | `369` | Cumulative viewers for the session (often `0` while live, populated after). |
| `openDate` | String? | `"2026-06-27 20:10:39"` | `"2026-06-26 20:35:31"` | Broadcast start. Format `yyyy-MM-dd HH:mm:ss`, **KST (UTC+9)**, no timezone suffix. |
| `closeDate` | String? | `null` | `"2026-06-27 00:57:26"` | Broadcast end. **`null` while live**, set when offline. Same format/zone as `openDate`. |
| `chatChannelId` | String? | `"N2aCh7"` | `"N2a5oY"` | Chat room id (used by the WebSocket/polling chat API). Present even when offline. |
| `liveCategory` | String? | `"Escape_from_Tarkov"` | `"music"` | Machine/category code (English/slug). |
| `liveCategoryValue` | String? | `"이스케이프 프롬 타르코프"` | `"이터널 리턴"` | Human-readable category/game name (localized). Use this for display. |
| `categoryType` | String? | `"GAME"` | `"ETC"` | Category bucket. Seen: `GAME`, `ETC`. *(uncertain: other values like `TALK`, `SPORTS` likely exist.)* |
| `tags` | String[] | `["신작","스팀","game"]` | `["버튜버","버츄얼","신입","노래"]` | Free-form tags. May be `[]`. |
| `adult` | Boolean | `false` | `false` | Adult (19+) flag for the stream. |
| `livePollingStatusJson` | String (stringified JSON) | `{"status":"STARTED","isPublishing":true,"playableStatus":"PLAYABLE","trafficThrottling":-1,"callPeriodMilliSecond":10000}` | same shape (see warning) | Polling hint. See §2.3. **Must be parsed as a nested JSON string.** |
| `p2pQuality` | String[] | `["720p","1080p"]` | `[]` | Quality tracks served via P2P. **Empty `[]` when offline.** Not needed for alerting; useful only if you build a player. |
| `livePlaybackJson` | String (stringified JSON) | large object (HLS/LLHLS URLs, encodingTracks, thumbnails) | present, `live.status:"ENDED"` | Stringified JSON with playback manifests. Only needed if you embed a player — **not required for alerts.** See §2.4. |
| `channel` | Object | see below | see below | Embedded channel summary (lets you skip Endpoint B for basic info). |
| `channel.channelId` | String | `7ce8032370ac5121dcabce7bad375ced` | `fd8516eb8d31a8a5147e94c281ae3f07` | |
| `channel.channelName` | String | `풍월량` | `은초이 Choy` | |
| `channel.channelImageUrl` | String? | profile png url | profile png url | Good offline thumbnail fallback. |
| `channel.verifiedMark` | Boolean | `true` | `false` | Verified/partner badge. |
| `chatActive` | Boolean | `true` | `true` | Whether chat is on. |
| `chatAvailableGroup` | String | `"ALL"` | `"ALL"` | Who may chat. |
| `chatAvailableCondition` | String | `"REAL_NAME"` | `"NONE"` | Extra chat gate (e.g. real-name verification). |
| `paidPromotion` | Boolean | `false` | `false` | Sponsored-content flag. |
| `krOnlyViewing` | Boolean | `false` | `false` | Korea-only restriction. |
| `clipActive` | Boolean | `true` | `true` | Clipping allowed. |
| `blindType` | String? | `null` | `null` | Set when the live is blinded/restricted. |
| `tvAppViewingPolicyType` | String | `"ALLOWED"` | `"ALLOWED"` | |
| `dropsCampaignNo` | Int? | `null` | `null` | Twitch-style "drops" campaign id when present (saw `1243` on another channel). |
| `watchPartyNo` / `watchPartyType` / `watchPartyTag` | mixed? | `null` | `null` | Watch-party metadata. |
| `adParameter.tag` | String | `"none"` | `"none"` | Ad targeting tag. |
| `membershipBenefitType` | String | `"NONE"` | `"NONE"` | |

Other low-value fields present (safe to ignore for this app): `cvExposure`, `minFollowerMinute`, `allowSubscriberInFollowerMode`, `timeMachineActive`, `timeMachinePlayback`, `previewPlaybackJson`, `radioModePlaybackJson`, `userAdultStatus`, `chatDonationRankingExposure`, `logPower{active,exposureWeeklyRanking,exposureMonthlyRanking}`, `earthquake`, `liveConnecting`, `skipPreRollAd`, `paidProduct`, `party`, `sporterAutoPublishLive`.

### 2.2 `liveImageUrl` `{type}` placeholder (VERIFIED template)

The URL contains the literal substring `image_{type}.jpg`. Replace `{type}` with a resolution:

```
.../thumbnail/image_480.jpg
.../thumbnail/image_720.jpg
.../thumbnail/image_1080.jpg
```

The embedded `livePlaybackJson.thumbnail.types` array advertised `["720","480","360","270","144"]` for these streams (i.e. `1080` is requestable for the main thumbnail per the task spec, but the snapshot template officially lists up to `720`). Recommended for the app: request `480` for list rows, `720`/`1080` for a detail/hero image.

Kotlin helper:
```kotlin
fun thumbnailUrl(template: String?, type: Int = 480): String? =
    template?.replace("{type}", type.toString())
```
Because `liveImageUrl` is `null` when offline, resolve with a fallback chain:
```kotlin
val img = thumbnailUrl(content.liveImageUrl)
    ?: content.defaultThumbnailImageUrl
    ?: content.channel?.channelImageUrl
```

### 2.3 `livePollingStatusJson` (parse note + WARNING)

It is a **string** containing JSON; parse it as a second step. Shape:
```json
{ "status":"STARTED", "isPublishing":true, "playableStatus":"PLAYABLE",
  "trafficThrottling":-1, "callPeriodMilliSecond":10000 }
```
- `status`: `STARTED` while live. *(uncertain: `ENDED`/`NONE` on other states.)*
- `callPeriodMilliSecond`: server's suggested poll interval (10000 ms = 10 s).

**WARNING (VERIFIED):** On the offline channel this field still reported `status:"STARTED"`, `isPublishing:true`. It can be **stale**, so **do NOT use `livePollingStatusJson` to decide live/offline.** Use top-level `status` (and/or Endpoint B `openLive`). The reliable "ended" signal inside the JSON blobs is `livePlaybackJson.live.status` which was `"STARTED"` (live) vs `"ENDED"` (offline) — but parsing the top-level `status` is simpler.

### 2.4 `livePlaybackJson` (only if building a player)

Stringified JSON. Notable nested paths (live example):
- `meta.videoId`, `meta.p2p` (bool), `meta.cdnInfo.cdnType` (`"GCDN"`).
- `live.status`: `"STARTED"` (live) / `"ENDED"` (offline) — reliable per-session end flag.
- `media[]`: `mediaId` `"HLS"` and `"LLHLS"`, each with `path` (master `.m3u8`, **time-limited signed URL** with `hdnts`/`exp`) and `encodingTrack[]` (`1080p/720p/480p/360p/144p/audioOnly` with `videoWidth/Height`, `videoBitRate`, `videoFrameRate`, `videoCodec:"H264"`).
- `thumbnail.snapshotThumbnailTemplate`: same `image_{type}.jpg` template; `thumbnail.types`.
Not needed for live-alert features. The signed HLS URLs expire (~hours) so fetch fresh before playback.

---

## 3. Endpoint B — Channel profile

```
GET https://api.chzzk.naver.com/service/v1/channels/{channelId}
```
- Full URL: `https://api.chzzk.naver.com/service/v1/channels/fd8516eb8d31a8a5147e94c281ae3f07`
- No query params.

### 3.1 Fields under `content` (VERIFIED)

| Field path | Type | Example | Meaning / notes |
|---|---|---|---|
| `channelId` | String? | `fd8516eb8d31a8a5147e94c281ae3f07` | Echoes the requested id. **`null` if channel doesn't exist** (see §3.2). |
| `channelName` | String | `은초이 Choy` | Display name. |
| `channelImageUrl` | String? | `https://nng-phinf.pstatic.net/.../image.png` | Profile picture (full-size URL, no placeholder). May be `null`. |
| `followerCount` | Int | `1607` | Follower total. |
| `openLive` | Boolean | `false` | **Live/offline flag** — `true` when the channel is currently broadcasting. Cheapest offline check (no JSON-in-JSON parsing). Matched `live-detail.status` in our tests. |
| `verifiedMark` | Boolean | `false` | Verified/partner badge. |
| `channelType` | String | `STREAMING` | `STREAMING` for a real streamer channel; `NORMAL` returned for the unknown/placeholder channel. |
| `channelDescription` | String | `노래/게임 방송하는 은초이 입니다 ...` | Bio (may contain emoji/newlines/email). |
| `subscriptionAvailability` | Boolean | `true` | Subscriptions offered. |
| `subscriptionPaymentAvailability` | Object | `{iapAvailability:false, iabAvailability:true}` | Payment rails. |
| `adMonetizationAvailability` | Boolean | `true` | |
| `activatedChannelBadgeIds` | String[] | `[]` | |
| `paidProductSaleAllowed` | Boolean | `false` | |

### 3.2 Non-existent channel behavior (VERIFIED — important edge case)

Unlike the live-detail endpoint (which returns `code:404` + Korean message), **Endpoint B returns `code:200`** with a placeholder `content`:
```json
{ "code":200, "message":null, "content":{
    "channelId":null, "channelName":"(알 수 없음)", "channelImageUrl":null,
    "channelType":"NORMAL", "channelDescription":"", "followerCount":0,
    "openLive":false, ... } }
```
So detect "bad/unknown channel id" by `content.channelId == null` (or `channelName == "(알 수 없음)"` = "(unknown)"), **not** by HTTP status.

---

## 4. How to detect live vs offline (summary — VERIFIED)

Reliable, in priority order:
1. **Endpoint A** `content.status == "OPEN"` → live; `"CLOSE"` → offline. (Primary.)
2. **Endpoint B** `content.openLive == true/false`. (Equivalent; lighter response, good for cheap polling.)
3. Inside `livePlaybackJson`: `live.status == "ENDED"` confirms offline.

Do **NOT** rely on: `livePollingStatusJson.status`, `concurrentUserCount`, `liveTitle`, or `liveImageUrl` presence alone — these can be stale/last-session values when offline (though `liveImageUrl`/`closeDate` are good *secondary* hints: offline ⇒ `liveImageUrl == null` and `closeDate != null`).

For a polling alert app: hit **Endpoint A** every ~10 s (honor `callPeriodMilliSecond`) and fire a "went live" notification on an `CLOSE→OPEN` transition. Endpoint A already embeds `channel{channelName, channelImageUrl, verifiedMark}`, so you can often skip Endpoint B; fetch B occasionally for `followerCount`/bio.

---

## 5. Kotlin / Retrofit notes

### 5.1 Retrofit service
```kotlin
interface ChzzkApi {
    @GET("service/v3/channels/{channelId}/live-detail")
    suspend fun getLiveDetail(@Path("channelId") channelId: String): Envelope<LiveDetail>

    @GET("service/v1/channels/{channelId}")
    suspend fun getChannel(@Path("channelId") channelId: String): Envelope<ChannelProfile>
}
```

### 5.2 Required UA interceptor (do not omit — request fails without UA)
```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val req = chain.request().newBuilder()
            .header("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        chain.proceed(req)
    }
    .build()

val api = Retrofit.Builder()
    .baseUrl("https://api.chzzk.naver.com/")   // trailing slash required
    .client(client)
    .addConverterFactory(MoshiConverterFactory.create())
    .build()
    .create(ChzzkApi::class.java)
```

### 5.3 Models (only the fields the app needs)
```kotlin
data class Envelope<T>(
    val code: Int,
    val message: String?,
    val content: T?
)

data class LiveDetail(
    val status: String?,                 // "OPEN" / "CLOSE"
    val liveId: Long?,
    val liveTitle: String?,
    val liveImageUrl: String?,           // has literal "{type}"; null when offline
    val defaultThumbnailImageUrl: String?,
    val concurrentUserCount: Int?,
    val accumulateCount: Int?,
    val openDate: String?,               // "yyyy-MM-dd HH:mm:ss" KST
    val closeDate: String?,              // null while live
    val chatChannelId: String?,
    val categoryType: String?,
    val liveCategory: String?,
    val liveCategoryValue: String?,
    val tags: List<String> = emptyList(),
    val adult: Boolean = false,
    val p2pQuality: List<String> = emptyList(),
    val livePollingStatusJson: String?,  // stringified JSON — parse separately
    val channel: ChannelSummary?
) {
    val isLive get() = status == "OPEN"
}

data class ChannelSummary(
    val channelId: String?,
    val channelName: String?,
    val channelImageUrl: String?,
    val verifiedMark: Boolean = false
)

data class ChannelProfile(
    val channelId: String?,              // null => channel does not exist
    val channelName: String?,
    val channelImageUrl: String?,
    val followerCount: Int = 0,
    val openLive: Boolean = false,       // live/offline flag
    val verifiedMark: Boolean = false
)
```

### 5.4 Parse the nested polling JSON (only if you use it)
```kotlin
data class LivePollingStatus(
    val status: String?,
    val isPublishing: Boolean?,
    val playableStatus: String?,
    val trafficThrottling: Int?,
    val callPeriodMilliSecond: Long?
)
// val poll = content.livePollingStatusJson?.let { moshi.adapter(LivePollingStatus::class.java).fromJson(it) }
```

### 5.5 Date parsing (KST)
```kotlin
val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
val openInstant = content.openDate?.let {
    java.time.LocalDateTime.parse(it, fmt)
        .atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant()
}
```

### 5.6 Error handling rules
- After every call, check `envelope.code == 200 && envelope.content != null`.
- Endpoint A: `code == 404` (+ `content == null`) ⇒ channel doesn't exist.
- Endpoint B: `code == 200` but `content.channelId == null` ⇒ channel doesn't exist.
- Network: a missing `User-Agent` yields a connection reset, not an HTTP error — always set the UA interceptor.

---

## 6. Sources
- Live verified responses from `api.chzzk.naver.com` (captured 2026-06-28).
- Open-source clients corroborating field names/endpoints:
  - python-chzzk: https://github.com/jonghwanhyeon/python-chzzk
  - chzzk4j (Java): https://github.com/R2turnTrue/chzzk4j
  - chzzk-sdk (JS): https://github.com/Kwabang/chzzk-sdk
  - streamlink chzzk plugin: https://github.com/streamlink/streamlink/blob/master/src/streamlink/plugins/chzzk.py
  - awesome-chzzk: https://github.com/dokdo2013/awesome-chzzk
  - Community API notes: https://gist.github.com/zeroday0619/2d03e11bd9e0a76e39915ade887058d5
