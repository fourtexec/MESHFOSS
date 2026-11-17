package ru.mesh.foss

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Найденное обновление на GitHub Releases. */
data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseUrl: String
)

/**
 * Проверка обновлений через GitHub Releases.
 *
 * Каналы (переключаются в Настройках → Приложение):
 *  - `releases` — только стабильные релизы (теги `vX.Y.Z`);
 *  - `prereleases` — плюс пререлизы (beta/rc-теги).
 *
 * Nightly-сборка (скользящий prerelease `nightly` из main) в проверки не
 * попадает: из неё нет автообновления, её ставят вручную со страницы релизов.
 * Сравнение версий — по семантике версий X.Y.Z[-pre], поэтому теги вида
 * `v2.0.0` обрабатываются корректно.
 */
object UpdateChecker {

    const val CHECK_INTERVAL_MS = 15L * 60L * 1000L

    /** Канал обновлений по умолчанию — только стабильные релизы. */
    const val CHANNEL_RELEASES = "releases"
    const val CHANNEL_PRERELEASES = "prereleases"

    /** Скользящий prerelease из main: никогда не предлагается как обновление. */
    const val NIGHTLY_TAG = "nightly"

    private const val RELEASES_API = "https://api.github.com/repos/mesh-ranker/app/releases?per_page=30"
    private const val RELEASES_PAGE = "https://github.com/mesh-ranker/app/releases"

    /** Канал обновлений из настроек приложения. */
    fun channel(context: Context): String =
        context.getSharedPreferences("mesh_settings", Context.MODE_PRIVATE)
            .getString("update_channel", CHANNEL_RELEASES) ?: CHANNEL_RELEASES

    /** Возвращает обновление, если на GitHub лежит релиз новее установленного. */
    fun check(context: Context): UpdateInfo? {
        return try {
            val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MESH-Ranker-Android")
            }
            try {
                if (connection.responseCode !in 200..299) return null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                pickUpdate(
                    JSONArray(body),
                    installedVersionName(context),
                    channel(context) == CHANNEL_PRERELEASES
                )
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Выбирает лучшее обновление из списка релизов: отбрасывает черновики,
     * nightly и (если канал — только релизы) пререлизы, затем берёт самую
     * новую по семантике версии версию новее установленной.
     */
    internal fun pickUpdate(
        releases: JSONArray,
        installed: String,
        withPrereleases: Boolean
    ): UpdateInfo? {
        var best: UpdateInfo? = null
        for (index in 0 until releases.length()) {
            val json = releases.optJSONObject(index) ?: continue
            if (json.optBoolean("draft")) continue
            val tag = json.optString("tag_name").trim()
            if (tag.equals(NIGHTLY_TAG, ignoreCase = true)) continue
            if (json.optBoolean("prerelease") && !withPrereleases) continue
            val version = tag.removePrefix("v")
            // Мусорный тег не предлагаем; если установленная версия не
            // разбирается — считаем кандидат новее.
            if (parseVersion(version) == null) continue
            val cmp = compareVersions(version, installed) ?: 1
            if (cmp <= 0) continue
            if (best != null && (compareVersions(version, best.versionName) ?: 0) <= 0) continue

            val releaseUrl = json.optString("html_url")
                .takeIf { it.isNotBlank() }
                ?: RELEASES_PAGE
            val apkUrl = firstApkUrl(json) ?: releaseUrl
            best = UpdateInfo(versionName = version, downloadUrl = apkUrl, releaseUrl = releaseUrl)
        }
        return best
    }

    private fun installedVersionName(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.versionName ?: ""
    }

    /** "2.0.0-beta.5" → ([2, 0, 0], ["beta", "5"]); null — если не разбирается. */
    private fun parseVersion(raw: String): Pair<List<Int>, List<String>>? {
        val clean = raw.trim().removePrefix("v")
        val core = clean.substringBefore('-')
        val nums = core.split('.').map { it.trim().toIntOrNull() ?: return null }
        if (nums.size != 3) return null
        val pre = clean.substringAfter('-', "")
        return nums to (if (pre.isEmpty()) emptyList() else pre.split('.'))
    }

    /**
     * Сравнение семантических версий: <0, 0, >0 как в compareTo.
     * Релиз новее пререлиза с тем же ядром (2.0.0 > 2.0.0-beta.5).
     * null — если хотя бы одна версия не разбирается.
     */
    internal fun compareVersions(a: String, b: String): Int? {
        val (aNums, aPre) = parseVersion(a) ?: return null
        val (bNums, bPre) = parseVersion(b) ?: return null
        for (i in 0..2) {
            if (aNums[i] != bNums[i]) return aNums[i].compareTo(bNums[i])
        }
        if (aPre.isEmpty() && bPre.isEmpty()) return 0
        if (aPre.isEmpty()) return 1
        if (bPre.isEmpty()) return -1
        val count = minOf(aPre.size, bPre.size)
        for (i in 0 until count) {
            val x = aPre[i]
            val y = bPre[i]
            if (x == y) continue
            val xn = x.toIntOrNull()
            val yn = y.toIntOrNull()
            return when {
                xn != null && yn != null -> xn.compareTo(yn)
                xn != null -> -1 // числовые идентификаторы младше буквенных
                yn != null -> 1
                else -> x.compareTo(y)
            }
        }
        return aPre.size.compareTo(bPre.size)
    }

    private fun firstApkUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                val url = asset.optString("browser_download_url")
                if (url.isNotBlank()) return url
            }
        }
        return null
    }
}
