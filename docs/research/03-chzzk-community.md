# Chzzk Community Feed (Wall) API — Implementation Reference

Research target: the community wall at
`https://chzzk.naver.com/fd8516eb8d31a8a5147e94c281ae3f07/community`
— fetch feed posts WITH full text, images, author, date, like count, comment count.

Channel id used for all verification: `fd8516eb8d31a8a5147e94c281ae3f07`
All facts below were verified live with `curl` on 2026-06-28 unless explicitly marked **UNCERTAIN**.

---

## 1. TL;DR — the real endpoint

The Chzzk "community wall" is **built on top of Naver's generic comment system**. There is **no separate `/feeds` endpoint** (those return `error_code 051 "API does not exist"`). The wall posts ARE top-level comment objects attached to the channel via `objectType = CHANNEL_POST`, `objectId = {channelId}`.

The reference bot's comment API is therefore **correct**, not wrong. Verified endpoint:

```
GET https://apis.naver.com/nng_main/nng_comment_api/v1/type/CHANNEL_POST/id/{channelId}/comments
```

Full verified example (returns 200 with real wall posts):

```
https://apis.naver.com/nng_main/nng_comment_api/v1/type/CHANNEL_POST/id/fd8516eb8d31a8a5147e94c281ae3f07/comments?limit=10&offset=0&orderType=DESC&pagingType=PAGE
```

Endpoints that were probed and **do NOT exist / do not work**:
- `…/nng_community_api/v1/channels/{id}/feeds` → `error_code 051` (does not exist)
- `…/nng_community_api/v1/channels/{id}/comments` → `error_code 051`
- `…/nng_main/v1/channels/{id}/feeds` (apis.naver.com) → `Not Found`
- `https://comm-api.game.naver.com/nng_main/...` (the chat host) → `404`. **Use `apis.naver.com` for this endpoint.**

---

## 2. Request

| Item | Value |
|------|-------|
| Method | `GET` |
| Scheme/Host | `https://apis.naver.com` |
| Path | `/nng_main/nng_comment_api/v1/type/CHANNEL_POST/id/{channelId}/comments` |
| Auth | **None required** for a public channel. Verified working with no cookies and even with no User-Agent. (Still send the UA header as a courtesy — see below.) |

### Query parameters (all verified)

| Param | Required | Verified values | Notes |
|-------|----------|-----------------|-------|
| `limit` | yes | **only `10` or `30`** | Any other value (1, 2, 3, 5, 9, 11, 15, 20, 25, 40, 50, 60, 100) returns `{"code":30002,"message":"잘못된 요청입니다."}` with `content:null`. This is a hard server-side allowlist, reproduced many times. **Use `limit=10`.** |
| `offset` | yes | `0`, `10`, `20`, … | 0-based. Page by adding `limit` each time. `offset=70` (of 74 total) returns the last 4 items. |
| `orderType` | yes | `DESC` (newest first) or `ASC` (oldest first) | `DESC` is what the wall shows. |
| `pagingType` | yes | `PAGE` | Offset paging mode. |

### Headers

```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
Accept: application/json
```

Response is `Content-Type: application/json`, `Cache-Control: no-store` (do not cache), server `nginx`.

---

## 3. Response shape (verified)

Top level:

```jsonc
{
  "code": 200,
  "message": null,
  "content": {
    "comments": {
      "page": { "next": 26170638, "prev": null }, // cursor = a commentId; next == null on the last page
      "data": [ /* array of post items, length == limit (or fewer on last page) */ ],
      "totalCount": 74,    // total number of wall posts on this channel
      "commentCount": 74   // same value here
    },
    "commentActive": true
  }
}
```

Each element of `content.comments.data[]` (a single wall post):

