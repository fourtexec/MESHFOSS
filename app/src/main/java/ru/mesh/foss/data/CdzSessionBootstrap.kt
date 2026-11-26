package ru.mesh.foss.data

import ru.mesh.foss.core.MeshLog

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

/**
 * Первичная SSO-сессия `uchebnik.mos.ru` по cookie МЭШ.
 *
 * Вход через JSON (`mos_cookies.json`) даёт куки school.mos.ru, но не
 * `Session-Id-External`/`Session-Eom-Profile`. Без них `refresh` отвечает
 * `401 Required cookies are missing`. Повторяем путь браузера:
 *
 *   school OAuth -> login.mos.ru -> school callback -> uchebnik /authenticate
 *   -> /acl/api/session/callback (code -> Session-* + accessTokenEom)
 *
 * Это только авторизация: попытка ЦДЗ здесь не создаётся.
 */
object CdzSessionBootstrap {

    private const val SCHOOL = "https://school.mos.ru"
    private const val BASE = "https://uchebnik.mos.ru"
    private const val UA =
        "Mozilla/5.0 (X11; Linux x86_64; rv:160.0) Gecko/20100101 Firefox/160.0"
    private const val HTML_ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    private const val SCOPE =
        "email phone profile oic_given_name oic_family_name oic_middle_name oic_birthdate"
    private const val ROLE = "student"
    private const val RGN = "77"

    internal data class Result(val token: String?, val cookies: Map<String, String>)

    /**
     * OAuth authorize, который открывает библиотека МЭШ. `redirect_uri` ведёт
     * обратно на `/authenticate` uchebnik, где появляется одноразовый `code`.
     */
    internal fun authorizeUrl(state: String): String {
        val redirect = "$BASE/authenticate?" + query(
            "backurl" to "/exam/",
            "role" to ROLE,
            "rgn" to RGN,
        )
        return "$SCHOOL/v1/oauth/sudir/authorize?" + query(
            "client_id" to "library",
            "scope" to SCOPE,
            "response_type" to "code",
            "response_mode" to "query",
            "redirect_uri" to redirect,
            "state" to state,
        )
    }

    /** Обмен одноразового OAuth code на cookie-сессию и accessTokenEom. */
    internal fun callbackUrl(state: String, code: String): String =
        "$BASE/acl/api/session/callback?" + query(
            "state" to state,
            "code" to code,
            "rgn" to RGN,
            "aupd_current_role" to ROLE,
        )

    /** URL из `<meta http-equiv="refresh" content="0; url=...">`. */
    internal fun metaRefreshUrl(html: String): String? {
        val head = Regex(
            "(?is)<meta[^>]+http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*content\\s*=\\s*[\"']([^\"']+)[\"']"
        ).find(html)?.groupValues?.getOrNull(1)
            ?: Regex(
                "(?is)<meta[^>]+content\\s*=\\s*[\"']([^\"']+)[\"'][^>]*http-equiv\\s*=\\s*[\"']?refresh[\"']?"
            ).find(html)?.groupValues?.getOrNull(1)
            ?: return null
        val url = Regex("(?i)url\\s*=\\s*(.+)$")
            .find(head)?.groupValues?.getOrNull(1)?.trim()
            ?: return null
        return unescapeAmp(url)
    }

    internal fun isUchebnikAuth(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.host?.equals("uchebnik.mos.ru", ignoreCase = true) == true &&
            uri.path.orEmpty().startsWith("/authenticate")
    }.getOrDefault(false)

    /** `code` и `state` из адреса `/authenticate?...`. */
    internal fun codeAndState(url: String): Pair<String, String>? = runCatching {
        val raw = URI(url).rawQuery ?: return@runCatching null
        val values = raw.split('&').mapNotNull { pair ->
            val key = decode(pair.substringBefore('='))
            val value = decode(pair.substringAfter('=', ""))
            if (key.isBlank()) null else key to value
        }.toMap()
        val code = values["code"].orEmpty()
        val state = values["state"].orEmpty()
        if (code.isBlank() || state.isBlank()) null else code to state
    }.getOrNull()

