package ru.mesh.foss

import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновление устаревшего токена МЭШ по сохранённым кукам — порт PoC `refresh.sh`.
 *
 * QR-вход даёт долгоживущие куки (Ltpatoken2, session-cookie, user_login, obr_id),
 * а сам токен `aupd_token` живёт около суток. Когда токен устарел, по этим кукам
 * можно пройти OAuth заново — без повторного QR:
 *
 *  1. GET school.mos.ru/v3/auth/sudir/login — сервер отдаёт Location (или meta
 *     refresh в HTML) на OAuth entry login.mos.ru;
 *  2. GET login.mos.ru/sps/oauth/ae?... — Ltpatoken2/session-cookie уже доверены,
 *     сервер сразу 302 с code на /v3/auth/sudir/callback;
 *  3. GET callback — в Set-Cookie приходит новый aupd_token.
 *
 * Ротация кук (сервер может обновить Ltpatoken2/session-cookie) возвращается
 * вместе с токеном — её нужно сохранить обратно в кейс.
 *
 * Android-классов нет: разбор URL/куки покрыт JVM-тестами.
 */
object CookieRefresh {

    private const val SCHOOL = "https://school.mos.ru"
    private const val LOGIN = "https://login.mos.ru"
    private const val UA =
        "Mozilla/5.0 (X11; Linux x86_64; rv:159.0) Gecko/20100101 Firefox/159.0"
    private const val HTML_ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

    /** Дефолтные куки веб-фронтенда — как их подмешивает refresh.sh. */
    private val DEFAULT_COOKIES = listOf(
        "auth_flag" to "main",
        "subsystem_id" to "2",
        "aupd_current_role" to "2:2"
    )

    private const val CALLBACK_MARK = "/v3/auth/sudir/callback?code="
    private val OAUTH_URL = Regex("https://login\\.mos\\.ru/sps/oauth/ae[^\"'<>\\s]*")

    /** Результат: новый токен и обновлённый cookie jar (с возможной ротацией). */
    data class Result(val token: String, val jar: Map<String, String>)

    /** Cookie-заголовок: содержимое кейса плюс дефолтные куки, которых в нём нет. */
    internal fun cookieHeader(jar: Map<String, String>): String {
        val all = LinkedHashMap(jar)
        DEFAULT_COOKIES.forEach { (key, value) -> if (!all.containsKey(key)) all[key] = value }
        return all.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** URL OAuth entry из meta refresh / ссылки в HTML (HTML-сущности раскрываются). */
    internal fun oauthUrlFromHtml(html: String): String? =
        OAUTH_URL.find(html)?.value?.replace("&amp;", "&")

    /** Это ли callback school.mos.ru с code — цель шага 2 флоу refresh.sh. */
    internal fun isCallbackUrl(url: String): Boolean = url.contains(CALLBACK_MARK)

    /**
     * Проходит OAuth по кукам и возвращает новый aupd_token.
     * null — МЭШ не выдал code или не обновил токен (куки устарели, нужен QR).
     */
    fun refresh(jar: Map<String, String>): Result? {
        if (jar.isEmpty()) return null
        val cookies = LinkedHashMap(jar)
        val old = cookies["aupd_token"]
        if (old.isNullOrBlank()) return null

        // 1. Старт: school.mos.ru отдаёт ссылку на OAuth entry.
        var response = get("$SCHOOL/v3/auth/sudir/login", "$SCHOOL/", cookies)
        val oauth = response.location?.let { resolve(response.url, it) }
            ?: oauthUrlFromHtml(response.body)
            ?: return null

        // 2. OAuth entry: сервер по доверенным кукам сразу отдаёт code.
        response = get(oauth, "$SCHOOL/", cookies)
        val callback = response.location?.let { resolve(response.url, it) } ?: return null
        if (!isCallbackUrl(callback)) return null

        // 3. Callback: новый токен приходит в Set-Cookie.
        get(callback, oauth, cookies)
        val fresh = cookies["aupd_token"]?.takeIf { it.isNotBlank() && it != old } ?: return null
        return Result(fresh, cookies)
    }

    // ------------------------------------------------------------------ HTTP --

    private class Http(val url: String, val location: String?, val body: String)

    private fun get(url: String, referer: String, cookies: MutableMap<String, String>): Http {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            useCaches = false
            runCatching { setRequestProperty("User-Agent", UA) }
            runCatching { setRequestProperty("Accept", HTML_ACCEPT) }
            runCatching { setRequestProperty("Referer", referer) }
            runCatching { setRequestProperty("Cookie", cookieHeader(cookies)) }
        }
        try {
            connection.responseCode // триггер запроса; дальше важен Location/тело
            storeCookies(connection, cookies)
            val location = connection.getHeaderField("Location")
            val stream = runCatching { connection.inputStream }.getOrNull() ?: connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Http(url, location, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun storeCookies(connection: HttpURLConnection, cookies: MutableMap<String, String>) {
        val fields = runCatching { connection.headerFields }.getOrNull() ?: return
        for ((key, values) in fields) {
            if (key == null || !key.equals("Set-Cookie", ignoreCase = true)) continue
            for (raw in values) {
                val pair = raw.substringBefore(';').trim()
                val idx = pair.indexOf('=')
                if (idx <= 0) continue
                val name = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) cookies[name] = value
            }
        }
    }

    private fun resolve(base: String, location: String): String = try {
        URL(URL(base), location).toString()
    } catch (_: Exception) {
        location
    }
}
