package ru.mesh.foss.data

import ru.mesh.foss.core.*

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.mesh.foss.data.api.MeshApiService
import ru.mesh.foss.data.model.AttendanceResponse
import ru.mesh.foss.data.model.VisitDurationsResponse

/**
 * HTTP-клиент МЭШ на Retrofit + Gson.
 *
 * Авторизация передаётся явной картой заголовков, а не глобальным mutable
 * interceptor: это важно, потому что у питания и обновления токена разные
 * правила. При ответе 401 клиент один раз обновляет токен и повторяет запрос.
 *
 * Файл не зависит от Android-классов — покрыт обычными JVM-тестами.
 */
class MeshClient(
    var baseUrl: String = MeshCore.HOST,
    var token: String? = null,
    var profileId: String? = null,
    private val timeoutMs: Int = 20_000,
    var autoRefresh: Boolean = true,
    private val onToken: ((String) -> Unit)? = null,
    val nameCache: MutableMap<String, String> = mutableMapOf(),
    private val onName: ((String, String) -> Unit)? = null
) {

    /** Счётчик запросов — используется в диагностике. */
    var requestCount: Int = 0
        private set

    private val gson = GsonBuilder().setLenient().create()

    private val http = OkHttpClient.Builder()
        .connectTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    /** Для ссылки запуска цифрового ДЗ нужен ручной разбор 3xx Location/body. */
    private val launchHttp by lazy {
        http.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }

    @Volatile
    private var cachedBase: String? = null

    @Volatile
    private var cachedService: MeshApiService? = null

    @Synchronized
    private fun api(): MeshApiService {
        val normalized = baseUrl.trimEnd('/') + "/"
        cachedService?.takeIf { cachedBase == normalized }?.let { return it }
        val built = Retrofit.Builder()
            .baseUrl(normalized)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(MeshApiService::class.java)
        cachedBase = normalized
        cachedService = built
        return built
    }

    private fun buildUrl(path: String, params: Map<String, Any?> = emptyMap()): String {
        val query = params.entries
            .filter { it.value != null && it.value.toString().isNotEmpty() }
            .joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value.toString(), "UTF-8")}"
            }
        return baseUrl.trimEnd('/') + path + if (query.isEmpty()) "" else "?$query"
    }

    private fun headers(
        extra: Map<String, String> = emptyMap(),
        includeAuthToken: Boolean = true
    ): Map<String, String> =
        MeshCore.baseHeaders() + MeshCore.authHeaders(token, profileId, includeAuthToken) + extra

    private fun refreshHeaders(current: String): Map<String, String> =
        MeshCore.baseHeaders() + MeshCore.authHeaders(current, null)

    private fun networkError(error: Exception): MeshCore.MeshError = when (error) {
        is SocketTimeoutException -> MeshCore.MeshError(Words.MeshRequestTimedOut.text, "timeout", 0, error)
        is UnknownHostException -> MeshCore.MeshError(Words.CannotReachSchoolMosRu.text, "network", 0, error)
        else -> MeshCore.MeshError(Words.NetworkError(error.message), "network", 0, error)
    }

    private fun httpError(code: Int, path: String): MeshCore.MeshError = when (code) {
        401 -> MeshCore.MeshError(Words.Unauthorised.text, "unauthorized", 401)
        403 -> MeshCore.MeshError(Words.AccessDenied.text, "forbidden", 403)
        404 -> MeshCore.MeshError(Words.MethodNotFound(path), "not-found", 404)
        else -> MeshCore.MeshError(Words.MeshErrorHttp(code), "http-$code", code)
    }

    /**
     * Единая точка выполнения запроса: коды, сеть и однократное обновление токена.
     * [callFactory] вызывается на каждую попытку: после refresh заголовки должны
     * пересобраться с новым токеном, иначе повтор снова получит 401.
     */
    private fun <T> execute(
        callFactory: () -> Call<T>,
        path: String,
        retryOn401: Boolean = true
    ): T {
        var attempt = 0
        val maxAttempts = if (autoRefresh && retryOn401) 2 else 1
        while (attempt < maxAttempts) {
            attempt++
            requestCount++
            val call = callFactory()
            if (MeshLog.enabled) MeshLog.log("HTTP", "→ " + describe(call.request()))
            val response = try {
                call.execute()
            } catch (error: SocketTimeoutException) {
                MeshLog.error("HTTP", error)
                throw networkError(error)
            } catch (error: UnknownHostException) {
                MeshLog.error("HTTP", error)
                throw networkError(error)
            } catch (error: IOException) {
                MeshLog.error("HTTP", error)
                throw networkError(error)
            } catch (error: Exception) {
                MeshLog.error("HTTP", error)
                throw networkError(error)
            }
            if (response.isSuccessful) {
                if (MeshLog.enabled) {
                    MeshLog.log("HTTP", "← ${response.code()} ${call.request().method} ${call.request().url}")
                }
                return response.body()
                    ?: throw MeshCore.MeshError(Words.InvalidResponseFromMesh.text, "invalid-json", response.code())
            }
            val code = response.code()
            val errorText = if (MeshLog.enabled) {
                runCatching { response.errorBody()?.string() }.getOrNull()
            } else {
                null
            }
            if (errorText == null) response.errorBody()?.close()
            if (MeshLog.enabled) {
                val tail = errorText?.take(300)?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                MeshLog.log("HTTP", "← $code ${call.request().method} ${call.request().url}$tail")
            }
            if (code == 401 && attempt < maxAttempts) {
                MeshLog.log("AUTH", "401 — обновляю токен: ${call.request().url}")
                if (refreshToken() != null) continue
                throw MeshCore.MeshError(Words.TokenExpiredAndCouldNotBeRenewed.text, "unauthorized", 401)
            }
            throw httpError(code, path)
        }
        throw MeshCore.MeshError(Words.RequestFailed(path), "exhausted")
    }

    /** Строка запроса с заголовками; токены и cookie замаскирует [Sensitive]. */
    private fun describe(request: Request): String {
        val headers = request.headers.names().joinToString(" ") { name ->
            "$name: ${request.header(name)}"
        }
        return "${request.method} ${request.url} $headers"
    }

    private fun toObject(element: JsonElement?): JSONObject? {
        if (element == null || element.isJsonNull) return null
        val text = element.toString()
        return try {
            JSONObject(text)
        } catch (error: Exception) {
            // Некоторые методы отдают массив на верхнем уровне.
            try {
                JSONObject().put("payload", JSONArray(text))
            } catch (inner: Exception) {
                MeshCore.extractTokenFromText(text)?.let { return JSONObject().put("token", it) }
                throw MeshCore.MeshError(Words.InvalidResponseFromMesh.text, "invalid-json", 0, error)
            }
        }
    }

    private fun <T> asObject(callFactory: () -> Call<T>, path: String): JSONObject? =
        toObject(execute(callFactory, path) as? JsonElement)

    /**
     * Обновление токена. Возвращает новый токен или null, если МЭШ его не выдал.
     * Запрос идёт с текущим токеном — так же, как это делает веб-страница
     * /v2/token/refresh?roleId=1&subsystem=2.
     */
    @Synchronized
    fun refreshToken(): String? {
        val current = token?.takeIf { it.isNotBlank() } ?: return null
        return try {
            requestCount++
            MeshLog.log("AUTH", "→ refresh token")
            val response = api()
                .refreshToken(MeshCore.ROLE_ID, MeshCore.SUBSYSTEM_ID, refreshHeaders(current))
                .execute()
            if (!response.isSuccessful) {
                MeshLog.log("AUTH", "refresh: HTTP ${response.code()}")
                return null
            }
            val body = response.body()?.string().orEmpty()
            val fresh = MeshCore.extractTokenFromText(body)
                ?: MeshCore.extractTokenFromUrl(body)
                ?: looseToken(body)
            if (!fresh.isNullOrBlank() && fresh != current) {
                MeshLog.log("AUTH", "refresh: ok")
                token = fresh
                onToken?.invoke(fresh)
                fresh
            } else {
                MeshLog.log("AUTH", "refresh: токен не получен")
                null
            }
        } catch (error: Exception) {
            MeshLog.error("AUTH", error)
            null
        }
    }

    /**
     * «Мягкое» извлечение токена для демо-режима: локальный сервер отдаёт
     * короткие токены вида mock-token-2, не проходящие строгую проверку.
     */
    private fun looseToken(body: String): String? {
        val loose = Regex("^https?://(127\\.0\\.0\\.1|localhost|\\[::1\\])(:\\d+)?", RegexOption.IGNORE_CASE)
            .containsMatchIn(baseUrl)
        if (!loose || body.isBlank()) return null
        return runCatching { JSONObject(body).optString("token").takeIf { it.isNotBlank() } }.getOrNull()
    }

    fun fetchProfile(): JSONObject {
        val json = asObject({ api().profile(headers()) }, MeshCore.Paths.PROFILE)
            ?: throw MeshCore.MeshError(Words.EmptyProfileResponse.text, "invalid-json")
        return MeshCore.normalizeProfile(json)
    }

    /**
     * Вход по токену: проверяем токен профилем и запоминаем ученика.
     * @return JSONObject { token, profile, child, profileId }
     */
    fun signIn(rawToken: String?): JSONObject {
        val candidate = MeshCore.extractToken(rawToken) ?: rawToken?.trim() ?: ""
        if (candidate.isEmpty()) throw MeshCore.MeshError(Words.EmptyToken.text, "bad-token")
        // Демо-режим работает с локальным сервером, где токены короткие.
        val loose = Regex("^https?://(127\\.0\\.0\\.1|localhost|\\[::1\\])(:\\d+)?", RegexOption.IGNORE_CASE)
            .containsMatchIn(baseUrl)
        if (!MeshCore.looksLikeToken(candidate) && !loose) {
            throw MeshCore.MeshError(Words.InvalidMeshToken.text, "bad-token")
        }
        token = candidate
        val profile = fetchProfile()
        val child = profile.optJSONObject("defaultChild")
        profileId = child?.optString("id")?.takeIf { it.isNotBlank() }
        return JSONObject()
            .put("token", candidate)
            .put("profile", profile)
            .put("child", child ?: JSONObject.NULL)
            .put("profileId", profileId ?: JSONObject.NULL)
    }

    fun getSubjects(personId: String?, date: String = MeshCore.today()): JSONArray {
        val json = asObject({ api().subjects(date, personId, headers()) }, MeshCore.Paths.SUBJECTS)
        val list = MeshCore.payloadList(json)
        val result = JSONArray()
        for (i in 0 until list.length()) {
            list.optJSONObject(i)?.let { result.put(MeshCore.normalizeSubject(it)) }
        }
        return result
    }

    fun getRank(personId: String?, date: String = MeshCore.today(), subjectId: String? = null): JSONArray {
        val json = asObject({ api().rank(personId, date, subjectId, headers()) }, MeshCore.Paths.RANK)
        return MeshCore.payloadList(json)
    }

    fun getMarks(studentId: String?, from: String, to: String): List<JSONObject> {
        val json = asObject({ api().marks(studentId, from, to, headers()) }, MeshCore.Paths.MARKS)
        val list = MeshCore.payloadList(json)
        return (0 until list.length())
            .mapNotNull { list.optJSONObject(it)?.let { mark -> MeshCore.normalizeMark(mark) } }
            .sortedByDescending { it.optString("date") }
    }

    /** Подробная информация об оценке (тема урока, комментарий, результаты по классу). */
    fun getMark(markId: String, personId: String?): JSONObject {
        val path = "${MeshCore.Paths.MARKS}/$markId"
        return asObject({ api().mark(markId, personId, markId, headers()) }, path) ?: JSONObject()
    }

    fun getSubjectMarks(studentId: String?): JSONArray {
        val json = asObject({ api().subjectMarks(studentId, headers()) }, MeshCore.Paths.SUBJECT_MARKS)
        return MeshCore.payloadList(json)
    }

    /** `subject_marks` с нормализованными предметами, периодами и оценками. */
    fun getSubjectMarksDetailed(studentId: String?): JSONArray =
        MeshCore.normalizeSubjectMarks(getSubjectMarks(studentId))

    /** Кэш учебных годов: список статичен, повторно запрашиваем только при force. */
    private var academicYearsCache: JSONArray? = null

    /** Учебные годы для селектора итоговых оценок (ответ кэшируется на сессию). */
    fun getAcademicYears(force: Boolean = false): JSONArray {
        academicYearsCache?.takeIf { !force }?.let { return it }
        val json = asObject({ api().academicYears(headers()) }, MeshCore.Paths.ACADEMIC_YEARS)
        val years = MeshCore.normalizeAcademicYears(MeshCore.payloadList(json))
        academicYearsCache = years
        return years
    }

    /**
     * Итоговые оценки за учебный год.
     * Для прошлых лет МЭШ просит `is_year_marks_only=true`, чтобы вернуть только годовые.
     */
    fun getFinalMarks(
        personId: String?,
        studentId: String?,
        academicYearId: String,
        yearOnly: Boolean = false
    ): JSONArray {
        val json = asObject(
            {
                api().finalMarks(
                    personId,
                    studentId,
                    academicYearId,
                    if (yearOnly) "true" else null,
                    headers()
                )
            },
            MeshCore.Paths.FINAL_MARKS
        )
        return MeshCore.normalizeFinalMarks(json)
    }

    fun getHomeworks(studentId: String?, from: String, to: String): List<JSONObject> {
        val json = asObject({ api().homeworks(studentId, from, to, headers()) }, MeshCore.Paths.HOMEWORKS)
        val list = MeshCore.payloadList(json)
        return (0 until list.length()).mapNotNull {
            list.optJSONObject(it)?.let { item -> MeshCore.normalizeHomework(item) }
        }
    }

    /**
     * Отметка домашнего задания «выполнено» (POST) или снятие отметки (DELETE).
     *
     * @param entryStudentId `homework_entry_student_id` из ответа ДЗ.
     * @param type тип задания (`oo`, `dz`, …) — МЭШ ждёт его в query в верхнем регистре.
     * @return true, если МЭШ подтвердил изменение.
     */
    fun setHomeworkDone(entryStudentId: String?, type: String?, done: Boolean): Boolean {
        val entry = entryStudentId?.trim().orEmpty()
        if (entry.isBlank()) throw MeshCore.MeshError(Words.MissingHomework.text, "bad-homework")
        val kind = type?.trim().takeUnless { it.isNullOrBlank() }?.uppercase(Locale.US) ?: "OO"
        val path = MeshCore.Paths.homeworkDone(entry)
        val body = if (done) {
            execute({ api().markHomeworkDone(entry, kind, headers()) }, path)
        } else {
            execute({ api().clearHomeworkDone(entry, kind, headers()) }, path)
        }.string()
        val json = runCatching { JSONObject(body) }.getOrNull()
        return json?.optBoolean("success", true) ?: true
    }

    /**
     * Ссылка запуска цифрового материала ДЗ (тест, «Облако знаний» и т.п.).
     * МЭШ отдаёт 302 на /api/launcher/v1/launch — этот адрес и открываем в браузере.
     */
    fun launchHomeworkMaterial(homeworkEntryId: String?, materialId: String?): String {
        val entry = homeworkEntryId?.trim().orEmpty()
        val material = materialId?.trim().orEmpty()
        if (entry.isBlank() || material.isBlank()) {
            throw MeshCore.MeshError(Words.MissingHomeworkMaterial.text, "bad-material")
        }
        val url = buildUrl(
            MeshCore.Paths.HOMEWORK_LAUNCH,
            mapOf("homework_entry_id" to entry, "material_id" to material)
        )
        var attempt = 0
        while (attempt < 2) {
            attempt++
            val request = Request.Builder()
                .url(url)
                .apply { headers().forEach { (key, value) -> header(key, value) } }
                .build()
            requestCount++
            val response = try {
                launchHttp.newCall(request).execute()
            } catch (error: Exception) {
                throw networkError(error)
            }
            response.use { res ->
                val status = res.code
                if (status == 401 && attempt < 2) {
                    if (refreshToken() != null) return@use
                    throw MeshCore.MeshError(Words.TokenExpiredAndCouldNotBeRenewed.text, "unauthorized", 401)
                }
                if (status == 401) throw MeshCore.MeshError(Words.Unauthorised.text, "unauthorized", 401)
                if (status == 403) throw MeshCore.MeshError(Words.AccessDenied.text, "forbidden", 403)
                if (status !in 200..399) {
                    throw MeshCore.MeshError(Words.CouldNotOpenMaterialHttp(status), "http-$status", status)
                }
                res.header("Location")?.takeIf { it.isNotBlank() }?.let { return MeshCore.absoluteUrl(it) }
                val text = res.body?.string().orEmpty().trim().trim('"')
                if (text.startsWith("http://") || text.startsWith("https://")) return text
                runCatching { JSONObject(text) }.getOrNull()?.let { json ->
                    listOf("url", "link", "location", "redirect", "activity_url").forEach { key ->
                        json.optString(key).takeIf { it.isNotBlank() }?.let { return MeshCore.absoluteUrl(it) }
                    }
                }
                if (status in 300..399) {
                    throw MeshCore.MeshError(Words.NoMaterialLinkReceived.text, "invalid-json", status)
                }
                throw MeshCore.MeshError(Words.NoMaterialLinkReceived.text, "invalid-json", status)
            }
        }
        throw MeshCore.MeshError(Words.CouldNotOpenMaterial.text, "exhausted")
    }

    fun getSchedule(studentId: String?, date: String = MeshCore.today()): JSONArray {
        val json = asObject({ api().schedule(studentId, date, headers()) }, MeshCore.Paths.SCHEDULE)
        return MeshCore.normalizeSchedule(json, date)
    }

    // ------------------------------------------------------------------ ЕДА ----

    /**
     * Дополнительные заголовки для API питания: в отличие от остальных методов,
     * методы `/api/food/...` ожидают токен ещё и в cookie `aupd_token` (как веб-страница).
     */
    private fun foodHeaders(): Map<String, String> {
        val current = token?.takeIf { it.isNotBlank() } ?: return emptyMap()
        return mapOf("Cookie" to "aupd_token=$current")
    }

    /** `clientId` в API питания — это JSON-объект `{"personId":"<guid>"}`. */
    private fun clientIdParam(personId: String): String =
        JSONObject().put("personId", personId).toString()

    private fun requirePersonId(personId: String?): String {
        val id = personId?.trim().orEmpty()
        if (id.isBlank()) throw MeshCore.MeshError(Words.SelectAStudentToViewMeals.text, "bad-food")
        return id
    }

    /** Состояние питания: доступность предзаказа, адрес столовой, неделя питания. */
    fun getFoodState(personId: String?): JSONObject {
        val id = requirePersonId(personId)
        val json = asObject(
            { api().foodState(clientIdParam(id), headers(foodHeaders(), includeAuthToken = false)) },
            MeshCore.Paths.FOOD_STATE
        )
        return MeshCore.normalizeFoodState(json)
    }

    /** Баланс лицевого счёта (рубли-копейки) по ученику. */
    fun getFoodBalance(personId: String?): JSONArray {
        val id = requirePersonId(personId)
        val clientIds = JSONArray().put(JSONObject().put("personId", id)).toString()
        val json = asObject(
            { api().foodBalance(clientIds, headers(foodHeaders(), includeAuthToken = false)) },
            MeshCore.Paths.FOOD_BALANCE
        )
        return MeshCore.normalizeFoodBalance(MeshCore.payloadList(json))
    }

    /** Меню комплексных обедов на диапазон дат (обычно один день). */
    fun getFoodMenu(personId: String?, from: String, to: String): JSONArray {
        val id = requirePersonId(personId)
        val json = asObject(
            { api().foodMenu(clientIdParam(id), from, to, "in", headers(foodHeaders(), includeAuthToken = false)) },
            MeshCore.Paths.FOOD_MENU
        )
        return MeshCore.normalizeFoodMenu(MeshCore.payloadList(json))
    }

    /** Заказы (предзаказы) питания на диапазон дат. */
    fun getFoodOrders(personId: String?, from: String, to: String): JSONArray {
        val id = requirePersonId(personId)
        val json = asObject(
            { api().foodOrders(clientIdParam(id), from, to, 50, headers(foodHeaders(), includeAuthToken = false)) },
            MeshCore.Paths.FOOD_ORDERS
        )
        return json?.optJSONArray("orders") ?: JSONArray()
    }

    // ---------------------------------------------------------- посещаемость ----

    /** Пропуски/причины по дням: `family/web/v1/attendance`. */
    fun getAttendance(studentId: String?, from: String, to: String): AttendanceResponse {
        val id = studentId?.trim().orEmpty()
        if (id.isBlank()) throw MeshCore.MeshError(Words.SelectAStudentToViewAttendance.text, "bad-student")
        return execute({ api().attendance(id, from, to, headers()) }, MeshCore.Paths.ATTENDANCE)
    }

    /** Фактические входы/выходы: `pass/entrances/v1/visit_durations`. */
    fun getVisitDurations(personId: String?, from: String, to: String): VisitDurationsResponse {
        val id = personId?.trim().orEmpty()
        if (id.isBlank()) throw MeshCore.MeshError(Words.SelectAStudentToViewAttendance.text, "bad-student")
        // Шлюз pass/entrances отвечает 401, если пришли ОБА заголовка
        // Authorization и Auth-Token, и принимает любой из них по отдельности
        // (проверено на school.mos.ru). Поэтому здесь — только Bearer.
        return execute(
            { api().visitDurations(id, from, to, headers(includeAuthToken = false)) },
            MeshCore.Paths.VISIT_DURATIONS
        )
    }

    /** Имя ученика по personId (с кэшем — на класс обычно один запрос на человека). */
    fun getName(personId: String?): String {
        val key = personId?.trim().orEmpty()
        if (key.isEmpty()) return Words.UnknownStudent.text
        synchronized(nameCache) {
            nameCache[key]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val name = try {
            val json = asObject({ api().profileById(key, headers()) }, MeshCore.Paths.PROFILE_BY_ID)
                ?: throw MeshCore.MeshError(Words.EmptyProfile.text, "invalid-json")
            val first = MeshCore.asString(MeshCore.firstDefined(json.opt("firstName"), json.opt("first_name")))
            val last = MeshCore.asString(MeshCore.firstDefined(json.opt("lastName"), json.opt("last_name")))
            // Как в веб-версии: «Даниил Б», а не «Б Даниил».
            listOf(first, last)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .ifBlank {
                    MeshCore.asString(MeshCore.firstDefined(json.opt("name"), json.opt("fio")))
                }
        } catch (error: Exception) {
            ""
        }
        // Ошибки и заглушки не кэшируем: иначе имя навсегда останется битым.
        if (name.isBlank()) return Words.Unnamed.text
        synchronized(nameCache) { nameCache[key] = name }
        onName?.invoke(key, name)
        return name
    }

    fun getNames(personIds: List<String>): Map<String, String> {
        val ids = personIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val result = java.util.Collections.synchronizedMap(linkedMapOf<String, String>())
        if (ids.isEmpty()) return result
        val pool = java.util.concurrent.Executors.newFixedThreadPool(minOf(6, ids.size))
        try {
            ids.forEach { id ->
                pool.execute {
                    val name = getName(id)
                    synchronized(result) { result[id] = name }
                }
            }
        } finally {
            pool.shutdown()
        }
        pool.awaitTermination(2, java.util.concurrent.TimeUnit.MINUTES)
        return LinkedHashMap(result)
    }

    /** Полная таблица рейтинга с именами и сводкой — то, что показывает вкладка «Рейтинг». */
    fun getRankBoard(
        personId: String?,
        date: String = MeshCore.today(),
        subjectId: String? = null,
        subjectName: String? = null,
        names: Map<String, String> = emptyMap(),
        monthly: Boolean = false
    ): JSONObject {
        val members = getRank(personId, date, subjectId)
        val ids = (0 until members.length())
            .mapNotNull { members.optJSONObject(it)?.optString("personId")?.takeIf { id -> id.isNotBlank() } }
        val resolved = LinkedHashMap<String, String>()
        synchronized(nameCache) { resolved.putAll(nameCache) }
        resolved.putAll(names)
        val missing = ids.filter { id ->
            val cached = resolved[id]
            cached.isNullOrBlank() || cached.startsWith("Ученик ") || cached == "Без имени"
        }
        if (missing.isNotEmpty()) {
            getNames(missing).forEach { (id, name) -> resolved[id] = name }
        }
        val rows = MeshCore.buildRankTable(members, resolved, personId, monthly)
        return JSONObject()
            .put("rows", rows)
            .put("summary", MeshCore.summarizeRank(rows, personId))
            .put("date", date)
            .put("subjectId", subjectId ?: JSONObject.NULL)
            .put("subjectName", subjectName ?: JSONObject.NULL)
            .put("period", if (monthly) "month" else "year")
    }
}
