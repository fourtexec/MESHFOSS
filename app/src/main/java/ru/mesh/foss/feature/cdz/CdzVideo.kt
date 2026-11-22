@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.R
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.CdzNativeClient

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    /** `atomic_object` может быть не видео, а фото/таблицей. */
    val image: Boolean = false,
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
    var fullscreen by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, context) {
        // Разрешение URL может застрять в refresh/bootstrap или сетевом запросе.
        // Через 25 секунд показываем честный fallback вместо вечного спиннера.
        val watchdog = launch {
            delay(25_000)
            if (state is CdzVideoState.Loading) {
                state = CdzVideoState.Failed(fallbackVideoUrl(block))
            }
        }
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
        watchdog.cancel()
    }

    when (val current = state) {
        CdzVideoState.Loading -> Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(modifier = Modifier.size(40.dp))
        }
        is CdzVideoState.Ready -> {
            if (current.info.image) {
                Column(
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
                    CdzRemoteImage(
                        url = current.info.url,
                        description = current.info.title,
                        modifier = modifier.fillMaxWidth(),
                    )
                }
            } else if (fullscreen) {
                CdzVideoFullscreen(
                    info = current.info,
                    onClose = { fullscreen = false },
                )
            } else {
                Column(
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
                    CdzVideoPlayer(
                        info = current.info,
                        onFullscreen = { fullscreen = true },
                    )
                    CdzVideoDownloadButton(current.info)
                    if (current.info.subtitlesUrl.isNotBlank()) {
                        Text(
                            text = Words.CdzVideoSubtitles.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
private fun CdzVideoPlayer(
    info: CdzVideoInfo,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    onFullscreen: (() -> Unit)? = null,
) {
    var failed by remember(info.url) { mutableStateOf(false) }
    var ready by remember(info.url) { mutableStateOf(false) }
    val holder = remember(info.url) { arrayOfNulls<VideoView>(1) }

    LaunchedEffect(info.url) {
        // VideoView может молча висеть на неподходящем URL (например, HLS или
        // картинке). Не оставляем пользователя с вечным спиннером.
        delay(20_000)
        if (!ready && !failed) failed = true
    }

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

    val playerModifier = if (fullscreen) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
    }
    Box(
        modifier = playerModifier.background(
            if (ready) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        key(info.url) {
            AndroidView(
                factory = { viewContext ->
                    VideoView(viewContext).apply {
                        holder[0] = this
                        visibility = View.INVISIBLE
                        val controller = MediaController(viewContext)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(Uri.parse(info.url), videoHeaders(info.url, info.cookieHeader))
                        setOnPreparedListener { player ->
                            player.isLooping = false
                            visibility = View.VISIBLE
                            ready = true
                        }
                        setOnErrorListener { _: MediaPlayer, _: Int, _: Int ->
                            failed = true
                            true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!ready) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(if (fullscreen) 52.dp else 40.dp)
                )
            }
        }
        if (onFullscreen != null) {
            IconButton(
                onClick = onFullscreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.52f), CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_fullscreen),
                    contentDescription = Words.CdzFullscreen.text,
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CdzVideoFullscreen(
    info: CdzVideoInfo,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context.findActivity()
        if (activity == null) return@DisposableEffect onDispose {}
        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = previousOrientation
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CdzVideoPlayer(
                    info = info,
                    modifier = Modifier.fillMaxSize(),
                    fullscreen = true,
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp)
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .semantics { contentDescription = Words.Close.text },
                ) {
                    Text(
                        text = "✕",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun CdzVideoDownloadButton(info: CdzVideoInfo) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            downloadVideo(context, info)
        } else {
            Toast.makeText(context, Words.CdzDownloadFailed.text, Toast.LENGTH_LONG).show()
        }
    }
    FilledTonalButton(
        onClick = {
            val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                downloadVideo(context, info)
            }
        },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_download),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(Words.Download.text)
    }
}

private fun downloadVideo(context: Context, info: CdzVideoInfo) {
    runCatching {
        val fileName = videoFileName(info)
        val request = DownloadManager.Request(Uri.parse(info.url)).apply {
            setTitle(info.title.ifBlank { Words.Download.text })
            setMimeType("video/mp4")
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            addRequestHeader("User-Agent", BrowserUserAgent)
            addRequestHeader("Referer", "$CdzBaseUrl/")
            val cookies = cookiesFor(info.url, info.cookieHeader)
            if (cookies.isNotBlank()) addRequestHeader("Cookie", cookies)
            // Всегда в общую папку «Загрузки», а не в Android/data/....
            // На Android 9 и ниже для этого заранее запрошено разрешение.
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, Words.CdzDownloadStarted.text, Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, Words.CdzDownloadFailed.text, Toast.LENGTH_LONG).show()
    }
}

private fun videoFileName(info: CdzVideoInfo): String {
    val lastSegment = Uri.parse(info.url).lastPathSegment.orEmpty()
    val extension = lastSegment.substringAfterLast('.', "")
        .lowercase()
        .takeIf { it in setOf("mp4", "m4v", "mov", "webm") }
        ?: "mp4"
    val base = info.title.trim()
        .ifBlank { lastSegment.substringBeforeLast('.').trim() }
        .ifBlank { "video" }
        .removeSuffix(".$extension")
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .trim()
        .take(72)
        .ifBlank { "video" }
    return "$base.$extension"
}

private fun cookiesFor(url: String, cookieHeader: String = ""): String =
    cookieHeader.ifBlank { CookieManager.getInstance().getCookie(url).orEmpty() }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun videoHeaders(url: String, cookieHeader: String = ""): Map<String, String> = buildMap {
    put("User-Agent", BrowserUserAgent)
    put("Referer", "$CdzBaseUrl/")
    val cookies = cookiesFor(url, cookieHeader)
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
        image = metadata?.image ?: looksLikeImageUrl(direct),
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
    val lower = value.substringBefore('?').lowercase()
    return lower.endsWith(".mp4") ||
        lower.endsWith(".m3u8") ||
        lower.endsWith(".webm") ||
        lower.endsWith(".mov") ||
        lower.contains("/video/")
}

private fun looksLikeImageUrl(value: String): Boolean {
    val lower = value.substringBefore('?').lowercase()
    return lower.endsWith(".png") ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".webp") ||
        lower.endsWith(".gif") ||
        lower.endsWith(".bmp") ||
        lower.endsWith(".svg")
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
    val contentType = meta.optString("content_type")
        .ifBlank { json.optString("content_type") }
        .trim()
        .lowercase()
    val image = contentType.startsWith("image") || looksLikeImageUrl(url)
    val video = contentType.startsWith("video") || looksLikeVideoUrl(url)
    // GameApp, документы и прочие атомы нельзя отдавать в VideoView.
    if (!image && !video) return null
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
        image = image,
    )
}

private fun absoluteCdzUrl(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    if (value.startsWith("//")) return "https:$value"
    return "$CdzBaseUrl/${value.trimStart('/')}"
}
