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
import ru.mesh.foss.data.model.PortfolioDiagnosticYear
import ru.mesh.foss.data.model.PortfolioEventsResponse
import ru.mesh.foss.feature.portfolio.PortfolioText
import java.util.Locale

/** JVM-тесты read-only контракта «Портфолио». */
class PortfolioTest {

    private lateinit var server: DemoMeshServer
    private var port = 0

    @Before
    fun start() {
        server = DemoMeshServer(seed = 17L, classSize = 12, tokenLifetime = 100)
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
    fun `portfolio context and study data are parsed`() {
        val client = client()
        val context = client.getPortfolioContext()

        assertEquals(DemoMeshServer.UUID_ME, context.personId)
        assertEquals("Демидов Пётр Алексеевич", context.personMeshInfo?.displayName)
        assertEquals("7 «Б»", context.personMeshInfo?.displayClass)
        assertEquals("Школа № 1234", context.personMeshInfo?.schoolName)

        val widgets = client.getPortfolioWidgets(DemoMeshServer.UUID_ME)
        assertEquals(2, widgets.size)
        assertEquals("Победитель", widgets.first().data?.reward)

        val events = client.getPortfolioEvents(DemoMeshServer.UUID_ME)
        assertEquals(2, events.size)
        assertEquals("48.0", events.first().result)
        assertEquals(50.0, events.first().maxScore ?: 0.0, 0.001)

        val rewards = client.getPortfolioRewards(DemoMeshServer.UUID_ME)
        assertEquals(1, rewards.size)
        assertEquals("7001", rewards.first().entityId)

        val finalMarks = client.getPortfolioFinalMarks(DemoMeshServer.UUID_ME)
        assertEquals(1, finalMarks.size)
        assertEquals("2025/26", finalMarks.first().yearTitle)
        assertEquals(2, finalMarks.first().subjects?.size)

        val average = client.getPortfolioAverageMark(DemoMeshServer.UUID_ME)
        assertNotNull(average)
        assertEquals(4.78, average?.averageMarkFive ?: 0.0, 0.001)
        assertEquals(2, average?.subjectsRank?.size)

        val diagnostics = client.getPortfolioDiagnostics(DemoMeshServer.UUID_ME)
        assertEquals(1, diagnostics.size)
        assertEquals(1, diagnostics.first().diagnostics?.totalDiagnostic?.total)
        assertEquals("Математика", diagnostics.first().diagnostics?.diagnostic?.first()?.subject)

        val employments = client.getPortfolioEmployments(DemoMeshServer.UUID_ME)
        assertEquals(1, employments.size)
        assertEquals("Кружок робототехники", employments.first().name)

        val cultural = client.getPortfolioCulturalVisits(DemoMeshServer.UUID_ME)
        assertEquals(1, cultural.size)
        assertEquals("Городской музей науки", cultural.first().culturalInstitutionName)
    }

    @Test
    fun `portfolio requests use studentportfolioweb without family headers`() {
        client().getPortfolioContext()

        assertEquals("studentportfolioweb", server.lastPortfolioSubsystem)
        assertEquals("", server.lastPortfolioProfileId)
        assertEquals("", server.lastPortfolioAuthToken)
        assertTrue(server.lastPortfolioAuthorization.startsWith("Bearer "))
    }

    @Test
    fun `explicit null collections do not crash portfolio parsing`() {
        val gson = GsonBuilder().setLenient().create()
        val response = gson.fromJson(
            """{"data":null}""",
            PortfolioEventsResponse::class.java,
        )
        assertNull(response.data)

        val year = gson.fromJson(
            """{"learningYear":"2025-2026","diagnostics":null}""",
            PortfolioDiagnosticYear::class.java,
        )
        assertEquals("2025-2026", year.learningYear)
        assertNull(year.diagnostics)
    }

    @Test
    fun `portfolio text formats marks scores and initials`() {
        assertEquals("4.78", PortfolioText.number(4.78))
        assertEquals("5", PortfolioText.number(5.0))
        assertEquals("88%", PortfolioText.percent(88.0))
        assertEquals("48 / 50", PortfolioText.score("48.0", 50.0))
        assertEquals("Призёр", PortfolioText.score("Призёр", null))
        assertEquals("ДП", PortfolioText.initials("Демидов Пётр Алексеевич"))
        assertEquals("10.12.2026", PortfolioText.date("2026-12-10", Locale("ru")))
    }
}
