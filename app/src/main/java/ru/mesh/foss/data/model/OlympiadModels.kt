package ru.mesh.foss.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Контракты публичного раздела «Олимпиады» (`/api/contests/exec/v1`).
 *
 * Ответы этого API в разных сезонах обрастают полями, поэтому модели
 * намеренно терпимы к отсутствующим данным: списки по умолчанию пустые,
 * а строковые id принимаются как строкой, так и объектом `{ "value": "…" }`.
 */
data class OlympiadOverviewRequest(
    @SerializedName("page") val page: OlympiadPageRequest = OlympiadPageRequest(),
    @SerializedName("searchString") val searchString: String = "",
    @SerializedName("sort") val sort: OlympiadSort = OlympiadSort(),
    @SerializedName("subjectIds") val subjectIds: List<String> = emptyList(),
    @SerializedName("stageCodes") val stageCodes: List<String> = emptyList(),
    @SerializedName("parallelIds") val parallelIds: List<String> = emptyList(),
    @SerializedName("years") val years: List<Int> = emptyList(),
    @SerializedName("includeCurrent") val includeCurrent: Boolean = true,
    @SerializedName("includeFuture") val includeFuture: Boolean = true,
    @SerializedName("includeCompleted") val includeCompleted: Boolean = true,
    @SerializedName("showParticipantContests") val showParticipantContests: Boolean = true,
)

data class OlympiadPageRequest(
    @SerializedName("number") val number: Int = 0,
    @SerializedName("size") val size: Int = 20,
)

data class OlympiadSort(
    @SerializedName("fieldName") val fieldName: String = "startAt",
    @SerializedName("order") val order: String = "DESC",
)

data class OlympiadOverviewResponse(
    @SerializedName("page") val page: OlympiadPage = OlympiadPage(),
    @SerializedName("hiddenTotal") val hiddenTotal: Int = 0,
) {
    val total: Int
        get() = hiddenTotal.takeIf { it > 0 }
            ?: page.totalElements?.toInt()?.takeIf { it > 0 }
            ?: page.content.size
}

data class OlympiadPage(
    @SerializedName("content") val content: List<OlympiadOverviewItem> = emptyList(),
    @SerializedName("totalElements") val totalElements: Long? = null,
    @SerializedName("totalPages") val totalPages: Int? = null,
    @SerializedName("number") val number: Int? = null,
    @SerializedName("size") val size: Int? = null,
)

data class OlympiadOverviewItem(
    @SerializedName("id") val id: JsonElement? = null,
    @SerializedName("contestId") val contestId: String? = null,
    @SerializedName("contestName") val contestName: String? = null,
    @SerializedName("startAt") val startAt: String? = null,
    @SerializedName("finishAt") val finishAt: String? = null,
    @SerializedName("statusCode") val statusCode: String? = null,
    @SerializedName("parallelIds") val parallelIds: List<String> = emptyList(),
    @SerializedName("subjectIds") val subjectIds: List<String> = emptyList(),
    @SerializedName("stageCode") val stageCode: String? = null,
    @SerializedName("teamContest") val teamContest: Boolean? = null,
    @SerializedName("recommendationExists") val recommendationExists: Boolean? = null,
) {
    /** В разных ответах id приходит строкой или объектом `{ value: … }`. */
    val resolvedContestId: String?
        get() = contestId?.takeIf { it.isNotBlank() } ?: id.flexibleString()
}

data class OlympiadDetail(
    @SerializedName("id") val id: JsonElement? = null,
    @SerializedName("contestName") val contestName: String? = null,
    @SerializedName("statusCode") val statusCode: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("commonInfo") val commonInfo: OlympiadCommonInfo? = null,
    @SerializedName("participationInfo") val participationInfo: OlympiadParticipationInfo? = null,
    @SerializedName("additionalInfo") val additionalInfo: OlympiadAdditionalInfo? = null,
    @SerializedName("prevContestStages") val prevContestStages: List<OlympiadPreviousStage> = emptyList(),
    @SerializedName("appealConfig") val appealConfig: JsonElement? = null,
    @SerializedName("recommendationExists") val recommendationExists: Boolean? = null,
    @SerializedName("teamContest") val teamContest: Boolean? = null,
) {
    val effectiveStatus: String?
        get() = statusCode?.takeIf { it.isNotBlank() } ?: status?.takeIf { it.isNotBlank() }
}

data class OlympiadCommonInfo(
    @SerializedName("stageCode") val stageCode: String? = null,
    @SerializedName("subjectIds") val subjectIds: List<String> = emptyList(),
    @SerializedName("levelCode") val levelCode: String? = null,
    @SerializedName("coverage") val coverage: OlympiadCoverage? = null,
    @SerializedName("groupId") val groupId: String? = null,
)

data class OlympiadCoverage(
    @SerializedName("districtIds") val districtIds: List<String> = emptyList(),
    @SerializedName("organizationIds") val organizationIds: List<String> = emptyList(),
)

