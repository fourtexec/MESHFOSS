package ru.mesh.foss

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.data.model.OlympiadAttemptResponse
import ru.mesh.foss.data.model.OlympiadDetail
import ru.mesh.foss.data.model.OlympiadDictionaries
import ru.mesh.foss.data.model.OlympiadOverviewResponse
import ru.mesh.foss.feature.olympiads.OlympiadAttemptIds
import ru.mesh.foss.feature.olympiads.OlympiadAttemptLink
import ru.mesh.foss.feature.olympiads.OlympiadText
import java.util.Locale

/** JVM-тесты контракта «Олимпиады»: Gson-модели, демо-сервер и чистый текст. */
class OlympiadTest {

    private lateinit var server: DemoMeshServer
    private var port = 0

    @Before
    fun start() {
        server = DemoMeshServer(seed = 11L, classSize = 12, tokenLifetime = 100)
        port = server.start()
    }

    @After
    fun stop() {
        server.stop()
    }

    private fun client() = MeshClient(
        baseUrl = "http://127.0.0.1:$port",
        token = DemoMeshServer.DEMO_TOKEN,
        profileId = "demo-student",
        timeoutMs = 5_000,
    )

    @Test
    fun `overview parses flexible ids and total`() {
        val gson = GsonBuilder().setLenient().create()
        val parsed = gson.fromJson(
            """
            {
              "page": {
                "content": [
                  {"id": {"value": "demo-1"}, "contestName": "Первая", "subjectIds": ["1"]},
                  {"id": "demo-2", "contestName": "Вторая"}
                ],
                "totalElements": 2
              },
              "hiddenTotal": 2
            }
            """.trimIndent(),
            OlympiadOverviewResponse::class.java,
        )

        assertEquals(2, parsed.total)
        assertEquals("demo-1", parsed.items[0].resolvedContestId)
        assertEquals("demo-2", parsed.items[1].resolvedContestId)
    }

    @Test
    fun `explicit null collections do not crash parsing`() {
        val gson = GsonBuilder().setLenient().create()
        val overview = gson.fromJson(
            """{"page":null,"hiddenTotal":0}""",
            OlympiadOverviewResponse::class.java,
        )
        assertEquals(0, overview.items.size)
        assertEquals(0, overview.total)

        val detail = gson.fromJson(
            """{"prevContestStages":null,"commonInfo":{"subjectIds":null},"additionalInfo":{"testContests":null}}""",
            OlympiadDetail::class.java,
        )
        assertTrue(detail.prevContestStages.orEmpty().isEmpty())
        assertTrue(detail.commonInfo?.subjectIds.orEmpty().isEmpty())
        assertTrue(detail.additionalInfo?.testContests.orEmpty().isEmpty())
        assertEquals("", OlympiadDictionaries().subjectNames(null))
    }

    @Test
    fun `rich text description becomes plain readable text`() {
        val json = """
            {"blocks":[
              {"type":"paragraph","content":"Первая <b>строка</b>"},
              {"type":"paragraph","content":"Вторая &amp; строка"}
            ]}
        """.trimIndent()

        assertEquals(
            "Первая строка\nВторая & строка",
            OlympiadText.plainDescription(json),
        )
        assertEquals(
            "Строка\nдве",
            OlympiadText.plainDescription("<p>Строка</p><p>две</p>"),
        )
    }

    @Test
    fun `date only formatting does not depend on timezone`() {
        assertEquals("10.12.2026", OlympiadText.formatDate("2026-12-10", Locale("ru")))
    }

    @Test
    fun `my and all olympiads plus search are separated`() {
        val client = client()

        val mine = client.getOlympiadOverview(showParticipantContests = true)
        assertEquals(2, mine.items.size)
        assertEquals(2, mine.total)
        assertEquals(DemoMeshServer.DEMO_OLYMPIAD_ID, mine.items.first().resolvedContestId)

        val all = client.getOlympiadOverview(showParticipantContests = false)
        assertEquals(3, all.items.size)
        assertEquals(3, all.total)

        val search = client.getOlympiadOverview(
            showParticipantContests = false,
            searchString = "физике",
        )
        assertEquals(1, search.items.size)
        assertEquals("demo-olympiad-3", search.items.first().resolvedContestId)
    }

    @Test
    fun `overview filters are serialized with Gson request fields`() {
        client().getOlympiadOverview(
            showParticipantContests = false,
            searchString = "физике",
            page = 1,
            size = 10,
            subjectIds = listOf("4"),
            stageCodes = listOf("REGIONAL"),
            parallelIds = listOf("9", "10"),
            years = listOf(2026, 2025),
            includeCurrent = false,
            includeFuture = true,
            includeCompleted = false,
        )

        val body = JsonParser.parseString(server.lastOlympiadOverviewBody).asJsonObject
        assertEquals("физике", body.get("searchString").asString)
        assertEquals(1, body.getAsJsonObject("page").get("number").asInt)
        assertEquals(10, body.getAsJsonObject("page").get("size").asInt)
        assertEquals("4", body.getAsJsonArray("subjectIds")[0].asString)
        assertEquals("REGIONAL", body.getAsJsonArray("stageCodes")[0].asString)
        assertEquals("9", body.getAsJsonArray("parallelIds")[0].asString)
        assertEquals("10", body.getAsJsonArray("parallelIds")[1].asString)
        assertEquals(2026, body.getAsJsonArray("years")[0].asInt)
        assertEquals(2025, body.getAsJsonArray("years")[1].asInt)
        assertFalse(body.get("includeCurrent").asBoolean)
        assertTrue(body.get("includeFuture").asBoolean)
        assertFalse(body.get("includeCompleted").asBoolean)
        assertFalse(body.get("showParticipantContests").asBoolean)
    }

