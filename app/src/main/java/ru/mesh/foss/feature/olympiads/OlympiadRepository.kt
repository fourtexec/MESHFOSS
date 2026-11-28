package ru.mesh.foss.feature.olympiads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.data.model.OlympiadDetail
import ru.mesh.foss.data.model.OlympiadDictionaries
import ru.mesh.foss.data.model.OlympiadOverviewResponse
import ru.mesh.foss.data.model.OlympiadParticipant
import ru.mesh.foss.data.model.OlympiadRecommendation
import ru.mesh.foss.data.model.OlympiadResult

/** Слой данных экрана «Олимпиады»: только вызовы Retrofit-клиента. */
interface OlympiadRepository {
    suspend fun overview(
        showParticipantContests: Boolean,
        searchString: String,
        filters: OlympiadFilters,
        page: Int,
        size: Int,
    ): OlympiadOverviewResponse

    suspend fun dictionaries(): OlympiadDictionaries
    suspend fun detail(contestId: String): OlympiadDetail
    suspend fun participant(contestId: String): OlympiadParticipant?
    suspend fun result(contestId: String): OlympiadResult?
    suspend fun recommendation(contestId: String): OlympiadRecommendation?
    suspend fun detailsLink(detailsUrl: String): String?
}

class MeshOlympiadRepository(private val client: MeshClient) : OlympiadRepository {

    override suspend fun overview(
        showParticipantContests: Boolean,
        searchString: String,
        filters: OlympiadFilters,
        page: Int,
        size: Int,
    ): OlympiadOverviewResponse = withContext(Dispatchers.IO) {
        client.getOlympiadOverview(
            showParticipantContests = showParticipantContests,
            searchString = searchString,
            page = page,
            size = size,
            subjectIds = filters.subjectIds.toList(),
            stageCodes = filters.stageCodes.toList(),
            parallelIds = filters.parallelIds.toList(),
            years = filters.years.toList(),
            includeCurrent = filters.includeCurrent,
            includeFuture = filters.includeFuture,
            includeCompleted = filters.includeCompleted,
        )
    }

    override suspend fun dictionaries(): OlympiadDictionaries = withContext(Dispatchers.IO) {
        client.getOlympiadDictionaries()
    }

    override suspend fun detail(contestId: String): OlympiadDetail = withContext(Dispatchers.IO) {
        client.getOlympiadDetail(contestId)
    }

    override suspend fun participant(contestId: String): OlympiadParticipant? =
        withContext(Dispatchers.IO) {
            client.getOlympiadParticipant(contestId)
        }

    override suspend fun result(contestId: String): OlympiadResult? = withContext(Dispatchers.IO) {
        client.getOlympiadResult(contestId)
    }

    override suspend fun recommendation(contestId: String): OlympiadRecommendation? =
        withContext(Dispatchers.IO) {
            client.getOlympiadRecommendation(contestId)
        }

    override suspend fun detailsLink(detailsUrl: String): String? = withContext(Dispatchers.IO) {
        client.getOlympiadDetailsLink(detailsUrl)
    }
}
