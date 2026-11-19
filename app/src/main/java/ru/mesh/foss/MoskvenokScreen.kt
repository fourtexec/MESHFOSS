@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package ru.mesh.foss

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/**
 * Экран «Москвёнок»: карта школьных документов с QR-кодом прохода.
 *
 * Сессия id.mos.ru берётся из [SessionStore.mosIdSession], а при первом
 * открытии получается по cookie jar'у веб-/QR-входа ([MoskvenokApi.obtainSession],
 * стадия 2 PoC). Дальше — API из flow.txt: objects (включена ли карта),
 * settings/docs (включить) и documents (QR-пейлоад + поля).
 *
 * QR показывается с полосой времени до устаревания; после истечения код
 * сереет и в центре появляется круглая кнопка refresh, запрашивающая новый
 * barcode_payload. Работает во всех дизайнах — карточки через GlassCard.
 */
@Composable
fun MoskvenokScreen(store: SessionStore, studentId: String?) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val deviceId = remember(context) {
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: "unknown"
    }

    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var objects by remember { mutableStateOf<JSONObject?>(null) }
    var doc by remember { mutableStateOf<JSONObject?>(null) }
    var enabling by remember { mutableStateOf(false) }
    var qrRefreshing by remember { mutableStateOf(false) }
    // Срок действия QR: barcode_valid_time приходит в миллисекундах (~5 минут).
    var validMs by remember { mutableStateOf(0L) }
    var expiresAt by remember { mutableStateOf(0L) }
    var remainingMs by remember { mutableStateOf(0L) }

    val payload = doc?.optString("barcode_payload").orEmpty()
    val expired = expiresAt > 0L && remainingMs <= 0L
    val qr = remember(payload) { if (payload.isBlank()) null else encodeQr(payload) }

    // Отсчёт до устаревания: абсолютное время, переживает уход в фон.
    LaunchedEffect(expiresAt) {
        if (expiresAt <= 0L) {
            remainingMs = 0L
            return@LaunchedEffect
        }
        while (isActive) {
            val left = expiresAt - System.currentTimeMillis()
            if (left <= 0L) {
                remainingMs = 0L
                break
            }
            remainingMs = left
            delay(250)
        }
    }

    suspend fun session(): String = withContext(Dispatchers.IO) {
        store.mosIdSession?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val jar = store.authCookies?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                buildMap { json.keys().forEach { key -> put(key, json.optString(key)) } }
            }.getOrNull()
        }.orEmpty()
        if (jar.isEmpty()) throw MoskvenokApi.MosError("no-session", 401)
        val sid = MoskvenokApi.obtainSession(jar, deviceId)
            ?: throw MoskvenokApi.MosError("no-session", 401)
        store.mosIdSession = sid
        sid
    }

    suspend fun fetchAll(refreshQr: Boolean): Pair<JSONObject, JSONObject?> =
        withContext(Dispatchers.IO) {
            var sid: String
            var objectsJson: JSONObject
            try {
                sid = session()
                objectsJson = MoskvenokApi.objects(sid, deviceId)
            } catch (error: MoskvenokApi.MosError) {
                // Сессия протухла — получаем заново по кукам входа и повторяем.
                if (error.code != 401) throw error
                store.mosIdSession = null
                sid = session()
                objectsJson = MoskvenokApi.objects(sid, deviceId)
            }
            var document: JSONObject? = null
            if (MoskvenokApi.isEnabled(objectsJson)) {
                val docId = MoskvenokApi.meshDocId(objectsJson, studentId)
                    ?: throw MoskvenokApi.MosError("no mesh document")
                document = MoskvenokApi.document(sid, deviceId, docId, refreshQr)
            }
            objectsJson to document
        }

    fun applyDocTiming(document: JSONObject) {
        val valid = document.optLong("barcode_valid_time", 0L)
        if (valid > 0L) {
            validMs = valid
            expiresAt = System.currentTimeMillis() + valid
            remainingMs = valid
        } else {
            validMs = 0L
            expiresAt = 0L
            remainingMs = 0L
        }
    }

    fun load(refreshQr: Boolean = false) {
        scope.launch {
            if (refreshQr) qrRefreshing = true else loading = true
            try {
                val (objectsJson, document) = fetchAll(refreshQr)
                objects = objectsJson
                doc = document
                errorText = null
                document?.let { applyDocTiming(it) }
            } catch (error: Exception) {
                // Ошибка обновления QR не должна прятать уже показанный код.
                if (!refreshQr) errorText = describeMoskvenok(error)
            } finally {
                loading = false
                qrRefreshing = false
            }
        }
    }

    fun enableMoskvenok() {
        scope.launch {
            enabling = true
            try {
                withContext(Dispatchers.IO) { MoskvenokApi.enable(session(), deviceId) }
                load()
            } catch (error: Exception) {
                errorText = describeMoskvenok(error)
                loading = false
            } finally {
                enabling = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val loadedObjects = objects
    val error = errorText
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            loading -> MoskvenokLoader()
            loadedObjects == null ->
                if (error != null) MoskvenokErrorCard(error) { load() } else MoskvenokLoader()
            !MoskvenokApi.isEnabled(loadedObjects) -> GlassCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        Words.Moskvenok.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Words.MoskvenokAbout.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { enableMoskvenok() },
                        enabled = !enabling,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (enabling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(Words.EnableMoskvenok.text)
                    }
                }
            }
            doc == null -> MoskvenokLoader()
            else -> {
                // --- QR-карточка -------------------------------------------
                val currentDoc = doc
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            currentDoc?.optString("doc_name").orEmpty().ifBlank { Words.Moskvenok.text },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qr != null) {
                                Image(
                                    bitmap = qr.asImageBitmap(),
                                    contentDescription = Words.Moskvenok.text,
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    colorFilter = if (expired) {
                                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                                    } else {
                                        null
                                    }
                                )
                            } else {
                                Text(
                                    payload,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (expired && qr != null) {
                                FilledIconButton(
                                    onClick = { load(refreshQr = true) },
                                    enabled = !qrRefreshing,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    if (qrRefreshing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(26.dp),
                                            strokeWidth = 3.dp
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_refresh),
                                            contentDescription = Words.MoskvenokRefreshQr.text,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (validMs > 0L) {
                            Spacer(Modifier.height(12.dp))
                            val total = validMs.toFloat()
                            LinearProgressIndicator(
                                progress = { (remainingMs / total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (expired) {
                                    Words.MoskvenokQrExpired.text
                                } else {
                                    Words.MoskvenokValidFor(formatClock(remainingMs))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (expired) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                // --- Поля документа ---------------------------------------
                val rows = currentDoc?.let { fieldRows(it) }.orEmpty()
                if (rows.isNotEmpty()) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            rows.forEachIndexed { index, (label, value) ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        label.ifBlank { value },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (label.isNotBlank() && value.isNotBlank()) {
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            value,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (index != rows.lastIndex) Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoskvenokLoader() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            Words.MoskvenokLoading.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MoskvenokErrorCard(message: String, onRetry: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text(Words.Retry.text) }
        }
    }
}

/** Строки документа с типом docname (label → value), как в официальном приложении. */
private fun fieldRows(doc: JSONObject): List<Pair<String, String>> {
    val fields = doc.optJSONArray("fields") ?: return emptyList()
    val rows = mutableListOf<Pair<String, String>>()
    for (i in 0 until fields.length()) {
        val field = fields.optJSONObject(i) ?: continue
        if (field.optString("type") != "docname") continue
        rows += field.optString("label") to field.optString("value")
    }
    return rows
}

private fun describeMoskvenok(error: Throwable): String = when {
    error is MoskvenokApi.MosError && error.code == 401 -> Words.MoskvenokNoSession.text
    error is MoskvenokApi.MosError || error is IOException -> Words.MoskvenokError.text
    else -> error.message?.takeIf { it.isNotBlank() } ?: Words.MoskvenokError.text
}

/** Оставшееся время вида 4:59 / 1:02:03. */
private fun formatClock(millis: Long): String {
    val total = (millis / 1000L).coerceAtLeast(0L)
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** Кодирует payload в QR (zxing) с тихой зоной и рисует Bitmap. */
private fun encodeQr(payload: String): Bitmap? = try {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0)
    val quiet = 4
    val scale = (1024 / (matrix.width + quiet * 2)).coerceAtLeast(1)
    val side = (matrix.width + quiet * 2) * scale
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = false
    }
    val offset = quiet * scale
    for (y in 0 until matrix.height) {
        var x = 0
        while (x < matrix.width) {
            if (matrix.get(x, y)) {
                var run = x + 1
                while (run < matrix.width && matrix.get(run, y)) run++
                canvas.drawRect(
                    (offset + x * scale).toFloat(),
                    (offset + y * scale).toFloat(),
                    (offset + run * scale).toFloat(),
                    (offset + (y + 1) * scale).toFloat(),
                    paint
                )
                x = run
            } else {
                x++
            }
        }
    }
    bitmap
} catch (error: Exception) {
    null
}
