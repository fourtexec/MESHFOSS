package ru.mesh.foss.data

import ru.mesh.foss.core.*
import ru.mesh.foss.data.api.GitHubApiService
import ru.mesh.foss.data.model.GitHubRelease

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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
 *  - `prereleases` — плюс пререлизы: nightly из main и preview/beta/rc-теги.
 *
 * Nightly (скользящий prerelease `nightly`, собирается из main) не выдаётся
 * авто-предупреждением — только при ручной проверке на канале «Пререлизы».
 *
 * Сравнение: если обе версии разбираются, сравниваются X.Y.Z и prerelease-
 * суффикс. Для nightly (у него нет нормальной semver-версии) используется
 * versionCode из тела релиза. Стабильный релиз с тем же X.Y.Z, что и
 * установленный preview/beta, не считается обновлением: пользователь уже
 * сидит на сборке из этого цикла, ему нужен только следующий патч (2.7.1+).
 */
object UpdateChecker {

    const val CHECK_INTERVAL_MS = 15L * 60L * 1000L

    /** Канал обновлений по умолчанию — только стабильные релизы. */
    const val CHANNEL_RELEASES = "releases"
    const val CHANNEL_PRERELEASES = "prereleases"

    /** Скользящий prerelease из main. */
    const val NIGHTLY_TAG = "nightly"

    private const val GITHUB_API = "https://api.github.com/"
    private const val REPO = "fourtexec/MESHFOSS"
    private const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    private val VERSION_CODE_IN_BODY = Regex("- versionCode:\\s*`(\\d+)`")
    private val VERSION_IN_NAME = Regex("""(\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)""")

