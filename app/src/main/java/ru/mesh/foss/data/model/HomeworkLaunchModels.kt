package ru.mesh.foss.data.model

import com.google.gson.annotations.SerializedName

/**
 * Ответ `GET /api/ej/partners/v1/homeworks/launch`.
 *
 * МЭШ может вернуть ссылку как обычную строку, как JSON-объект с одним из
 * перечисленных полей или как 302 с `Location`. Все варианты сводятся к
 * одному URL, который затем открывается в браузере/нативном ЦДЗ.
 */
data class HomeworkLaunchLink(
    @SerializedName("url") val url: String? = null,
    @SerializedName("link") val link: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("redirect") val redirect: String? = null,
    @SerializedName("activity_url") val activityUrl: String? = null,
) {
    /** Первая непустая ссылка; строковое `"null"` из старых ответов отбрасывается. */
    fun firstUrl(): String? = listOf(url, link, location, redirect, activityUrl)
        .firstOrNull { !it.isNullOrBlank() && it != "null" }
}
