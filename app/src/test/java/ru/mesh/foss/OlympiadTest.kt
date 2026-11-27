package ru.mesh.foss

import com.google.gson.GsonBuilder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.data.model.OlympiadOverviewResponse
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
        assertEquals("demo-1", parsed.page.content[0].resolvedContestId)
        assertEquals("demo-2", parsed.page.content[1].resolvedContestId)
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
        assertEquals(2, mine.page.content.size)
        assertEquals(2, mine.total)
        assertEquals(DemoMeshServer.DEMO_OLYMPIAD_ID, mine.page.content.first().resolvedContestId)

        val all = client.getOlympiadOverview(showParticipantContests = false)
        assertEquals(3, all.page.content.size)
        assertEquals(3, all.total)

        val search = client.getOlympiadOverview(
            showParticipantContests = false,
            searchString = "физике",
        )
        assertEquals(1, search.page.content.size)
        assertEquals("demo-olympiad-3", search.page.content.first().resolvedContestId)
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

        val detail = client.getOlympiadDetail(DemoMeshServer.DEMO_OLYMPIAD_PAST_ID)
        assertEquals("Демо-олимпиада по информатике", detail.contestName)
        assertEquals("PUBLISHED_FINAL_RESULTS", detail.effectiveStatus)
        assertEquals("MUNICIPAL", detail.commonInfo?.stageCode)

        val result = client.getOlympiadResult(DemoMeshServer.DEMO_OLYMPIAD_PAST_ID)
        assertNotNull(result)
        assertTrue(result!!.hasResult)
        assertEquals(18.0, result.resultModel?.score?.mark ?: 0.0, 0.001)
        assertEquals("Победитель", result.resultModel?.reward?.label)

        assertNotNull(client.getOlympiadRecommendation(DemoMeshServer.DEMO_OLYMPIAD_ID))
        assertNull(client.getOlympiadRecommendation("demo-olympiad-3"))
    }
}
