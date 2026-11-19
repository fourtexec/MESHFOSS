package ru.mesh.foss

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Карта «Москвёнок» (id.mos.ru): получение сессии «Моя ID» и API экрана.
 *
 * Сессия — порт стадии 2 PoC `mosid_full_poc.py`, ровно как она работает:
 * обычный плоский cookie jar (store.authCookies, name→value) шлётся везде как
 * есть; GET /api/v3/edm/auth/login/{device}/{platform} отдаёт 302 на OAuth и
 * ставит cookie `sessionid`; убираем prompt=login, идём по OAuth-цепочке
 * (SSO от QR/веб-входа) до id.mos.ru callback, в HTML — mosid:// ссылка с
 * session_id. Сессия кэшируется в SessionStore.mosIdSession.
 *
 * API (flow.txt) авторизуется только заголовком x-sessionid, плюс x-deviceid
 * (android id) и user-agent официального приложения «Мой ID».
 *
 * Файл без классов Android (java/net + org.json) — покрыт JVM-тестами.
 */
object MoskvenokApi {

    private const val ID = "https://id.mos.ru"
    private const val PLATFORM = "android"
    private const val CALLBACK_MARK = "/api/v1/edm/auth/login_callback/"

    /** User-agent официального приложения «Мой ID» — из captured-запросов flow.txt. */
    private const val API_UA = "mosdoc/2.17.0-store.3005 (Android; ONEPLUS CPH2719; 16)"

    /** UA стадии 2 PoC (OAuth-страницы login.mos.ru). */
    private const val OAUTH_UA =
        "Mozilla/5.0 (X11; Linux x86_64; rv:160.0) Gecko/20100101 Firefox/160.0"

    /** Куки стадии 1, привязанные к конкретному OAuth-запросу (PoC drop_auth_cookies). */
    private val EPHEMERAL = setOf("fm", "origin", "oauth_az", "css", "forLinking")

    /** POST /api/v3/edm/mp/settings/docs — включает Москвёнок (content-length 33). */
    private const val ENABLE_BODY = "{\"profile\":[22],\"favorites\":[22]}"

    private val MOSID_LINK = Regex("mosid://[^\"'\\s<>]+")
    private val SESSION_ID_PARAM = Regex("[?&]session_id=([^&\"'\\s<>]+)")

    /** code = 401 — сессия невалидна (нужно получить заново); -1 — сессии нет вообще. */
    class MosError(message: String, val code: Int = 0) : Exception(message)

    // ------------------------------------------------------------- сессия --

    /**
     * Сессия id.mos.ru по cookie jar'у QR/веб-входа — стадия 2 PoC.
     * Возвращает sessionid либо null, если SSO-сессия не активна.
     */
    fun obtainSession(jar: Map<String, String>, device: String): String? {
        val cookies = LinkedHashMap(jar)
        cookies.keys.removeAll(EPHEMERAL)

        val htmlAccept = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )

        // 1. GET /auth/login/{device}/{platform} -> 302 на OAuth entry + cookie sessionid.
        val start = http(
            "$ID/api/v3/edm/auth/login/$device/$PLATFORM",
            cookies,
            follow = false,
            headers = htmlAccept
        )
        val location = start.location ?: return null
        if (start.code != 302) return null
        start.cookies["sessionid"]?.takeIf { it.isNotBlank() }?.let { cookies["sessionid"] = it }

        // 2. OAuth-цепочка без prompt=login — переиспользуем SSO от входа (PoC).
        var url = stripParam(location, "prompt")
        var reachedCallback = false
        var html = ""
        for (hop in 0 until 15) {
            val response = http(url, cookies, follow = false, headers = htmlAccept)
            val next = response.location
            if (response.code in 300..399 && next != null) {
                val target = resolve(url, next)
                if (target.contains(CALLBACK_MARK)) {
                    html = http(target, cookies, follow = false, headers = htmlAccept).body
                    reachedCallback = true
                    break
                }
                url = target
            } else {
                break
            }
        }
        if (!reachedCallback) return null

