@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.marks

import ru.mesh.foss.R
import ru.mesh.foss.core.MeshCore
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.model.MarkDetail
import ru.mesh.foss.data.model.MarkFile
import ru.mesh.foss.ui.GlassCard

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

private val IMAGE_TYPES = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif")
private val DOWNLOAD_TYPES = IMAGE_TYPES + setOf("pdf", "txt", "zip", "doc", "docx", "xls", "xlsx", "ppt", "pptx")
private val MIME_EXTENSIONS = mapOf(
    "image/jpeg" to "jpeg",
    "image/png" to "png",
    "image/webp" to "webp",
    "image/bmp" to "bmp",
    "image/gif" to "gif",
    "image/heic" to "heic",
    "image/heif" to "heif",
    "image/avif" to "avif",
    "application/pdf" to "pdf",
    "text/plain" to "txt",
    "application/zip" to "zip",
    "application/msword" to "doc",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
    "application/vnd.ms-excel" to "xls",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
    "application/vnd.ms-powerpoint" to "ppt",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
)

private val markFileHttp by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

private object MarkFileImageCache {
    // Ограничиваем кэш по памяти, а не по числу картинок: сканы могут быть крупными.
    val images = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }
}

@Composable
fun MarkFilesSection(detail: MarkDetail) {
    val resultFiles = detail.resultFiles.orEmpty()
    val studentWorkFiles = detail.studentWorkResultFiles.orEmpty()
    val groups: List<Pair<String, List<MarkFile>>> = buildList {
        if (resultFiles.isNotEmpty()) add(Words.MarkFiles.text to resultFiles)
        if (studentWorkFiles.isNotEmpty()) add(Words.StudentWorkFiles.text to studentWorkFiles)
    }
    if (groups.isEmpty()) return

    Spacer(Modifier.size(4.dp))
    groups.forEach { (title, files) ->
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            files.sortedBy { it.ordinal ?: Int.MAX_VALUE }.forEach { file ->
                MarkFileRow(file)
            }
        }
    }
}

@Composable
private fun MarkFileRow(file: MarkFile) {
    val context = LocalContext.current
    val url = MeshCore.absoluteUrl(file.fileUrl)
    val canDownload = url.startsWith("https://") || url.startsWith("http://")
    val isImage = canDownload && markFileIsImage(file.type)
    var preview by remember(url) { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enqueueMarkFileDownload(context, file)
        } else {
            Toast.makeText(context, Words.CdzDownloadFailed.text, Toast.LENGTH_LONG).show()
        }
    }
    val download = {
        if (canDownload) {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                enqueueMarkFileDownload(context, file)
            }
        }
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canDownload, onClick = download)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isImage) {
                MarkRemoteImage(
                    url = url,
                    description = fileTitle(file),
                    contentScale = ContentScale.Crop,
                    compactError = true,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { preview = true }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_attachment),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fileTitle(file),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        markFileTypeLabel(file.type).takeIf { it.isNotBlank() },
                        markFileSizeLabel(file.size).takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = download, enabled = canDownload) {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = Words.Download.text
                )
            }
        }
    }

    if (preview) {
        MarkImagePreview(
            url = url,
            title = fileTitle(file),
            onDownload = download,
            onDismiss = { preview = false }
        )
    }
}

@Composable
private fun MarkImagePreview(
    url: String,
    title: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                MarkRemoteImage(
                    url = url,
                    description = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDownload) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = Words.Download.text,
                        tint = Color.White
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(Words.Close.text, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun MarkRemoteImage(
    url: String,
    description: String,
    contentScale: ContentScale,
    compactError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(url) { mutableStateOf(MarkFileImageCache.images.get(url)) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/147 Mobile Safari/537.36")
                    .header("Referer", "https://school.mos.ru/")
                    .build()
                markFileHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    if (body.contentLength() > 25L * 1024 * 1024) return@use null
                    BitmapFactory.decodeStream(body.byteStream())?.asImageBitmap()
                }
            }.getOrNull()
        }
        if (loaded != null) {
            MarkFileImageCache.images.put(url, loaded)
            bitmap = loaded
        } else {
            failed = true
        }
    }

    val current = bitmap
    when {
        current != null -> Image(
            bitmap = current,
            contentDescription = description.takeIf { it.isNotBlank() },
            contentScale = contentScale,
            modifier = modifier
        )
        failed -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            if (compactError) {
                Icon(
                    painter = painterResource(R.drawable.ic_attachment),
                    contentDescription = Words.CouldNotOpenFile.text,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    Words.CouldNotOpenFile.text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            LoadingIndicator(modifier = Modifier.size(22.dp))
        }
    }
}

private fun fileTitle(file: MarkFile): String {
    val number = (file.ordinal ?: 0) + 1
    return Words.FileNumber(number)
}

private fun enqueueMarkFileDownload(context: Context, file: MarkFile) {
    val url = MeshCore.absoluteUrl(file.fileUrl)
    if (!url.startsWith("https://") && !url.startsWith("http://")) return
    runCatching {
        val extension = markFileExtension(file.type).ifBlank { "bin" }
        val name = buildString {
            append("MESH_mark_")
            append(file.id ?: file.ordinal?.toString() ?: "file")
            append('.')
            append(extension)
        }
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(name)
            markFileMimeType(extension)?.let { setMimeType(it) }
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/147 Mobile Safari/537.36")
            addRequestHeader("Referer", "https://school.mos.ru/")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, Words.CdzDownloadStarted.text, Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, Words.CdzDownloadFailed.text, Toast.LENGTH_LONG).show()
    }
}

internal fun markFileExtension(type: String?): String {
    val raw = type.orEmpty().trim().lowercase(Locale.US)
    MIME_EXTENSIONS[raw]?.let { return it }
    return raw
        .substringAfterLast('/')
        .substringAfterLast('.')
        .filter { it.isLetterOrDigit() }
        .takeIf { it in DOWNLOAD_TYPES }
        ?: ""
}

internal fun markFileIsImage(type: String?): Boolean =
    markFileExtension(type) in IMAGE_TYPES

internal fun markFileTypeLabel(type: String?): String =
    markFileExtension(type).uppercase(Locale.US)

internal fun markFileSizeLabel(size: String?): String {
    val bytes = size?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: return ""
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024L -> "${(bytes + 512) / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun markFileMimeType(extension: String): String? = when (extension) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    "gif" -> "image/gif"
    "heic", "heif" -> "image/heic"
    "avif" -> "image/avif"
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "zip" -> "application/zip"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "xls" -> "application/vnd.ms-excel"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "ppt" -> "application/vnd.ms-powerpoint"
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    else -> null
}
