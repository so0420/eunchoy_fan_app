# Naver Cafe — Member Article List + Full Article Content (Mobile/`apis.naver.com`)

Research target: Cafe **은초이** (`eunchoy`), `cafeId = 31466153`,
member `memberKey = eD2ZYpSl1N5IUVBze12Ni2qk3fbd9TnEpAP8-RvcKD8` (nick "은초이", the cafe manager / streamer).

All endpoints below were probed **live** with `curl` on 2026-06-27. Every request **must** send a
desktop/mobile browser `User-Agent`; `apis.naver.com` returns `403 Forbidden` (plain Apache HTML) to
some paths and behaves inconsistently without it.

```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
```

Status legend used below: **VERIFIED** = observed in a live response; **LOGIN-GATED (unverified body)** =
endpoint confirmed to exist but full success body requires Naver login cookies which we did not have.

---

## 0. TL;DR / Authentication model

- **Login = Naver session cookies `NID_AUT` and `NID_SES`** (set on domain `.naver.com`).
  There is **no** API key / OAuth for these internal `apis.naver.com/cafe-web/*` endpoints — they are the
  same XHR endpoints the mobile web client calls, authenticated purely by cookie.
- Obtain the cookies via an **in-app WebView login** at `https://nid.naver.com/nidlogin.login`
  (or just load `https://m.cafe.naver.com/...` and let the user log in), then harvest
  `NID_AUT` + `NID_SES` from the WebView `CookieManager` and replay them on the `apis.naver.com` calls.
- **Three visibility tiers observed:**
  | Article `isOpen` | Content API without login | Notes |
  |---|---|---|
  | `true` (전체공개 / open) | **Full content returned** ✅ | No cookies needed |
  | `false` (member-only) | `errorCode 0004 "로그인하지 않았습니다."` ❌ | Needs `NID_AUT`/`NID_SES` + cafe membership |
  | deleted / missing | `errorCode 4003 "삭제되었거나 존재하지 않는 게시글입니다."` | — |
- The **member article LIST V3 endpoint requires login for ANY data** (returns `status 500 / code 0004`
  even though the member page is otherwise public). A **public list fallback** exists (`ArticleListV2dot1.json`,
  §2) that returns articles for the whole cafe — each row carries `memberKey`, so you can client-side
  filter to one member without login.

---

## 1. Member article list — `CafeMemberNetworkArticleListV3`  (LOGIN-GATED)

This is the API backing the member page
`https://cafe.naver.com/f-e/cafes/31466153/members/eD2ZYpSl1N5IUVBze12Ni2qk3fbd9TnEpAP8-RvcKD8`.

**Request**
```
GET https://apis.naver.com/cafe-web/cafe-mobile/CafeMemberNetworkArticleListV3
      ?search.cafeId=31466153
      &search.memberKey=eD2ZYpSl1N5IUVBze12Ni2qk3fbd9TnEpAP8-RvcKD8
      &search.perPage=15
      &search.page=1
      &requestFrom=A
Headers:
  User-Agent: <browser UA, required>
  Cookie: NID_AUT=<...>; NID_SES=<...>        # REQUIRED — without it: error 0004
  Referer: https://cafe.naver.com/            # recommended
```

**VERIFIED response when NOT logged in** (HTTP 200, JSON body):
```json
{ "message": { "status": "500",
               "error": { "code": "0004", "msg": "로그인하지 않았습니다" } } }
```
So: **every field on this endpoint is login-gated.** There is no partial/anonymous data.

**Success envelope (LOGIN-GATED — body not directly observed; shape inferred from sibling list APIs):**
- `message.status` = `"200"`
- `message.error.code` / `message.error.msg` = empty on success
- `message.result.articleList[]` — array of article rows
- `message.result.hasNext` (boolean) for pagination

**Expected per-row fields** (the task spec lists `articleid, subject, writeDateTimestamp, writernickname,
readCount, commentCount`). The exact JSON key **casing for V3 could not be verified live** because the
endpoint is fully login-gated. The closely-related **public** list API (`ArticleListV2dot1.json`, §2 —
VERIFIED) returns the same conceptual data with **camelCase** keys:
`articleId`, `subject`, `writeDateTimestamp`, `writerNickname`, `readCount`, `commentCount`, `memberKey`.
**Recommendation:** model V3 with camelCase keys (matches every other observed cafe-web list API), and if a
live login test shows lowercase (`articleid`/`writernickname`), add `@SerializedName` aliases.

