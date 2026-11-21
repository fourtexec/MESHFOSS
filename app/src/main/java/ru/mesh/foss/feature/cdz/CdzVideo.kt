@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.Words
import ru.mesh.foss.data.CdzNativeClient

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.webkit.CookieManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val CdzBaseUrl = "https://uchebnik.mos.ru"
private const val BrowserUserAgent =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/147.0 Mobile Safari/537.36"
private val MaterialUuid = Regex(
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
)

private data class CdzVideoInfo(
    val url: String,
    val subtitlesUrl: String,
    val title: String,
    val posterUrl: String,
    val cookieHeader: String = "",
)

private sealed class CdzVideoState {
    data object Loading : CdzVideoState()
    data class Ready(val info: CdzVideoInfo) : CdzVideoState()
    data class Failed(val fallbackUrl: String) : CdzVideoState()
}

/**
 * Видео из `content/atomic` или варианта ответа.
 *
 * В задании обычно приходит `atomic_id`, а настоящий MP4 лежит в
 * `GET /cms/api/v5/materials/{uuid}` → `custom_meta.file_url`. Поэтому сначала
 * пробуем прямой URL из блока, затем метаданные материала. Если воспроизвести
 * файл нельзя, остаётся честная кнопка «Браузер».
 */
@Composable
fun CdzRemoteVideo(
    block: JSONObject,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val context = LocalContext.current
    val key = remember(block) {
        listOf(
            block.optString("atomic_id"),
            block.optString("material_id"),
            block.optString("uuid"),
            block.optString("file_url"),
            block.optString("video_url"),
            block.optString("relative_url"),
            block.optString("url"),
        ).joinToString("|")
    }
    var state by remember(key) { mutableStateOf<CdzVideoState>(CdzVideoState.Loading) }

    LaunchedEffect(key, context) {
        state = withContext(Dispatchers.IO) {
            val info = try {
                resolveCdzVideo(context, block)
            } catch (_: Exception) {
                null
            }
            if (info == null) {
                CdzVideoState.Failed(fallbackVideoUrl(block))
            } else {
                CdzVideoState.Ready(info)
            }
        }
    }

    when (val current = state) {
        CdzVideoState.Loading -> Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(modifier = Modifier.size(26.dp))
        }
        is CdzVideoState.Ready -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showTitle && current.info.title.isNotBlank()) {
                Text(
                    text = current.info.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            CdzVideoPlayer(current.info)
            if (current.info.subtitlesUrl.isNotBlank()) {
                Text(
                    text = Words.CdzVideoSubtitles.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is CdzVideoState.Failed -> CdzVideoFallback(
            url = current.fallbackUrl,
            modifier = modifier,
        )
    }
}

@Composable
private fun CdzVideoFallback(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = Words.CdzVideoUnavailable.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (url.isNotBlank()) {
            TextButton(onClick = { openBrowser(context, url) }) {
                Text(Words.CdzOpenInBrowser.text)
            }
        }
    }
}

@Composable
private fun CdzVideoPlayer(info: CdzVideoInfo) {
    var failed by remember(info.url) { mutableStateOf(false) }
    val holder = remember(info.url) { arrayOfNulls<VideoView>(1) }

    DisposableEffect(info.url) {
        onDispose {
            holder[0]?.stopPlayback()
            holder[0] = null
        }
    }

    if (failed) {
        CdzVideoFallback(info.url)
        return
    }

    key(info.url) {
        AndroidView(
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    holder[0] = this
                    val controller = MediaController(viewContext)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setVideoURI(Uri.parse(info.url), videoHeaders(info.url, info.cookieHeader))
                    setOnPreparedListener { player ->
                        player.isLooping = false
                    }
                    setOnErrorListener { _: MediaPlayer, _: Int, _: Int ->
                        failed = true
                        true
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium),
        )
    }
}

private fun videoHeaders(url: String, cookieHeader: String = ""): Map<String, String> = buildMap {
    put("User-Agent", BrowserUserAgent)
    put("Referer", "$CdzBaseUrl/")
    val cookies = cookieHeader.ifBlank {
        CookieManager.getInstance().getCookie(url).orEmpty()
    }
    if (cookies.isNotBlank()) put("Cookie", cookies)
}

private fun openBrowser(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private suspend fun resolveCdzVideo(context: Context, block: JSONObject): CdzVideoInfo? {
    val direct = directVideoUrl(block)
    // В задании обычно есть только `atomic_id`; в материале — UUID страницы.
    // Пробуем оба вида ссылок, но не задерживаем уже готовый прямой MP4
    // ожиданием метаданных. API требует Bearer и Profile-Id, поэтому идём
    // через сессионный клиент, а прямой HTTP оставляем запасным путём.
    var sessionCookies = ""
    val metadata = if (direct.isBlank()) {
        val ids = materialIdCandidates(block)
        val viaClient = try {
            if (ids.isEmpty()) {
                null
            } else {
                val client = CdzNativeClient.fromSession(context, "$CdzBaseUrl/")
                var found: CdzVideoInfo? = null
                for (id in ids) {
                    found = client.loadMaterial(id)?.let(::videoInfoFromMetadata)
                    if (found != null) break
                }
                if (found != null) {
                    sessionCookies = client.currentCookies().entries.joinToString("; ") {
                        "${it.key}=${it.value}"
                    }
                }
                found
            }
        } catch (_: Exception) {
            null
        }
        viaClient ?: ids.firstNotNullOfOrNull { id -> fetchVideoMetadata(id) }
    } else {
        null
    }
    val url = direct.ifBlank { metadata?.url.orEmpty() }
    if (url.isBlank()) return null
    return CdzVideoInfo(
        url = url,
        subtitlesUrl = metadata?.subtitlesUrl
            ?: absoluteCdzUrl(block.optString("subtitles_file_url")),
        title = metadata?.title ?: block.optString("description").trim(),
        posterUrl = metadata?.posterUrl
            ?: absoluteCdzUrl(
                block.optString("full_cover_url")
                    .ifBlank { block.optString("preview_url") }
            ),
        cookieHeader = sessionCookies,
    )
}

private fun materialIdCandidates(block: JSONObject): List<String> {
    val candidates = linkedSetOf<String>()
    listOf("atomic_id", "material_id", "uuid").forEach { key ->
        block.optString(key).trim()
            .takeIf { it.isNotBlank() }
            ?.let(candidates::add)
    }
    listOf(
        "relative_url",
        "url",
        "launch_url",
        "card_url",
        "file_url",
        "video_url",
    ).forEach { key ->
        MaterialUuid.find(block.optString(key))?.value?.let(candidates::add)
    }
    return candidates.toList()
}

private fun directVideoUrl(block: JSONObject): String {
    val file = block.optString("file_url")
        .ifBlank { block.optString("video_url") }
        .trim()
    if (file.isNotBlank()) return absoluteCdzUrl(file)
    for (key in listOf("url", "relative_url")) {
        val value = block.optString(key).trim()
        if (looksLikeVideoUrl(value)) return absoluteCdzUrl(value)
    }
    return ""
}

private fun looksLikeVideoUrl(value: String): Boolean {
    val lower = value.lowercase()
    return lower.contains(".mp4") ||
        lower.contains(".m3u8") ||
        lower.contains(".webm") ||
        lower.contains(".mov") ||
        lower.contains("/video/")
}

private fun fallbackVideoUrl(block: JSONObject): String {
    val direct = absoluteCdzUrl(
        block.optString("launch_url")
            .ifBlank { block.optString("card_url") }
            .ifBlank { block.optString("relative_url") }
            .ifBlank { block.optString("url") }
    )
    if (direct.isNotBlank()) return direct
    val materialId = materialIdCandidates(block)
        .firstOrNull { MaterialUuid.matches(it) }
        ?: return ""
    return "$CdzBaseUrl/material/$materialId"
}

private fun fetchVideoMetadata(atomicId: String): CdzVideoInfo? {
    val endpoint = "$CdzBaseUrl/cms/api/v5/materials/$atomicId"
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", BrowserUserAgent)
        setRequestProperty("Referer", "$CdzBaseUrl/")
        CookieManager.getInstance().getCookie(endpoint)
            ?.takeIf { it.isNotBlank() }
            ?.let { setRequestProperty("Cookie", it) }
    }
    return try {
        if (connection.responseCode !in 200..299) return null
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        videoInfoFromMetadata(JSONObject(body))
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private fun videoInfoFromMetadata(json: JSONObject): CdzVideoInfo? {
    val meta = json.optJSONObject("custom_meta") ?: JSONObject()
    val url = absoluteCdzUrl(
        meta.optString("file_url").ifBlank { json.optString("file_url") }
    )
    if (url.isBlank()) return null
    return CdzVideoInfo(
        url = url,
        subtitlesUrl = absoluteCdzUrl(
            meta.optString("subtitles_file_url")
                .ifBlank { json.optString("subtitles_file_url") }
        ),
        title = json.optString("title").trim(),
        posterUrl = absoluteCdzUrl(
            json.optString("full_cover_url")
                .ifBlank { json.optString("cover_url") }
                .ifBlank { meta.optString("preview_url") }
        ),
    )
}

private fun absoluteCdzUrl(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    if (value.startsWith("//")) return "https:$value"
    return "$CdzBaseUrl/${value.trimStart('/')}"
}