    private val api: GitHubApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_API)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(GitHubApiService::class.java)
    }

    /** Канал обновлений из настроек приложения. */
    fun channel(context: Context): String =
        context.getSharedPreferences("mesh_settings", Context.MODE_PRIVATE)
            .getString("update_channel", CHANNEL_RELEASES) ?: CHANNEL_RELEASES

    fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info)
    }

    /**
     * Возвращает обновление, если на GitHub лежит релиз новее установленного.
     * [includeNightly] — ручная проверка: на канале «Пререлизы» можно предложить
     * nightly из main; авто-предупреждение его не выдаёт.
     */
    fun check(context: Context, includeNightly: Boolean = false): UpdateInfo? {
        return try {
            val response = api.releases(
                30,
                "application/vnd.github+json",
                "MESH-Ranker-Android"
            ).execute()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return null
            }
            pickUpdate(
                response.body().orEmpty(),
                installedVersionName(context),
                installedVersionCode(context),
                channel(context) == CHANNEL_PRERELEASES,
                includeNightly
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Выбирает лучшее обновление из списка релизов: отбрасывает черновики,
     * nightly (если он не разрешён) и (если канал — только релизы) пререлизы.
     */
    internal fun pickUpdate(
        releases: List<GitHubRelease>,
        installed: String,
        installedCode: Long,
        withPrereleases: Boolean,
        includeNightly: Boolean
    ): UpdateInfo? {
        var best: UpdateInfo? = null
        var bestVersion: String? = null
        var bestCode: Long? = null
        // Debug-сборка добавляет -debug к versionName. Это не часть semver,
        // поэтому убираем суффикс и здесь: pickUpdate могут вызвать и тесты,
        // и другие вызывающие без контекста PackageManager.
        val installedVersion = installed.removeSuffix("-debug")
        for (release in releases) {
            if (release.draft) continue
            val tag = release.tagName?.trim().orEmpty()
            val isNightly = tag.equals(NIGHTLY_TAG, ignoreCase = true)
            if (isNightly && !includeNightly) continue
            if (release.prerelease && !withPrereleases) continue
            val version = releaseVersion(release)
            val code = versionCodeFromBody(release)
            if (!isNewer(release, version, code, installedVersion, installedCode)) continue

            val releaseUrl = release.htmlUrl?.takeIf { it.isNotBlank() } ?: RELEASES_PAGE
            val apkUrl = firstApkUrl(release) ?: releaseUrl
            val take = best == null ||
                compareCandidates(version, code, bestVersion, bestCode) > 0
            if (take) {
                best = UpdateInfo(versionName = version, downloadUrl = apkUrl, releaseUrl = releaseUrl)
                bestVersion = version
                bestCode = code
            }
        }
        return best
    }

    private fun installedVersionName(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        // Debug-сборка добавляет -debug; для сравнения это не отдельная версия.
        return info.versionName.orEmpty().removeSuffix("-debug")
    }

    /**
     * Для тега `preview-16` версия берётся из имени релиза
     * (`MESH Ranker 2.7.0-preview.16`). Для обычных тегов — из тега.
     */
    private fun releaseVersion(release: GitHubRelease): String {
        val tag = release.tagName?.trim().orEmpty().removePrefix("v")
        // У nightly нет semver-версии: для UI оставляем имя тега, а новизну
        // определяем по versionCode из тела релиза.
        if (tag.equals(NIGHTLY_TAG, ignoreCase = true)) return NIGHTLY_TAG
        if (tag.isNotBlank() && parseVersion(tag) != null) return tag
        val fromName = VERSION_IN_NAME.find(release.name.orEmpty())?.groupValues?.get(1)
        return fromName?.takeIf { parseVersion(it) != null } ?: tag
    }

    /** versionCode из тела релиза (`- versionCode: `96``); null, если нет. */
    private fun versionCodeFromBody(release: GitHubRelease): Long? =
        VERSION_CODE_IN_BODY.find(release.body.orEmpty())?.groupValues?.get(1)?.toLongOrNull()

    /**
     * Новая ли версия. Для nightly — по versionCode (он монотонно растёт).
     * Для обычных версий — semver с одним правилом: stable X.Y.Z не новее
     * установленного X.Y.Z-preview/beta/rc.
     */
    private fun isNewer(
        release: GitHubRelease,
        candidate: String,
        candidateCode: Long?,
        installed: String,
        installedCode: Long
    ): Boolean {
        if (release.tagName?.trim().equals(NIGHTLY_TAG, ignoreCase = true)) {
            return candidateCode != null && candidateCode > installedCode
        }
        val candidateParsed = parseVersion(candidate)
        val installedParsed = parseVersion(installed)
        if (candidateParsed != null && installedParsed != null) {
            val core = compareCore(candidateParsed.first, installedParsed.first)
            if (core != 0) return core > 0
            if (installedParsed.second.isNotEmpty() && candidateParsed.second.isEmpty()) {
                // Установлен 2.7.0-preview.16, на GitHub лежит stable 2.7.0.
                // Это не обновление: ждём 2.7.1+ или следующий preview.
                return false
            }
            if (installedParsed.second.isEmpty() && candidateParsed.second.isNotEmpty()) {
                return false
            }
            if (candidateParsed.second.isEmpty() && installedParsed.second.isEmpty()) {
                return false
            }
            return comparePre(candidateParsed.second, installedParsed.second) > 0
        }
        return candidateCode != null && candidateCode > installedCode
    }

    /** Сравнение двух кандидатов между собой. */
    private fun compareCandidates(
        aVersion: String,
        aCode: Long?,
        bVersion: String?,
        bCode: Long?
    ): Int {
        val a = parseVersion(aVersion)
        val b = parseVersion(bVersion.orEmpty())
        if (a != null && b != null) {
            val core = compareCore(a.first, b.first)
            if (core != 0) return core
            val pre = comparePre(a.second, b.second)
            if (pre != 0) return pre
        }
        if (aCode != null && bCode != null) return aCode.compareTo(bCode)
        return 0
    }

    /** "2.0.0-beta.5" → ([2, 0, 0], ["beta", "5"]); null — если не разбирается. */
    private fun parseVersion(raw: String): Pair<List<Int>, List<String>>? {
        val clean = raw.trim().removePrefix("v").removeSuffix("-debug").substringBefore('+')
        val core = clean.substringBefore('-')
        val nums = core.split('.').map { it.trim().toIntOrNull() ?: return null }
        if (nums.size != 3) return null
        val pre = clean.substringAfter('-', "")
        return nums to (if (pre.isEmpty()) emptyList() else pre.split('.'))
    }

    private fun compareCore(a: List<Int>, b: List<Int>): Int {
        for (i in 0..2) {
            if (a[i] != b[i]) return a[i].compareTo(b[i])
        }
        return 0
    }

    private fun comparePre(a: List<String>, b: List<String>): Int {
        val count = minOf(a.size, b.size)
        for (i in 0 until count) {
            val x = a[i]
            val y = b[i]
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
        return a.size.compareTo(b.size)
    }

    /**
     * Сравнение семантических версий: <0, 0, >0 как в compareTo.
     * Релиз новее пререлиза с тем же ядром (2.0.0 > 2.0.0-beta.5).
     * null — если хотя бы одна версия не разбирается.
     */
    internal fun compareVersions(a: String, b: String): Int? {
        val (aNums, aPre) = parseVersion(a) ?: return null
        val (bNums, bPre) = parseVersion(b) ?: return null
        val core = compareCore(aNums, bNums)
        if (core != 0) return core
        if (aPre.isEmpty() && bPre.isEmpty()) return 0
        if (aPre.isEmpty()) return 1
        if (bPre.isEmpty()) return -1
        return comparePre(aPre, bPre)
    }

    private fun firstApkUrl(release: GitHubRelease): String? {
        for (asset in release.assets.orEmpty()) {
            val name = asset.name.orEmpty()
            if (name.endsWith(".apk", ignoreCase = true)) {
                val url = asset.browserDownloadUrl.orEmpty()
                if (url.isNotBlank()) return url
            }
        }
        return null
    }
}