> Practical guidance: because V3 needs login but §2 does not, prefer the **§2 public list filtered by
> `memberKey`** for the "member's articles" feature when the user is logged out, and use V3 only when you
> already hold session cookies (it is authoritative and includes member-only posts).

---

## 2. Public cafe article list — `ArticleListV2dot1.json`  (VERIFIED, NO LOGIN)

Use this as the no-login fallback. Each row contains `memberKey`, so filter client-side to one member.

**Request (VERIFIED, returns data with no cookies):**
```
GET https://apis.naver.com/cafe-web/cafe2/ArticleListV2dot1.json
      ?search.clubid=31466153
      &search.queryType=lastArticle      # newest-first across the whole cafe
      &search.page=1
      &search.perPage=40
      # &search.menuid=<boardId>          # optional: restrict to one board
Headers: User-Agent: <browser UA, required>
```
(Note: the legacy sibling `ArticleList.json` returns **403 Forbidden** — use the `V2dot1` path.)

**VERIFIED response (truncated, real values):**
```json
{
  "message": {
    "status": "200",
    "error": { "code": "", "msg": "" },
    "result": {
      "cafeId": 31466153,
      "cafeName": "은초이",
      "cafeStaff": false,
      "cafeMember": false,
      "hasNext": true,
      "articleList": [
        {
          "cafeId": 31466153,
          "articleId": 1722,
          "refArticleId": 1722,
          "menuId": 26,
          "menuName": "🩵방송 on!",
          "menuType": "B",
          "boardType": "L",
          "subject": "일요일 게릴라니까... 지금도 가능한가....",
          "memberKey": "eD2ZYpSl1N5IUVBze12Ni2qk3fbd9TnEpAP8-RvcKD8",
          "maskedMemberId": "eun_****",
          "writerNickname": "은초이",
          "memberLevel": 999,
          "memberLevelIconId": 5,
          "profileImage": "",
          "newArticle": true,
          "openArticle": true,
          "blindArticle": false,
          "marketArticle": false,
          "attachImage": false,
          "attachMusic": false,
          "attachMovie": false,
          "attachFile": false,
          "popular": false,
          "enableComment": true,
          "hasNewComment": true,
          "readCount": 9,
          "commentCount": 18,
          "likeItCount": 2,
          "writeDateTimestamp": 1782586673757,
          "lastCommentedTimestamp": 1782588925194
        }
        /* ... */
      ]
    }
  }
}
```

**VERIFIED field paths** (per `message.result.articleList[i]`):
| JSON path | Type | Example | Notes |
|---|---|---|---|
| `articleId` | int | `1722` | use as `{ARTICLE_ID}` for the content API (§3) |
| `subject` | string | `"일요일 게릴라니까..."` | title (HTML-entity decoded already) |
| `writeDateTimestamp` | long (epoch **millis**) | `1782586673757` | not seconds — divide by 1000 for `Date` |
| `writerNickname` | string | `"은초이"` | |
| `memberKey` | string | `"eD2ZYp...RvcKD8"` | **filter on this** to get one member's posts |
| `maskedMemberId` | string | `"eun_****"` | masked login id |
| `readCount` | int | `9` | |
| `commentCount` | int | `18` | |
| `likeItCount` | int | `2` | |
| `openArticle` | bool | `true` | `false` → content API needs login (§3) |
| `menuId` / `menuName` | int / string | `26` / `"🩵방송 on!"` | board id/name |
| `attachImage` / `attachFile` / `attachMovie` | bool | `false` | presence flags |
| `lastCommentedTimestamp` | long (millis) | `1782588925194` | |
| `boardType` | string | `"L"` | L=list board |
- Envelope: `message.result.hasNext` (bool) drives pagination; `message.result.cafeName` = `"은초이"`.

---

## 3. Full article CONTENT — `cafe-articleapi/v3` (VERIFIED for open articles)

**Request**
```
GET https://apis.naver.com/cafe-web/cafe-articleapi/v3/cafes/31466153/articles/{ARTICLE_ID}
      ?query=
      &useCafeId=true
      &requestFrom=A
Headers:
  User-Agent: <browser UA, required>
  Cookie: NID_AUT=<...>; NID_SES=<...>   # REQUIRED only for member-only (isOpen=false) articles
```
Example used: `{ARTICLE_ID}=1717` (open) and `1722` (open, by target member) → full body returned with **no
cookies**. `1721` (`openArticle:false`) → `errorCode 0004`. `1` (missing) → `errorCode 4003`.

