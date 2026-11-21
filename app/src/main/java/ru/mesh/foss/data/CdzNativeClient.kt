package ru.mesh.foss.data

import ru.mesh.foss.core.MeshLog
import ru.mesh.foss.core.Sensitive

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Нативный клиент ЦДЗ (`uchebnik.mos.ru`) по docs/cdz_api.md.
 *
 * Работает в отдельном окне: держит cookie-сессию WebView и accessTokenEom,
 * который обновляет сам. Все чувствительные заголовки пишутся только в лог
 * через [Sensitive]. Класс не делает автосохранения: ответ уходит на сервер
 * только явным вызовом [sendAnswer].
 */
class CdzNativeClient(
    private val pageUrl: String,
    private val token: String?,
    private val profileId: String?,
    private val userId: String?,
    private val personId: String?,
    private val cookies: Map<String, String>,
) {
    companion object {
        private const val BASE = "https://uchebnik.mos.ru"
        private const val SECURE = "$BASE/webtests/exam/rest/secure"
        // uchebnik привязывает сессию к desktop UA: с Android UA refresh отдаёт 401.
        private const val UA = "Mozilla/5.0 (X11; Linux x86_64; rv:160.0) Gecko/20100101 Firefox/160.0"
        const val PROFILE_KEY = "cdz_native_profile_v1"

        /** uchebnik ждёт профиль school (1002991417), а не student_id (36847597). */
        fun profileJson(profileId: String?, userId: String?): String =
            JSONObject().put("id", profileId.orEmpty()).put("type", "student").toString()

        /**
         * Клиент из активной сессии приложения: те же cookie, токен и профиль,
         * что и у WebView. Нужен и для прохождения, и для нативного результата.
         */
        fun fromSession(context: Context, pageUrl: String): CdzNativeClient {
            val store = SessionStore(context, AccountRegistry(context).activeScope())
            val jar = CookieJson.parse(store.authCookies).toMutableMap()
            CookieManager.getInstance().getCookie(BASE)
                ?.split(';')?.forEach { pair ->
                    val key = pair.substringBefore('=').trim()
                    val value = pair.substringAfter('=').trim()
                    if (key.isNotBlank() && value.isNotBlank()) jar[key] = value
                }
            val token = jar["aupd_token"]?.takeIf { it.isNotBlank() } ?: store.token
            MeshLog.log(
                "CDZ",
                "session: cookies=${jar.size} есть токен=${!token.isNullOrBlank()}"
            )
            return CdzNativeClient(
                pageUrl = pageUrl,
                token = token,
                profileId = store.studentId,
                userId = jar["obr_id"],
                personId = store.personId,
                cookies = jar,
            )
        }
    }

    private var accessToken: String = token.orEmpty()
    private val jar = LinkedHashMap(cookies)
    private val boundary = "----meshfosscdz" + System.currentTimeMillis().toString(16)

    @Volatile
    private var challengeId: String = ""

    /** accessTokenEom (15 мин) и его claims — User-Id/Profile-Id для uchebnik. */
    private val tokenClaims: JSONObject = runCatching {
        val part = accessToken.substringAfter('.').substringBefore('.')
        val padded = part + "=".repeat((4 - part.length % 4) % 4)
        JSONObject(String(java.util.Base64.getUrlDecoder().decode(padded), Charsets.UTF_8))
    }.getOrDefault(JSONObject())
    private var eomUserId: String = tokenClaims.optString("usr").takeIf { it.isNotBlank() }
        ?: tokenClaims.optString("sub").orEmpty()
    private var eomProfileId: String = tokenClaims.optJSONArray("prf")
        ?.optJSONObject(0)?.optString("id").orEmpty()
    private var eomProfileJson: String = profileJson(eomProfileId, eomUserId)

    fun setChallengeId(value: String) { challengeId = value }
    fun updateCookie(name: String, value: String) {
        if (name.isNotBlank() && value.isNotBlank()) jar[name] = value
    }
    fun currentToken(): String? = token
    fun currentProfileId(): String? = profileId
    fun currentUserId(): String? = userId
    fun currentPersonId(): String? = personId
    fun currentCookies(): Map<String, String> = jar.toMap()

    private fun params(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val query = pageUrl.substringAfter('?', "")
        query.split('&').forEach { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            if (key.isNotBlank() && value.isNotBlank()) out[URLEncoder.encode(key, "UTF-8")] = value
        }
        return out
    }

    private fun param(name: String): String {
        val query = pageUrl.substringAfter('?', "")
        return query.split('&').firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "").orEmpty()
    }

    private fun cookieHeader(): String = jar.entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun baseHeaders(contentType: String): Map<String, String> = buildMap {
        put("User-Agent", UA)
        put("Accept", "application/json;charset=UTF-8")
        put("Content-Type", contentType)
        put("User-Id", eomUserId.ifBlank { userId.orEmpty() })
        put("Profile-Id", eomProfileId.ifBlank { profileId.orEmpty() })
        put("Profile", eomProfileJson.ifBlank { profileJson(profileId, userId) })
        if (accessToken.isNotBlank()) put("Authorization", "Bearer $accessToken")
        put("X-Mes-Subsystem", "testw")
        put("Origin", BASE)
        put("Referer", pageUrl)
        if (jar.isNotEmpty()) put("Cookie", cookieHeader())
    }

    private data class HttpResult(val status: Int, val body: String, val headers: Map<String, List<String>>)

    private fun request(method: String, url: String, body: String?, contentType: String = "application/json"): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        baseHeaders(contentType).forEach { (key, value) -> if (value.isNotBlank()) connection.setRequestProperty(key, value) }
        if (body != null) {
            connection.doOutput = true
            val bytes = body.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
        }
        return try {
            val status = connection.responseCode
            val stream = runCatching { connection.inputStream }.getOrNull() ?: connection.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
            HttpResult(status, text, connection.headerFields.mapValues { it.value.toList() })
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Обмен cookie-сессии на 15-минутный accessTokenEom. Это НЕ создание попытки —
     * только авторизация (docs §3.5 и §16.1). Повторяем перед работой, т.к. токен живёт 15 мин.
     */
    suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
        val headers = buildMap {
            put("User-Agent", UA)
            put("Accept", "application/json;charset=UTF-8")
            put("Content-Type", "application/json")
            // docs §16.1: первый refresh уходит без Bearer и пользовательских ID —
            // сервер выдаёт accessTokenEom и profileId по cookie-сессии.
            put("X-Mes-Subsystem", "testw")
            put("X-Mes-Subsystem", "testw")
            put("Origin", BASE)
            put("Referer", pageUrl)
            if (jar.isNotEmpty()) put("Cookie", cookieHeader())
        }
        val connection = URL("$BASE/acl/api/session/v2/refresh").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        headers.forEach { (key, value) -> if (value.isNotBlank()) connection.setRequestProperty(key, value) }
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(4)
        connection.outputStream.use { it.write("null".toByteArray(Charsets.UTF_8)) }
        val status: Int
        val text: String
        val cookies: List<String>
        try {
            status = connection.responseCode
            cookies = connection.headerFields["Set-Cookie"].orEmpty()
            val stream = runCatching { connection.inputStream }.getOrNull() ?: connection.errorStream
            text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
        MeshLog.log("CDZ", "refresh: HTTP $status setCookie=${cookies.size}")
        if (status !in 200..299) {
            MeshLog.log("CDZ", "refresh body: ${text.take(200)}")
            return@withContext false
        }
        cookies.forEach { raw ->
            val pair = raw.substringBefore(';')
            val name = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=').trim()
            if (name.isNotBlank() && value.isNotBlank()) jar[name] = value
        }
        val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
        json.optString("accessTokenEom").takeIf { it.isNotBlank() }?.let { accessToken = it }
        json.optString("profileId").takeIf { it.isNotBlank() }?.let { id ->
            eomProfileId = id
            val prf = tokenClaims.optJSONArray("prf")
            var found = JSONObject().put("id", id).put("type", "student")
            if (prf != null) for (i in 0 until prf.length()) {
                val p = prf.optJSONObject(i) ?: continue
                if (p.optString("id") == id) { found = p; break }
            }
            eomProfileJson = found.toString()
        }
        val claims = runCatching {
            val part = accessToken.substringAfter('.').substringBefore('.')
            val padded = part + "=".repeat((4 - part.length % 4) % 4)
            JSONObject(String(java.util.Base64.getUrlDecoder().decode(padded), Charsets.UTF_8))
        }.getOrDefault(JSONObject())
        claims.optString("usr").takeIf { it.isNotBlank() }?.let { eomUserId = it }
        MeshLog.log(
            "CDZ",
            "refresh ok: user=${eomUserId.isNotBlank()} profile=${eomProfileId.isNotBlank()} " +
                "token=${accessToken.isNotBlank()}"
        )
        accessToken.isNotBlank()
    }

    /**
     * Метаданные видео-материала: `custom_meta.file_url` и субтитры.
     *
     * Это только чтение. Если accessTokenEom ещё нет или он истёк, сначала
     * обновляем сессию; повторяем запрос один раз после 401.
     */
    suspend fun loadMaterial(materialId: String): JSONObject? = withContext(Dispatchers.IO) {
        if (materialId.isBlank()) return@withContext null
        if (accessToken.isBlank()) refreshSession()
        fun fetch(): HttpResult = request(
            "GET",
            "$BASE/cms/api/v5/materials/$materialId",
            null,
        )
        var result = fetch()
        if (result.status == 401 && refreshSession()) result = fetch()
        if (result.status !in 200..299) {
            MeshLog.log("CDZ", "material: HTTP ${result.status}")
            return@withContext null
        }
        runCatching { JSONObject(result.body) }.getOrNull()
    }

    /**
     * Лаунчер уже содержит activity_url и все параметры запуска (§4.1 дока):
     * context, registration, actor, role, endpoint, fetch. Собираем страницу
     * ЦДЗ сразу, без дополнительного сетевого перехода.
     */
    fun derivePageUrl(startUrl: String): String? {
        val query = startUrl.substringAfter('?', "")
        val params = LinkedHashMap<String, String>()
        query.split('&').forEach { pair ->
            if (pair.isBlank()) return@forEach
            val key = runCatching { URLDecoder.decode(pair.substringBefore('='), "UTF-8") }.getOrNull()
            val value = runCatching { URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8") }.getOrNull()
            if (!key.isNullOrBlank() && value != null) params[key] = value
        }
        val activity = params.remove("activity_url")?.takeIf {
            it.startsWith("http://") || it.startsWith("https://")
        } ?: return null
        val rest = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        MeshLog.log(
            "CDZ",
            "derivePageUrl: activity=${activity.substringBefore('?').take(180)} params=${params.keys}"
        )
        return activity + if (rest.isBlank()) "" else "?$rest"
    }
    suspend fun resolveChallengeUrl(startUrl: String): String = withContext(Dispatchers.IO) {
        var current = startUrl
        val seen = mutableSetOf<String>()
        repeat(12) { hop ->
            if (seen.contains(current)) {
                throw IllegalStateException("цикл редиректов на шаге $hop")
            }
            seen.add(current)
            MeshLog.log("CDZ", "redirect #$hop -> ${current.substringBefore('?').take(180)}")
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/147.0.0.0 Mobile Safari/537.36")
            if (token.orEmpty().isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            if (jar.isNotEmpty()) connection.setRequestProperty("Cookie", cookieHeader())

            val status: Int
            val body: String
            val location: String?
            val setCookies: List<String>
            try {
                status = connection.responseCode
                location = connection.getHeaderField("Location")
                setCookies = connection.headerFields["Set-Cookie"].orEmpty()
                val stream = runCatching { connection.inputStream }.getOrNull() ?: connection.errorStream
                body = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
            } finally {
                connection.disconnect()
            }
            setCookies.forEach { raw ->
                val pair = raw.substringBefore(';')
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=').trim()
                if (name.isNotBlank() && value.isNotBlank()) {
                    jar[name] = value
                    MeshLog.trace("CDZ") { "cookie $name обновлена" }
                }
            }
            MeshLog.log(
                "CDZ",
                "redirect #$hop: HTTP $status " +
                    "location=${location?.substringBefore('?')?.take(120).orEmpty()} body=${body.take(120)}"
            )

            val candidates = listOfNotNull(
                location,
                body.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.trim(),
                Regex("https?://[^\"'<>\\ ]+").find(body)?.value
            )
            val next = candidates.firstOrNull {
                it.contains("/exam/challenge/") || it.startsWith("http")
            }
            if (current.contains("/exam/challenge/")) return@withContext current
            if (next == null) {
                throw IllegalStateException("редирект не найден: HTTP $status")
            }
            current = next.replace("&amp;", "&")
        }
        throw IllegalStateException("не удалось дойти до страницы ЦДЗ")
    }
    suspend fun loadChallenge(challenge: String): JSONObject = withContext(Dispatchers.IO) {
        setChallengeId(challenge)
        val info = request("GET", "$SECURE/challenge/$challenge/info", null)
        val user = request("GET", "$SECURE/auth/aclx/user_info", null)
        MeshLog.log("CDZ", "challenge: HTTP ${info.status}, user: HTTP ${user.status}")
        JSONObject().put("info", runCatching { JSONObject(info.body) }.getOrDefault(JSONObject()))
            .put("user", runCatching { JSONObject(user.body) }.getOrDefault(JSONObject()))
    }

    /**
     * Проверяем info до старта: если попытка уже идёт, start-attempt всё равно
     * продолжит её, но мы явно логируем число начатых попыток и last_attempt_id.
     * Это защита от случайного создания новой попытки.
     */
    suspend fun checkAttempts(challenge: String): JSONObject = withContext(Dispatchers.IO) {
        setChallengeId(challenge)
        val info = request("GET", "$SECURE/challenge/$challenge/info", null)
        val json = runCatching { JSONObject(info.body) }.getOrDefault(JSONObject())
        val attempts = json.optInt("number_of_attempt", 0)
        val last = json.optString("last_attempt_id", "")
        MeshLog.log(
            "CDZ",
            "попытки: number_of_attempt=$attempts last_attempt_id=$last HTTP=${info.status}"
        )
        json
    }
    /** История попыток: assignments/my -> context/{id}/result (docs §5). */
    suspend fun attemptHistory(challenge: String): JSONObject = withContext(Dispatchers.IO) {
        setChallengeId(challenge)
        val mine = request("GET", "$SECURE/challenge/$challenge/assignments/my", null)
        val mineJson = runCatching { JSONObject(mine.body) }.getOrDefault(JSONObject())
        val contextId = mineJson.optJSONObject("assignment")?.optLong("assignment_context_id") ?: 0L
        MeshLog.log("CDZ", "history: assignments/my HTTP=${mine.status} context=$contextId")
        val attempts = JSONArray()
        if (contextId > 0L) {
            val history = request(
                "GET",
                "$SECURE/challenge/assignment/context/$contextId/result",
                null
            )
            val historyJson = runCatching { JSONObject(history.body) }.getOrDefault(JSONObject())
            val results = historyJson.optJSONArray("user_results")
            if (results != null) {
                for (i in 0 until results.length()) {
                    val list = results.optJSONObject(i)?.optJSONArray("attempts") ?: continue
                    for (j in 0 until list.length()) {
                        val item = list.optJSONObject(j) ?: continue
                        attempts.put(item)
                    }
                }
            }
            MeshLog.log("CDZ", "history: попыток=${attempts.length()}")
        }
        mineJson.put("attempts", attempts)
    }
    /** Старт или продолжение попытки — один и тот же вызов (docs §6.1). */
    suspend fun startAttempt(challenge: String): JSONObject = withContext(Dispatchers.IO) {
        setChallengeId(challenge)
        val accountName = "77:$personId"
        val actor = JSONObject().put("objectType", "Agent").put(
            "account", JSONObject().put("name", accountName)
        ).toString()
        val body = JSONObject()
            .put("actorParam", actor)
            .put("registrationParam", param("registration"))
            .put("activityIdParam", param("activityId").ifBlank { "$BASE/exam/challenge/$challenge" })
            .put("resultOverviewUrl", "$BASE/exam/challenge/$challenge/attempt/{attemptId}")
            .put(
                "resultManagementUrl",
                "$BASE/exam/challenge/$challenge/context/{assignmentContextId}/attempt/{attemptId}"
            )
            .put("context", param("context"))
            .put("contextCorrupted", false)
            .toString()
        val result = request("POST", "$SECURE/challenge/$challenge/start-attempt", body)
        val started = runCatching { JSONObject(result.body) }.getOrDefault(JSONObject())
        MeshLog.log(
            "CDZ",
            "start-attempt: HTTP ${result.status} attemptId=${started.optLong("challenge_attempt_id")} " +
                "startedAt=${started.optString("challenge_attempt_started_at")} " +
                "elongated=${started.has("challenge_attempt_started_at")}"
        )
        if (result.status !in 200..299) {
            throw IllegalStateException("start-attempt HTTP ${result.status}")
        }
        JSONObject(result.body)
    }

    /** Отправка одного ответа на сервер. Вызывается только кнопкой «Сохранить». */
    suspend fun sendAnswer(taskId: Long, attemptId: Long, answer: JSONObject) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("challenge_task_id", taskId)
            .put("challenge_attempt_id", attemptId)
            .put("answer", answer)
            .toString()
        val form = "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"request\"; filename=\"blob\"\r\n" +
            "Content-Type: application/json\r\n\r\n" +
            payload + "\r\n--$boundary--\r\n"
        val result = request(
            "POST",
            "$SECURE/challenge/task/answer",
            form,
            "multipart/form-data; boundary=$boundary"
        )
        MeshLog.log("CDZ", "answer $taskId: HTTP ${result.status}")
        if (result.status !in 200..299) {
            throw IllegalStateException("answer HTTP ${result.status}")
        }
    }

    suspend fun completeAttempt(challenge: String) = withContext(Dispatchers.IO) {
        setChallengeId(challenge)
        val result = request(
            "POST",
            "$SECURE/challenge/$challenge/complete_attempt",
            JSONObject().put("challenge_id", challenge.toIntOrNull() ?: 0).toString()
        )
        MeshLog.log("CDZ", "complete: HTTP ${result.status}")
        if (result.status !in 200..299) {
            throw IllegalStateException("complete HTTP ${result.status}")
        }
    }

    suspend fun attemptResult(challenge: String, attemptId: Long): JSONObject = withContext(Dispatchers.IO) {
        val result = request("GET", "$SECURE/challenge/$challenge/attempt/$attemptId/result", null)
        MeshLog.log("CDZ", "result: HTTP ${result.status}")
        runCatching { JSONObject(result.body) }.getOrDefault(JSONObject())
    }

    fun cookiePairsFromWeb(): Map<String, String> {
        val raw = runCatching { CookieManager.getInstance().getCookie(BASE) }.getOrNull().orEmpty()
        val result = LinkedHashMap(jar)
        raw.split(';').map { it.trim() }.filter { it.contains('=') }.forEach { pair ->
            val key = pair.substringBefore('=').trim()
            val value = pair.substringAfter('=').trim()
            if (key.isNotBlank() && value.isNotBlank()) result[key] = value
        }
        return result
    }
}
