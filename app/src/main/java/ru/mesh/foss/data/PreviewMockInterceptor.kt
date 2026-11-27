package ru.mesh.foss.data

import com.google.gson.GsonBuilder
import java.util.Calendar
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import ru.mesh.foss.core.MeshCore

/**
 * Локальный in-memory перехватчик для предпросмотра приложения без входа в МЭШ.
 * Работает внутри OkHttp-стека Retrofit + Gson без сетевых запросов и сокетов.
 */
internal class PreviewMockInterceptor : Interceptor {

    companion object {
        const val PREVIEW_TOKEN = "meshfoss-preview-session"
        const val PREVIEW_STUDENT_ID = "17234613"
        const val PREVIEW_PERSON_ID = "11111111-1111-4111-8111-111111111111"

        /** Синтетические олимпиады предпросмотра. */
        const val PREVIEW_OLYMPIAD_ID = "demo-olympiad-1"
        const val PREVIEW_OLYMPIAD_PAST_ID = "demo-olympiad-2"
        private const val OLYMPIAD_BASE = "/api/contests/exec/v1/"
        private const val PORTFOLIO_BASE = "/portfolio/app/"

        private val SUBJECTS = listOf(
            1 to "Алгебра",
            2 to "Геометрия",
            3 to "Русский язык",
            4 to "Литература",
            5 to "Английский язык",
            6 to "История",
            7 to "Физика",
            8 to "Информатика"
        )

        private val CLASSMATES = listOf(
            PREVIEW_PERSON_ID to Triple("Пётр", "Демидов", "Алексеевич"),
            "p-001" to Triple("Анна", "Белова", "Сергеевна"),
            "p-002" to Triple("Максим", "Виноградов", "Иванович"),
            "p-003" to Triple("София", "Гусева", "Дмитриевна"),
            "p-004" to Triple("Егор", "Кузнецов", "Алексеевич"),
            "p-005" to Triple("Дарья", "Лебедева", "Павловна"),
            "p-006" to Triple("Кирилл", "Морозов", "Сергеевич"),
            "p-007" to Triple("Алиса", "Никитина", "Ивановна")
        )

        private val AVERAGES = mapOf(
            PREVIEW_PERSON_ID to mapOf(1 to 4.85, 2 to 4.70, 3 to 4.55, 4 to 4.90, 5 to 4.80, 6 to 4.65, 7 to 4.95, 8 to 5.00),
            "p-001" to mapOf(1 to 4.90, 2 to 4.85, 3 to 4.92, 4 to 4.88, 5 to 4.75, 6 to 4.80, 7 to 4.72, 8 to 4.84),
            "p-002" to mapOf(1 to 4.60, 2 to 4.45, 3 to 4.30, 4 to 4.50, 5 to 4.40, 6 to 4.55, 7 to 4.68, 8 to 4.75),
            "p-003" to mapOf(1 to 4.78, 2 to 4.65, 3 to 4.82, 4 to 4.95, 5 to 4.88, 6 to 4.70, 7 to 4.52, 8 to 4.60),
            "p-004" to mapOf(1 to 4.20, 2 to 4.10, 3 to 3.95, 4 to 4.15, 5 to 4.35, 6 to 4.25, 7 to 4.40, 8 to 4.55),
            "p-005" to mapOf(1 to 4.50, 2 to 4.42, 3 to 4.68, 4 to 4.74, 5 to 4.62, 6 to 4.48, 7 to 4.36, 8 to 4.44),
            "p-006" to mapOf(1 to 3.92, 2 to 3.85, 3 to 4.05, 4 to 4.12, 5 to 3.98, 6 to 4.18, 7 to 4.10, 8 to 4.25),
            "p-007" to mapOf(1 to 4.45, 2 to 4.38, 3 to 4.52, 4 to 4.60, 5 to 4.70, 6 to 4.35, 7 to 4.28, 8 to 4.50)
        )
    }

