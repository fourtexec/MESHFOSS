package ru.mesh.foss

import ru.mesh.foss.core.MeshCore

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Демо-сервер МЭШ для Android: поднимается на 127.0.0.1 и отдаёт те же методы,
 * что и настоящий school.mos.ru (тот же формат ответов, что у мок-сервера
 * в tools/mock-mesh-server.js).
 *
 * Нужен, чтобы посмотреть интерфейс без аккаунта МЭШ: «Демо-режим» в меню.
 * Данные генерируются детерминированно (seed), поэтому таблицы всегда одинаковые.
 *
 * Файл не зависит от Android — используется и в JVM-тестах (MeshClientTest).
 */
class DemoMeshServer(
    private val seed: Long = 20_261_012L,
    private val classSize: Int = 26,
    var tokenLifetime: Int = 3
) {

    companion object {
        const val UUID_ME = "11111111-1111-4111-8111-111111111111"
        const val UUID_SIBLING = "22222222-2222-4222-8222-222222222222"
        const val DEMO_TOKEN = "mock-token-1"

        /** Демо-страница партнёра ЦДЗ. */
        const val DEMO_CDZ_PAGE = "/demo/cdz"

        /** Синтетические олимпиады демо-режима (без реальных id и данных). */
        const val DEMO_OLYMPIAD_ID = "demo-olympiad-1"
        const val DEMO_OLYMPIAD_PAST_ID = "demo-olympiad-2"

        private const val OLYMPIAD_BASE = "/api/contests/exec/v1/"
        private const val PORTFOLIO_BASE = "/portfolio/app/"

        /** Служебный ключ: ответ отдаётся как есть (строка), а не JSON-объектом. */
        private const val RAW_BODY = "__raw_body__"

        val SUBJECTS = listOf(
            1 to "Алгебра", 2 to "Геометрия", 3 to "Русский язык", 4 to "Литература",
            5 to "Английский язык", 6 to "История", 7 to "Обществознание", 8 to "География",
            9 to "Биология", 10 to "Физика", 11 to "Химия", 12 to "Информатика",
            13 to "ОБЖ", 14 to "Физическая культура"
        )

        private val SURNAMES = listOf(
            "Абрамов", "Белова", "Виноградов", "Гусева", "Дмитриев", "Ершова", "Жукова", "Зайцев",
            "Иванова", "Кузнецов", "Лебедева", "Морозов", "Никитина", "Орлов", "Петрова", "Романов",
            "Смирнова", "Тимофеев", "Ульянова", "Фролова", "Харитонов", "Цветкова", "Чернов", "Шилова",
            "Щербаков", "Юдина", "Яковлев"
        )
        private val FIRST_M = listOf("Артём", "Максим", "Иван", "Дмитрий", "Егор", "Кирилл", "Никита", "Александр", "Пётр", "Лев")
        private val FIRST_F = listOf("Анна", "Мария", "София", "Ева", "Полина", "Дарья", "Виктория", "Ксения", "Алиса", "Вера")
        private val MIDDLE_M = listOf("Алексеевич", "Сергеевич", "Дмитриевич", "Иванович", "Павлович")
        private val MIDDLE_F = listOf("Алексеевна", "Сергеевна", "Дмитриевна", "Ивановна", "Павловна")
        private val TEACHERS = listOf("Соколова И. П.", "Гаврилов А. Н.", "Мельникова О. В.", "Ткачук Д. С.", "Васильева Е. А.")
        private val ROOMS = listOf("204", "211", "306", "каб. 118", "спортзал", "актовый зал")
        private val FORMS = listOf(
            "Контрольная работа", "Самостоятельная работа", "Ответ на уроке",
            "Домашняя работа", "Диктант", "Тест", "Проект", "Зачёт"
        )

        private val OLYMPIAD_SUBJECTS = listOf(
            "1" to "Математика",
            "2" to "Русский язык",
            "3" to "Информатика",
            "4" to "Физика",
            "5" to "История",
        )
        private val OLYMPIAD_PARALLELS = listOf(
            "7" to "7 класс",
            "8" to "8 класс",
            "9" to "9 класс",
            "10" to "10 класс",
            "11" to "11 класс",
        )
        private val OLYMPIAD_STAGES = listOf(
            "SCHOOL" to "Школьный этап",
            "MUNICIPAL" to "Муниципальный этап",
            "REGIONAL" to "Региональный этап",
            "FINAL" to "Заключительный этап",
        )
        private val OLYMPIAD_LEVELS = listOf(
            "1" to "I уровень",
            "2" to "II уровень",
            "3" to "III уровень",
        )

        private val OLYMPIADS = listOf(
            DemoOlympiad(
                id = DEMO_OLYMPIAD_ID,
                name = "Демо-олимпиада по математике",
                status = "REGISTRATION",
                startAt = "2026-12-10T10:00:00+03:00",
                finishAt = "2026-12-20T18:00:00+03:00",
                subjectIds = listOf("1"),
                parallelIds = listOf("7", "8"),
                stageCode = "SCHOOL",
                team = false,
                mine = true,
                hasResult = false,
            ),
            DemoOlympiad(
                id = DEMO_OLYMPIAD_PAST_ID,
                name = "Демо-олимпиада по информатике",
                status = "PUBLISHED_FINAL_RESULTS",
                startAt = "2026-11-05T09:00:00+03:00",
                finishAt = "2026-11-12T18:00:00+03:00",
                subjectIds = listOf("3"),
                parallelIds = listOf("8"),
                stageCode = "MUNICIPAL",
                team = false,
                mine = true,
                hasResult = true,
            ),
            DemoOlympiad(
                id = "demo-olympiad-3",
                name = "Демо-олимпиада по физике",
                status = "STARTED",
                startAt = "2026-11-25T10:00:00+03:00",
                finishAt = "2026-12-02T18:00:00+03:00",
                subjectIds = listOf("4"),
                parallelIds = listOf("9", "10"),
                stageCode = "REGIONAL",
                team = true,
                mine = false,
                hasResult = false,
            ),
        )
    }

    private data class DemoOlympiad(
        val id: String,
        val name: String,
        val status: String,
        val startAt: String,
        val finishAt: String,
        val subjectIds: List<String>,
        val parallelIds: List<String>,
        val stageCode: String,
        val team: Boolean,
        val mine: Boolean,
        val hasResult: Boolean,
    )

    private data class Student(
        val personId: String,
        val lastName: String,
        val firstName: String,
        val middleName: String
    ) {
        /** Средние оценки по предметам (база «силы» ученика). */
        val profile = mutableMapOf<Int, Double>()
    }

    /** marks[personId][subjectId] = список оценок */
    private val marks = mutableMapOf<String, MutableMap<Int, MutableList<Mark>>>()
    private data class Mark(val value: Int, val date: String, val weight: Int, val form: String)

    private val students = mutableListOf<Student>()
    private val issuedTokens = mutableSetOf(DEMO_TOKEN)
    private val tokenUses = mutableMapOf(DEMO_TOKEN to 0)
    private var tokenCounter = 1
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    private lateinit var me: Student

    var port: Int = 0
        private set

    /** Заголовки последнего запроса `homeworks/launch` — для проверки в тестах. */
    @Volatile
    var lastLaunchCookie: String = ""
        private set

    @Volatile
    var lastLaunchReferer: String = ""
        private set

    /** Заголовки последнего запроса раздела «Олимпиады» — для проверки контракта. */
    @Volatile
    var lastOlympiadSubsystem: String = ""
        private set

    @Volatile
    var lastOlympiadProfileId: String = ""
        private set

    @Volatile
    var lastOlympiadAuthToken: String = ""
        private set

    @Volatile
    var lastOlympiadAuthorization: String = ""
        private set

    /** Заголовки последнего запроса раздела «Портфолио» — для проверки контракта. */
    @Volatile
    var lastPortfolioSubsystem: String = ""
        private set

    @Volatile
    var lastPortfolioProfileId: String = ""
        private set

    @Volatile
    var lastPortfolioAuthToken: String = ""
        private set

    @Volatile
    var lastPortfolioAuthorization: String = ""
        private set

    @Volatile
    var lastPortfolioCookie: String = ""
        private set

    init {
        generate()
    }

    /** Индекс «моего» ученика в списке класса. */
    private fun myIndex(): Int = 12.coerceAtMost(classSize - 1).coerceAtLeast(0)

    /** Числовой id ребёнка из профиля → personId (contingent_guid) в демо-данных. */
    private fun personIdForStudentId(studentId: String?): String =
        if (studentId == "17234614" && students.isNotEmpty()) students.first().personId else me.personId

    private fun generate() {
        val random = Random(seed)
        val mineIndex = myIndex()
        for (index in 0 until classSize) {
            val female = random.nextBoolean()
            val lastName = SURNAMES[index % SURNAMES.size] + if (female) "а" else ""
            val firstName = (if (female) FIRST_F else FIRST_M)[random.nextInt(if (female) FIRST_F.size else FIRST_M.size)]
            val middleName = (if (female) MIDDLE_F else MIDDLE_M)[random.nextInt(MIDDLE_F.size)]
            val student = Student(
                // «Мой» ученик и брат/сестра опознаются по contingent_guid из профиля:
                // ровно эти personId приходят потом в запросах ?personId=...
                personId = when (index) {
                    mineIndex -> UUID_ME
                    0 -> UUID_SIBLING
                    else -> "p-%03d-%s".format(index + 1, (index * 7919 % 1000).toString(36))
                },
                lastName = lastName,
                firstName = firstName,
                middleName = middleName
            )
            val base = 3.15 + random.nextDouble() * 1.6
            SUBJECTS.forEach { (id, name) ->
                val tilt = when {
                    name in listOf("Алгебра", "Геометрия", "Физика", "Информатика") -> 0.25
                    name in listOf("Физическая культура", "ОБЖ") -> 0.05
                    else -> -0.1
                }
                student.profile[id] = (base + tilt + (random.nextDouble() - 0.5) * 1.1).coerceIn(2.3, 5.0)
            }
            students.add(student)
        }
        me = students[mineIndex]

        val today = MeshCore.today()
        var cursor = MeshCore.schoolYearStart(today)
        val history = mutableListOf<String>()
        while (cursor < today && history.size < 120) {
            history.add(cursor)
            cursor = MeshCore.addDays(cursor, 9)
        }

        students.forEach { student ->
            val bySubject = mutableMapOf<Int, MutableList<Mark>>()
            SUBJECTS.forEach { (id, _) ->
                val ability = student.profile[id] ?: 4.0
                val list = mutableListOf<Mark>()
                history.forEachIndexed { index, date ->
                    if (random.nextDouble() < 0.55) {
                        var value = Math.round(ability + (random.nextDouble() - 0.42) * 1.15 + Math.sin(index / 2.0) * 0.18).toInt()
                        value = value.coerceIn(2, 5)
                        list.add(Mark(value, date, listOf(1, 1, 1, 2, 3)[random.nextInt(5)], FORMS[random.nextInt(FORMS.size)]))
                    }
                }
                bySubject[id] = list
            }
            marks[student.personId] = bySubject
        }
    }

    private fun averageOf(values: List<Int>): Double? =
        if (values.isEmpty()) null else MeshCore.round(values.average(), 2)

    /** Средние по предметам: subjectId = null → по всем предметам вместе. */
    private fun averages(personId: String, subjectId: Int?, toDate: String? = null): Map<Int, Double?> {
        val perSubject = marks[personId] ?: return emptyMap()
        val result = mutableMapOf<Int, Double?>()
        SUBJECTS.forEach { (id, _) ->
            if (subjectId != null && subjectId != id) return@forEach
            val values = (perSubject[id] ?: emptyList()).filter { toDate == null || it.date <= toDate }.map { it.value }
            result[id] = averageOf(values)
        }
        return result
    }

    private fun overallAverage(personId: String, toDate: String? = null): Double? {
        val values = averages(personId, null, toDate).values.filterNotNull()
        return if (values.isEmpty()) null else MeshCore.round(values.average(), 2)
    }

    /**
     * Тренд карточки предмета или строки рейтинга: «+0.25», «-0.10» или «0.00» —
     * насколько средний балл вырос (упал) за месяц. Считается по данным, а не
     * случайно, поэтому таблицы демо-режима всегда одинаковые, а пустая строка
     * означает «нет данных».
     */
    private fun trendText(current: Double?, previous: Double?): String {
        if (current == null || previous == null) return ""
        val delta = MeshCore.round(current - previous, 2) ?: return ""
        val sign = when {
            delta > 0 -> "+"
            delta < 0 -> "-"
            else -> ""
        }
        return sign + "%.2f".format(java.util.Locale.ROOT, Math.abs(delta))
    }

    // ------------------------------------------------------------------ жизнь ---

    /** Запускает сервер на свободном порту 127.0.0.1. */
    fun start(): Int {
        val socket = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        port = socket.localPort
        executor = Executors.newFixedThreadPool(4)
        executor?.submit {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (error: Exception) {
                    break
                }
                executor?.submit { handle(client) }
            }
        }
        return port
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        runCatching { executor?.shutdownNow() }
    }

    fun baseUrl(): String = "http://127.0.0.1:$port"

    private fun handle(client: Socket) {
        client.use { socket ->
            try {
                val input = BufferedInputStream(socket.getInputStream())
                val requestLine = readHttpLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val path = parts[1]
                var authorization = ""
                var authToken = ""
                var profileId = ""
                var cookie = ""
                var referer = ""
                var subsystem = ""
                var contentLength = 0
                while (true) {
                    val line = readHttpLine(input) ?: break
                    if (line.isEmpty()) break
                    val lower = line.lowercase()
                    when {
                        lower.startsWith("authorization:") -> authorization = line.substringAfter(':').trim()
                        lower.startsWith("auth-token:") -> authToken = line.substringAfter(':').trim()
                        lower.startsWith("profile-id:") -> profileId = line.substringAfter(':').trim()
                        lower.startsWith("x-mes-subsystem:") -> subsystem = line.substringAfter(':').trim()
                        lower.startsWith("cookie:") -> cookie = line.substringAfter(':').trim()
                        lower.startsWith("referer:") -> referer = line.substringAfter(':').trim()
                        lower.startsWith("content-length:") -> contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                    }
                }
                // Тело читаем ровно contentLength байт: поиск может быть кириллицей.
                val body = if (contentLength > 0) {
                    val buffer = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = input.read(buffer, read, contentLength - read)
                        if (n < 0) break
                        read += n
                    }
                    String(buffer, 0, read, Charsets.UTF_8)
                } else {
                    ""
                }
                val token = authorization.removePrefix("Bearer").trim().ifBlank { authToken }
                val bothAuth = authorization.isNotBlank() && authToken.isNotBlank()
                val (pathOnly, _) = query(path)
                if (pathOnly.startsWith("/api/contests/exec/v1/")) {
                    lastOlympiadSubsystem = subsystem
                    lastOlympiadProfileId = profileId
                    lastOlympiadAuthToken = authToken
                    lastOlympiadAuthorization = authorization
                }
                if (pathOnly.startsWith(PORTFOLIO_BASE)) {
                    lastPortfolioSubsystem = subsystem
                    lastPortfolioProfileId = profileId
                    lastPortfolioAuthToken = authToken
                    lastPortfolioAuthorization = authorization
                    lastPortfolioCookie = cookie
                }
                if (pathOnly == DEMO_CDZ_PAGE) {
                    respondRaw(socket, 200, "text/html; charset=utf-8", demoCdzPage(path))
                    return
                }
                val (status, payload) = route(path, token, method, body, bothAuth, profileId, cookie, referer)
                if (payload.has(RAW_BODY)) {
                    respondRaw(socket, status, "application/json", payload.optString(RAW_BODY))
                } else {
                    respond(socket, status, payload)
                }
            } catch (error: Exception) {
                runCatching { respond(socket, 500, JSONObject().put("message", "demo error: ${error.message}")) }
            }
        }
    }

    /** Строка HTTP-заголовка в UTF-8; читаем байты, чтобы тело с кириллицей не сбивало Content-Length. */
    private fun readHttpLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) {
                return if (out.size() == 0) null else out.toString("UTF-8")
            }
            if (byte == '\n'.code) break
            out.write(byte)
        }
        return out.toString("UTF-8").trimEnd('\r')
    }

    private fun query(path: String): Pair<String, Map<String, String>> {
        val index = path.indexOf('?')
        if (index < 0) return path to emptyMap()
        val params = path.substring(index + 1).split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2) {
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
            } else {
                null
            }
        }.toMap()
        return path.substring(0, index) to params
    }

    private fun respondRaw(socket: Socket, status: Int, contentType: String, text: String) {
        val body = text.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $status ${if (status == 200) "OK" else if (status == 302) "Found" else "Error"}\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        val out: OutputStream = socket.getOutputStream()
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    /** Простая страница вместо партнёра ЦДЗ в демо-режиме. */
    private fun demoCdzPage(fullPath: String): String {
        val (_, params) = query(fullPath)
        val title = (cdzCatalog(params["material_id"].orEmpty())?.title ?: "ЦДЗ")
            .replace("<", "&lt;")
        return "<!doctype html><html lang=ru><meta charset=utf-8>" +
            "<meta name=viewport content='width=device-width,initial-scale=1'>" +
            "<body style='font-family:sans-serif;padding:24px;background:#101418;color:#e6e9ef'>" +
            "<h2>$title</h2><p>Демо-режим: здесь открылся бы материал партнёра " +
            "(«Облако Знаний» или тест Библиотеки МЭШ).</p></body></html>"
    }

    private fun respond(socket: Socket, status: Int, payload: JSONObject) {
        val body = if (status == 204) ByteArray(0) else payload.toString().toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $status ${if (status == 200) "OK" else if (status == 204) "No Content" else "Error"}\r\n")
            if (status != 204) append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        val out: OutputStream = socket.getOutputStream()
        out.write(header.toByteArray(Charsets.UTF_8))
        if (body.isNotEmpty()) out.write(body)
        out.flush()
    }

    private fun route(
        fullPath: String,
        token: String,
        method: String = "GET",
        body: String = "",
        bothAuth: Boolean = false,
        profileId: String = "",
        cookie: String = "",
        referer: String = "",
    ): Pair<Int, JSONObject> {
        val (path, params) = query(fullPath)

        // school.mos.ru: шлюз pass/entrances отдаёт 401, если пришли ОБА заголовка
        // Authorization и Auth-Token, и принимает любой из них по отдельности.
        if (bothAuth && path == MeshCore.Paths.VISIT_DURATIONS) {
            return 401 to JSONObject().put("message", "Authorization and Auth-Token together")
        }

        // LRS не принимает Profile-Id от родительского/школьного профиля:
        // веб-дневник шлёт этот запрос без него. Старый клиент из-за этого
        // получал 401, затем бесполезный refresh 403 и «Статус недоступен».
        if (profileId.isNotBlank() && path == MeshCore.Paths.LRS_RESULTS) {
            return 401 to JSONObject().put("message", "Profile-Id is not allowed for LRS")
        }

        // обновление токена: нужен действующий токен
        if (path == MeshCore.Paths.TOKEN_REFRESH) {
            if (!issuedTokens.contains(token)) {
                return 401 to JSONObject().put("message", "Unauthorized")
            }
            val next = "mock-token-${++tokenCounter}"
            issuedTokens.add(next)
            tokenUses[next] = 0
            return 200 to JSONObject()
                .put("token", next)
                .put("role_id", MeshCore.ROLE_ID)
                .put("subsystem", MeshCore.SUBSYSTEM)
                .put("expires_at", System.currentTimeMillis() + 3_600_000)
        }

        if (!issuedTokens.contains(token)) return 401 to JSONObject().put("message", "Unauthorized")
        tokenUses[token] = (tokenUses[token] ?: 0) + 1
        if ((tokenUses[token] ?: 0) > tokenLifetime) {
            // «протухший» токен: клиент обязан обновить его сам
            return 401 to JSONObject().put("message", "Token expired")
        }

        val today = MeshCore.today()

        return when (path) {
            MeshCore.Paths.PROFILE -> 200 to JSONObject()
                .put("hash", "demo-hash")
                .put(
                    "profile",
                    JSONObject()
                        .put("id", 900_001)
                        .put("last_name", "Демидова")
                        .put("first_name", "Ольга")
                        .put("middle_name", "Сергеевна")
                        .put("email", "parent@example.ru")
                        .put("phone", "+7 (999) 000-00-00")
                )
                .put(
                    "children",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", 17_234_613)
                                .put("contingent_guid", UUID_ME)
                                .put("last_name", me.lastName)
                                .put("first_name", me.firstName)
                                .put("middle_name", me.middleName)
                                .put("class_name", "7 «Б»")
                                .put("school_name", "ГБОУ Школа № 1234")
                        )
                        .put(
                            JSONObject()
                                .put("id", 17_234_614)
                                .put("contingent_guid", UUID_SIBLING)
                                .put("last_name", students.first().lastName)
                                .put("first_name", students.first().firstName)
                                .put("middle_name", students.first().middleName)
                                .put("class_name", "3 «А»")
                                .put("school_name", "ГБОУ Школа № 1234")
                        )
                )

            MeshCore.Paths.PROFILE_BY_ID -> {
                val personId = params["personId"]
                val student = students.firstOrNull { it.personId == personId }
                if (student == null) {
                    404 to JSONObject().put("message", "person not found")
                } else {
                    200 to JSONObject()
                        .put("firstName", student.firstName)
                        .put("lastName", student.lastName)
                        .put("middleName", student.middleName)
                }
            }

            MeshCore.Paths.SUBJECTS -> {
                val personId = params["personId"] ?: UUID_ME
                val date = params["date"] ?: today
                val mine = averages(personId, null, date)
                // тот же период месяц назад — из него считается тренд
                val before = averages(personId, null, MeshCore.addDays(date, -30))
                val payload = JSONArray()
                SUBJECTS.forEach { (id, name) ->
                    val all = students.mapNotNull { averages(it.personId, id, date)[id] }
                    val value = mine[id]
                    val place = MeshCore.placeOf(all, value)
                    payload.put(
                        JSONObject()
                            .put("subjectId", id)
                            .put("subjectName", name)
                            .put(
                                "rank",
                                JSONObject()
                                    .put("averageMarkFive", value ?: JSONObject.NULL)
                                    .put("rankPlace", place ?: JSONObject.NULL)
                                    .put("rankStatus", if (value == null) "NO_DATA" else if ((place ?: 99) <= all.size * 0.3) "TOP" else "MIDDLE")
                                    .put("trend", trendText(value, before[id]))
                            )
                            .put("isExam", id == 1 || id == 3)
                    )
                }
                200 to JSONObject().put("payload", payload)
            }

            MeshCore.Paths.RANK -> {
                val subjectId = params["subjectId"]?.toIntOrNull()
                val date = params["date"] ?: today
                val previousDate = MeshCore.addDays(date, -30)
                val rows = students.map { student ->
                    val value = if (subjectId != null) averages(student.personId, subjectId, date)[subjectId] else overallAverage(student.personId, date)
                    val before = if (subjectId != null) averages(student.personId, subjectId, previousDate)[subjectId] else overallAverage(student.personId, previousDate)
                    Triple(student.personId, value, before)
                }
                val currentValues = rows.mapNotNull { it.second }
                val previousValues = rows.mapNotNull { it.third }
                val payload = JSONArray()
                rows.forEach { (personId, value, before) ->
                    payload.put(
                        JSONObject()
                            .put("personId", personId)
                            .put("imageId", 0)
                            .put(
                                "rank",
                                JSONObject()
                                    .put("averageMarkFive", value ?: JSONObject.NULL)
                                    .put("rankPlace", MeshCore.placeOf(currentValues, value) ?: JSONObject.NULL)
                                    .put("rankStatus", if ((MeshCore.placeOf(currentValues, value) ?: 99) <= 5) "TOP" else "MIDDLE")
                                    .put("trend", trendText(value, before))
                            )
                            .put(
                                "previousRank",
                                JSONObject()
                                    .put("averageMarkFive", before ?: JSONObject.NULL)
                                    .put("rankPlace", MeshCore.placeOf(previousValues, before) ?: JSONObject.NULL)
                            )
                    )
                }
                200 to JSONObject().put("payload", payload)
            }

            MeshCore.Paths.MARKS -> {
                val studentId = params["student_id"] ?: UUID_ME
                val from = params["from"] ?: "2000-01-01"
                val to = params["to"] ?: today
                val personId = personIdForStudentId(studentId)
                val payload = JSONArray()
                SUBJECTS.forEach { (id, name) ->
                    (marks[personId]?.get(id) ?: emptyList()).forEachIndexed { index, mark ->
                        if (mark.date < from || mark.date > to) return@forEachIndexed
                        payload.put(
                            JSONObject()
                                .put("id", "$id$index${personId.takeLast(3)}")
                                .put("value", mark.value)
                                .put("subject_id", id)
                                .put("subject_name", name)
                                .put("control_form_name", mark.form)
                                .put("weight", mark.weight)
                                .put("created_at", mark.date)
                                .put("is_exam", id % 7 == 0)
                                .put("comment", if (index % 7 == 3) "Были недочёты в оформлении" else "")
                        )
                    }
                }
                200 to JSONObject().put("payload", payload)
            }

            MeshCore.Paths.SUBJECT_MARKS -> {
                val studentId = params["student_id"] ?: UUID_ME
                val personId = personIdForStudentId(studentId)
                val payload = JSONArray()
                SUBJECTS.forEach { (id, name) ->
                    val values = (marks[personId]?.get(id) ?: emptyList()).map { it.value }
                    payload.put(
                        JSONObject()
                            .put("subject_id", id)
                            .put("subject_name", name)
                            .put("marks", JSONArray(values))
                            .put("average", averageOf(values) ?: JSONObject.NULL)
                            .put("final_mark", if (values.isEmpty()) JSONObject.NULL else Math.round(values.average()).toInt())
                    )
                }
                200 to JSONObject().put("payload", payload)
            }

            MeshCore.Paths.HOMEWORKS -> {
                val from = params["from"] ?: MeshCore.addDays(today, -7)
                val to = params["to"] ?: MeshCore.addDays(today, 7)
                val random = Random(4242)
                val payload = JSONArray()
                var cursor = from
                while (cursor <= to) {
                    val calendar = MeshCore.parseIso(cursor)
                    val weekday = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                    if (weekday != java.util.Calendar.SATURDAY && weekday != java.util.Calendar.SUNDAY) {
                        val count = 3 + random.nextInt(3)
                        repeat(count) { index ->
                            val (subjectId, subjectName) = SUBJECTS[random.nextInt(SUBJECTS.size)]
                            val page = 10 + random.nextInt(150)
                            val entryId = cursor.replace("-", "").toLong() * 100 + subjectId * 10 + index
                            payload.put(
                                JSONObject()
                                    .put("homework_entry_student_id", "$cursor-$subjectId-$index")
                                    .put("homework_entry_id", entryId)
                                    .put("type", "oo")
                                    .put("materials", if (index == 0) demoCdzMaterials(entryId) else JSONArray())
                                    .put("subject_id", subjectId)
                                    .put("subject_name", subjectName)
                                    .put("date_assigned_on", cursor)
                                    .put("date_prepared", MeshCore.addDays(cursor, 1 + random.nextInt(3)))
                                    .put("description", "Параграф $page, вопросы 1–4 письменно")
                                    .put("is_done", random.nextDouble() > 0.55)
                                    .put("attachments", if (random.nextDouble() > 0.9) JSONArray().put(JSONObject().put("name", "task.pdf")) else JSONArray())
                            )
                        }
                    }
                    cursor = MeshCore.addDays(cursor, 1)
                }
                200 to JSONObject().put("payload", payload)
            }

            MeshCore.Paths.SCHEDULE -> {
                val date = params["date"] ?: today
                val random = Random(date.replace("-", "").toLong())
                val pool = SUBJECTS.shuffled(random).take(7)
                val begins = listOf("08:30", "09:25", "10:30", "11:25", "12:30", "13:25", "14:30")
                val ends = listOf("09:15", "10:10", "11:15", "12:10", "13:15", "14:10", "15:15")
                val payload = JSONArray()
                pool.forEachIndexed { index, (subjectId, subjectName) ->
                    payload.put(
                        JSONObject()
                            .put("date", date)
                            .put("lesson_number", index + 1)
                            .put("begin_time", begins[index])
                            .put("end_time", ends[index])
                            .put("subject_name", subjectName)
                            .put("teacher_name", TEACHERS[random.nextInt(TEACHERS.size)])
                            .put("room_name", ROOMS[random.nextInt(ROOMS.size)])
                            .put("homework", if (random.nextBoolean()) "см. домашнее задание в журнале" else "")
                            .put("mark", if (random.nextDouble() > 0.8) 5 else JSONObject.NULL)
                            .put("is_cancel", random.nextDouble() > 0.96)
                    )
                }
                200 to JSONObject().put("payload", payload)
            }

            MeshCore.Paths.ATTENDANCE -> 200 to JSONObject().put("attendance", JSONArray())

            MeshCore.Paths.VISIT_DURATIONS -> 200 to JSONObject().put("payload", JSONArray())
            MeshCore.Paths.ADDITIONAL_MATERIALS -> {
                if (method != "POST") return 404 to JSONObject().put("message", "POST only")
                val request = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
                val asked = request.optJSONArray("materials") ?: JSONArray()
                val materials = JSONArray()
                var entryStudentId: Any = JSONObject.NULL
                for (i in 0 until asked.length()) {
                    val item = asked.optJSONObject(i) ?: continue
                    entryStudentId = item.opt("homework_entry_student_id") ?: JSONObject.NULL
                    val info = cdzCatalog(item.optString("uuid")) ?: continue
                    materials.put(
                        JSONObject()
                            .put("uuid", item.optString("uuid"))
                            .put("type", info.type)
                            .put("type_name", info.typeName)
                            .put("title", info.title)
                            .put("description", info.description)
                            .put("action_id", 1)
                            .put("action_name", "Пройти")
                            .put("partner", JSONObject().put("id", 282).put("name", info.partner).put("icon_url", JSONObject.NULL))
                            .put(
                                "urls",
                                JSONArray()
                                    .put(JSONObject().put("type", "view").put("url", "https://uchebnik.mos.ru/material/demo"))
                                    .put(JSONObject().put("type", "edit").put("url", "null"))
                            )
                    )
                }
                200 to JSONObject().put(
                    "additional_materials",
                    JSONArray().put(
                        JSONObject()
                            .put("purpose", "for_home")
                            .put(
                                "material_groups",
                                JSONArray().put(
                                    JSONObject()
                                        .put("selected_mode", "execute")
                                        .put("homework_entry_student_id", entryStudentId)
                                        .put("amount", materials.length())
                                        .put("materials", materials)
                                )
                            )
                    )
                )
            }

            MeshCore.Paths.LRS_RESULTS -> {
                val context = runCatching { JSONObject(params["context"].orEmpty()) }.getOrDefault(JSONObject())
                val materialId = context.optString("material_id")
                val content = JSONArray()
                // Детерминированно: первый материал выполнен, второй — нет, третий — через раз.
                val slot = materialId.substringAfterLast('-').toIntOrNull() ?: 9
                val workId = context.optLong("work_id")
                if (slot == 0 || (slot == 2 && workId % 2L == 0L)) {
                    val max = if (slot == 0) 5 else 1
                    val score = if (slot == 0) 4 else 1
                    val items = JSONArray()
                    repeat(max) { n ->
                        items.put(
                            JSONObject()
                                .put("task_number", 800_000_000 + n)
                                .put("score", if (n < score) 1 else 0)
                                .put("max_score", 1)
                        )
                    }
                    content.put(
                        JSONObject()
                            .put("result_id", 360_000_000 + slot)
                            .put("final", true)
                            .put("status", 2)
                            .put("success", true)
                            .put("started_at", MeshCore.addDays(today, -1) + "T15:39:29.694538Z")
                            .put("duration", 106 + slot * 40)
                            .put("recommended_mark", if (slot == 0) 4 else 1)
                            .put("recommended_mark_scale", if (slot == 0) 5 else 1)
                            .put("url_details", "${baseUrl()}$DEMO_CDZ_PAGE?material_id=$materialId&result=1")
                            .put("items", items)
                    )
                }
                200 to JSONObject()
                    .put("content", content)
                    .put("totalElements", content.length())
                    .put("empty", content.length() == 0)
            }

            MeshCore.Paths.HOMEWORK_LAUNCH -> {
                lastLaunchCookie = cookie
                lastLaunchReferer = referer
                val entry = params["homework_entry_id"].orEmpty()
                val material = params["material_id"].orEmpty()
                // Как настоящий МЭШ: student_id вместо entry_id даёт 400 (§7.1).
                if (entry.contains('-') || entry.isBlank() || material.isBlank()) {
                    return 400 to JSONObject().put("code", 400).put("error_text", "argument \"content\" is null")
                }
                // 302 без Location, URL — в теле ответа.
                302 to JSONObject().put(RAW_BODY, "${baseUrl()}$DEMO_CDZ_PAGE?material_id=$material&registration=${java.util.UUID.randomUUID()}")
            }

            OLYMPIAD_BASE + "contests/overview" -> {
                val request = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
                val mineOnly = request.optBoolean("showParticipantContests", true)
                val search = request.optString("searchString").trim()
                val pageRequest = request.optJSONObject("page")
                val number = pageRequest?.optInt("number", 0) ?: 0
                val size = (pageRequest?.optInt("size", 20) ?: 20).coerceIn(1, 50)
                val filtered = OLYMPIADS.filter { olympiad ->
                    (!mineOnly || olympiad.mine) &&
                        (search.isBlank() || olympiad.name.contains(search, ignoreCase = true))
                }
                val content = JSONArray()
                filtered.drop(number * size).take(size).forEach { olympiad ->
                    content.put(olympiadOverview(olympiad))
                }
                val totalPages = if (filtered.isEmpty()) 0 else (filtered.size + size - 1) / size
                200 to JSONObject()
                    .put(
                        "page",
                        JSONObject()
                            .put("content", content)
                            .put("totalElements", filtered.size)
                            .put("totalPages", totalPages)
                            .put("number", number)
                            .put("size", size)
                    )
                    .put("hiddenTotal", filtered.size)
            }

            OLYMPIAD_BASE + "subjects" -> 200 to dictionary(OLYMPIAD_SUBJECTS)
            OLYMPIAD_BASE + "parallels" -> 200 to dictionary(OLYMPIAD_PARALLELS)
            OLYMPIAD_BASE + "contests/stages" -> 200 to dictionary(OLYMPIAD_STAGES)
            OLYMPIAD_BASE + "contests/levels" -> 200 to dictionary(OLYMPIAD_LEVELS)

            else -> {
                if (path.startsWith(PORTFOLIO_BASE)) {
                    return portfolioRoute(path)
                }
                if (path.startsWith(OLYMPIAD_BASE + "contests/")) {
                    val tail = path.removePrefix(OLYMPIAD_BASE + "contests/")
                    val id = tail.substringBefore('/')
                    val olympiad = OLYMPIADS.firstOrNull { it.id == id }
                    if (olympiad == null) {
                        return 404 to JSONObject().put("message", "demo: unknown olympiad")
                    }
                    when {
                        tail == id -> 200 to olympiadDetail(olympiad)
                        tail.endsWith("/participant") -> {
                            if (!olympiad.mine) {
                                404 to JSONObject().put("message", "demo: not a participant")
                            } else {
                                200 to JSONObject()
                                    .put("passingState", "REGISTERED")
                                    .put("covered", true)
                                    .put(
                                        "resultsPublicationInfoCode",
                                        if (olympiad.hasResult) {
                                            "CONTEST_FINISHED_RESULTS_EXIST"
                                        } else {
                                            "CONTEST_IN_PROGRESS_RESULTS_NOT_EXIST"
                                        }
                                    )
                            }
                        }
                        tail.endsWith("/recommendation") -> {
                            if (!olympiad.mine) {
                                // Как в HAR: рекомендации нет — 204 без тела.
                                204 to JSONObject()
                            } else {
                                200 to JSONObject().put(
                                    "recommendation",
                                    "Демо-рекомендация: проверьте расписание и попробуйте пробный тур."
                                )
                            }
                        }
                        tail.endsWith("/result") -> 200 to olympiadResult(olympiad)
                        tail.endsWith("/url-details/link") -> 200 to JSONObject()
                            .put("link", "https://example.org/demo-result")
                        else -> 404 to JSONObject().put("message", "demo: unknown olympiad path")
                    }
                } else {
                    404 to JSONObject().put("message", "demo: unknown path $path")
                }
            }
        }
    }

    // ---------------------------------------------------------- ПОРТФОЛИО ----

    private fun portfolioRoute(path: String): Pair<Int, JSONObject> {
        if (path == PORTFOLIO_BASE + "portfolio/users/context/") {
            return 200 to JSONObject().put("data", portfolioContext())
        }
        if (!path.startsWith(PORTFOLIO_BASE + "persons/")) {
            return 404 to JSONObject().put("message", "demo: unknown portfolio path")
        }
        val tail = path.removePrefix(PORTFOLIO_BASE + "persons/")
        val personId = tail.substringBefore('/')
        if (personId != UUID_ME) {
            return 404 to JSONObject().put("message", "demo: unknown portfolio person")
        }
        return when {
            tail.endsWith("/widgets/list") ->
                200 to JSONObject().put("data", portfolioWidgets())
            tail.contains("/events/list") ->
                200 to JSONObject().put("data", portfolioEvents())
            tail.contains("/rewards/list") ->
                200 to JSONObject().put("data", portfolioRewards())
            tail.contains("/academic-performance/final-mark") ->
                200 to JSONObject().put("data", portfolioFinalMarks())
            tail.contains("/v2/academic-performance/average-mark") ->
                200 to JSONObject().put("data", portfolioAverageMark())
            tail.contains("/independent-diagnostic-grouped") ->
                200 to JSONObject().put("data", portfolioDiagnostics())
            tail.contains("/employments/list") ->
                200 to JSONObject().put("data", portfolioEmployments())
            tail.contains("/cultural/list") ->
                200 to JSONObject().put("data", portfolioCultural())
            else -> 404 to JSONObject().put("message", "demo: unknown portfolio path")
        }
    }

    private fun portfolioContext(): JSONObject = JSONObject()
        .put("meshId", UUID_ME)
        .put(
            "personMeshInfo",
            JSONObject()
                .put("classLevel", "7")
                .put("schoolName", "Школа № 1234")
                .put("lastname", "Демидов")
                .put("firstname", "Пётр")
                .put("patronymic", "Алексеевич")
                .put("class", "7 «Б»")
                .put("meshId", UUID_ME)
        )

    private fun portfolioWidgets(): JSONArray = JSONArray()
        .put(
            JSONObject()
                .put("blockName", "OLYMPIAD")
                .put(
                    "data",
                    JSONObject()
                        .put("name", "Школьный этап по математике, 7 класс")
                        .put("reward", "Победитель")
                        .put("subjectName", "Математика")
                        .put("creationDate", "2026-11-19T14:10:05")
                )
        )
        .put(
            JSONObject()
                .put("blockName", "OLYMPIAD")
                .put(
                    "data",
                    JSONObject()
                        .put("name", "Школьный этап по информатике, 7 класс")
                        .put("reward", "Призёр")
                        .put("subjectName", "Информатика")
                        .put("creationDate", "2026-11-18T12:00:00")
                )
        )

    private fun portfolioEvents(): JSONArray = JSONArray()
        .put(
            JSONObject()
                .put("id", 7001L)
                .put("name", "Школьный этап по математике, 7 класс")
                .put("result", "48.0")
                .put("maxScore", 50.0)
                .put("startDate", "2026-11-19")
                .put("stageEvent", "Школьный этап")
                .put("category", JSONObject().put("value", "Учеба"))
                .put("source", JSONObject().put("value", "МЭШ Олимпиады"))
                .put("subjects", JSONArray().put(JSONObject().put("value", "Математика")))
        )
        .put(
            JSONObject()
                .put("id", 7002L)
                .put("name", "Школьный этап по информатике, 7 класс")
                .put("result", "42.0")
                .put("maxScore", 50.0)
                .put("startDate", "2026-11-18")
                .put("stageEvent", "Школьный этап")
                .put("category", JSONObject().put("value", "Учеба"))
                .put("source", JSONObject().put("value", "МЭШ Олимпиады"))
                .put("subjects", JSONArray().put(JSONObject().put("value", "Информатика")))
        )

    private fun portfolioRewards(): JSONArray = JSONArray()
        .put(
            JSONObject()
                .put("id", 8001L)
                .put("name", "Победитель школьного этапа")
                .put("date", "2026-11-20")
                .put("category", JSONObject().put("value", "Учеба"))
                .put("statusReward", JSONObject().put("value", "Победитель"))
                .put("entityId", "7001")
        )

    private fun portfolioFinalMarks(): JSONArray = JSONArray()
        .put(
            JSONObject()
                .put("yearTitle", "2025/26")
                .put("educationLevel", "Основное общее образование")
                .put("averageAllSubjects", 4.72)
                .put(
                    "subjects",
                    JSONArray()
                        .put(JSONObject().put("name", "Алгебра").put("yearValue", "5"))
                        .put(JSONObject().put("name", "Русский язык").put("yearValue", "4"))
                )
        )

    private fun portfolioAverageMark(): JSONObject = JSONObject()
        .put("averageMarkFive", 4.78)
        .put(
            "subjectsRank",
            JSONArray()
                .put(JSONObject().put("subjectName", "Алгебра").put("averageMarkFiveSubject", 4.9))
                .put(JSONObject().put("subjectName", "Информатика").put("averageMarkFiveSubject", 5.0))
        )

    private fun portfolioDiagnostics(): JSONArray = JSONArray()
        .put(
            JSONObject()
                .put("learningYear", "2025-2026")
                .put(
                    "diagnostics",
                    JSONObject()
                        .put("totalDiagnostic", JSONObject().put("total", 1))
                        .put(
                            "bestResult",
                            JSONObject()
                                .put("bestResultSubject", "Математика")
                                .put("bestResultPercent", 88.0)
                        )
                        .put(
                            "diagnostic",
                            JSONArray().put(
                                JSONObject()
                                    .put("subject", "Математика")
                                    .put("eventDate", "2026-03-12")
                                    .put("percentResult", 88.0)
                                    .put("levelType", "Высокий")
                            )
                        )
                )
        )

    private fun portfolioEmployments(): JSONArray = JSONArray()
        .put(
            JSONObject()
                .put("name", "Кружок робототехники")
                .put("startDate", "2026-09-01")
                .put("endDate", "2027-05-31")
                .put("category", JSONObject().put("value", "Дополнительное образование"))
        )

    private fun portfolioCultural(): JSONArray = JSONArray()
        .put(
            JSONObject()
                .put("visitTime", "2026-10-12")
                .put("culturalInstitutionType", "Музей")
                .put("culturalInstitutionName", "Городской музей науки")
        )

    // ---------------------------------------------------------- ОЛИМПИАДЫ ----

    private fun dictionary(items: List<Pair<String, String>>): JSONObject {
        val array = JSONArray()
        items.forEach { (id, name) ->
            array.put(JSONObject().put("id", id).put("name", name))
        }
        // Справочники в API — голый JSON-массив, а не объект.
        return JSONObject().put(RAW_BODY, array.toString())
    }

    private fun stringArray(values: List<String>): JSONArray {
        val array = JSONArray()
        values.forEach { array.put(it) }
        return array
    }

    private fun olympiadOverview(olympiad: DemoOlympiad): JSONObject =
        JSONObject()
            .put("id", JSONObject().put("value", olympiad.id))
            .put("contestName", olympiad.name)
            .put("startAt", olympiad.startAt)
            .put("finishAt", olympiad.finishAt)
            .put("statusCode", olympiad.status)
            .put("parallelIds", stringArray(olympiad.parallelIds))
            .put("subjectIds", stringArray(olympiad.subjectIds))
            .put("stageCode", olympiad.stageCode)
            .put("teamContest", olympiad.team)
            .put("recommendationExists", olympiad.mine)

    private fun olympiadDetail(olympiad: DemoOlympiad): JSONObject {
        val previous = JSONObject()
            .put("contestId", olympiad.id)
            .put("name", "Предыдущий этап демо-олимпиады")
            .put("subjects", JSONArray().put(JSONObject().put("id", "1").put("name", "Математика")))
            .put("parallels", JSONArray().put(JSONObject().put("id", "7").put("name", "7 класс")))
            .put("stage", JSONObject().put("id", "SCHOOL").put("name", "Школьный этап"))
            .put("startedAt", "2025-11-15T10:00:00+03:00")
            .put("rewards", JSONArray().put(JSONObject().put("id", "WINNER").put("name", "Победитель")))
            .put("minRank", 1)
            .put("minMark", 15.0)
        return JSONObject()
            .put("id", JSONObject().put("value", olympiad.id))
            .put("contestName", olympiad.name)
            .put("statusCode", olympiad.status)
            .put(
                "commonInfo",
                JSONObject()
                    .put("stageCode", olympiad.stageCode)
                    .put("subjectIds", stringArray(olympiad.subjectIds))
                    .put("levelCode", "1")
                    .put(
                        "coverage",
                        JSONObject()
                            .put("districtIds", JSONArray())
                            .put("organizationIds", JSONArray())
                    )
            )
            .put(
                "participationInfo",
                JSONObject()
                    .put("parallelIds", stringArray(olympiad.parallelIds))
                    .put("teamContest", olympiad.team)
                    .put("startAt", olympiad.startAt)
                    .put("finishAt", olympiad.finishAt)
            )
            .put(
                "additionalInfo",
                JSONObject()
                    .put(
                        "contestDescription",
                        "{\"blocks\":[{\"type\":\"paragraph\",\"content\":\"Демо-описание олимпиады без реальных данных.\"},{\"type\":\"paragraph\",\"content\":\"Проверка отображения rich-text.\"}]}"
                    )
                    .put("testContests", JSONArray().put("https://example.org/demo-practice"))
            )
            .put("prevContestStages", JSONArray().put(previous))
            .put("recommendationExists", olympiad.mine)
            .put("teamContest", olympiad.team)
    }

    private fun olympiadResult(olympiad: DemoOlympiad): JSONObject {
        if (!olympiad.hasResult) {
            return JSONObject().put("hasResult", false)
        }
        return JSONObject()
            .put("hasResult", true)
            .put(
                "resultModel",
                JSONObject()
                    .put("startedAt", olympiad.startAt)
                    .put("finishedAt", olympiad.finishAt)
                    .put(
                        "score",
                        JSONObject()
                            .put("mark", 18.0)
                            .put("markScale", 25.0)
                            .put("passedCount", 7)
                            .put("totalCount", 10)
                            .put("partialPassedCount", 2)
                            .put("wrongCount", 1)
                    )
                    .put("reward", JSONObject().put("code", "WINNER").put("name", "Победитель"))
                    .put(
                        "participationStatus",
                        JSONObject()
                            .put("code", "IN_COMPETITION")
                            .put("name", "В основном зачёте")
                            .put("justification", "Демо-статус участия")
                    )
                    .put("portfolioInfo", "Результат можно добавить в портфолио")
                    .put(
                        "detailsUrl",
                        "/api/contests/exec/v1/contests/${olympiad.id}/result/demo-result/url-details/link"
                    )
            )
    }

    // ------------------------------------------------------------- ЦДЗ демо --

    private data class DemoCdz(
        val type: String,
        val typeName: String,
        val title: String,
        val description: String,
        val partner: String
    )

    private val cdzTemplates = listOf(
        DemoCdz("fizikon_module", "Самостоятельная работа", "Воздух и его состав",
            "Интерактивная работа с автоматической проверкой.", "Облако Знаний"),
        DemoCdz("test_task_binding", "Задание", "Основные компоненты воздуха",
            "Задание из Библиотеки МЭШ.", "Библиотека МЭШ"),
        DemoCdz("test_spec_binding", "Тематический тест", "Входное тестирование",
            "Тест по теме урока.", "Библиотека МЭШ")
    )

    /** uuid демо-ЦДЗ: cdz-<homework_entry_id>-<номер шаблона>. */
    private fun cdzCatalog(uuid: String): DemoCdz? {
        if (!uuid.startsWith("cdz-")) return null
        val slot = uuid.substringAfterLast('-').toIntOrNull() ?: return null
        return cdzTemplates.getOrNull(slot)
    }

    private fun demoCdzMaterials(entryId: Long): JSONArray {
        val list = JSONArray()
        val count = 1 + (entryId % 3).toInt()
        for (slot in 0 until count) {
            val info = cdzTemplates[slot]
            list.put(
                JSONObject()
                    .put("uuid", "cdz-$entryId-$slot")
                    .put("type", info.type)
                    .put("selected_mode", "execute")
                    .put("type_name", if (slot == 0) "Облако знаний" else "Прочее")
                    .put("title", info.title)
                    .put("urls", JSONArray())
                    .put("action_id", 1)
                    .put("action_name", "Пройти")
            )
        }
        return list
    }
}
