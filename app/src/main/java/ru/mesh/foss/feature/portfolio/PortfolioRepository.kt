package ru.mesh.foss.feature.portfolio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.data.model.PortfolioAverageMark
import ru.mesh.foss.data.model.PortfolioContext
import ru.mesh.foss.data.model.PortfolioCulturalVisit
import ru.mesh.foss.data.model.PortfolioDiagnosticYear
import ru.mesh.foss.data.model.PortfolioEmployment
import ru.mesh.foss.data.model.PortfolioEvent
import ru.mesh.foss.data.model.PortfolioFinalYear
import ru.mesh.foss.data.model.PortfolioReward
import ru.mesh.foss.data.model.PortfolioWidget

/** Слой данных экрана «Портфолио»: только read-only вызовы Retrofit-клиента. */
interface PortfolioRepository {
    suspend fun context(): PortfolioContext
    suspend fun widgets(personId: String): List<PortfolioWidget>
    suspend fun events(personId: String): List<PortfolioEvent>
    suspend fun rewards(personId: String): List<PortfolioReward>
    suspend fun finalMarks(personId: String): List<PortfolioFinalYear>
    suspend fun averageMark(personId: String): PortfolioAverageMark?
    suspend fun diagnostics(personId: String): List<PortfolioDiagnosticYear>
    suspend fun employments(personId: String): List<PortfolioEmployment>
    suspend fun culturalVisits(personId: String): List<PortfolioCulturalVisit>
}

class MeshPortfolioRepository(private val client: MeshClient) : PortfolioRepository {

    override suspend fun context(): PortfolioContext = withContext(Dispatchers.IO) {
        client.getPortfolioContext()
    }

    override suspend fun widgets(personId: String): List<PortfolioWidget> =
        withContext(Dispatchers.IO) { client.getPortfolioWidgets(personId) }

    override suspend fun events(personId: String): List<PortfolioEvent> =
        withContext(Dispatchers.IO) { client.getPortfolioEvents(personId) }

    override suspend fun rewards(personId: String): List<PortfolioReward> =
        withContext(Dispatchers.IO) { client.getPortfolioRewards(personId) }

    override suspend fun finalMarks(personId: String): List<PortfolioFinalYear> =
        withContext(Dispatchers.IO) { client.getPortfolioFinalMarks(personId) }

    override suspend fun averageMark(personId: String): PortfolioAverageMark? =
        withContext(Dispatchers.IO) { client.getPortfolioAverageMark(personId) }

    override suspend fun diagnostics(personId: String): List<PortfolioDiagnosticYear> =
        withContext(Dispatchers.IO) { client.getPortfolioDiagnostics(personId) }

    override suspend fun employments(personId: String): List<PortfolioEmployment> =
        withContext(Dispatchers.IO) { client.getPortfolioEmployments(personId) }

    override suspend fun culturalVisits(personId: String): List<PortfolioCulturalVisit> =
        withContext(Dispatchers.IO) { client.getPortfolioCulturalVisits(personId) }
}
