@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package ru.mesh.foss.feature.moskvenok

import ru.mesh.foss.core.*
import ru.mesh.foss.data.*
import ru.mesh.foss.ui.*
import ru.mesh.foss.R

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/** Сколько раз добиваться непустого barcode_payload, прежде чем показать ошибку. */
private const val QR_PAYLOAD_ATTEMPTS = 6

/** Пауза между повторными запросами QR: сервер успевает закончить генерацию. */
private const val QR_PAYLOAD_RETRY_MS = 1_200L

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
fun MoskvenokScreen(store: SessionStore) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Официальное приложение шлёт Android ID в ВЕРХНЕМ регистре (из captured
    // flow — ID в caps). Сервер регистрирует устройство на логине в таком
    // виде: с нижним регистром settings/docs принимается (эхо 200), но не
    // сохраняется, а документ отдаёт 500. Регистр чувствителен и для привязки
    // сессии: несовпадение session/deviceid -> 403 (старая закэшированная сессия
    // в этом случае сама переполучится).
    val deviceId = remember(context) {
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.uppercase() ?: "unknown"
    }

    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var objects by remember { mutableStateOf<JSONObject?>(null) }
    var doc by remember { mutableStateOf<JSONObject?>(null) }
    var enabling by remember { mutableStateOf(false) }
    var qrRefreshing by remember { mutableStateOf(false) }
    // Сервер асинхронно генерирует документы: ответ 202 «Идет обработка документов».
    var processing by remember { mutableStateOf(false) }
    // Срок действия QR: barcode_valid_time приходит в миллисекундах (~5 минут).
    var validMs by remember { mutableStateOf(0L) }
    var expiresAt by remember { mutableStateOf(0L) }
    var remainingMs by remember { mutableStateOf(0L) }

    val payload = doc?.let { MoskvenokApi.barcodePayload(it) }.orEmpty()
    val expired = expiresAt > 0L && remainingMs <= 0L
    val qr = remember(payload) { if (payload.isBlank()) null else encodeQrBitmap(payload) }

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
                MeshLog.log("Moskvenok", "qr: устарел, нужен refresh")
                break
            }
            remainingMs = left
            delay(250)
        }
    }

    suspend fun session(): String = withContext(Dispatchers.IO) {
        store.mosIdSession?.takeIf { it.isNotBlank() }?.let {
            MeshLog.log("Moskvenok", "session: кэш sid=$it")
            return@withContext it
        }
        val jar = store.authCookies?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                buildMap { json.keys().forEach { key -> put(key, json.optString(key)) } }
            }.getOrNull()
        }.orEmpty()
        if (jar.isEmpty()) {
            MeshLog.log("Moskvenok", "session: нет кук веб-входа (auth_cookies пусты)")
            throw MoskvenokApi.MosError("no-session", 401)
        }
        val sid = MoskvenokApi.obtainSession(jar, deviceId)
        if (sid == null) {
            MeshLog.log("Moskvenok", "session: obtainSession не вернул сессию")
            throw MoskvenokApi.MosError("no-session", 401)
        }
        store.mosIdSession = sid
        MeshLog.log("Moskvenok", "session: получена sid=$sid")
        sid
    }

    /** objects с однократным переполучением сессии при 401. */
    suspend fun fetchObjects(): Pair<String, JSONObject> = withContext(Dispatchers.IO) {
        // Во время ожидания обработки (202) НЕ шлём force-refresh: он может
        // перезапускать генерацию документов — тогда обработка не закончится.
        val force = !processing
        try {
            val sid = session()
            sid to MoskvenokApi.objects(sid, deviceId, forceRefresh = force)
        } catch (error: MoskvenokApi.MosError) {
            // Сессия протухла — получаем заново по кукам входа и повторяем.
            if (error.code != 401) throw error
            MeshLog.log("Moskvenok", "objects: 401 — переполучаем сессию")
            store.mosIdSession = null
            val sid = session()
            sid to MoskvenokApi.objects(sid, deviceId, forceRefresh = force)
        }
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

    /**
     * Обновляет objects (и документ, если карта включена). Возвращает, включена ли
     * карта. objects кладётся в состояние сразу — падение GET documents не должно
     * прятать реальное состояние карточки (раньше из-за этого после POST включения
     * экран снова показывал кнопку «Включить»).
     */
    suspend fun refreshAll(refreshQr: Boolean): Boolean {
        val (sid, objectsJson) = try {
            fetchObjects()
        } catch (error: Exception) {
            // Ошибка обновления QR не должна прятать уже показанный код.
            if (refreshQr) {
                MeshLog.log("Moskvenok", "load: objects ошибка при обновлении QR (игнор): $error")
                return objects?.let { MoskvenokApi.isEnabled(it) } ?: false
            }
            MeshLog.log("Moskvenok", "load: objects ошибка: $error")
            processing = false
            errorText = describeMoskvenok(error)
            return false
        }
        objects = objectsJson
        // 202 «Идет обработка документов» — не данные: ждём и опрашиваем,
        // пока сервер не соберёт документы.
        if (objectsJson.optInt("refresh") != 0 && objectsJson.has("message")) {
            processing = true
            MeshLog.log("Moskvenok", "load: objects — 202 обработка")
            return false
        }
        processing = false
        val enabledNow = MoskvenokApi.isEnabled(objectsJson)
        if (!enabledNow) {
            doc = null
            errorText = null
            MeshLog.log("Moskvenok", "load: isEnabled=false")
            return false
        }
        val docId = MoskvenokApi.meshDocId(objectsJson, store.personId)
        if (docId == null) {
            MeshLog.log("Moskvenok", "load: isEnabled=true, но id документа нет")
            if (!refreshQr) errorText = describeMoskvenok(MoskvenokApi.MosError("no mesh document"))
            return true
        }
        try {
            var document = withContext(Dispatchers.IO) {
                MoskvenokApi.document(sid, deviceId, docId, refreshQr)
            }
            if (document.optInt("refresh") != 0 && document.has("message")) {
                processing = true
                MeshLog.log("Moskvenok", "load: документ — 202 обработка")
                return true
            }
            processing = false
            // Сразу после включения сервер может отдать документ без barcode_payload
            // (пустой квадрат) или JSON null (Android optString даёт "null").
            // Добиваемся именно непустой строки, а не просто непустого optString.
            var payloadTry = 1
            while (
                MoskvenokApi.barcodePayload(document) == null &&
                payloadTry < QR_PAYLOAD_ATTEMPTS
            ) {
                payloadTry++
                delay(QR_PAYLOAD_RETRY_MS)
                MeshLog.log(
                    "Moskvenok",
                    "load: документ без payload, повтор $payloadTry/$QR_PAYLOAD_ATTEMPTS (force-refresh)"
                )
                document = withContext(Dispatchers.IO) {
                    MoskvenokApi.document(sid, deviceId, docId, refresh = true)
                }
                if (document.optInt("refresh") != 0 && document.has("message")) {
                    processing = true
                    MeshLog.log("Moskvenok", "load: документ — 202 обработка (повтор)")
                    return true
                }
            }
            if (MoskvenokApi.barcodePayload(document) == null) {
                MeshLog.log("Moskvenok", "load: payload так и не пришёл")
                throw MoskvenokApi.MosError("empty barcode")
            }
            doc = document
            errorText = null
            applyDocTiming(document)
            MeshLog.log("Moskvenok", 
                "load: isEnabled=true doc=$docId valid=${document.optLong("barcode_valid_time")} ms" +
                    (if (refreshQr) " (QR обновлён)" else "")
            )
        } catch (error: Exception) {
            // Падение documents не должно сбрасывать уже показанное состояние.
            MeshLog.log("Moskvenok", 
                "load: documents ошибка${if (refreshQr) " при обновлении QR (игнор)" else ""}: $error"
            )
            if (!refreshQr) errorText = describeMoskvenok(error)
        }
        return true
    }

    fun load(refreshQr: Boolean = false) {
        scope.launch {
            if (refreshQr) qrRefreshing = true else loading = true
            try {
                refreshAll(refreshQr)
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
                // Flow: POST settings/docs — только «если выключен». Если GET objects
                // уже отдаёт type: mesh в groups — не шлём (в т.ч. на кнопке повтора).
                val alreadyEnabled = objects?.let { MoskvenokApi.isEnabled(it) } == true
                if (alreadyEnabled) {
                    MeshLog.log("Moskvenok", 
                        "enable: уже включено (type: mesh в groups) — POST не шлём, берём документ"
                    )
                } else {
                    MeshLog.log("Moskvenok", 
                        """enable: POST /api/v3/edm/mp/settings/docs {"profile":[22],"favorites":[22]}"""
                    )
                    withContext(Dispatchers.IO) { MoskvenokApi.enable(session(), deviceId) }
                    MeshLog.log("Moskvenok", "enable: POST принят — берём документ по uuid (как official, flow)")
                }
                // Официальное приложение после POST сразу запрашивает документ по uuid
                // и не перепроверяет groups — делаем так же.
                val docId = MoskvenokApi.meshDocId(objects ?: JSONObject(), store.personId)
                if (docId == null) {
                    MeshLog.log("Moskvenok", "enable: нет uuid ученика — документ не запросить")
                    errorText = Words.MoskvenokEnableFailed.text
                } else {
                    // Пока payload не пришёл — doc не ставим: иначе пустой квадрат.
                    // После POST сервер отвечает 202 «Идет обработка документов»:
                    // первая попытка ждёт до 45 с, следующие (с x-force-refresh)
                    // добиваются именно непустой строки barcode_payload.
                    var fetched: JSONObject? = null
                    for (attempt in 1..QR_PAYLOAD_ATTEMPTS) {
                        if (attempt > 1) delay(QR_PAYLOAD_RETRY_MS)
                        try {
                            val document = withContext(Dispatchers.IO) {
                                MoskvenokApi.document(
                                    session(), deviceId, docId,
                                    refresh = attempt > 1,
                                    processingBudgetMs = if (attempt == 1) 45_000L else 20_000L
                                )
                            }
                            fetched = document
                            if (MoskvenokApi.barcodePayload(document) != null) {
                                MeshLog.log("Moskvenok", "enable: документ получен, попытка $attempt")
                                break
                            }
                            if (document.optInt("refresh") != 0 && document.has("message")) {
                                processing = true
                                MeshLog.log(
                                    "Moskvenok",
                                    "enable: документ, попытка $attempt — 202 обработка, ждём"
                                )
                                break
                            }
                            MeshLog.log(
                                "Moskvenok",
                                "enable: документ, попытка $attempt — без barcode_payload"
                            )
                        } catch (docError: Exception) {
                            MeshLog.log("Moskvenok", "enable: документ, попытка $attempt: $docError")
                        }
                    }
                    val document = fetched?.takeIf {
                        MoskvenokApi.barcodePayload(it) != null
                    }
                    if (document != null) {
                        doc = document
                        processing = false
                        applyDocTiming(document)
                        errorText = null
                        MeshLog.log("Moskvenok", 
                            "enable: QR doc=$docId " +
                                "(objects включена: ${objects?.let { MoskvenokApi.isEnabled(it) } ?: false})"
                        )
                    } else {
                        // Документ не пришёл — сверяемся с objects и честно сообщаем.
                        val enabledNow = refreshAll(false)
                        if (!enabledNow && !processing && errorText == null) {
                            MeshLog.log("Moskvenok", "enable: не подтвердилось")
                            errorText = Words.MoskvenokEnableFailed.text
                        }
                    }
                }
            } catch (error: Exception) {
                MeshLog.log("Moskvenok", "enable: ошибка: $error")
                errorText = describeMoskvenok(error)
            } finally {
                enabling = false
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    // Сервер асинхронно генерирует документы (202) — опрашиваем сами.
    LaunchedEffect(processing) {
        if (!processing) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        var lastRenew = 0L
        while (processing) {
            delay(3000)
            // 202 может висеть на уровне сессии: sid, созданный в момент
            // зависшей обработки, наследует флаг refresh и не сбрасывается —
            // пересоздание сессии происходит только при 401/403, а 202 не
            // ошибка авторизации. Раз в 2 минуты (первый раз — через 60 с)
            // обнуляем sid: свежий sid здорового аккаунта отдаёт 200.
            val now = System.currentTimeMillis()
            val due = if (lastRenew == 0L) {
                now - startedAt >= 60_000
            } else {
                now - lastRenew >= 120_000
            }
            if (due) {
                lastRenew = now
                MeshLog.log("Moskvenok", "session: 202 не проходит — пересоздаём сессию (свежий sid)")
                store.mosIdSession = null
            }
            refreshAll(false)
        }
    }

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
            // 202 «Идет обработка документов» — сервер собирает документы.
            processing && doc == null -> MoskvenokProcessingCard(
                onRetry = { enableMoskvenok() },
                retrying = enabling
            )
            // QR показываем, пока есть документ: включение подтверждается именно
            // им (официальное приложение не смотрит groups после POST).
            doc == null && !MoskvenokApi.isEnabled(loadedObjects) -> GlassCard(
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
            // Карта включена, но documents не пришёл — не крутим спиннер вечно.
            doc == null ->
                if (error != null) MoskvenokErrorCard(error) { load() } else MoskvenokLoader()
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
                                    onClick = {
                                        MeshLog.log("Moskvenok", "qr: нажат refresh — новый barcode_payload")
                                        load(refreshQr = true)
                                    },
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
        Spacer(Modifier.height(LocalLiquidBottomPadding.current))
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
private fun MoskvenokProcessingCard(onRetry: () -> Unit, retrying: Boolean) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                Words.MoskvenokProcessing.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Серверная обработка может зависнуть — повторный POST settings/docs
            // перепоставляет документы в очередь (то же, что жмёт official).
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry, enabled = !retrying) {
                if (retrying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(Words.MoskvenokRetryEnable.text)
            }
        }
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
