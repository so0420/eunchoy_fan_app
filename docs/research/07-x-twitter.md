# X / Twitter — @Eun_choy: Viewing + Best-Effort New-Tweet Detection (No Paid API)

**Target account:** `@Eun_choy` (display name 은초이) — VTuber, also on Chzzk channel `fd8516eb8d31a8a5147e94c281ae3f07`.
**Research date:** 2026-06-28. All HTTP results below were obtained live with `curl` on this date.
**Mandatory request header for every call** (X/Nitter/CDN all behave differently for non-browser UAs):

```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36
```

> TL;DR: For **reading**, embed `https://x.com/Eun_choy` in a WebView (expect a login modal after a few posts). For **new-tweet push detection**, the only bridge that actually returned valid, current RSS today is **`https://nitter.net/Eun_choy/rss`**. Everything else (RSSHub public, xcancel RSS, other Nitter instances, the embed/syndication timeline endpoints) is dead, challenge-walled, or gated. Design for graceful degradation: WebView always works; RSS polling is a best-effort add-on behind a feature flag with a configurable, swappable bridge URL.

---

## 1. WebView Embed URL for Reading the Timeline

### Live behavior verified today (2026-06-28)

| URL | HTTP | Notes |
|-----|------|-------|
| `https://x.com/Eun_choy` | **200** (85 KB JS shell) | Canonical. Serves SPA shell; tweets render via JS. |
| `https://twitter.com/Eun_choy` | 301 → `https://x.com/Eun_choy` | Legacy domain redirects to x.com. |
| `https://mobile.twitter.com/Eun_choy` | 302 → `https://twitter.com/Eun_choy` (→ then 301 → x.com) | **Do not use** — double redirect, deprecated host. |
| `https://platform.twitter.com` embed timeline widget | Deprecated | See below. |

### Recommendation: `https://x.com/Eun_choy` in a WebView

- `mobile.twitter.com` is fully retired (chains two redirects to x.com). `twitter.com` 301s to `x.com`. Use the final canonical URL directly: **`https://x.com/Eun_choy`**.
- The profile page is a client-rendered SPA. A plain WebView with JavaScript enabled renders it fine.

### The login wall (verified via research, expected behavior)

- Since ~April 2025, X shows logged-out visitors only a **few posts**, then forces a **login/sign-up modal** that blocks further scrolling. Individual **tweet permalink** URLs (`https://x.com/Eun_choy/status/<id>`) tend to render more fully when opened directly.
- There is **no reliable, supported logged-out timeline**. Plan the UX around "you can preview recent posts; tap to open in the X app / browser for the full thread."

### Twitter embed timeline widget — DO NOT use

- `https://syndication.twitter.com/srv/timeline-profile/screen-name/Eun_choy` returns 200 but the embedded JSON now has **`timeline.entries: []`** (empty) — the widget no longer returns tweets for logged-out embedders.
- `https://cdn.syndication.twimg.com/timeline/profile?screen_name=Eun_choy` returns **empty body** (0 bytes).
- The classic `platform.twitter.com` embedded-timeline widget is officially **deprecated** and only renders for logged-in users; logged-out embeds show "Nothing to see here." Not viable.

### Nitter as a reading fallback in WebView

- `https://nitter.net/Eun_choy` renders a clean, logged-out, JS-light HTML timeline (no login wall). Good **secondary** reading surface, but instance liveness is volatile (see §2). Treat as best-effort, not primary.

### Android WebView config notes (Kotlin)

```kotlin
webView.settings.apply {
    javaScriptEnabled = true            // x.com SPA needs JS
    domStorageEnabled = true            // required; x.com uses localStorage
    userAgentString = DESKTOP_OR_MOBILE_CHROME_UA  // some flows behave better with a real Chrome UA
    loadsImagesAutomatically = true
    mediaPlaybackRequiresUserGesture = true
}
// Keep navigation inside the app for x.com hosts; hand off status permalinks to the X app if installed.
webView.webViewClient = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
        val host = req.url.host ?: return false
        return if (host.endsWith("x.com") || host.endsWith("twitter.com")) false else {
            startActivity(Intent(Intent.ACTION_VIEW, req.url)); true
        }
    }
}
```

