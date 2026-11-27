package ru.mesh.foss.data.model

import com.google.gson.annotations.SerializedName

/** Релиз GitHub Releases, нужный проверке обновлений. */
data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("prerelease") val prerelease: Boolean = false,
    @SerializedName("draft") val draft: Boolean = false,
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("assets") val assets: List<GitHubAsset>? = null,
)

/** APK-ассет релиза. */
data class GitHubAsset(
    @SerializedName("name") val name: String? = null,
    @SerializedName("browser_download_url") val browserDownloadUrl: String? = null,
)
