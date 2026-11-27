package ru.mesh.foss.data.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url
import ru.mesh.foss.data.model.OlympiadDetail
import ru.mesh.foss.data.model.OlympiadDetailsLink
import ru.mesh.foss.data.model.OlympiadDictionaryItem
import ru.mesh.foss.data.model.OlympiadOverviewRequest
import ru.mesh.foss.data.model.OlympiadOverviewResponse
import ru.mesh.foss.data.model.OlympiadParticipant
import ru.mesh.foss.data.model.OlympiadRecommendation
import ru.mesh.foss.data.model.OlympiadResult

/**
 * Публичный контракт раздела «Олимпиады».
 *
 * База Retrofit — `https://school.mos.ru/api/contests/exec/v1/`.
 * Раздел использует отдельный заголовок `x-mes-subsystem: constuweb`,
 * поэтому контракт вынесен из семейного [MeshApiService].
 */
interface OlympiadApiService {

    @POST("contests/overview")
    fun overview(
        @Body body: OlympiadOverviewRequest,
        @HeaderMap headers: Map<String, String>,
    ): Call<OlympiadOverviewResponse>

    @GET("subjects")
    fun subjects(@HeaderMap headers: Map<String, String>): Call<List<OlympiadDictionaryItem>>

    @GET("parallels")
    fun parallels(@HeaderMap headers: Map<String, String>): Call<List<OlympiadDictionaryItem>>

    @GET("contests/stages")
    fun stages(@HeaderMap headers: Map<String, String>): Call<List<OlympiadDictionaryItem>>

    @GET("contests/levels")
    fun levels(@HeaderMap headers: Map<String, String>): Call<List<OlympiadDictionaryItem>>

    @GET("contests/{contestId}")
    fun detail(
        @Path("contestId") contestId: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<OlympiadDetail>

    @GET("contests/{contestId}/participant")
    fun participant(
        @Path("contestId") contestId: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<OlympiadParticipant>

    @GET("contests/{contestId}/recommendation")
    fun recommendation(
        @Path("contestId") contestId: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<OlympiadRecommendation>

    @GET("contests/{contestId}/result")
    fun result(
        @Path("contestId") contestId: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<OlympiadResult>

    /**
     * `detailsUrl` из результата — это API-ссылка, а не страница для браузера.
     * Она возвращает `{ "link": "https://…" }`; из ссылки приложение берёт
     * ID попытки и показывает результат нативно.
     */
    @GET
    fun detailsLink(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<OlympiadDetailsLink>
}