- Consider a "Open in X app" deep link: `Intent(ACTION_VIEW, Uri.parse("twitter://user?screen_name=Eun_choy"))` with a browser fallback to `https://x.com/Eun_choy`.

---

## 2. New-Tweet Detection — Live Bridge Test Results (2026-06-28)

Each row was curled live today with the required browser UA (unless noted).

| Bridge / endpoint | HTTP | Result | Usable? |
|---|---|---|---|
| **`https://nitter.net/Eun_choy/rss`** | **200**, `application/rss+xml`, ~29.5 KB | **Valid RSS, 20 real items**, newest `Wed, 20 May 2026 04:07:34 GMT`. 5/5 sequential requests succeeded. | **YES — primary** |
| `https://nitter.privacyredirect.com/Eun_choy/rss` | 200, `text/html` | Anubis "Making sure you're not a bot!" challenge page (not RSS). | No |
| `https://nitter.tiekoetter.com/Eun_choy/rss` | 200, `text/html` | Same Anubis bot-challenge page. | No |
| `https://nitter.poast.org/Eun_choy/rss` | 403 | Blocked. | No |
| `https://nitter.space/Eun_choy/rss` | 403 | Cloudflare block. | No |
| `https://lightbrd.com/Eun_choy/rss` | 403 | Cloudflare block. | No |
| `https://nitter.kuuro.net/Eun_choy/rss` | 404 | Not serving this path. | No |
| `https://nitter.privacydev.net/...` | 000 | Connection failed (host down/unreachable). | No |
| `https://nitter.lucabased.xyz/...` | 000 | Connection failed. | No |
| `https://nitter.holo-mix.com/...` | 000 | Connection failed. | No |
| `https://nitter.net.ipv6.army/...` | 000 | Connection failed. | No |
| `https://xcancel.com/Eun_choy/rss` | 302 → `https://rss.xcancel.com/Eun_choy/rss` | Browser UA → 400 "This URL only works inside an RSS client." Feed-reader UA → 200 but body is a **whitelist-required placeholder feed** (must email `rss [AT] xcancel [DOT] com` with an ID to get a reader whitelisted). Real tweets NOT served out-of-the-box. | No (gated) |
| `https://xcancel.com/Eun_choy` (HTML) | 403 | Blocked. | No |
| `https://rsshub.app/twitter/user/Eun_choy` (public instance) | 302 → `https://google.com/404` | Route **disabled** on the public demo instance. | No |
| `https://syndication.twitter.com/srv/timeline-profile/screen-name/Eun_choy` | 200 | `timeline.entries: []` — empty, gated. | No |
| `https://cdn.syndication.twimg.com/timeline/profile?screen_name=Eun_choy` | 200, 0 bytes | Empty. | No |

### Verified working: nitter.net RSS structure

`GET https://nitter.net/Eun_choy/rss` (send the browser UA). Returns RSS 2.0:

```
rss > channel
  ├─ title            "은초이 / @Eun_choy"
  ├─ link             "https://nitter.net/Eun_choy"
  ├─ image > url      avatar, proxied: https://nitter.net/pic/pbs.twimg.com%2Fprofile_images%2F...
  ├─ ttl              40
  └─ item   (×20, newest first)
       ├─ title       tweet text, plain (e.g. "3d초이 마니 사랑해죠🩵")
       ├─ dc:creator  "@Eun_choy"   (becomes RT author for retweets)
       ├─ description  CDATA HTML: <p>…</p> plus <img src="https://nitter.net/pic/media%2F…jpg"> ;
       │               quote-tweets render as nested <blockquote>
       ├─ pubDate     RFC-822, e.g. "Wed, 20 May 2026 04:07:34 GMT"
       ├─ guid        isPermaLink="false", value = numeric tweet **snowflake ID**
       │               e.g. 2056949932809207877
       └─ link        "https://nitter.net/Eun_choy/status/2056949932809207877#m"
```

