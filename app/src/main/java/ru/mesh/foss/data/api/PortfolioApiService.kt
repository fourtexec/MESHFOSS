package ru.mesh.foss.data.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Path
import retrofit2.http.Query
import ru.mesh.foss.data.model.PortfolioAverageMarkResponse
import ru.mesh.foss.data.model.PortfolioContextResponse
import ru.mesh.foss.data.model.PortfolioCulturalResponse
import ru.mesh.foss.data.model.PortfolioDiagnosticsResponse
import ru.mesh.foss.data.model.PortfolioEmploymentsResponse
import ru.mesh.foss.data.model.PortfolioEventsResponse
import ru.mesh.foss.data.model.PortfolioFinalMarksResponse
import ru.mesh.foss.data.model.PortfolioRewardsResponse
import ru.mesh.foss.data.model.PortfolioWidgetsResponse

/**
 * Read-only контракт веб-портфолио МЭШ.
 *
 * База Retrofit — `https://school.mos.ru/portfolio/app/`.
 * Веб-портфолио шлёт `x-mes-subsystem: studentportfolioweb` и Bearer-токен,
 * но не семейные `Profile-Id`/`Profile-Type`/`Auth-Token`.
 */
interface PortfolioApiService {

    @GET("portfolio/users/context/")
    fun context(@HeaderMap headers: Map<String, String>): Call<PortfolioContextResponse>

    @GET("persons/{personId}/widgets/list")
    fun widgets(
        @Path("personId") personId: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<PortfolioWidgetsResponse>

    @GET("persons/{personId}/events/list")
    fun events(
        @Path("personId") personId: String,
        @Query("size") size: Int,
        @HeaderMap headers: Map<String, String>,
    ): Call<PortfolioEventsResponse>

    @GET("persons/{personId}/rewards/list")
    fun rewards(
        @Path("personId") personId: String,
        @Query("size") size: Int,
        @HeaderMap headers: Map<String, String>,
    ): Call<PortfolioRewardsResponse>

    @GET("persons/{personId}/academic-performance/final-mark")
    fun finalMarks(
        @Path("personId") personId: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<PortfolioFinalMarksResponse>

    @GET("persons/{personId}/v2/academic-performance/average-mark")
    fun averageMark(
        @Path("personId") personId: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<PortfolioAverageMarkResponse>

    @GET("persons/{personId}/independent-diagnostic-grouped")
    fun diagnostics(
        @Path("personId") personId: String,
        @Query("count") count: Int,
        @HeaderMap headers: Map<String, String>,
    ): Call<PortfolioDiagnosticsResponse>

    @GET("persons/{personId}/employments/list")
    fun employments(
        @Path("personId") personId: String,
        @Query("size") size: Int,
        @HeaderMap headers: Map<String, String>,
    ): Call<PortfolioEmploymentsResponse>

    @GET("persons/{personId}/cultural/list/")
    fun cultural(
        @Path("personId") personId: String,
        @Query("size") size: Int,
        @HeaderMap headers: Map<String, String>,
    ): Call<PortfolioCulturalResponse>
}
