@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.cdz

import android.graphics.BitmapFactory
import android.util.LruCache
import android.webkit.CookieManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private object CdzImageCache {
    val bitmaps = LruCache<String, ImageBitmap>(24)
}

/**
 * Картинка задания или варианта ответа.
 *
 * Загрузка идёт в IO-потоке с таймаутами, результат кешируется в памяти.
 * Пока картинки нет, показывается компактный M3E-индикатор, а не серая заглушка.
 */
@Composable
fun CdzRemoteImage(
    url: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (url.isBlank()) return
    var bitmap by remember(url) { mutableStateOf(CdzImageCache.bitmaps.get(url)) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/147.0 Mobile Safari/537.36"
                )
                connection.setRequestProperty("Referer", "https://uchebnik.mos.ru/")
                CookieManager.getInstance().getCookie(url)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { cookies -> connection.setRequestProperty("Cookie", cookies) }
                connection.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
        if (loaded != null) {
            CdzImageCache.bitmaps.put(url, loaded)
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
            contentScale = ContentScale.Fit,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(MaterialTheme.shapes.medium),
        )
        failed -> Text(
            text = description.ifBlank { "…" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        else -> Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = 160.dp),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(modifier = Modifier.size(22.dp))
        }
    }
}