**Key for detection:** `<guid>` is the tweet snowflake ID. Snowflake IDs are **monotonically increasing**, so "newer than X" = `id > lastSeenId` (use a 64-bit `Long`/`BigInteger` comparison, not string). Strip the trailing `#m` and rewrite the host to `x.com` to produce a canonical permalink: `https://x.com/Eun_choy/status/<id>`.

> Freshness caveat: newest item today is dated **2026-05-20**. That is the account's genuine last post (no tweets since), not staleness — the feed reflects the live account.

### Bonus verified endpoint: per-tweet hydration via react-tweet CDN

`GET https://cdn.syndication.twimg.com/tweet-result?id=<id>&lang=en&token=<token>` **works** (200, `application/json`, ~2 KB) and returns full tweet JSON:

```json
{ "__typename":"Tweet", "favorite_count":5, "lang":"ko",
  "created_at":"2026-05-20T04:07:34.000Z", "text":"…",
  "id_str":"2056949932809207877",
  "entities":{"media":[{"expanded_url":"https://x.com/Eun_choy/status/2056949932809207877/photo/1", …}]} }
```

The `token` is required and is derived from the id (react-tweet algorithm):

```
token = ((Number(id) / 1e15) * Math.PI).toString(36).replace(/(0+|\.)/g, "")
// id 2056949932809207877  ->  token "4zi3k1i81s"
```

Kotlin equivalent:
```kotlin
fun syndicationToken(id: Long): String {
    val v = (id.toDouble() / 1e15) * Math.PI
    return java.math.BigDecimal(v).toString()
        .let { toBase36(v) }            // implement base-36 of the double
        .replace(Regex("(0+|\\.)"), "")
}
```
**Limitation:** this endpoint hydrates a **known** tweet id — it cannot *discover* new tweets (the profile-timeline variant is empty/gated). Use it only to enrich/verify items found via RSS, or to render a tapped tweet without the WebView.

### Flakiness summary

- **nitter.net** was the single reliable source today (5/5 OK, full feed). Historically Nitter instances die or get Cloudflare/Anubis-walled with little notice — treat it as **best-effort, expected to break**.
- Public **RSSHub** Twitter route is disabled on the demo host; a **self-hosted** RSSHub works only if you supply `TWITTER_AUTH_TOKEN` (auth_token cookies from a logged-in web session) and is prone to sudden 403s. Out of scope for a no-account app unless you run your own infra.
- **xcancel** requires manual per-reader email whitelisting. Not automatable.

---

## 3. Recommended Graceful-Degradation Design

**Principle:** WebView reading is always available and never depends on a bridge. New-tweet push is an **optional, best-effort** layer behind a swappable bridge URL, so when (not if) a bridge dies, the app degrades to "open the WebView" instead of breaking.

### Tier model

1. **Tier 0 — Reading (always on):** WebView → `https://x.com/Eun_choy`. UX copes with the login wall (preview + "open in X"). Nitter HTML as optional secondary reader.
2. **Tier 1 — Push detection (best-effort, flagged):** Poll a configured RSS bridge. Default `https://nitter.net/Eun_choy/rss`; the base URL/template is **remote-configurable** (e.g. Firebase Remote Config / a small JSON you host) so you can hot-swap instances when one dies without shipping an app update.
3. **Tier 2 — Enrichment (optional):** For a tweet id found in RSS, optionally call `cdn.syndication.twimg.com/tweet-result` to render rich content in-app.

### Polling + new-tweet detection logic

```kotlin
data class FeedItem(val id: Long, val text: String, val publishedAt: Instant, val permalink: String)

// Worker (WorkManager periodic, e.g. 15–30 min; respect Doze / battery):
suspend fun poll(): List<FeedItem> {
    val xml = bridgeApi.getRss()                 // try configured bridge
    val items = parseRss(xml)                     // XmlPullParser; ignore non-RSS bodies
    val fresh = items.filter { it.id > prefs.lastSeenId }
    if (fresh.isNotEmpty()) {
        prefs.lastSeenId = items.maxOf { it.id }  // store as Long
        notifyNewTweets(fresh)
    }
    return fresh
}
```