    private val gson = GsonBuilder().serializeNulls().create()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val path = url.encodedPath
        val params = url.queryParameterNames.associateWith { name -> url.queryParameter(name).orEmpty() }
        val (status, payload) = route(path, params)
        val json = gson.toJson(payload)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message(if (status == 200) "OK" else "Preview")
            .body(json.toResponseBody(jsonMediaType))
            .build()
    }

    private fun route(path: String, params: Map<String, String>): Pair<Int, Any> {
        val today = MeshCore.today()
        return when {
            path == MeshCore.Paths.PROFILE -> 200 to mapOf(
                "hash" to "preview-hash",
                "profile" to mapOf(
                    "id" to 900001,
                    "last_name" to "Демидова",
                    "first_name" to "Ольга",
                    "middle_name" to "Сергеевна"
                ),
                "children" to listOf(
                    mapOf(
                        "id" to PREVIEW_STUDENT_ID.toLong(),
                        "contingent_guid" to PREVIEW_PERSON_ID,
                        "last_name" to "Демидов",
                        "first_name" to "Пётр",
                        "middle_name" to "Алексеевич",
                        "class_name" to "7 «Б»",
                        "school_name" to "Школа № 1234"
                    )
                )
            )

            path == MeshCore.Paths.PROFILE_BY_ID -> {
                val id = params["personId"].orEmpty()
                val person = CLASSMATES.firstOrNull { it.first == id }?.second
                if (person == null) {
                    404 to mapOf("message" to "person not found")
                } else {
                    200 to mapOf(
                        "firstName" to person.first,
                        "lastName" to person.second,
                        "middleName" to person.third
                    )
                }
            }

            path == MeshCore.Paths.SUBJECTS -> {
                val mine = AVERAGES[PREVIEW_PERSON_ID].orEmpty()
                val payload = SUBJECTS.mapIndexed { idx, (id, name) ->
                    val avg = mine[id] ?: 4.5
                    val allValues = AVERAGES.values.mapNotNull { it[id] }
                    val place = MeshCore.placeOf(allValues, avg) ?: (idx + 1)
                    mapOf(
                        "subjectId" to id,
                        "subjectName" to name,
                        "rank" to mapOf(
                            "averageMarkFive" to avg,
                            "rankPlace" to place,
                            "rankStatus" to if (place <= 3) "TOP" else "MIDDLE",
                            "trend" to if (idx % 2 == 0) "UP" else "STABLE"
                        ),
                        "isExam" to (id == 1 || id == 3)
                    )
                }
                200 to mapOf("payload" to payload)
            }

            path == MeshCore.Paths.RANK -> {
                val subjectId = params["subjectId"]?.toIntOrNull()
                val scores = CLASSMATES.map { (id, _) ->
                    val map = AVERAGES[id].orEmpty()
                    val avg = if (subjectId != null) (map[subjectId] ?: 4.3) else map.values.average()
                    id to (Math.round(avg * 100.0) / 100.0)
                }
                val values = scores.map { it.second }
                val payload = scores.map { (id, avg) ->
                    val place = MeshCore.placeOf(values, avg) ?: 1
                    val prev = Math.round((avg - 0.04) * 100.0) / 100.0
                    mapOf(
                        "personId" to id,
                        "imageId" to 0,
                        "rank" to mapOf(
                            "averageMarkFive" to avg,
                            "rankPlace" to place,
                            "rankStatus" to if (place <= 3) "TOP" else "MIDDLE",
                            "trend" to if (id == PREVIEW_PERSON_ID) "UP" else "STABLE"
                        ),
                        "previousRank" to mapOf(
                            "averageMarkFive" to prev,
                            "rankPlace" to place
                        )
                    )
                }
                200 to mapOf("payload" to payload)
            }

            path == MeshCore.Paths.MARKS -> {
                val payload = listOf(
                    markItem("901", 5, 8, "Информатика", "Проект", 2, today, "Отличная реализация"),
                    markItem("902", 5, 7, "Физика", "Лабораторная работа", 2, today),
                    markItem("903", 5, 1, "Алгебра", "Контрольная работа", 3, MeshCore.addDays(today, -1)),
                    markItem("904", 4, 3, "Русский язык", "Сочинение", 2, MeshCore.addDays(today, -2), "Обратить внимание на пунктуацию"),
                    markItem("905", 5, 4, "Литература", "Ответ на уроке", 1, MeshCore.addDays(today, -3)),
                    markItem("906", 5, 5, "Английский язык", "Аудирование", 1, MeshCore.addDays(today, -4)),
                    markItem("907", 4, 6, "История", "Тест", 1, MeshCore.addDays(today, -5)),
                    markItem("908", 5, 2, "Геометрия", "Самостоятельная работа", 2, MeshCore.addDays(today, -6))
                )
                200 to mapOf("payload" to payload)
            }

            path.startsWith("${MeshCore.Paths.MARKS}/") -> {
                val id = path.substringAfterLast('/')
                200 to mapOf(
                    "id" to id,
                    "value" to 5,
                    "subject_name" to "Алгебра",
                    "control_form_name" to "Контрольная работа",
                    "weight" to 3,
                    "created_at" to today,
                    "comment" to "Предпросмотр деталей оценки",
                    "topic_name" to "Квадратные уравнения",
                    "class_results" to mapOf(
                        "total_students" to 26,
                        "marks_distributions" to listOf(
                            mapOf("mark_value" to 5, "number_of_students" to 11, "percentage_of_students" to 42),
                            mapOf("mark_value" to 4, "number_of_students" to 10, "percentage_of_students" to 38),
                            mapOf("mark_value" to 3, "number_of_students" to 5, "percentage_of_students" to 20)
                        )
                    )
                )
            }

            path == MeshCore.Paths.SUBJECT_MARKS -> {
                val mine = AVERAGES[PREVIEW_PERSON_ID].orEmpty()
                val payload = SUBJECTS.map { (id, name) ->
                    val avg = mine[id] ?: 4.6
                    val list = if (avg >= 4.8) listOf(5, 5, 5, 4, 5) else listOf(5, 4, 5, 4, 5)
                    mapOf(
                        "subject_id" to id,
                        "subject_name" to name,
                        "marks" to list,
                        "average" to avg,
                        "final_mark" to Math.round(avg).toInt()
                    )
                }
                200 to mapOf("payload" to payload)
            }

            path == MeshCore.Paths.ACADEMIC_YEARS -> 200 to mapOf(
                "payload" to listOf(
                    mapOf("id" to 1, "name" to "2026–2027", "current_year" to true)
                )
            )

            path == MeshCore.Paths.FINAL_MARKS -> {
                val subjects = SUBJECTS.map { (id, name) ->
                    val avg = AVERAGES[PREVIEW_PERSON_ID]?.get(id) ?: 4.7
                    val mark = Math.round(avg).toInt()
                    mapOf(
                        "id" to id,
                        "name" to name,
                        "year_mark" to mark,
                        "periods" to listOf(
                            mapOf("id" to 1, "ordinal" to "I", "name" to "Первая", "value" to mark),
                            mapOf("id" to 2, "ordinal" to "II", "name" to "Вторая", "value" to avg)
                        )
                    )
                }
                200 to mapOf("subjects" to subjects)
            }

            path == MeshCore.Paths.HOMEWORKS -> {
                val from = params["from"] ?: MeshCore.addDays(today, -3)
                val hw = listOf(
                    mapOf(
                        "homework_entry_student_id" to "pv-hw-1",
                        "homework_entry_id" to 101L,
                        "type" to "OO",
                        "materials" to emptyList<Any>(),
                        "subject_id" to 1,
                        "subject_name" to "Алгебра",
                        "date_assigned_on" to today,
                        "date_prepared" to MeshCore.addDays(today, 1),
                        "description" to "§ 14, № 412–416 письменно",
                        "is_done" to false,
                        "attachments" to emptyList<Any>()
                    ),
                    mapOf(
                        "homework_entry_student_id" to "pv-hw-2",
                        "homework_entry_id" to 102L,
                        "type" to "OO",
                        "materials" to emptyList<Any>(),
                        "subject_id" to 7,
                        "subject_name" to "Физика",
                        "date_assigned_on" to today,
                        "date_prepared" to MeshCore.addDays(today, 1),
                        "description" to "Подготовить отчёт по лабораторной работе № 3",
                        "is_done" to true,
                        "attachments" to emptyList<Any>()
                    ),
                    mapOf(
                        "homework_entry_student_id" to "pv-hw-3",
                        "homework_entry_id" to 103L,
                        "type" to "OO",
                        "materials" to emptyList<Any>(),
                        "subject_id" to 5,
                        "subject_name" to "Английский язык",
                        "date_assigned_on" to from,
                        "date_prepared" to today,
                        "description" to "Unit 4, ex. 5–7; выучить новые слова",
                        "is_done" to false,
                        "attachments" to emptyList<Any>()
                    )
                )
                200 to mapOf("payload" to hw)
            }

            path.startsWith("/api/family/web/v1/homeworks/") && path.endsWith("/done") ->
                200 to mapOf("success" to true)

            path == MeshCore.Paths.SCHEDULE -> {
                val date = params["date"] ?: today
                val lessons = listOf(
                    lessonItem(date, 1, "08:30", "09:15", "Алгебра", "Соколова И. П.", "204", "§ 14, № 412–416", 5),
                    lessonItem(date, 2, "09:25", "10:10", "Русский язык", "Мельникова О. В.", "306", "Упр. 184", null),
                    lessonItem(date, 3, "10:30", "11:15", "Физика", "Гаврилов А. Н.", "каб. 118", "Отчёт № 3", 5),
                    lessonItem(date, 4, "11:25", "12:10", "Английский язык", "Васильева Е. А.", "211", "Unit 4, ex. 5–7", null),
                    lessonItem(date, 5, "12:30", "13:15", "История", "Ткачук Д. С.", "308", "§ 9 пересказ", 4),
                    lessonItem(date, 6, "13:25", "14:10", "Информатика", "Гаврилов А. Н.", "402", "Практикум: графы", 5)
                )
                200 to mapOf("payload" to lessons)
            }

            path == MeshCore.Paths.ATTENDANCE -> 200 to mapOf(
                "attendance" to listOf(
                    mapOf(
                        "date" to MeshCore.addDays(today, -2),
                        "summary" to "Уважительная причина",
                        "notified" to true,
                        "lessons" to listOf(
                            mapOf("subject_name" to "История", "description" to "Олимпиада")
                        )
                    )
                ),
                "days_count" to 1,
                "year_description" to "2026–2027"
            )

            path == MeshCore.Paths.VISIT_DURATIONS -> 200 to mapOf(
                "payload" to listOf(
                    mapOf(
                        "date" to today,
                        "visits" to listOf(
                            mapOf(
                                "in" to "08:16",
                                "out" to "14:28",
                                "duration" to "6ч 12м",
                                "organizationShortName" to "Школа № 1234"
                            )
                        )
                    )
                )
            )

            path == MeshCore.Paths.FOOD_STATE -> 200 to mapOf(
                "discountedMeals" to false,
                "preorderAllowed" to true,
                "foodboxAllowed" to true,
                "addresses" to listOf(
                    mapOf(
                        "address" to mapOf(
                            "id" to 1,
                            "organizationName" to "ГБОУ Школа № 1234",
                            "text" to "ул. Школьная, д. 12"
                        ),
                        "settings" to mapOf(
                            "foodboxAvailable" to true,
                            "preorderAvailable" to true
                        )
                    )
                )
            )

            path == MeshCore.Paths.FOOD_BALANCE -> 200 to mapOf(
                "payload" to listOf(
                    mapOf(
                        "contractId" to 17234613L,
                        "balance" to 48550L,
                        "expenseDayLimit" to 35000L
                    )
                )
            )

            path == MeshCore.Paths.FOOD_MENU -> 200 to mapOf(
                "payload" to listOf(
                    mapOf(
                        "onDate" to today,
                        "items" to listOf(
                            mapOf(
                                "id" to 1,
                                "name" to "Комплексный обед № 1",
                                "kind" to 2,
                                "price" to 24000L,
                                "preorderAllowed" to true,
                                "paymentTypes" to listOf(1),
                                "complexItems" to listOf(
                                    mapOf("dish" to mapOf("id" to 11, "name" to "Борщ со сметаной", "weight" to "250 г", "calories" to 180)),
                                    mapOf("dish" to mapOf("id" to 12, "name" to "Котлета куриная с пюре", "weight" to "220 г", "calories" to 340)),
                                    mapOf("dish" to mapOf("id" to 13, "name" to "Компот из сухофруктов", "weight" to "200 мл", "calories" to 90))
                                )
                            ),
                            mapOf(
                                "id" to 2,
                                "name" to "Завтрак школьный",
                                "kind" to 0,
                                "price" to 13500L,
                                "preorderAllowed" to true,
                                "paymentTypes" to listOf(1),
                                "complexItems" to listOf(
                                    mapOf("dish" to mapOf("id" to 21, "name" to "Сырники со сгущёнкой", "weight" to "150 г", "calories" to 290)),
                                    mapOf("dish" to mapOf("id" to 22, "name" to "Чай с лимоном", "weight" to "200 мл", "calories" to 45))
                                )
                            )
                        )
                    )
                )
            )

            path == MeshCore.Paths.FOOD_ORDERS -> 200 to mapOf("orders" to emptyList<Any>())

            path == OLYMPIAD_BASE + "contests/overview" -> 200 to mapOf(
                "page" to mapOf(
                    "content" to previewOlympiads(),
                    "totalElements" to 3,
                    "totalPages" to 1,
                    "number" to 0,
                    "size" to 20
                ),
                "hiddenTotal" to 3
            )

            path == OLYMPIAD_BASE + "subjects" -> 200 to previewOlympiadSubjects()
            path == OLYMPIAD_BASE + "parallels" -> 200 to previewOlympiadParallels()
            path == OLYMPIAD_BASE + "contests/stages" -> 200 to previewOlympiadStages()
            path == OLYMPIAD_BASE + "contests/levels" -> 200 to previewOlympiadLevels()

            path.startsWith(OLYMPIAD_BASE + "contests/") -> {
                val tail = path.removePrefix(OLYMPIAD_BASE + "contests/")
                val id = tail.substringBefore('/')
                val known = previewOlympiads().firstOrNull { (it["id"] as Map<*, *>)["value"] == id }
                when {
                    known == null -> 404 to mapOf("message" to "preview: unknown olympiad")
                    tail == id -> 200 to previewOlympiadDetail(id)
                    tail.endsWith("/participant") -> if (id == "demo-olympiad-3") {
                        404 to mapOf("message" to "preview: not a participant")
                    } else {
                        200 to mapOf(
                            "passingState" to "REGISTERED",
                            "covered" to true,
                            "resultsPublicationInfoCode" to if (id == PREVIEW_OLYMPIAD_PAST_ID) {
                                "CONTEST_FINISHED_RESULTS_EXIST"
                            } else {
                                "CONTEST_IN_PROGRESS_RESULTS_NOT_EXIST"
                            }
                        )
                    }
                    tail.endsWith("/recommendation") -> 200 to mapOf(
                        "recommendation" to "Демо-рекомендация: проверьте расписание и попробуйте пробный тур."
                    )
                    tail.endsWith("/url-details/link") -> 200 to mapOf(
                        "link" to
                            "https://uchebnik.mos.ru/contests/challenge/100000001/attempt/200000001"
                    )
                    tail.endsWith("/result") -> 200 to if (id == PREVIEW_OLYMPIAD_PAST_ID) {
                        mapOf(
                            "hasResult" to true,
                            "resultModel" to mapOf(
                                "startedAt" to "2026-11-05T09:00:00+03:00",
                                "finishedAt" to "2026-11-12T18:00:00+03:00",
                                "score" to mapOf(
                                    "mark" to 18.0,
                                    "markScale" to 25.0,
                                    "passedCount" to 7,
                                    "totalCount" to 10,
                                    "partialPassedCount" to 2,
                                    "wrongCount" to 1
                                ),
                                "reward" to mapOf("code" to "WINNER", "name" to "Победитель"),
                                "participationStatus" to mapOf(
                                    "code" to "IN_COMPETITION",
                                    "name" to "В основном зачёте",
                                    "justification" to "Демо-статус участия"
                                ),
                                "portfolioInfo" to "Результат можно добавить в портфолио",
                                "detailsUrl" to
                                    "/api/contests/exec/v1/contests/$id/result/demo-result/url-details/link"
                            )
                        )
                    } else {
                        mapOf("hasResult" to false)
                    }
                    else -> 404 to mapOf("message" to "preview: unknown olympiad path")
                }
            }

            path == PORTFOLIO_BASE + "portfolio/users/context/" ->
                200 to mapOf("data" to previewPortfolioContext())

            path.startsWith(PORTFOLIO_BASE + "persons/") -> {
                val tail = path.removePrefix(PORTFOLIO_BASE + "persons/")
                val personId = tail.substringBefore('/')
                if (personId != PREVIEW_PERSON_ID) {
                    404 to mapOf("message" to "preview: unknown portfolio person")
                } else {
                    when {
                        tail.endsWith("/widgets/list") ->
                            200 to mapOf("data" to previewPortfolioWidgets())
                        tail.contains("/events/list") ->
                            200 to mapOf("data" to previewPortfolioEvents())
                        tail.contains("/rewards/list") ->
                            200 to mapOf("data" to previewPortfolioRewards())
                        tail.contains("/academic-performance/final-mark") ->
                            200 to mapOf("data" to previewPortfolioFinalMarks())
                        tail.contains("/v2/academic-performance/average-mark") ->
                            200 to mapOf("data" to previewPortfolioAverageMark())
                        tail.contains("/independent-diagnostic-grouped") ->
                            200 to mapOf("data" to previewPortfolioDiagnostics())
                        tail.contains("/employments/list") ->
                            200 to mapOf("data" to previewPortfolioEmployments())
                        tail.contains("/cultural/list") ->
                            200 to mapOf("data" to previewPortfolioCultural())
                        else -> 404 to mapOf("message" to "preview: unknown portfolio path")
                    }
                }
            }

            else -> 200 to mapOf("payload" to emptyList<Any>())
        }
    }

    private fun previewOlympiads(): List<Map<String, Any>> = listOf(
        mapOf(
            "id" to mapOf("value" to PREVIEW_OLYMPIAD_ID),
            "contestName" to "Демо-олимпиада по математике",
            "startAt" to "2026-12-10T10:00:00+03:00",
            "finishAt" to "2026-12-20T18:00:00+03:00",
            "statusCode" to "REGISTRATION",
            "parallelIds" to listOf("7", "8"),
            "subjectIds" to listOf("1"),
            "stageCode" to "SCHOOL",
            "teamContest" to false,
            "recommendationExists" to true
        ),
        mapOf(
            "id" to mapOf("value" to PREVIEW_OLYMPIAD_PAST_ID),
            "contestName" to "Демо-олимпиада по информатике",
            "startAt" to "2026-11-05T09:00:00+03:00",
            "finishAt" to "2026-11-12T18:00:00+03:00",
            "statusCode" to "PUBLISHED_FINAL_RESULTS",
            "parallelIds" to listOf("8"),
            "subjectIds" to listOf("3"),
            "stageCode" to "MUNICIPAL",
            "teamContest" to false,
            "recommendationExists" to true
        ),
        mapOf(
            "id" to mapOf("value" to "demo-olympiad-3"),
            "contestName" to "Демо-олимпиада по физике",
            "startAt" to "2026-11-25T10:00:00+03:00",
            "finishAt" to "2026-12-02T18:00:00+03:00",
            "statusCode" to "STARTED",
            "parallelIds" to listOf("9", "10"),
            "subjectIds" to listOf("4"),
            "stageCode" to "REGIONAL",
            "teamContest" to true,
            "recommendationExists" to false
        )
    )

    private fun previewOlympiadDetail(id: String): Map<String, Any> {
        val item = previewOlympiads().first { (it["id"] as Map<*, *>)["value"] == id }
        val subjectIds = item["subjectIds"] as? List<String> ?: emptyList()
        val parallelIds = item["parallelIds"] as? List<String> ?: emptyList()
        return mapOf(
            "id" to mapOf("value" to id),
            "contestName" to (item["contestName"] as? String).orEmpty(),
            "statusCode" to (item["statusCode"] as? String).orEmpty(),
            "commonInfo" to mapOf(
                "stageCode" to (item["stageCode"] as? String).orEmpty(),
                "subjectIds" to subjectIds,
                "levelCode" to "1",
                "coverage" to mapOf(
                    "districtIds" to emptyList<String>(),
                    "organizationIds" to emptyList<String>()
                )
            ),
            "participationInfo" to mapOf(
                "parallelIds" to parallelIds,
                "teamContest" to (item["teamContest"] as? Boolean ?: false),
                "startAt" to (item["startAt"] as? String).orEmpty(),
                "finishAt" to (item["finishAt"] as? String).orEmpty()
            ),
            "additionalInfo" to mapOf(
                "contestDescription" to
                    "{\"blocks\":[{\"type\":\"paragraph\",\"content\":\"Демо-описание олимпиады без реальных данных.\"},{\"type\":\"paragraph\",\"content\":\"Проверка отображения rich-text.\"}]}",
                "testContests" to listOf("https://example.org/demo-practice")
            ),
            "prevContestStages" to listOf(
                mapOf(
                    "contestId" to id,
                    "name" to "Предыдущий этап демо-олимпиады",
                    "subjects" to listOf(mapOf("id" to "1", "name" to "Математика")),
                    "parallels" to listOf(mapOf("id" to "7", "name" to "7 класс")),
                    "stage" to mapOf("id" to "SCHOOL", "name" to "Школьный этап"),
                    "startedAt" to "2025-11-15T10:00:00+03:00",
                    "rewards" to listOf(mapOf("id" to "WINNER", "name" to "Победитель")),
                    "minRank" to 1,
                    "minMark" to 15.0
                )
            ),
            "recommendationExists" to true,
            "teamContest" to (item["teamContest"] as? Boolean ?: false)
        )
    }

    private fun previewOlympiadSubjects(): List<Map<String, String>> = listOf(
        mapOf("id" to "1", "name" to "Математика"),
        mapOf("id" to "2", "name" to "Русский язык"),
        mapOf("id" to "3", "name" to "Информатика"),
        mapOf("id" to "4", "name" to "Физика"),
        mapOf("id" to "5", "name" to "История")
    )

    private fun previewOlympiadParallels(): List<Map<String, String>> = listOf(
        mapOf("id" to "7", "name" to "7 класс"),
        mapOf("id" to "8", "name" to "8 класс"),
        mapOf("id" to "9", "name" to "9 класс"),
        mapOf("id" to "10", "name" to "10 класс"),
        mapOf("id" to "11", "name" to "11 класс")
    )

    private fun previewOlympiadStages(): List<Map<String, String>> = listOf(
        mapOf("id" to "SCHOOL", "name" to "Школьный этап"),
        mapOf("id" to "MUNICIPAL", "name" to "Муниципальный этап"),
        mapOf("id" to "REGIONAL", "name" to "Региональный этап"),
        mapOf("id" to "FINAL", "name" to "Заключительный этап")
    )

    private fun previewOlympiadLevels(): List<Map<String, String>> = listOf(
        mapOf("id" to "1", "name" to "I уровень"),
        mapOf("id" to "2", "name" to "II уровень"),
        mapOf("id" to "3", "name" to "III уровень")
    )

    private fun previewPortfolioContext(): Map<String, Any> = mapOf(
        "meshId" to PREVIEW_PERSON_ID,
        "personMeshInfo" to mapOf(
            "classLevel" to "7",
            "schoolName" to "Школа № 1234",
            "lastname" to "Демидов",
            "firstname" to "Пётр",
            "patronymic" to "Алексеевич",
            "class" to "7 «Б»",
            "meshId" to PREVIEW_PERSON_ID
        )
    )

    private fun previewPortfolioWidgets(): List<Map<String, Any>> = listOf(
        mapOf(
            "blockName" to "OLYMPIAD",
            "data" to mapOf(
                "name" to "Школьный этап по математике, 7 класс",
                "reward" to "Победитель",
                "subjectName" to "Математика",
                "creationDate" to "2026-11-19T14:10:05"
            )
        ),
        mapOf(
            "blockName" to "OLYMPIAD",
            "data" to mapOf(
                "name" to "Школьный этап по информатике, 7 класс",
                "reward" to "Призёр",
                "subjectName" to "Информатика",
                "creationDate" to "2026-11-18T12:00:00"
            )
        )
    )

    private fun previewPortfolioEvents(): List<Map<String, Any>> = listOf(
        mapOf(
            "id" to 7001L,
            "name" to "Школьный этап по математике, 7 класс",
            "result" to "48.0",
            "maxScore" to 50.0,
            "startDate" to "2026-11-19",
            "stageEvent" to "Школьный этап",
            "category" to mapOf("code" to 1, "value" to "Учеба"),
            "source" to mapOf("code" to 18, "value" to "МЭШ Олимпиады"),
            "subjects" to listOf(mapOf("value" to "Математика")),
            "participationStatus" to mapOf("value" to "В основном зачёте")
        ),
        mapOf(
            "id" to 7002L,
            "name" to "Школьный этап по информатике, 7 класс",
            "result" to "42.0",
            "maxScore" to 50.0,
            "startDate" to "2026-11-18",
            "stageEvent" to "Школьный этап",
            "category" to mapOf("code" to 1, "value" to "Учеба"),
            "source" to mapOf("code" to 18, "value" to "МЭШ Олимпиады"),
            "subjects" to listOf(mapOf("value" to "Информатика")),
            "participationStatus" to mapOf("value" to "В основном зачёте")
        ),
        mapOf(
            "id" to 7003L,
            "name" to "Городской конкурс проектов",
            "result" to "Призёр",
            "startDate" to "2026-10-05",
            "stageEvent" to "Городской этап",
            "category" to mapOf("code" to 2, "value" to "Проекты"),
            "source" to mapOf("code" to 4, "value" to "МЭШ"),
            "subjects" to listOf(mapOf("value" to "Технология")),
            "participationStatus" to mapOf("value" to "Командный зачёт")
        )
    )

    private fun previewPortfolioRewards(): List<Map<String, Any>> = listOf(
        mapOf(
            "id" to 8001L,
            "name" to "Победитель",
            "date" to "2026-11-20",
            "category" to mapOf("value" to "Учеба"),
            "statusReward" to mapOf("value" to "Победитель"),
            "entityId" to "7001"
        ),
        mapOf(
            "id" to 8002L,
            "name" to "Призёр",
            "date" to "2026-11-19",
            "category" to mapOf("value" to "Учеба"),
            "statusReward" to mapOf("value" to "Призёр"),
            "entityId" to "7002"
        )
    )

    private fun previewPortfolioFinalMarks(): List<Map<String, Any>> = listOf(
        mapOf(
            "yearTitle" to "2025/26",
            "educationLevel" to "Основное общее образование",
            "averageAllSubjects" to 4.72,
            "subjects" to listOf(
                mapOf("name" to "Алгебра", "yearValue" to "5", "yearFivePointValue" to 5),
                mapOf("name" to "Русский язык", "yearValue" to "4", "yearFivePointValue" to 4),
                mapOf("name" to "Информатика", "yearValue" to "5", "yearFivePointValue" to 5)
            )
        )
    )

    private fun previewPortfolioAverageMark(): Map<String, Any> = mapOf(
        "averageMarkFive" to 4.78,
        "subjectsRank" to listOf(
            mapOf("subjectName" to "Алгебра", "averageMarkFiveSubject" to 4.9),
            mapOf("subjectName" to "Геометрия", "averageMarkFiveSubject" to 4.7),
            mapOf("subjectName" to "Русский язык", "averageMarkFiveSubject" to 4.6),
            mapOf("subjectName" to "Информатика", "averageMarkFiveSubject" to 5.0)
        )
    )

    private fun previewPortfolioDiagnostics(): List<Map<String, Any>> = listOf(
        mapOf(
            "learningYear" to "2025-2026",
            "diagnostics" to mapOf(
                "totalDiagnostic" to mapOf("total" to 2),
                "bestResult" to mapOf(
                    "bestResultSubject" to "Математика",
                    "bestResultPercent" to 88.0
                ),
                "diagnostic" to listOf(
                    mapOf(
                        "subject" to "Математика",
                        "eventDate" to "2026-03-12",
                        "percentResult" to 88.0,
                        "levelType" to "Высокий",
                        "url" to "https://example.org/demo-diagnostic"
                    ),
                    mapOf(
                        "subject" to "Русский язык",
                        "eventDate" to "2026-02-18",
                        "percentResult" to 74.0,
                        "levelType" to "Базовый"
                    )
                )
            )
        )
    )

    private fun previewPortfolioEmployments(): List<Map<String, Any>> = listOf(
        mapOf(
            "name" to "Кружок робототехники",
            "startDate" to "2026-09-01",
            "endDate" to "2027-05-31",
            "category" to mapOf("value" to "Дополнительное образование"),
            "achievementActivityFormat" to mapOf("value" to "Очно")
        )
    )

    private fun previewPortfolioCultural(): List<Map<String, Any>> = listOf(
        mapOf(
            "visitTime" to "2026-10-12",
            "culturalInstitutionType" to "Музей",
            "culturalInstitutionName" to "Городской музей науки",
            "address" to "ул. Демонстрационная, 1"
        )
    )

    private fun markItem(
        id: String,
        value: Int,
        subjectId: Int,
        subjectName: String,
        form: String,
        weight: Int,
        date: String,
        comment: String = ""
    ) = mapOf(
        "id" to id,
        "value" to value,
        "subject_id" to subjectId,
        "subject_name" to subjectName,
        "control_form_name" to form,
        "weight" to weight,
        "created_at" to date,
        "is_exam" to false,
        "comment" to comment
    )

    private fun lessonItem(
        date: String,
        number: Int,
        begin: String,
        end: String,
        subject: String,
        teacher: String,
        room: String,
        homework: String,
        mark: Int?
    ) = mapOf(
        "date" to date,
        "lesson_number" to number,
        "begin_time" to begin,
        "end_time" to end,
        "subject_name" to subject,
        "teacher_name" to teacher,
        "room_name" to room,
        "homework" to homework,
        "mark" to mark,
        "is_cancel" to false
    )
}
