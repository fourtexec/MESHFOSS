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
                val payload = SUBJECTS.map { (id, name) ->
                    val avg = AVERAGES[PREVIEW_PERSON_ID]?.get(id) ?: 4.7
                    val mark = Math.round(avg).toInt()
                    mapOf(
                        "subject_id" to id,
                        "subject_name" to name,
                        "year_mark" to mark,
                        "periods" to listOf(
                            mapOf("ordinal" to "I", "name" to "Первая", "value" to mark),
                            mapOf("ordinal" to "II", "name" to "Вторая", "value" to avg)
                        )
                    )
                }
                200 to mapOf("payload" to payload)
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

            else -> 200 to mapOf("payload" to emptyList<Any>())
        }
    }

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
