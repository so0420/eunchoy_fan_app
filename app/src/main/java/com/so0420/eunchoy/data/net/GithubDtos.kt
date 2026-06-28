package com.so0420.eunchoy.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GitHub "releases/latest" response (only the fields we need). */
@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)