**Error bodies (VERIFIED):**
```json
// not logged in / member-only:
{"result":{"errorCode":"0004","reason":"로그인하지 않았습니다.",
           "more":{"cafeUrl":"eunchoy","cafeName":"은초이","cafeId":31466153}}}
// deleted / nonexistent:
{"result":{"errorCode":"4003","reason":"삭제되었거나 존재하지 않는 게시글입니다.",
           "more":{"cafeUrl":"eunchoy","cafeName":"은초이","cafeId":31466153}}}
```
> Detect errors by presence of `result.errorCode` (string). On success there is **no** `errorCode`; the
> payload is `result.article`.

**VERIFIED success envelope — top-level `result` keys:**
`cafeId, articleId, pageId, pageGroupId, heads, article, comments, advert, cafe, user, attaches, tags,
authority, editorVersion, readOnlyModeInfo, articleRegion, standardReportPopup, commAdSupport,
cafeStatList, isReadOnlyMode, isW800`

### 3a. `result.article` (the post itself) — VERIFIED keys
`id, refArticleId, menu, subject, writer, subscribeWriter, writeDate, readCount, commentCount,
repostCount, decorator, existScrapAddContent, template, contentHtml, customElements, gdid,
replyListOrder, self, isNotice, isNewComment, isDeleteParent, isMarket, isGroupPurchase,
isPersonalTrade, isReadable, isBlind, isOpen, isSearchOpen, isEnableScrap, scrapCount,
isEnableExternal, isEnableSocialPlugin, isWriteComment, isAutoSourcing`

| Field path | Type | Example | Notes |
|---|---|---|---|
| `result.article.id` | int | `1722` | article id |
| `result.article.subject` | string | `"일요일 게릴라니까..."` | title |
| `result.article.contentHtml` | string (HTML) | see §3d | **the body** — SmartEditor (SE) HTML, images inline |
| `result.article.writeDate` | long (millis) | `1782586673757` | |
| `result.article.readCount` | int | `9` | |
| `result.article.commentCount` | int | `18` | |
| `result.article.isOpen` | bool | `true` | open vs member-only |
| `result.article.isReadable` | bool | `true` | false → blocked |
| `result.article.isBlind` | bool | `false` | |
| `result.article.gdid` | string | `"90000004_01E022A9000006BA00000000"` | global doc id |
| `result.article.menu` | object | `{id:26,name:"🩵방송 on!",menuType:"B",boardType:"L"}` | board (name HTML-entity encoded e.g. `&#129653;`) |

### 3b. `result.article.writer` (author) — VERIFIED keys
`memberKey, baMemberKey, nick, image, memberLevel, memberLevelName, memberLevelIconUrl,
currentPopularMember, allowMemberAlarm, isCafeMember`

| Field path | Type | Example |
|---|---|---|
| `result.article.writer.memberKey` | string | `"eD2ZYpSl1N5IUVBze12Ni2qk3fbd9TnEpAP8-RvcKD8"` |
| `result.article.writer.nick` | string | `"은초이"` |
| `result.article.writer.image.url` | string | `"https://cafeptthumb-phinf.pstatic.net/.../은초이님.png"` (or default `https://ssl.pstatic.net/static/cafe/cafe_pc/default/cafe_profile_77.png`) |
| `result.article.writer.memberLevelName` | string | `"카페매니저"` |
| `result.article.writer.memberLevelIconUrl` | string | `"https://cafe.pstatic.net/levelicon/1/5_999.gif"` |

### 3c. Images & attachments — VERIFIED
- **Inline images live INSIDE `contentHtml`** as SmartEditor (`se-image`) components, e.g.:
  ```html
  <div class="se-component se-image se-l-default" id="SE-e7505e7f-...">
    ...<img src="https://cafeptthumb-phinf.pstatic.net/<dir>/<hash>.JPEG/Screenshot_20260625_191508_NAVER.jpg?type=w1600"
            alt="" class="se-image-resource" />
  ```
  → To extract image URLs without rendering, regex `<img[^>]+class="se-image-resource"[^>]+src="([^"]+)"`.
  The `?type=w1600` query is a resize directive (`w773`, `w800`, `w1600`, etc. all valid).
- **`result.attaches`** (array, top-level of `result`) holds **downloadable file attachments** (not inline
  images). **VERIFIED empty `[]`** for the image-only post 1717. For posts with `attachFile:true` expect
  objects with name/size/url here (exact shape not captured — mark **uncertain**). Download is governed by
  `result.authority.isEnableAttachFileDownload` (VERIFIED `true`).
- `result.tags` = `[]` (VERIFIED empty here).