    /**
     * Поднимает сессию uchebnik по школьным cookie. Cookie jar обновляется
     * новыми `aupd_token`/`Session-*`; вызывающий сохраняет его в аккаунт.
     */
    internal fun bootstrap(cookies: MutableMap<String, String>): Result? {
        if (cookies.isEmpty()) return null
        val state = UUID.randomUUID().toString().replace("-", "")
        var current = authorizeUrl(state)
        var code: String? = null
        var callbackState: String? = null

        for (hop in 0 until 8) {
            val response = get(current, cookies, HTML_ACCEPT)
            MeshLog.log(
                "CDZ",
                "sso #$hop: HTTP ${response.status} loc=${response.location != null} " +
                    "setCookie=${response.setCookies.size}"
            )
            if (response.status !in 200..399) return null
            val next = response.location ?: metaRefreshUrl(response.body) ?: return null
            val resolved = resolve(current, next)
            if (isUchebnikAuth(resolved)) {
                val pair = codeAndState(resolved) ?: return null
                code = pair.first
                callbackState = pair.second
                break
            }
            current = resolved
        }

        val freshCode = code ?: return null
        val freshState = callbackState ?: return null
        if (freshState != state) {
            MeshLog.log("CDZ", "sso: state не совпал")
            return null
        }

        val callback = get(callbackUrl(freshState, freshCode), cookies, "application/json, text/plain, */*")
        MeshLog.log(
            "CDZ",
            "sso callback: HTTP ${callback.status} setCookie=${callback.setCookies.size}"
        )
        if (callback.status !in 200..299) {
            val error = runCatching {
                org.json.JSONObject(callback.body).optString("error_code")
            }.getOrNull().orEmpty()
            MeshLog.log("CDZ", "sso callback error=${error.ifBlank { "unknown" }}")
            return null
        }

        val body = callback.body.trim()
        val token = body.takeIf { looksLikeJwt(it) }
            ?: runCatching {
                org.json.JSONObject(body).optString("accessTokenEom")
                    .takeIf { it.isNotBlank() }
            }.getOrNull()
        if (token.isNullOrBlank() && !hasSessionCookies(cookies)) {
            MeshLog.log("CDZ", "sso: нет токена и Session-*")
            return null
        }
        MeshLog.log(
            "CDZ",
            "sso ok: token=${!token.isNullOrBlank()} session=${hasSessionCookies(cookies)}"
        )
        return Result(token, cookies.toMap())
    }

    internal fun hasSessionCookies(cookies: Map<String, String>): Boolean =
        !cookies["Session-Id-External"].isNullOrBlank() &&
            !cookies["Session-Eom-Profile"].isNullOrBlank()

    // ------------------------------------------------------------------ HTTP --

    private class Http(
        val status: Int,
        val location: String?,
        val body: String,
        val setCookies: List<String>,
    )

    private fun get(url: String, cookies: MutableMap<String, String>, accept: String): Http {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            useCaches = false
            runCatching { setRequestProperty("User-Agent", UA) }
            runCatching { setRequestProperty("Accept", accept) }
            runCatching { setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9") }
            runCatching { setRequestProperty("Referer", "$BASE/exam/") }
            if (cookies.isNotEmpty()) {
                runCatching { setRequestProperty("Cookie", cookieHeader(cookies)) }
            }
        }
        try {
            val status = connection.responseCode
            val location = connection.getHeaderField("Location")
            val setCookies = setCookies(connection)
            storeCookies(setCookies, cookies)
            val stream = runCatching { connection.inputStream }.getOrNull() ?: connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Http(status, location, body, setCookies)
        } finally {
            connection.disconnect()
        }
    }

    private fun setCookies(connection: HttpURLConnection): List<String> {
        val fields = runCatching { connection.headerFields }.getOrNull() ?: return emptyList()
        return fields.entries
            .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value.orEmpty() }
    }

    private fun storeCookies(rawCookies: List<String>, cookies: MutableMap<String, String>) {
        rawCookies.forEach { raw ->
            val pair = raw.substringBefore(';').trim()
            val idx = pair.indexOf('=')
            if (idx <= 0) return@forEach
            val name = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) cookies[name] = value
        }
    }

    private fun cookieHeader(cookies: Map<String, String>): String =
        cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    // ------------------------------------------------------------------ URL ---

    private fun query(vararg params: Pair<String, String>): String =
        params.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun resolve(base: String, next: String): String =
        runCatching { URI(base).resolve(next).toString() }.getOrDefault(next)

    private fun unescapeAmp(value: String): String =
        value.replace("&amp;", "&").replace("&#38;", "&")

    private fun looksLikeJwt(value: String): Boolean =
        value.length > 100 && value.count { it == '.' } == 2 &&
            value.substringBefore('.').isNotBlank() &&
            value.substringAfterLast('.').isNotBlank()
}
