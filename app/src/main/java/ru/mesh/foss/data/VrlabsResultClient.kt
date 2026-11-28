package ru.mesh.foss.data

import com.google.gson.GsonBuilder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.mesh.foss.data.api.ProgrammingApiService
import ru.mesh.foss.data.model.ProgrammingResultPage
import ru.mesh.foss.data.model.ProgrammingState
import ru.mesh.foss.data.model.ProgrammingTask
import ru.mesh.foss.data.model.VrlabsLaunchContext
import ru.mesh.foss.data.model.VrlabsTokenRequest
import ru.mesh.foss.data.model.VrlabsTokenResponse

/** Сырые данные программистской попытки до преобразования в UI-модель. */
data class ProgrammingAttemptPayload(
    val resultPage: ProgrammingResultPage?,
    val state: ProgrammingState,
    val tasks: List<ProgrammingTask>,
)

/**
 * Клиент `school.mos.ru/vrlabs/informatics`.
 *
 * OAuth/cookie-обмен вынесен в OkHttp (протокольное исключение из правил
 * Retrofit), а чтение результата идёт через Retrofit + Gson. Только GET:
 * попытки не создаются и не завершаются.
 */
class VrlabsResultClient(
    private val session: CdzNativeClient,
) {
    companion object {
        private const val API_BASE = "https://school.mos.ru/vrlabs/informatics/api/"
        private const val SCHOOL = "https://school.mos.ru"
        private const val UA =
            "Mozilla/5.0 (X11; Linux x86_64; rv:160.0) Gecko/20100101 Firefox/160.0"
        private val URL_IN_TEXT = Regex("""https?://[^"'\s<>]+""", RegexOption.IGNORE_CASE)
        private val URL_TRAILING = charArrayOf(')', ']', ',', ';', '.', '"', '\'')
    }

    private val gson = GsonBuilder().setLenient().create()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val api: ProgrammingApiService = Retrofit.Builder()
        .baseUrl(API_BASE)
        .client(http)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(ProgrammingApiService::class.java)

    suspend fun load(
        competitionId: Long,
        assignmentId: Long,
        pageUrl: String,
    ): ProgrammingAttemptPayload = withContext(Dispatchers.IO) {
        if (competitionId <= 0L || assignmentId <= 0L) {
            throw IllegalArgumentException("bad vrlabs ids")
        }
        val resolvedPage = resolvePageUrl(pageUrl)
        val params = queryParams(resolvedPage)
        val token = token(resolvedPage, params)
        val headers = apiHeaders(resolvedPage, token)
        val state = api.state(assignmentId, headers)
        val resultPage = runCatching { api.resultPage(competitionId, headers) }.getOrNull()
        val expectedTaskIds = state.tasks.orEmpty()
            .mapNotNull { it.task?.id }
            .distinct()
        val expectedTaskCount = expectedTaskIds.size
            .takeIf { it > 0 }
            ?: resultPage?.taskCount
            ?: 0
        val taskList = coroutineScope {
            state.tasks.orEmpty()
                .sortedBy { it.task?.order ?: Int.MAX_VALUE }
                .mapNotNull { stateTask ->
                    val taskId = stateTask.task?.id ?: return@mapNotNull null
                    async {
                        runCatching { api.task(assignmentId, taskId, headers) }.getOrNull()
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
        if (expectedTaskCount > 0 && taskList.isEmpty()) {
            throw IllegalStateException("vrlabs tasks unavailable")
        }
        ProgrammingAttemptPayload(resultPage, state, taskList)
    }

    private suspend fun resolvePageUrl(pageUrl: String): String {
        val clean = pageUrl.replace("&amp;", "&").trim()
        val parsed = clean.toHttpUrlOrNull() ?: return clean
        if (isVrlabsResult(clean)) return clean

        val base = parsed.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
        val activity = URLEncoder.encode(base, "UTF-8")
        val schoolUrl = "$SCHOOL/api/launcher/v1/url?activity_url=$activity"
        val first = getText(schoolUrl, clean, auth = false)?.trim().orEmpty()
        if (first.isBlank()) return clean
        directPageFromText(first)?.let { return it }

        val launcher = httpUrlsIn(first).firstOrNull {
            it.encodedPath.endsWith("/launcher/api/v1/launch") ||
                it.encodedPath.endsWith("/launcher/api/v1/url")
        } ?: return clean
        val launchPath = launcher.encodedPath
        val uchebnikUrl = when {
            launchPath.endsWith("/launcher/api/v1/launch") ->
                launcher.newBuilder()
                    .encodedPath(launchPath.dropLast("launch".length) + "url")
                    .build()
                    .toString()
            launchPath.endsWith("/launcher/api/v1/url") -> launcher.toString()
            else -> return clean
        }
        session.refreshSession()
        val second = getText(uchebnikUrl, first, auth = true)?.trim().orEmpty()
        if (second.isBlank()) return clean
        return directPageFromText(second) ?: clean
    }

    private suspend fun token(pageUrl: String, params: Map<String, String>): String {
        val authUrl = params["authurl"].orEmpty().ifBlank { SCHOOL }
        val callback = "$authUrl/vrlabs/informatics/oauth/callback"
        val state = System.currentTimeMillis().toString(36)
        val authorize = buildString {
            append(authUrl.trimEnd('/'))
            append("/v1/oauth/authorize?client_id=weblab")
            append("&scope=oic_given_name+oic_family_name+oic_middle_name")
            append("&response_mode=query")
            append("&redirect_uri=")
            append(URLEncoder.encode(callback, "UTF-8"))
            append("&response_type=code&state=")
            append(URLEncoder.encode(state, "UTF-8"))
        }
        val response = execute(authorize, pageUrl, auth = false)
            ?: throw IllegalStateException("vrlabs oauth unavailable")
        val callbackUrl = response.use { callbackResponse ->
            val location = callbackResponse.header("Location")
            val html = callbackResponse.body?.string().orEmpty()
            location
                ?: Regex(
                    """<meta[^>]+content\s*=\s*["'][^"']*url=([^"']+)["']""",
                    RegexOption.IGNORE_CASE,
                ).find(html)?.groupValues?.getOrNull(1)
                ?: throw IllegalStateException("vrlabs oauth callback missing")
        }
        val callbackValue = callbackUrl.trim().replace("&amp;", "&")
        val parsed = callbackValue.toHttpUrlOrNull()
            ?: authorize.toHttpUrlOrNull()?.resolve(callbackValue)
            ?: throw IllegalStateException("vrlabs oauth callback invalid")
        val code = parsed.queryParameter("code").orEmpty()
        val callbackState = parsed.queryParameter("state").orEmpty()
        if (code.isBlank() || callbackState.isBlank()) {
            throw IllegalStateException("vrlabs oauth code missing")
        }
        val activityId = params["activityId"].orEmpty().ifBlank { pageUrl }
        val actor = params["actor"].orEmpty().ifBlank { "{}" }
        val endpoint = params["endpoint"].orEmpty()
        val fetch = params["fetch"].orEmpty()
        val registration = params["registration"].orEmpty()
        val role = params["role"].orEmpty().ifBlank { "student" }
        val launch = VrlabsLaunchContext(
            code = code,
            state = callbackState,
            activityId = encode(activityId),
            actor = encode(actor),
            authurl = authUrl,
            endpoint = encode(endpoint),
            fetch = encode(fetch),
            registration = registration,
            role = role,
            redirectUrl = callback,
        )
        val headers = mapOf(
            "Accept" to "application/json, text/plain, */*",
            "Content-Type" to "application/json",
            "Origin" to SCHOOL,
            "Referer" to callback,
            "Cookie" to cookieHeader(),
            "User-Agent" to UA,
        )
        val responseToken: VrlabsTokenResponse = api.token(VrlabsTokenRequest(launch), headers)
        return responseToken.token?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("vrlabs token missing")
    }

    private fun apiHeaders(pageUrl: String, token: String): Map<String, String> = mapOf(
        "Accept" to "application/json, text/plain, */*",
        "Referer" to pageUrl,
        "Authorization" to "Bearer $token",
        "Cookie" to cookieHeader(),
        "User-Agent" to UA,
    )

    private fun execute(url: String, referer: String, auth: Boolean): okhttp3.Response? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .header("Referer", referer)
            .apply {
                val cookies = cookieHeader()
                if (cookies.isNotBlank()) header("Cookie", cookies)
                if (auth) {
                    session.currentAccessToken()?.let { header("Authorization", "Bearer $it") }
                }
            }
            .build()
        val response = runCatching { http.newCall(request).execute() }.getOrNull() ?: return null
        rememberCookies(response)
        return response
    }

    private fun getText(url: String, referer: String, auth: Boolean): String? {
        val response = execute(url, referer, auth) ?: return null
        response.use {
            val body = it.body?.string().orEmpty()
            if (body.isNotBlank()) return body
            // Некоторые launcher-редиректы отдают только Location.
            val location = it.header("Location")?.takeIf { value -> value.isNotBlank() }
                ?: return null
            return (location.toHttpUrlOrNull()
                ?: url.toHttpUrlOrNull()?.resolve(location))?.toString()
        }
    }

    private fun rememberCookies(response: okhttp3.Response) {
        response.headers("Set-Cookie").forEach { raw ->
            val cookie: Cookie = Cookie.parse(response.request.url, raw) ?: return@forEach
            if (cookie.value.isNotBlank()) {
                session.updateCookie(cookie.name, cookie.value)
            }
        }
        session.persistCookiesNow()
    }

    private fun cookieHeader(): String =
        session.currentCookies().entries.joinToString("; ") { "${it.key}=${it.value}" }

    private fun queryParams(url: String): Map<String, String> {
        val parsed = url.toHttpUrlOrNull() ?: return emptyMap()
        return buildMap {
            parsed.queryParameterNames.forEach { name ->
                parsed.queryParameter(name)?.let { put(name, it) }
            }
        }
    }

    private fun isVrlabsResult(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val path = parsed.encodedPath.lowercase()
        return path.startsWith("/vrlabs/informatics/student/olympiad/competition/") &&
            "/participation/results/" in path
    }

    private fun httpUrlsIn(text: String): List<HttpUrl> {
        val decoded = text
            .replace("&amp;", "&")
            .replace("\\/", "/")
        val result = mutableListOf<HttpUrl>()
        decoded.trim().toHttpUrlOrNull()?.let(result::add)
        URL_IN_TEXT.findAll(decoded).forEach { match ->
            match.value.trimEnd(*URL_TRAILING).toHttpUrlOrNull()?.let(result::add)
        }
        return result.distinctBy { it.toString() }
    }

    /**
     * Ищет прямую страницу результата в ответе launcher'а. Ответ может быть
     * готовым URL, HTML/JSON с URL или вложенной launcher-ссылкой с
     * `activity_url`/`activityId`.
     */
    private fun directPageFromText(text: String, depth: Int = 0): String? {
        if (depth > 4) return null
        httpUrlsIn(text).forEach { candidate ->
            val candidateText = candidate.toString()
            if (isVrlabsResult(candidateText)) return candidateText
            listOf("activity_url", "activityId").forEach { key ->
                candidate.queryParameter(key)?.let { nested ->
                    directPageFromText(nested, depth + 1)?.let { return it }
                }
            }
        }
        return null
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")
}