- **Dedup/ordering:** persist `lastSeenId` (Long). New = `guid > lastSeenId`. On first run, seed `lastSeenId` with the current newest id and emit nothing (avoid a backlog notification storm).
- **Validate the body:** a 200 can still be an Anubis/Cloudflare HTML challenge. Reject any response whose body doesn't start with `<?xml`/`<rss` or whose channel `<title>` contains "not a bot"/"not yet whitelisted". On rejection, mark the bridge unhealthy.
- **Health tracking & fallback:** keep an ordered list of candidate bridge URLs (remote-configured). On N consecutive failures/invalid bodies, advance to the next; if all fail, disable Tier 1 and surface a quiet "live tweet alerts paused — tap to open X" state. Never crash or spam.
- **Backoff:** exponential backoff on failures; honor `<ttl>` (nitter.net advertised `ttl` 40 min) as a minimum interval. Don't poll faster than ~15 min to avoid getting the instance rate-limited/banned.

### Kotlin / Retrofit / OkHttp notes

- **Always set the browser UA** via an OkHttp interceptor — Nitter and the CDN behave differently without it:
  ```kotlin
  val client = OkHttpClient.Builder().addInterceptor { chain ->
      chain.proceed(chain.request().newBuilder()
          .header("User-Agent",
              "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
              "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
          .build())
  }.build()
  ```
- **RSS parsing:** the bodies are XML, not JSON. Use Android's `XmlPullParser` (`Xml.newPullParser()`) or `SimpleXmlConverterFactory`/`tikxml` — **not** Gson/Moshi. Keep Retrofit returning `ResponseBody`/`String` and parse manually so you can also detect challenge HTML.
- **Make the bridge base URL injectable** (constructor/Remote-Config value) rather than a compile-time `@GET` path, so instances can be swapped at runtime:
  ```kotlin
  interface RssApi { @GET fun getRss(@Url url: String): Call<ResponseBody> }
  // call api.getRss(remoteConfig.bridgeUrl)  e.g. "https://nitter.net/Eun_choy/rss"
  ```
- **Tweet hydration call** (Tier 2): plain `@GET` to `https://cdn.syndication.twimg.com/tweet-result` with query params `id`, `lang=en`, `token` (compute per the algorithm above); parse JSON with Moshi (`__typename`, `text`, `created_at`, `favorite_count`, `entities.media[].expanded_url`, `id_str`).
- **Background scheduling:** `WorkManager` `PeriodicWorkRequest` (min 15 min) with a `NetworkType.CONNECTED` constraint; show a low-priority notification channel for new-tweet alerts; coalesce multiple new tweets into one notification.

### Failure-mode matrix (what the app should do)

| Condition | App behavior |
|---|---|
| Bridge 403 / 000 / challenge HTML | Mark unhealthy, try next bridge, else pause Tier 1 silently. |
| All bridges down | Tier 1 off; banner "live alerts paused"; Tier 0 WebView still works. |
| x.com login wall in WebView | Show preview + "Open in X app / browser" CTA. |
| New tweet found | One notification (coalesced) → tap opens `https://x.com/Eun_choy/status/<id>`. |

---

## Appendix — Exact commands used (reproduce/verify)

```bash
UA='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
curl -s -H "User-Agent: $UA" 'https://nitter.net/Eun_choy/rss'                 # 200, valid RSS, 20 items
curl -s -H "User-Agent: $UA" 'https://x.com/Eun_choy'                          # 200 SPA shell
curl -s -H "User-Agent: $UA" 'https://rsshub.app/twitter/user/Eun_choy' -D -   # 302 -> google.com/404 (dead)
curl -s -H "User-Agent: $UA" \
  'https://cdn.syndication.twimg.com/tweet-result?id=2056949932809207877&lang=en&token=4zi3k1i81s'  # 200 JSON
```

**Uncertain / time-sensitive (re-verify before launch):**
- Nitter instance liveness changes weekly; `nitter.net` working today is not a guarantee. Keep the bridge list remote-configurable.
- The `tweet-result` token algorithm and endpoint are unofficial (react-tweet internals) and can change without notice.
- The exact number of posts X shows before the login wall, and whether `x.com/Eun_choy` ever renders fully logged-out, can vary by region/session and over time.
