package com.so0420.eunchoy.data.update

import com.so0420.eunchoy.BuildConfig
import com.so0420.eunchoy.data.Config
import com.so0420.eunchoy.data.model.UpdateInfo
import com.so0420.eunchoy.data.net.GithubRelease
import com.so0420.eunchoy.data.net.Net

/** Result of an update check — distinguishes "no update" from "couldn't check" (network/rate limit). */
sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data object Failed : UpdateResult
}

/** Checks GitHub Releases for a newer APK. Tries the API, then falls back to the (un-rate-limited) Atom feed. */
object UpdateChecker {

    suspend fun check(): UpdateResult {
        val latest = fetchLatest() ?: return UpdateResult.Failed
        val version = latest.tag.trim().trimStart('v', 'V')
        return if (isNewer(version, BuildConfig.VERSION_NAME)) {
            UpdateResult.Available(UpdateInfo(version, latest.apkUrl, latest.notes))
        } else {
            UpdateResult.UpToDate
        }
    }

    private data class Latest(val tag: String, val apkUrl: String, val notes: String)

    private suspend fun fetchLatest(): Latest? = apiLatest() ?: atomLatest()

    /** Primary: GitHub API (exact asset URL + release notes). Null on error / rate limit (HTTP 403). */
    private suspend fun apiLatest(): Latest? = runCatching {
        val rel = Net.json.decodeFromString<GithubRelease>(Net.getText(Config.releasesApiUrl()))
        val apk = rel.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        if (rel.draft || rel.prerelease || apk == null) {
            null
        } else {
            Latest(rel.tagName, apk.browserDownloadUrl, rel.body.trim())
        }
    }.getOrNull()

    /** Fallback: parse the latest tag from releases.atom and build the conventional APK url. */
    private suspend fun atomLatest(): Latest? = runCatching {
        val xml = Net.getText(Config.releasesAtomUrl())
        val tag = Regex("/releases/tag/([^\"<]+)").find(xml)?.groupValues?.getOrNull(1)
        if (tag.isNullOrBlank()) {
            null
        } else {
            Latest(tag, Config.releaseApkUrl(tag), "새 버전이 있어요. 자세한 변경 내용은 릴리즈 페이지에서 볼 수 있어요.")
        }
    }.getOrNull()

    /** True if [remote] is a strictly newer version than [current] (numeric dot/dash compare). */
    fun isNewer(remote: String, current: String): Boolean {
        val r = parse(remote)
        val c = parse(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun parse(v: String): List<Int> =
        v.split(".", "-", "_").mapNotNull { part -> part.filter(Char::isDigit).toIntOrNull() }
}