### 3d. `contentHtml` example (VERIFIED, article 1722, text-only)
```html
<div class="se-viewer se-theme-default" lang="ko-KR">
  <div class="se-main-container">
    <div class="se-component se-text se-l-default" id="SE-71a8118c-...">
      ...<p class="se-text-paragraph ..."><span ...>ㅎㅎㅎㅎㅎ.ㅎ.ㅎ.ㅎ.ㅎ.ㅎ..ㅎ.ㅎㅎㅎㅎ.ㅎ.ㅎ.ㅎ..!!!</span></p>...
    </div>
  </div>
</div>
```
This is a **full standalone SmartEditor document** — render it directly in a WebView with the SE viewer CSS,
or strip tags for a text preview. Korean text is UTF-8; some attributes use HTML entities.

### 3e. `result.user` (the *caller's* session) & `result.authority` — VERIFIED, useful for gating
- `result.user.isLogin` (bool) — **VERIFIED `false` for anonymous**. Use this to detect whether your
  cookies were accepted.
- `result.user.isCafeMember`, `result.user.memberKey`, `result.user.permission.{isCafeManager,...}`.
- `result.authority.{isWriteComment, isEnableAttachFileDownload, isSharable, ...}` — UI gating flags.

### 3f. `result.comments` — VERIFIED keys
- `result.comments.items[]`, `result.comments.alarm`, `result.comments.disableWriteReason`.
- Comment item keys: `id, refId, writer, content, updateDate, memberLevel, memberLevelName,
  bestComment, isArticleWriter, isDeleted, isNew, ...`. `content` is the comment text; `updateDate` is
  epoch **millis**. (Returned inline with the article — no separate call needed for the first page.)

---

## 4. Canonical mobile article URL (WebView fallback) — VERIFIED 200

Use when content is login-gated, SE rendering is complex, or the API errors. `cafeUrl` for this cafe is
**`eunchoy`** (from `result.cafe.url` / error `more.cafeUrl`).

- **Preferred (new mobile SPA):**
  `https://m.cafe.naver.com/ca-fe/web/cafes/eunchoy/articles/{ARTICLE_ID}`  → HTTP 200 (`text/html`)
- **Legacy (still 200, uses numeric ids — handy when you only have cafeId):**
  `https://m.cafe.naver.com/ArticleRead.nhn?clubid=31466153&articleid={ARTICLE_ID}`
- PC equivalent (the member/article SPA): `https://cafe.naver.com/f-e/cafes/31466153/articles/{ARTICLE_ID}`

Load these in the **same WebView that holds the login cookies** so member-only posts render. Member page to
embed for "all of this member's posts": the original
`https://cafe.naver.com/f-e/cafes/31466153/members/eD2ZYpSl1N5IUVBze12Ni2qk3fbd9TnEpAP8-RvcKD8`.

---

## 5. Kotlin / Retrofit notes

**Cookie capture from WebView (after user logs in):**
```kotlin
val cm = CookieManager.getInstance()
val raw = cm.getCookie("https://m.cafe.naver.com")   // "NID_AUT=...; NID_SES=...; ..."
// keep only NID_AUT & NID_SES; send back via an OkHttp Interceptor:
val client = OkHttpClient.Builder()
  .addInterceptor { ch ->
    ch.proceed(ch.request().newBuilder()
      .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
      .header("Referer", "https://cafe.naver.com/")
      .apply { if (raw != null) header("Cookie", raw) }
      .build())
  }.build()
```

**Retrofit interfaces** (note: `apis.naver.com` ignores the `Accept` header for JSON; it always returns
`application/json;charset=utf-8`. Dots in query keys are fine as literal `@Query` names.):
```kotlin
interface NaverCafeApi {
  // §3 — single article content (open posts work without cookies)
  @GET("cafe-web/cafe-articleapi/v3/cafes/{cafeId}/articles/{articleId}")
  suspend fun getArticle(
    @Path("cafeId") cafeId: Long,
    @Path("articleId") articleId: Long,
    @Query("query") query: String = "",
    @Query("useCafeId") useCafeId: Boolean = true,
    @Query("requestFrom") requestFrom: String = "A",
  ): ArticleResponse

  // §1 — member list (REQUIRES NID_AUT/NID_SES)
  @GET("cafe-web/cafe-mobile/CafeMemberNetworkArticleListV3")
  suspend fun getMemberArticles(
    @Query("search.cafeId") cafeId: Long,
    @Query("search.memberKey") memberKey: String,
    @Query("search.perPage") perPage: Int = 15,
    @Query("search.page") page: Int = 1,
    @Query("requestFrom") requestFrom: String = "A",
  ): MemberListResponse

  // §2 — public list fallback (NO login); filter rows by memberKey client-side
  @GET("cafe-web/cafe2/ArticleListV2dot1.json")
  suspend fun getCafeArticles(
    @Query("search.clubid") clubId: Long,
    @Query("search.queryType") queryType: String = "lastArticle",
    @Query("search.page") page: Int = 1,
    @Query("search.perPage") perPage: Int = 40,
  ): PublicListResponse
}

// Base URL: https://apis.naver.com/
```

