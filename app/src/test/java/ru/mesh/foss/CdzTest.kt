package ru.mesh.foss

import ru.mesh.foss.core.MeshCdz
import ru.mesh.foss.core.MeshCore
import ru.mesh.foss.core.UiLanguage
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.data.model.HomeworkLaunchLink

import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * ЦДЗ: разбор ответов по docs/mesh-cdz-api.md (§3–§7) и сквозной сценарий
 * на демо-сервере. Данные синтетические.
 */
class CdzTest {

    @Before
    fun russian() = UiLanguage.select("ru")

    // ------------------------------------------------------------ разбор ----

    private val homeworkJson = """
        {
          "type": "oo",
          "description": "Продолжение устного опроса + цдз",
          "materials": [
            { "uuid": "aaaa-1", "type": "fizikon_module", "selected_mode": "execute",
              "type_name": "Облако знаний", "title": "Воздух и его состав",
              "urls": [], "action_id": 1, "action_name": "Пройти" },
            { "uuid": "aaaa-2", "type": "test_spec_binding", "selected_mode": "execute",
              "type_name": "Тестовая спецификация (Тест)", "title": "Входное тестирование",
              "urls": [ { "type": "view", "url": "https://uchebnik.mos.ru/exam/challenge/1" } ],
              "action_id": 1, "action_name": "Пройти" },
            { "type": "attachments", "title": "task.pdf", "urls": [ { "url": "/files/task.pdf" } ] }
          ],
          "homework_entry_student_id": 5305857296,
          "homework_entry_id": 355435138,
          "subject_name": "Химия",
          "date_prepared_for": "2026-11-24T00:00:00",
          "is_done": false
        }
    """.trimIndent()

    @Test
    fun `ДЗ различает homework_entry_id и homework_entry_student_id`() {
        val hw = MeshCore.normalizeHomework(JSONObject(homeworkJson))
        assertEquals("355435138", hw.getString("homeworkEntryId"))
        assertEquals("5305857296", hw.getString("entryStudentId"))
    }

    @Test
    fun `ЦДЗ помечаются, вложения — нет, прямых ссылок у ЦДЗ нет`() {
        val materials = MeshCore.normalizeHomework(JSONObject(homeworkJson)).getJSONArray("materials")
        val fizikon = materials.getJSONObject(0)
        val test = materials.getJSONObject(1)
        val file = materials.getJSONObject(2)
        assertTrue(fizikon.getBoolean("isCdz"))
        assertTrue(test.getBoolean("isCdz"))
        assertFalse(file.getBoolean("isCdz"))
        assertEquals("Самостоятельная работа", fizikon.getString("typeName"))
        assertEquals("Тематический тест", test.getString("typeName"))
        // Ссылка без контекста не засчитывается — запуск только через launch.
        assertEquals("", test.getString("url"))
        assertEquals("execute", test.getString("selectedMode"))
        assertTrue(file.getString("url").endsWith("/files/task.pdf"))
    }

    @Test
    fun `тело additional_materials`() {
        val materials = MeshCore.normalizeHomework(JSONObject(homeworkJson)).getJSONArray("materials")
        val list = (0 until 2).map { materials.getJSONObject(it) }
        val body = MeshCdz.additionalMaterialsBody("5305857296", list)
        val items = body.getJSONArray("materials")
        assertEquals(2, items.length())
        val first = items.getJSONObject(0)
        assertEquals("aaaa-1", first.getString("uuid"))
        assertEquals("for_home", first.getString("purpose"))
        assertEquals("execute", first.getString("selected_mode"))
        assertEquals(5305857296L, first.getLong("homework_entry_student_id"))
    }

