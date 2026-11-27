package ru.mesh.foss.data.model

import com.google.gson.annotations.SerializedName

/**
 * Контракты read-only раздела «Портфолио» (`/portfolio/app/...`).
 *
 * Ответы веб-портфолио оборачивают полезные данные в `{ "data": …, "result": "OK" }`.
 * Списки и вложенные объекты намеренно nullable: Gson не вызывает конструктор
 * data-класса и может оставить `null` там, где в JSON поле отсутствует.
 */
data class PortfolioContextResponse(
    @SerializedName("data") val data: PortfolioContext? = null,
)

data class PortfolioContext(
    @SerializedName("meshId") val meshId: String? = null,
    @SerializedName("personMeshInfo") val personMeshInfo: PortfolioPersonMeshInfo? = null,
) {
    /** UUID ученика для `/persons/{id}/...`; в вебе это `meshId` из контекста. */
    val personId: String?
        get() = personMeshInfo?.meshId?.takeIf { it.isNotBlank() }
            ?: meshId?.takeIf { it.isNotBlank() }
}

data class PortfolioPersonMeshInfo(
    @SerializedName("classLevel") val classLevel: String? = null,
    @SerializedName("schoolName") val schoolName: String? = null,
    @SerializedName("lastname") val lastName: String? = null,
    @SerializedName("firstname") val firstName: String? = null,
    @SerializedName("patronymic") val patronymic: String? = null,
    @SerializedName("class") val className: String? = null,
    @SerializedName("meshId") val meshId: String? = null,
) {
    val displayName: String
        get() = listOf(lastName, firstName, patronymic)
            .mapNotNull { it?.trim()?.takeIf { part -> part.isNotEmpty() } }
            .joinToString(" ")

    val displayClass: String?
        get() = className?.takeIf { it.isNotBlank() }
            ?: classLevel?.takeIf { it.isNotBlank() }
}

data class PortfolioWidgetsResponse(
    @SerializedName("data") val data: List<PortfolioWidget>? = null,
)

data class PortfolioWidget(
    @SerializedName("blockName") val blockName: String? = null,
    @SerializedName("data") val data: PortfolioWidgetData? = null,
)

data class PortfolioWidgetData(
    @SerializedName("name") val name: String? = null,
    @SerializedName("reward") val reward: String? = null,
    @SerializedName("subjectName") val subjectName: String? = null,
    @SerializedName("sourceCode") val sourceCode: Int? = null,
    @SerializedName("creationDate") val creationDate: String? = null,
)

data class PortfolioEventsResponse(
    @SerializedName("data") val data: List<PortfolioEvent>? = null,
)

data class PortfolioEvent(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("result") val result: String? = null,
    @SerializedName("maxScore") val maxScore: Double? = null,
    @SerializedName("startDate") val startDate: String? = null,
    @SerializedName("endDate") val endDate: String? = null,
    @SerializedName("stageEvent") val stageEvent: String? = null,
    @SerializedName("category") val category: PortfolioDictionary? = null,
    @SerializedName("source") val source: PortfolioDictionary? = null,
    @SerializedName("format") val format: PortfolioDictionary? = null,
    @SerializedName("subjects") val subjects: List<PortfolioDictionary>? = null,
    @SerializedName("participationStatus") val participationStatus: PortfolioDictionary? = null,
    @SerializedName("levelEvent") val levelEvent: PortfolioDictionary? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("resultUrl") val resultUrl: String? = null,
    @SerializedName("contestLink") val contestLink: String? = null,
)

data class PortfolioRewardsResponse(
    @SerializedName("data") val data: List<PortfolioReward>? = null,
)

data class PortfolioReward(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("date") val date: String? = null,
    @SerializedName("category") val category: PortfolioDictionary? = null,
    @SerializedName("source") val source: PortfolioDictionary? = null,
    @SerializedName("statusReward") val statusReward: PortfolioDictionary? = null,
    @SerializedName("rewardType") val rewardType: PortfolioDictionary? = null,
    @SerializedName("levelReward") val levelReward: PortfolioDictionary? = null,
    @SerializedName("subjectCode") val subjectCode: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("entityId") val entityId: String? = null,
)

data class PortfolioFinalMarksResponse(
    @SerializedName("data") val data: List<PortfolioFinalYear>? = null,
)

data class PortfolioFinalYear(
    @SerializedName("yearTitle") val yearTitle: String? = null,
    @SerializedName("educationLevel") val educationLevel: String? = null,
    @SerializedName("averageAllSubjects") val averageAllSubjects: Double? = null,
    @SerializedName("subjects") val subjects: List<PortfolioFinalSubject>? = null,
)