**DTO sketch (use Gson/Moshi; `@SerializedName` shown where casing matters):**
```kotlin
// §3
data class ArticleResponse(val result: ArticleResult)
data class ArticleResult(
  val errorCode: String? = null,          // non-null => error (0004 login, 4003 missing)
  val reason: String? = null,
  val article: Article? = null,
  val cafe: Cafe? = null,
  val user: SessionUser? = null,
  val attaches: List<Attach> = emptyList()
)
data class Article(
  val id: Long, val subject: String, val contentHtml: String,
  val writeDate: Long, val readCount: Int, val commentCount: Int,
  val isOpen: Boolean, val isReadable: Boolean, val isBlind: Boolean,
  val writer: Writer, val menu: Menu
)
data class Writer(val memberKey: String, val nick: String, val image: ImageRef,
                  val memberLevelName: String?, val memberLevelIconUrl: String?)
data class ImageRef(val url: String, val service: String?, val type: String?)
data class Menu(val id: Long, val name: String, val boardType: String)
data class SessionUser(val isLogin: Boolean, val isCafeMember: Boolean, val memberKey: String?)
data class Attach(val name: String? = null, val url: String? = null) // shape UNCERTAIN

// §1 / §2 — both wrap data in `message`
data class PublicListResponse(val message: Message)
data class Message(val status: String, val error: ApiError?, val result: ListResult?)
data class ApiError(val code: String, val msg: String)
data class ListResult(val cafeName: String?, val hasNext: Boolean,
                      val articleList: List<ArticleRow> = emptyList())
data class ArticleRow(
  @SerializedName("articleId") val articleId: Long,   // V3 may emit "articleid" — add alias if needed
  val subject: String,
  val writeDateTimestamp: Long,                        // epoch MILLIS
  @SerializedName("writerNickname") val writerNickname: String, // V3 may emit "writernickname"
  val memberKey: String, val readCount: Int, val commentCount: Int,
  val openArticle: Boolean, val attachImage: Boolean
)
```

**Implementation tips**
- All timestamps are **epoch milliseconds** (`writeDate`, `writeDateTimestamp`, `lastCommentedTimestamp`,
  comment `updateDate`).
- Error detection: §3 → check `result.errorCode != null`; §1/§2 → check `message.status != "200"`.
- For "member's posts" feature: try §1 (V3) when logged in (authoritative, includes member-only); else
  page §2 and filter `articleList` where `memberKey == target`. Note §2 is cafe-wide newest-first, so you
  may page several times to collect enough of one member's posts.
- Render `contentHtml` in a `WebView` (`loadDataWithBaseURL("https://cafe.naver.com", html, "text/html",
  "UTF-8", null)`) for fidelity, or extract `<img class="se-image-resource" src>` for a media-only view.
- Member-only posts: open §4 mobile URL in the cookie-bearing WebView instead of fighting the API.

---

## 6. Open questions / uncertain items
- **V3 (`CafeMemberNetworkArticleListV3`) success body & exact key casing** — not captured (login-gated).
  Modeled on the VERIFIED `ArticleListV2dot1.json` camelCase keys; verify against a live logged-in call.
- **`result.attaches[]` object shape** — only observed empty `[]`. Capture a post with `attachFile:true`
  (e.g. a `.zip`/`.pdf` attachment) while logged in to confirm field names (name/size/downloadUrl).
- Whether cafe membership (not just any Naver login) is required for specific member-only boards — error
  `0004` is generic "not logged in"; a logged-in-but-non-member case may surface a different code.

## Sources
- Live `curl` against `apis.naver.com` (2026-06-27) — primary, all VERIFIED facts above.
- https://github.com/kasumielf/NaverCafeArticleExtractor — confirms `cafe2/ArticleListV2dot1.json` usage.
- https://github.com/naver/cafe-sdk-ios — official Naver Cafe SDK (native login/session context).