```jsonc
{
  "comment": {
    "commentId": 26980552,
    "commentType": "COMMENT",
    "replyCount": 0,                 // NOTE: stays 0 here; do NOT use for comment count
    "parentCommentId": 0,            // 0 => top-level wall post
    "content": "이번주 스케줄표 입니당💙",  // FULL post text; may contain \n line breaks and emoji
    "mentionedUserIdHash": null,
    "mentionedUserNickname": null,
    "secret": false,
    "hideByCleanBot": false,
    "deleted": false,
    "createdDate": "20260621170043", // yyyyMMddHHmmss in KST (UTC+9) -- see timezone note
    "attaches": [                    // CAN BE null (text-only posts) OR an array; can hold multiple images
      {
        "commentId": 26980552,
        "attachType": "PHOTO",       // only PHOTO observed across this channel
        "attachValue": "https://nng-phinf.pstatic.net/MjAyNjA2MjFfMjA5/MDAx....JPEG/image.jpg",
        "extraJson": "{\"width\":1980,\"height\":1080}", // STRING containing JSON -> parse for dimensions
        "order": 0,                  // NOT reliably sequential (saw 0,0,0,0,5 in a 5-image post)
        "createdDate": "2026-06-21T08:00:43.000+00:00", // ISO-8601 UTC
        "updatedDate": "2026-06-21T08:00:43.000+00:00"
      }
    ],
    "objectType": "CHANNEL_POST",
    "objectId": "fd8516eb8d31a8a5147e94c281ae3f07", // the channel id
    "loungeId": "",
    "onlyOneEmoji": false,
    "childObjectCount": 2,           // == COMMENT COUNT shown on the post
    "childCommentActive": true
  },
  "user": {
    "userIdHash": "fd8516eb8d31a8a5147e94c281ae3f07",
    "userNickname": "은초이 Choy",
    "profileImageUrl": "https://nng-phinf.pstatic.net/.../image.png",
    "userLevel": 0,
    "writer": false,
    "badge": { "imageUrl": "https://ssl.pstatic.net/static/nng/glive/icon/streamer.png" },
    "title": { "name": "스트리머", "color": "#D9B04F" },
    "userRoleCode": "streamer",      // wall posts on this channel are all authored by the streamer
    "secretOpen": false,
    "buffnerf": null,
    "privateUserBlock": false,
    "verifiedMark": false,
    "activatedChannelBadgeIds": []
  },
  "buffNerf": {
    "buffCount": 7,                  // == LIKE COUNT on the post
    "nerfCount": 0
  }
}
```

### Field mapping for the task requirements

| Needed | Exact JSON path (per item in `content.comments.data[]`) | Example value |
|--------|--------------------------------------------------------|---------------|
| Full text content | `comment.content` | `"이번주 스케줄표 입니당💙"` (may contain `\n`) |
| Images (URLs) | `comment.attaches[].attachValue` where `comment.attaches[].attachType == "PHOTO"` | `https://nng-phinf.pstatic.net/.../image.jpg` |
| Image dimensions | `JSON.parse(comment.attaches[].extraJson).width / .height` | `1980 × 1080` |
| Author name | `user.userNickname` | `"은초이 Choy"` |
| Author avatar | `user.profileImageUrl` | `https://nng-phinf.pstatic.net/.../image.png` |
| Author role/badge | `user.userRoleCode`, `user.badge.imageUrl`, `user.title.name` | `"streamer"`, streamer icon, `"스트리머"` |
| Post date | `comment.createdDate` | `"20260621170043"` (yyyyMMddHHmmss, KST) |
| Like count | `buffNerf.buffCount` | `7` |
| Comment count | `comment.childObjectCount` | `2` (do NOT use `comment.replyCount`, which is `0`) |
| Stable post id | `comment.commentId` | `26980552` |
| Deleted/hidden flags | `comment.deleted`, `comment.hideByCleanBot`, `comment.secret` | booleans |

### Timezone note (verified)
`comment.createdDate = "20260621170043"` (17:00:43) corresponds to the same attach's
`createdDate = "2026-06-21T08:00:43.000+00:00"` (08:00:43 UTC). 08:00 UTC + 9h = 17:00.
So **`comment.createdDate` is local Korea time (KST, UTC+9)** with no timezone marker. Parse it as KST.

