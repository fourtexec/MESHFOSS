package ru.mesh.foss.data

import ru.mesh.foss.core.MeshLog
import ru.mesh.foss.core.Sensitive

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
        const val PROFILE_KEY = "cdz_native_profile_v1"

        fun profileJson(profileId: String?, userId: String?): String =
            JSONObject().put("id", profileId.orEmpty()).put("type", "student").toString()
    }

    private var accessToken: String = token.orEmpty()
    private val jar = LinkedHashMap(cookies)
    private val boundary = "----meshfosscdz" + System.currentTimeMillis().toString(16)

    @Volatile
    private var challengeId: String = ""

    fun setChallengeId(value: String) {
        challengeId = value
    }
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
            if (key.isNotBlank() && value.isNotBlank()) {
                out[URLEncoder.encode(key, "UTF-8")] = value
            }
        }
        return out
    }

    private fun param(name: String): String {
        val query = pageUrl.substringAfter('?', "")
        return query.split('&').firstOrNull {
            it.substringBefore('=') == name
        }?.substringAfter('=', "").orEmpty()
    }

    private fun cookieHeader(): String = jar.entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun baseHeaders(contentType: String): Map<String, String> = buildMap {
        put("Accept", "application/json;charset=UTF-8")
        put("Content-Type", contentType)
        put("User-Id", userId.orEmpty())
        put("Profile-Id", profileId.orEmpty())
        put("Profile", profileJson(profileId, userId))
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
        baseHeaders(contentType).forEach { (key, value) -> connection.setRequestProperty(key, value) }
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
            val headers = connection.headerFields.mapValues { it.value.toList() }
            HttpResult(status, text, headers)
        } finally {
            connection.disconnect()
        }
    }

    /** Первый токен uchebnik можно получить по cookie без Bearer (docs §13.7). */
    suspend fun refreshSession() = withContext(Dispatchers.IO) {
        val result = request("POST", "$BASE/acl/api/session/v2/refresh", "null")
        MeshLog.log("CDZ", "refresh: HTTP ${result.status}")
        runCatching {
            val json = JSONObject(result.body)
            json.optString("accessTokenEom").takeIf { it.isNotBlank() }?.let { accessToken = it }
            json.optString("accessTokenAupd").takeIf { it.isNotBlank() }?.let { jar["aupd_token"] = it }
            json.optString("profileId").takeIf { it.isNotBlank() }?.let { jar["Session-Eom-Profile"] = it }
        }
        result.status in 200..299
    }

    /**
     * Нативный проход по цепочке лаунчера school.mos.ru -> uchebnik.mos.ru.
     * HttpURLConnection не идёт за редиректами сам: мы читаем Location/тело,
     * сохраняем Set-Cookie и повторяем запрос с обновлённым jar'ом.
     */
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
        MeshLog.log("CDZ", "derivePageUrl: activity=$activity params=${params.keys}")
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
            MeshLog.log("CDZ", "redirect #$hop -> ${current.take(180)}")
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
            MeshLog.log("CDZ", "redirect #$hop: HTTP $status location=${location?.take(120).orEmpty()} body=${body.take(120)}")

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
        val actor = JSONObject().put("objectType", "Agent").put(
            "account", JSONObject().put("name", "77:$personId")
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