    @Test
    fun `ответ additional_materials раскладывается по uuid`() {
        val json = JSONObject(
            """
            { "additional_materials": [ { "purpose": "for_home", "material_groups": [ {
                "selected_mode": "execute", "amount": 1, "materials": [ {
                  "uuid": "aaaa-1", "type": "fizikon_module", "type_name": "Самостоятельная работа",
                  "title": "Воздух и его состав", "description": "Приложение разработано…",
                  "partner": { "id": 282, "name": "Облако Знаний", "icon_url": "https://example.org/i.png" },
                  "urls": [
                    { "type": "view", "url": "https://school.mos.ru/api/launcher/v1/launch?activity_url=x" },
                    { "type": "edit", "url": "null" },
                    { "type": "launch", "url": "https://school.mos.ru/api/launcher/v1/launch?activity_url=y" }
                  ] } ] } ] } ] }
            """.trimIndent()
        )
        val map = MeshCdz.normalizeAdditionalMaterials(json)
        val card = map.getValue("aaaa-1")
        assertEquals("Облако Знаний", card.getString("partnerName"))
        assertEquals("Приложение разработано…", card.getString("description"))
        assertTrue(card.getString("viewUrl").endsWith("activity_url=x"))
        assertTrue(card.getString("launchUrl").endsWith("activity_url=y"))
    }

    @Test
    fun `ссылка запуска из JSON разбирается Gson-моделью`() {
        val link = Gson().fromJson(
            """{"activity_url":"https://school.mos.ru/api/launcher/v1/launch?activity_url=x","url":null}""",
            HomeworkLaunchLink::class.java
        )
        assertEquals(
            "https://school.mos.ru/api/launcher/v1/launch?activity_url=x",
            link.firstUrl()
        )
        val oldNull = Gson().fromJson("""{"url":"null"}""", HomeworkLaunchLink::class.java)
        assertEquals(null, oldNull.firstUrl())
    }

    @Test
    fun `контекст LRS — work_type строкой, work_id числом`() {
        val context = JSONObject(MeshCdz.lrsContext("aaaa-2", "person-uuid", "355435138"))
        assertEquals("aaaa-2", context.getString("material_id"))
        assertEquals("person-uuid", context.getString("person_id"))
        assertEquals("1", context.get("work_type"))
        assertEquals(355435138L, (context.get("work_id") as Number).toLong())
    }

    @Test
    fun `пустой LRS — не выполнено`() {
        val result = MeshCdz.normalizeLrsResults(JSONObject().put("content", JSONArray()))
        assertFalse(result.getBoolean("done"))
        assertEquals("Не выполнено", MeshCdz.statusText(result))
        assertEquals("", MeshCdz.scoreText(result))
    }

    @Test
    fun `LRS — выполнено, баллы по заданиям, ссылка на результат`() {
        val json = JSONObject(
            """
            { "content": [
              { "result_id": 1, "final": false, "status": 1, "started_at": "2026-11-21T10:00:00Z", "items": [] },
              { "result_id": 2, "final": true, "status": 2, "success": true,
                "started_at": "2026-11-20T15:39:29.694538Z", "duration": 106,
                "recommended_mark": 1, "recommended_mark_scale": 1,
                "score": null, "max_score": null,
                "url_details": "https://uchebnik.mos.ru/exam/challenge/1/attempt/2",
                "items": [ { "score": 1, "max_score": 1 } ] }
            ], "totalElements": 2 }
            """.trimIndent()
        )
        val result = MeshCdz.normalizeLrsResults(json)
        assertTrue(result.getBoolean("done"))
        assertEquals(2, result.getInt("attempts"))
        assertEquals(1.0, result.getDouble("score"), 0.0)
        assertEquals(1.0, result.getDouble("maxScore"), 0.0)
        assertEquals("https://uchebnik.mos.ru/exam/challenge/1/attempt/2", result.getString("resultUrl"))
        // Москва: UTC+3 → 18:39, как в веб-дневнике.
        assertEquals(
            "Выполнено 20 ноя в 18:39 за 1 м 46 с",
            MeshCdz.statusText(result, TimeZone.getTimeZone("Europe/Moscow"))
        )
        assertEquals("1/1 баллов", MeshCdz.scoreText(result))
    }

    @Test
    fun `незавершённая попытка — начато`() {
        val json = JSONObject().put(
            "content",
            JSONArray().put(JSONObject().put("final", false).put("status", 1).put("started_at", "2026-11-21T10:00:00Z"))
        )
        val result = MeshCdz.normalizeLrsResults(json)
        assertFalse(result.getBoolean("done"))
        assertEquals("Начато, не завершено", MeshCdz.statusText(result))
    }

