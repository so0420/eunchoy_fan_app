package com.so0420.eunchoy.data.update

import com.so0420.eunchoy.BuildConfig
import com.so0420.eunchoy.data.Config
import com.so0420.eunchoy.data.model.UpdateInfo
import com.so0420.eunchoy.data.net.GithubRelease
import com.so0420.eunchoy.data.net.Net

/** Checks GitHub Releases for a newer APK than the installed build. */
object UpdateChecker {

    /** Returns an [UpdateInfo] if a newer release with an APK asset exists, else null. */
    suspend fun check(): UpdateInfo? = runCatching {
        val release = Net.json.decodeFromString<GithubRelease>(Net.getText(Config.releasesApiUrl()))
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        if (release.draft || release.prerelease || apk == null) {
            null
        } else {
            val remote = release.tagName.trim().trimStart('v', 'V')
            if (isNewer(remote, BuildConfig.VERSION_NAME)) {
                UpdateInfo(version = remote, downloadUrl = apk.browserDownloadUrl, notes = release.body.trim())
            } else {
                null
            }
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
