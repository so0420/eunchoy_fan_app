package com.so0420.eunchoy.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Date parsing/formatting. Naver APIs use KST (UTC+9) with no zone suffix. */
object DateUtil {
    private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    private val SLASH = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val COMPACT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    /** "yyyy-MM-dd HH:mm:ss" (KST) -> Instant. */
    fun parseKstSlash(s: String?): Instant? = s?.let {
        runCatching { LocalDateTime.parse(it, SLASH).atZone(KST).toInstant() }.getOrNull()
    }

    /** "yyyyMMddHHmmss" (KST) -> Instant. */
    fun parseKstCompact(s: String?): Instant? = s?.let {
        runCatching { LocalDateTime.parse(it, COMPACT).atZone(KST).toInstant() }.getOrNull()
    }

    /** ISO-8601 with offset, e.g. YouTube "2026-05-15T08:19:27+00:00". */
    fun parseIso(s: String?): Instant? = s?.let {
        runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
    }

    /** RFC-1123, e.g. Nitter "Wed, 20 May 2026 04:07:34 GMT". */
    fun parseRfc1123(s: String?): Instant? = s?.let {
        runCatching { OffsetDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
            .getOrNull()
    }

    fun fromEpochMillis(ms: Long?): Instant? = ms?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }

    private val DISPLAY = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

    /** Absolute KST timestamp for detail rows. */
    fun formatKst(instant: Instant?): String =
        instant?.atZone(KST)?.format(DISPLAY) ?: ""

    /** "방금 전 / N분 전 / N시간 전 / N일 전 / yyyy.MM.dd" relative to [now]. */
    fun relative(instant: Instant?, now: Instant = Instant.now()): String {
        if (instant == null) return ""
        val sec = (now.epochSecond - instant.epochSecond).coerceAtLeast(0)
        return when {
            sec < 60 -> "방금 전"
            sec < 3600 -> "${sec / 60}분 전"
            sec < 86_400 -> "${sec / 3600}시간 전"
            sec < 7 * 86_400 -> "${sec / 86_400}일 전"
            else -> instant.atZone(KST).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
        }
    }

    /** Seconds -> "H:MM:SS" / "M:SS" for VOD durations. */
    fun duration(totalSec: Int): String {
        if (totalSec <= 0) return ""
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