---

## 4. Pagination (verified)

Offset-based. `totalCount` tells you how many posts exist.

```
page 1: offset=0,  limit=10
page 2: offset=10, limit=10
...
last:   offset=70, limit=10 -> returns 4 items, content.comments.page.next == null
```

Stop when any of these is true:
- returned `data.length < limit`, or
- `content.comments.page.next == null`, or
- `offset >= content.comments.totalCount`.

`page.next` / `page.prev` are commentId cursors; with `pagingType=PAGE` you do not need them — just increment `offset`. (Cursor-based paging via `page.next` was not required and is left **UNCERTAIN**.)

---

## 5. Per-post comments / replies (bodies)

For the feed list you do **not** need this — the comment **count** is `comment.childObjectCount`.
If you later need the actual reply text under a post, note these probes all FAILED:
- `…/comments/{commentId}/replies` → 404
- `…/comments/{commentId}/recomments` → 404
- `…/type/COMMENT/id/{commentId}/comments` → `Bad Request`
- adding `parentCommentId={id}` (with or without `commentType=REPLY`) to the main call → **param is ignored**, returns the same top-level list.

**UNCERTAIN:** the exact endpoint to fetch reply bodies was not identified. Treat `childObjectCount` as the authoritative comment count for the UI.

---

## 6. Important gotchas