        // 3. session_id из mosid:// ссылки (иначе cookie sessionid, который ставит callback).
        return sessionFromMosid(html) ?: cookies["sessionid"]?.takeIf { it.isNotBlank() }
    }

    // ---------------------------------------------------------------- API --

    /** GET /api/v3/edm/mp/objects — есть ли «Москвёнок» (type: mesh в groups). */
    fun objects(session: String, device: String): JSONObject =
        api("GET", "$ID/api/v3/edm/mp/objects", session, device, refresh = true)

    /** POST /api/v3/edm/mp/settings/docs {"profile":[22],"favorites":[22]} — включает карту. */
    fun enable(session: String, device: String): JSONObject =
        api("POST", "$ID/api/v3/edm/mp/settings/docs", session, device,
            body = ENABLE_BODY, allowEmpty = true)

    /**
     * GET /api/v3/edm/mp/documents/{docId}?type=mesh — QR-пейлоад и поля.
     * [refresh] = true добавляет x-force-refresh — для нового barcode_payload.
     */
    fun document(session: String, device: String, docId: String, refresh: Boolean = false): JSONObject =
        api("GET", "$ID/api/v3/edm/mp/documents/$docId?type=mesh", session, device, refresh = refresh)

    /** Включена ли карта: items[].type == "mesh" (или title «Москвёнок») в groups. */
    fun isEnabled(objects: JSONObject): Boolean {
        val groups = objects.optJSONArray("groups") ?: return false
        for (i in 0 until groups.length()) {
            val group = groups.optJSONObject(i) ?: continue
            if (group.optString("title") == "Москвёнок") return true
            val items = group.optJSONArray("items") ?: JSONArray()
            for (j in 0 until items.length()) {
                if (items.optJSONObject(j)?.optString("type") == "mesh") return true
            }
        }
        return false
    }

    /** id документа для GET /documents/…: из objects, иначе сборка из uuid ученика. */
    fun meshDocId(objects: JSONObject, studentId: String?): String? {
        val groups = objects.optJSONArray("groups")
        if (groups != null) {
            for (i in 0 until groups.length()) {
                val items = groups.optJSONObject(i)?.optJSONArray("items") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    if (item.optString("type") == "mesh") {
                        item.optString("id").takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
        }
        return studentId?.takeIf { it.isNotBlank() }?.let { "combined/mesh/${it}_mesh_bal" }
    }

    /** session_id из HTML с mosid:// ссылкой (window.location = "mosid://auth?…"). */
    internal fun sessionFromMosid(html: String): String? =
        MOSID_LINK.find(html)?.value?.let { link ->
            SESSION_ID_PARAM.find(link)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        }

    /** Убирает query-параметр [name] из URL (PoC strip_param: prompt=login). */
    internal fun stripParam(url: String, name: String): String = try {
        val uri = URI(url)
        val query = uri.query ?: url
        val parts = query.split('&')
        val kept = parts.filterNot { it.startsWith("$name=") }
        if (kept.size == parts.size) url
        else {
            URI(
                uri.scheme, uri.authority, uri.path,
                kept.joinToString("&").ifBlank { null },
                uri.fragment
            ).toString()
        }
    } catch (error: Exception) {
        url
    }

    // -------------------------------------------------------------- сетевая --

    private class Response(
        val code: Int,
        val location: String?,
        val cookies: Map<String, String>,
        val body: String
    )

    private fun api(
        method: String,
        url: String,
        session: String,
        device: String,
        refresh: Boolean = false,
        body: String? = null,
        allowEmpty: Boolean = false
    ): JSONObject {
        val headers = mutableMapOf(
            "User-Agent" to API_UA,
            "x-sessionid" to session,
            "x-deviceid" to device
        )
        if (refresh) headers["x-force-refresh"] = "true"
        // Не следуем за редиректами: 3xx на API = сессия невалидна.
        val response = http(url, follow = false, method = method, body = body, headers = headers)
        if (response.code in 300..399 || response.code == 401 || response.code == 403) {
            throw MosError("unauthorized", 401)
        }
        if (response.code !in 200..299) throw MosError("HTTP ${response.code}", response.code)
        if (response.body.isBlank()) {
            if (allowEmpty) return JSONObject()
            throw MosError("empty response", response.code)
        }
        return try {
            JSONObject(response.body)
        } catch (error: Exception) {
            throw MosError("invalid json", response.code)
        }
    }

    private fun http(
        url: String,
        cookies: Map<String, String> = emptyMap(),
        follow: Boolean = true,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap()
    ): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = follow
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.requestMethod = method
            connection.setRequestProperty("Accept-Encoding", "gzip")
            // UA по умолчанию — OAuth-страницы; API передаёт свой (headers перезапишет).
            connection.setRequestProperty("User-Agent", OAUTH_UA)
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (cookies.isNotEmpty()) {
                connection.setRequestProperty(
                    "Cookie",
                    cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                )
            }
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val responseCookies = mutableMapOf<String, String>()
            connection.headerFields?.forEach { (key, values) ->
                if (key.equals("Set-Cookie", ignoreCase = true)) {
                    values.forEach { raw ->
                        val pair = raw.substringBefore(';')
                        val separator = pair.indexOf('=')
                        if (separator > 0) {
                            responseCookies[pair.substring(0, separator).trim()] =
                                pair.substring(separator + 1).trim()
                        }
                    }
                }
            }
            return Response(
                code = code,
                location = connection.getHeaderField("Location"),
                cookies = responseCookies,
                body = readBody(connection, code)
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Ответ может быть gzip (Accept-Encoding: gzip — как в flow.txt). */
    private fun readBody(connection: HttpURLConnection, code: Int): String = try {
        val stream = if (code >= 400) connection.errorStream else connection.inputStream
        val raw = stream?.use { it.readBytes() } ?: ByteArray(0)
        val encoding = connection.getHeaderField("Content-Encoding").orEmpty()
        val bytes = if (encoding.contains("gzip", ignoreCase = true)) {
            GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
        } else {
            raw
        }
        String(bytes, Charsets.UTF_8)
    } catch (error: Exception) {
        ""
    }

    private fun resolve(base: String, location: String): String = try {
        URL(URL(base), location).toString()
    } catch (error: Exception) {
        location
    }
}