data class PortfolioFinalSubject(
    @SerializedName("name") val name: String? = null,
    @SerializedName("yearValue") val yearValue: String? = null,
    @SerializedName("yearFivePointValue") val yearFivePointValue: Int? = null,
)

data class PortfolioAverageMarkResponse(
    @SerializedName("data") val data: PortfolioAverageMark? = null,
)

data class PortfolioAverageMark(
    @SerializedName("averageMarkFive") val averageMarkFive: Double? = null,
    @SerializedName("subjectsRank") val subjectsRank: List<PortfolioSubjectAverage>? = null,
)

data class PortfolioSubjectAverage(
    @SerializedName("subjectId") val subjectId: Long? = null,
    @SerializedName("subjectName") val subjectName: String? = null,
    @SerializedName("averageMarkFiveSubject") val averageMarkFiveSubject: Double? = null,
)

data class PortfolioDiagnosticsResponse(
    @SerializedName("data") val data: List<PortfolioDiagnosticYear>? = null,
)

data class PortfolioDiagnosticYear(
    @SerializedName("learningYear") val learningYear: String? = null,
    @SerializedName("diagnostics") val diagnostics: PortfolioDiagnostics? = null,
)

data class PortfolioDiagnostics(
    @SerializedName("diagnostic") val diagnostic: List<PortfolioDiagnostic>? = null,
    @SerializedName("totalDiagnostic") val totalDiagnostic: PortfolioDiagnosticTotal? = null,
    @SerializedName("bestResult") val bestResult: PortfolioDiagnosticBest? = null,
)

data class PortfolioDiagnosticTotal(
    @SerializedName("total") val total: Int? = null,
    @SerializedName("totalLevelType") val totalLevelType: PortfolioDiagnosticLevels? = null,
)

data class PortfolioDiagnosticLevels(
    @SerializedName("totalLevelBelowBase") val belowBase: Int? = null,
    @SerializedName("totalLevelBase") val base: Int? = null,
    @SerializedName("totalLevelHight") val high: Int? = null,
    @SerializedName("totalLevelOverHight") val overHigh: Int? = null,
)

data class PortfolioDiagnosticBest(
    @SerializedName("bestResultPercent") val bestResultPercent: Double? = null,
    @SerializedName("bestResultValue") val bestResultValue: Double? = null,
    @SerializedName("maxResultValue") val maxResultValue: Double? = null,
    @SerializedName("bestResultEventDate") val bestResultEventDate: String? = null,
    @SerializedName("bestResultLevelType") val bestResultLevelType: String? = null,
    @SerializedName("bestResultSubject") val bestResultSubject: String? = null,
    @SerializedName("markValue5") val markValue5: Int? = null,
)

data class PortfolioDiagnostic(
    @SerializedName("eventDate") val eventDate: String? = null,
    @SerializedName("subject") val subject: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("maxResult") val maxResult: Double? = null,
    @SerializedName("resultValue") val resultValue: Double? = null,
    @SerializedName("percentResult") val percentResult: Double? = null,
    @SerializedName("levelType") val levelType: String? = null,
    @SerializedName("markValue5") val markValue5: Int? = null,
    @SerializedName("note") val note: String? = null,
    @SerializedName("url") val url: String? = null,
)

data class PortfolioEmploymentsResponse(
    @SerializedName("data") val data: List<PortfolioEmployment>? = null,
)

data class PortfolioEmployment(
    @SerializedName("name") val name: String? = null,
    @SerializedName("startDate") val startDate: String? = null,
    @SerializedName("endDate") val endDate: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("category") val category: PortfolioDictionary? = null,
    @SerializedName("source") val source: PortfolioDictionary? = null,
    @SerializedName("achievementActivityFormat") val format: PortfolioDictionary? = null,
)

data class PortfolioCulturalResponse(
    @SerializedName("data") val data: List<PortfolioCulturalVisit>? = null,
)

data class PortfolioCulturalVisit(
    @SerializedName("visitTime") val visitTime: String? = null,
    @SerializedName("eventType") val eventType: String? = null,
    @SerializedName("culturalInstitutionType") val culturalInstitutionType: String? = null,
    @SerializedName("culturalInstitutionName") val culturalInstitutionName: String? = null,
    @SerializedName("culturalInstitutionShortName") val culturalInstitutionShortName: String? = null,
    @SerializedName("address") val address: String? = null,
)

data class PortfolioDictionary(
    @SerializedName("code") val code: Int? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("id") val id: Int? = null,
)
