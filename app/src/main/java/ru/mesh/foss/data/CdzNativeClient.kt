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

    suspend fun loadChallenge(challenge: String): JSONObject = withContext(Dispatchers.IO) {
        setChallengeId(challenge)
        val info = request("GET", "$SECURE/challenge/$challenge/info", null)
        val user = request("GET", "$SECURE/auth/aclx/user_info", null)
        MeshLog.log("CDZ", "challenge: HTTP ${info.status}, user: HTTP ${user.status}")
        JSONObject().put("info", runCatching { JSONObject(info.body) }.getOrDefault(JSONObject()))
            .put("user", runCatching { JSONObject(user.body) }.getOrDefault(JSONObject()))
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
        MeshLog.log("CDZ", "start: HTTP ${result.status}")
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