data class OlympiadParticipationInfo(
    @SerializedName("parallelIds") val parallelIds: List<String> = emptyList(),
    @SerializedName("teamContest") val teamContest: Boolean? = null,
    @SerializedName("startAt") val startAt: String? = null,
    @SerializedName("finishAt") val finishAt: String? = null,
)

data class OlympiadAdditionalInfo(
    @SerializedName("contestDescription") val contestDescription: String? = null,
    @SerializedName("testContests") val testContests: List<String> = emptyList(),
)

data class OlympiadPreviousStage(
    @SerializedName("contestId") val contestId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("contestName") val contestName: String? = null,
    @SerializedName("subjects") val subjects: List<OlympiadDictionaryItem> = emptyList(),
    @SerializedName("parallels") val parallels: List<OlympiadDictionaryItem> = emptyList(),
    @SerializedName("stage") val stage: OlympiadDictionaryItem? = null,
    @SerializedName("startedAt") val startedAt: String? = null,
    @SerializedName("rewards") val rewards: List<OlympiadDictionaryItem> = emptyList(),
    @SerializedName("minRank") val minRank: Int? = null,
    @SerializedName("minMark") val minMark: Double? = null,
    @SerializedName("parallelIds") val parallelIds: List<String> = emptyList(),
    @SerializedName("subjectIds") val subjectIds: List<String> = emptyList(),
    @SerializedName("stageCode") val stageCode: String? = null,
) {
    val title: String?
        get() = name?.takeIf { it.isNotBlank() } ?: contestName?.takeIf { it.isNotBlank() }
}

data class OlympiadDictionaryItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
) {
    val label: String?
        get() = fullName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() }
}

data class OlympiadDictionaries(
    val subjects: List<OlympiadDictionaryItem> = emptyList(),
    val parallels: List<OlympiadDictionaryItem> = emptyList(),
    val stages: List<OlympiadDictionaryItem> = emptyList(),
    val levels: List<OlympiadDictionaryItem> = emptyList(),
) {
    fun subjectNames(ids: List<String>): String = names(ids, subjects)

    fun parallelNames(ids: List<String>): String = names(ids, parallels)

    fun stageName(id: String?): String? = nameOf(id, stages)

    fun levelName(id: String?): String? = nameOf(id, levels)

    private fun names(ids: List<String>, items: List<OlympiadDictionaryItem>): String =
        ids.mapNotNull { nameOf(it, items) }.distinct().joinToString(", ")

    private fun nameOf(id: String?, items: List<OlympiadDictionaryItem>): String? {
        val key = id?.trim().orEmpty()
        if (key.isEmpty()) return null
        return items.firstOrNull { it.id == key }?.label
    }
}

data class OlympiadParticipant(
    @SerializedName("passingState") val passingState: String? = null,
    @SerializedName("covered") val covered: Boolean? = null,
    @SerializedName("personId") val personId: String? = null,
    @SerializedName("personalInfo") val personalInfo: OlympiadPersonalInfo? = null,
    @SerializedName("resultsPublicationInfoCode") val resultsPublicationInfoCode: String? = null,
)

data class OlympiadPersonalInfo(
    @SerializedName("name") val name: String? = null,
    @SerializedName("surname") val surname: String? = null,
)

data class OlympiadResult(
    @SerializedName("hasResult") val hasResult: Boolean = false,
    @SerializedName("resultModel") val resultModel: OlympiadResultModel? = null,
)

data class OlympiadResultModel(
    @SerializedName("startedAt") val startedAt: String? = null,
    @SerializedName("finishedAt") val finishedAt: String? = null,
    @SerializedName("score") val score: OlympiadScore? = null,
    @SerializedName("reward") val reward: OlympiadDictionaryItem? = null,
    @SerializedName("participationStatus") val participationStatus: OlympiadParticipationStatus? = null,
    @SerializedName("portfolioInfo") val portfolioInfo: String? = null,
    @SerializedName("detailsUrl") val detailsUrl: String? = null,
)

data class OlympiadScore(
    @SerializedName("mark") val mark: Double? = null,
    @SerializedName("markScale") val markScale: Double? = null,
    @SerializedName("passedCount") val passedCount: Int? = null,
    @SerializedName("totalCount") val totalCount: Int? = null,
    @SerializedName("partialPassedCount") val partialPassedCount: Int? = null,
    @SerializedName("wrongCount") val wrongCount: Int? = null,
)

data class OlympiadParticipationStatus(
    @SerializedName("code") val code: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("justification") val justification: String? = null,
)

data class OlympiadRecommendation(
    @SerializedName("recommendation") val recommendation: String? = null,
)

/** Строковое значение гибкого id из JSON (строка, число или объект `{ value }`). */
internal fun JsonElement?.flexibleString(): String? {
    val element = this ?: return null
    if (element.isJsonNull) return null
    if (element.isJsonPrimitive) {
        return runCatching { element.asString }.getOrNull()?.takeIf { it.isNotBlank() }
    }
    if (element.isJsonObject) {
        return element.asJsonObject.get("value").flexibleString()
    }
    return null
}