1. **`limit` must be 10 or 30.** Anything else => `code 30002`, `content:null`. Hard-code `limit=10`.
2. **`comment.attaches` can be `null`** (text-only posts), not just empty. Guard against null in Kotlin.
3. **A post can have multiple images;** `order` is not reliably sorted — sort by `order` defensively but expect ties.
4. **`extraJson` is a JSON string**, not an object — parse it (or ignore if you don't need dimensions).
5. Use **like = `buffNerf.buffCount`**, **comments = `comment.childObjectCount`**. `replyCount` is 0 and misleading.
6. On this channel every wall post is authored by the streamer (`userRoleCode == "streamer"`). Other channels may have non-streamer roles; do not assume.
7. No pinned-post flag is present in this response (no `pin`/`pinned` field). **UNCERTAIN** whether pinned posts are exposed elsewhere.
8. Response is `no-store`; implement your own polling/caching for the alert app.
9. Image hosts (`nng-phinf.pstatic.net`, `ssl.pstatic.net`) are public CDN URLs — load directly (Coil/Glide). They generally need no special headers; if a 403 ever appears, add a `Referer: https://chzzk.naver.com/` header (**UNCERTAIN**, not needed in testing).

---

## 7. Kotlin / Retrofit notes

### Retrofit service

```kotlin
interface ChzzkCommunityApi {
    @GET("nng_main/nng_comment_api/v1/type/CHANNEL_POST/id/{channelId}/comments")
    suspend fun getCommunityFeed(
        @Path("channelId") channelId: String,
        @Query("limit") limit: Int = 10,          // MUST be 10 or 30
        @Query("offset") offset: Int = 0,
        @Query("orderType") orderType: String = "DESC",
        @Query("pagingType") pagingType: String = "PAGE",
    ): CommentEnvelope
}
```

### Retrofit/OkHttp setup

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val req = chain.request().newBuilder()
            .header("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "application/json")
            .build()
        chain.proceed(req)
    }
    .build()

val api = Retrofit.Builder()
    .baseUrl("https://apis.naver.com/")      // trailing slash required by Retrofit
    .client(client)
    .addConverterFactory(MoshiConverterFactory.create()) // or kotlinx-serialization
    .build()
    .create(ChzzkCommunityApi::class.java)
```

### DTOs (Moshi/kotlinx — note nullable `attaches`, `message`)

```kotlin
@JsonClass(generateAdapter = true)
data class CommentEnvelope(
    val code: Int,
    val message: String?,
    val content: CommentContent?,        // null when code != 200 (e.g. 30002)
)

@JsonClass(generateAdapter = true)
data class CommentContent(
    val comments: CommentsBlock,
    val commentActive: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class CommentsBlock(
    val page: Page,
    val data: List<FeedItem>,
    val totalCount: Int,
    val commentCount: Int,
)

@JsonClass(generateAdapter = true)
data class Page(val next: Long?, val prev: Long?)

@JsonClass(generateAdapter = true)
data class FeedItem(
    val comment: Comment,
    val user: CommentUser,
    val buffNerf: BuffNerf?,
)

@JsonClass(generateAdapter = true)
data class Comment(
    val commentId: Long,
    val content: String,
    val createdDate: String,             // "yyyyMMddHHmmss" in KST
    val attaches: List<Attach>?,         // NULLABLE
    val childObjectCount: Int,           // comment count
    val replyCount: Int,                 // ignore (always 0 here)
    val parentCommentId: Long,
    val deleted: Boolean,
    val hideByCleanBot: Boolean,
    val secret: Boolean,
    val objectType: String,              // "CHANNEL_POST"
    val objectId: String,                // channel id
)

@JsonClass(generateAdapter = true)
data class Attach(
    val attachType: String,              // "PHOTO"
    val attachValue: String,             // image URL
    val extraJson: String?,              // JSON string: {"width":..,"height":..}
    val order: Int,
)

@JsonClass(generateAdapter = true)
data class CommentUser(
    val userIdHash: String,
    val userNickname: String,
    val profileImageUrl: String?,
    val userRoleCode: String?,           // "streamer", etc.
    val badge: Badge?,
    val title: Title?,
)

@JsonClass(generateAdapter = true)
data class Badge(val imageUrl: String?)

@JsonClass(generateAdapter = true)
data class Title(val name: String?, val color: String?)

@JsonClass(generateAdapter = true)
data class BuffNerf(val buffCount: Int, val nerfCount: Int)  // buffCount == likes
```

### Parsing helpers

```kotlin
// Date: "20260621170043" -> Instant (treat as Asia/Seoul)
private val FEED_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
fun parseFeedDate(raw: String): Instant =
    LocalDateTime.parse(raw, FEED_FMT)
        .atZone(ZoneId.of("Asia/Seoul"))
        .toInstant()

// Images for a post (null-safe, PHOTO only, sorted by order)
fun FeedItem.imageUrls(): List<String> =
    comment.attaches.orEmpty()
        .filter { it.attachType == "PHOTO" }
        .sortedBy { it.order }
        .map { it.attachValue }

fun FeedItem.likeCount() = buffNerf?.buffCount ?: 0
fun FeedItem.commentCount() = comment.childObjectCount
```

### Paging logic

```kotlin
suspend fun loadAllPosts(channelId: String): List<FeedItem> {
    val all = mutableListOf<FeedItem>()
    var offset = 0
    val limit = 10
    while (true) {
        val env = api.getCommunityFeed(channelId, limit = limit, offset = offset)
        val block = env.content?.comments ?: break
        all += block.data
        offset += limit
        if (block.data.size < limit || block.page.next == null || offset >= block.totalCount) break
    }
    return all
}
```

For a "new community post" alert: poll page 1 (`offset=0, orderType=DESC, limit=10`) and compare the
max `comment.commentId` (monotonically increasing) against the last seen id.

---

## 8. Sources
- Live `curl` against `https://apis.naver.com/nng_main/nng_comment_api/v1/type/CHANNEL_POST/id/{channelId}/comments` (primary, all field values above).
- Unofficial client libraries cross-referenced for the comment/CHANNEL_POST model:
  [kimcore/chzzk](https://github.com/kimcore/chzzk),
  [R2turnTrue/chzzk4j](https://github.com/R2turnTrue/chzzk4j),
  [jonghwanhyeon/python-chzzk](https://github.com/jonghwanhyeon/python-chzzk),
  [dokdo2013/awesome-chzzk](https://github.com/dokdo2013/awesome-chzzk).