    @Test
    fun `длительность`() {
        assertEquals("1 м 46 с", MeshCdz.formatDuration(106))
        assertEquals("40 с", MeshCdz.formatDuration(40))
        assertEquals("1 ч 2 м", MeshCdz.formatDuration(3720))
        assertEquals("2 м", MeshCdz.formatDuration(120))
    }

    @Test
    fun `разбор времени ISO-8601`() {
        assertEquals(0L, MeshCdz.parseInstant("1970-01-01T00:00:00Z"))
        assertEquals(0L, MeshCdz.parseInstant("1970-01-01T03:00:00.123+03:00"))
        assertEquals(null, MeshCdz.parseInstant("вчера"))
    }

    @Test
    fun `только Облако Знаний запускает таймер`() {
        assertTrue(MeshCdz.startsTimer("fizikon_module"))
        assertFalse(MeshCdz.startsTimer("test_task_binding"))
    }

    // ---------------------------------------------------- демо-сервер ----

    private var server: DemoMeshServer? = null

    @After
    fun stop() {
        server?.stop()
    }

    private fun demoClient(): MeshClient {
        val demo = DemoMeshServer(seed = 7L, classSize = 26, tokenLifetime = 50)
        val port = demo.start()
        server = demo
        return MeshClient(
            baseUrl = "http://127.0.0.1:$port",
            token = DemoMeshServer.DEMO_TOKEN,
            timeoutMs = 5_000,
            cookieJar = {
                mapOf(
                    "aupd_token" to DemoMeshServer.DEMO_TOKEN,
                    "student_person_id" to "synthetic-contingent",
                    // Посторонняя кука не должна раздувать заголовок до 431.
                    "analytics_blob" to "x".repeat(9_000)
                )
            }
        )
    }

    @Test
    fun `сквозной сценарий ЦДЗ на демо-сервере`() {
        val client = demoClient()
        val today = MeshCore.today()
        val homework = client.getHomeworks(null, MeshCore.addDays(today, -10), today)
            .first { hw ->
                val materials = hw.getJSONArray("materials")
                (0 until materials.length()).count { materials.getJSONObject(it).optBoolean("isCdz") } >= 2
            }
        val status = client.getCdzStatus(homework, DemoMeshServer.UUID_ME)
        val materials = homework.getJSONArray("materials")
        val first = status.getJSONObject(materials.getJSONObject(0).getString("uuid"))
        val second = status.getJSONObject(materials.getJSONObject(1).getString("uuid"))

        assertEquals("Облако Знаний", first.getString("partnerName"))
        assertTrue(first.getJSONObject("result").getBoolean("done"))
        assertEquals("4/5 баллов", MeshCdz.scoreText(first.getJSONObject("result")))
        assertFalse(second.getJSONObject("result").getBoolean("done"))

        // Запуск: 302 с URL в теле; каждая ссылка новая (одноразовая).
        val launch1 = client.launchHomeworkMaterial(homework.getString("homeworkEntryId"), materials.getJSONObject(0).getString("uuid"))
        val launch2 = client.launchHomeworkMaterial(homework.getString("homeworkEntryId"), materials.getJSONObject(0).getString("uuid"))
        assertTrue(launch1.contains(DemoMeshServer.DEMO_CDZ_PAGE))
        assertTrue(launch1 != launch2)
        val demo = server ?: error("demo server is not running")
        assertTrue(demo.lastLaunchCookie.contains("aupd_token=${DemoMeshServer.DEMO_TOKEN}"))
        assertTrue(demo.lastLaunchCookie.contains("student_person_id=synthetic-contingent"))
        assertFalse(demo.lastLaunchCookie.contains("analytics_blob"))
        assertTrue(demo.lastLaunchCookie.length < 7_000)
        assertTrue(demo.lastLaunchReferer.endsWith("/diary/homeworks"))
    }

    @Test(expected = MeshCore.MeshError::class)
    fun `запуск с homework_entry_student_id отклоняется`() {
        val client = demoClient()
        val today = MeshCore.today()
        val homework = client.getHomeworks(null, MeshCore.addDays(today, -10), today)
            .first { it.getJSONArray("materials").length() > 0 }
        client.launchHomeworkMaterial(
            homework.getString("entryStudentId"),
            homework.getJSONArray("materials").getJSONObject(0).getString("uuid")
        )
    }
}