    @Test
    fun `olympiad requests use constuweb without family profile headers`() {
        client().getOlympiadOverview(showParticipantContests = true)

        assertEquals("constuweb", server.lastOlympiadSubsystem)
        assertEquals("", server.lastOlympiadProfileId)
        assertEquals("", server.lastOlympiadAuthToken)
        assertTrue(server.lastOlympiadAuthorization.startsWith("Bearer "))
    }

    @Test
    fun `dictionaries detail result and recommendation are parsed`() {
        val client = client()
        val dictionaries = client.getOlympiadDictionaries()

        assertEquals(5, dictionaries.subjects.size)
        assertEquals("Математика", dictionaries.subjectNames(listOf("1")))
        assertEquals("8 класс", dictionaries.parallelNames(listOf("8")))
        assertEquals("Школьный этап", dictionaries.stageName("SCHOOL"))
        assertEquals(listOf(2026, 2025, 2024), dictionaries.years)

        val detail = client.getOlympiadDetail(DemoMeshServer.DEMO_OLYMPIAD_PAST_ID)
        assertEquals("Демо-олимпиада по информатике", detail.contestName)
        assertEquals("PUBLISHED_FINAL_RESULTS", detail.effectiveStatus)
        assertEquals("MUNICIPAL", detail.commonInfo?.stageCode)

        val participant = client.getOlympiadParticipant(DemoMeshServer.DEMO_OLYMPIAD_PAST_ID)
        assertNotNull(participant)
        assertEquals("REGISTERED", participant?.passingState)
        assertNull(client.getOlympiadParticipant("demo-olympiad-3"))

        val result = client.getOlympiadResult(DemoMeshServer.DEMO_OLYMPIAD_PAST_ID)
        assertNotNull(result)
        assertTrue(result!!.hasResult)
        assertEquals(18.0, result.resultModel?.score?.mark ?: 0.0, 0.001)
        assertEquals("Победитель", result.resultModel?.reward?.label)
        val detailsUrl = result.resultModel?.detailsUrl
        assertNotNull(detailsUrl)
        val detailsLink = client.getOlympiadDetailsLink(detailsUrl!!)
        assertEquals(
            "https://school.mos.ru/api/launcher/v1/launch" +
                "?activity_url=https%3A%2F%2Fuchebnik.mos.ru%2Fcontests" +
                "%2Fchallenge%2F123456789%2Fattempt%2F987654321&role=student",
            detailsLink,
        )
        assertEquals(
            OlympiadAttemptIds(challengeId = 123456789L, attemptId = 987654321L),
            OlympiadAttemptLink.parse(detailsLink),
        )

        assertNotNull(client.getOlympiadRecommendation(DemoMeshServer.DEMO_OLYMPIAD_ID))
        // 204 без тела — это нормальное отсутствие рекомендации, не ошибка.
        assertNull(client.getOlympiadRecommendation("demo-olympiad-3"))
    }

    @Test
    fun `details link exposes native challenge and attempt ids`() {
        assertEquals(
            OlympiadAttemptIds(challengeId = 123456789L, attemptId = 987654321L),
            OlympiadAttemptLink.parse(
                "https://uchebnik.mos.ru/contests/challenge/123456789/attempt/987654321?role=student"
            ),
        )
        assertEquals(
            OlympiadAttemptIds(challengeId = 123456789L, attemptId = 987654321L),
            OlympiadAttemptLink.parse(
                "https://uchebnik.mos.ru/exam/challenge/123456789/attempt/987654321"
            ),
        )
        assertEquals(
            OlympiadAttemptIds(challengeId = 123456789L, attemptId = 987654321L),
            OlympiadAttemptLink.parse(
                "https://school.mos.ru/api/launcher/v1/launch" +
                    "?activity_url=https%3A%2F%2Fuchebnik.mos.ru%2Fcontests%2Fchallenge" +
                    "%2F123456789%2Fattempt%2F987654321&role=student"
            ),
        )
        assertEquals(
            OlympiadAttemptIds(challengeId = 123456789L, attemptId = 987654321L),
            OlympiadAttemptLink.parse(
                "https://uchebnik.mos.ru/launcher/api/v1/launch" +
                    "?activityId=https%253A%252F%252Fuchebnik.mos.ru%252Fcontests" +
                    "%252Fchallenge%252F123456789%252Fattempt%252F987654321"
            ),
        )
        assertEquals(
            "https://uchebnik.mos.ru/contests/challenge/123456789/attempt/987654321",
            OlympiadAttemptLink.parse(
                "https://uchebnik.mos.ru/contests/challenge/123456789/attempt/987654321"
            )?.pageUrl,
        )
        assertNull(OlympiadAttemptLink.parse("https://example.org/demo-result"))
    }

    @Test
    fun `contest attempt response is parsed by gson`() {
        val gson = GsonBuilder().setLenient().create()
        val parsed = gson.fromJson(
            """
            {
              "attempt_info": {
                "attempt_id": 987654321,
                "attempt_num": 1,
                "got_score": 5,
                "max_score": 10
              },
              "results": [
                {
                  "test_group_num": 0,
                  "challenge_tasks": [
                    {"id": 123, "task_order": 0, "score": 5, "max_score": 5}
                  ]
                }
              ]
            }
            """.trimIndent(),
            OlympiadAttemptResponse::class.java,
        )

        assertEquals(5, parsed.attemptInfo?.get("got_score")?.asInt)
        assertEquals(1, parsed.results?.size())
        assertEquals(
            123L,
            parsed.results?.first()?.asJsonObject
                ?.getAsJsonArray("challenge_tasks")
                ?.first()?.asJsonObject
                ?.get("id")?.asLong,
        )
    }
}
