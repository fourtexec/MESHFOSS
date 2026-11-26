package ru.mesh.foss.data.api

import com.google.gson.JsonElement
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import ru.mesh.foss.data.model.AttendanceResponse
import ru.mesh.foss.data.model.VisitDurationsResponse

/**
 * Явный контракт REST API МЭШ.
 *
 * Все методы получают готовую карту заголовков: так проще менять роль,
 * профиль и cookie, не пряча авторизацию в глобальном interceptor.
 * Типизированные ответы используются для новых экранов, а исторические
 * разделы временно работают с [JsonElement] и нормализуются в MeshCore.
 */
interface MeshApiService {

    @GET("api/family/web/v1/profile")
    fun profile(@HeaderMap headers: Map<String, String>): Call<JsonElement>

    @GET("api/gamification/v1/profiles")
    fun profileById(
        @Query("personId") personId: String?,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    @GET("api/ej/rating/v1/rank/subjects")
    fun subjects(
        @Query("date") date: String,
        @Query("personId") personId: String?,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    @GET("api/ej/rating/v1/rank/class")
    fun rank(
        @Query("personId") personId: String?,
        @Query("date") date: String,
        @Query("subjectId") subjectId: String?,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    @GET("api/family/web/v1/marks")
    fun marks(
        @Query("student_id") studentId: String?,
        @Query("from") from: String,
        @Query("to") to: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    @GET("api/family/web/v1/marks/{markId}")
    fun mark(
        @Path("markId") markId: String,
        @Query("person_id") personId: String?,
        @Query("markId") markIdQuery: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    @GET("api/family/web/v1/subject_marks")
    fun subjectMarks(
        @Query("student_id") studentId: String?,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    @GET("api/ej/core/family/v1/academic_years")
    fun academicYears(@HeaderMap headers: Map<String, String>): Call<JsonElement>

    @GET("api/family/web/v1/final_marks/v2")
    fun finalMarks(
        @Query("person_id") personId: String?,
        @Query("student_id") studentId: String?,
        @Query("academic_year_id") academicYearId: String,
        @Query("is_year_marks_only") yearOnly: String?,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    @GET("api/family/web/v1/homeworks")
    fun homeworks(
        @Query("student_id") studentId: String?,
        @Query("from") from: String,
        @Query("to") to: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    /** Расширенные карточки цифровых материалов ДЗ. */
    @POST("api/family/materials/v1/additional_materials")
    fun additionalMaterials(
        @Body body: RequestBody,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    /** Статус выполнения материала ЦДЗ в LRS. */
    @GET("api/lrs/v2/results")
    fun materialResult(
        @Query("context") context: String,
        @Query("size") size: Int,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    @POST("api/family/web/v1/homeworks/{entryStudentId}/done")
    fun markHomeworkDone(
        @Path("entryStudentId") entryStudentId: String,
        @Query("type") type: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<ResponseBody>

    @DELETE("api/family/web/v1/homeworks/{entryStudentId}/done")
    fun clearHomeworkDone(
        @Path("entryStudentId") entryStudentId: String,
        @Query("type") type: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<ResponseBody>

    @GET("api/family/web/v1/schedule")
    fun schedule(
        @Query("student_id") studentId: String?,
        @Query("date") date: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    /**
     * Endpoint продления сессии может ответить JSON, HTML-страницей или
     * редиректом, поэтому тело читается как сырой текст, а не через Gson.
     */
    @GET("v2/token/refresh")
    fun refreshToken(
        @Query("roleId") roleId: String,
        @Query("subsystem") subsystem: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<ResponseBody>

    @GET("api/food/meals/v3/clients/allowed-food-state")
    fun foodState(
        @Query("clientId") clientId: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    @GET("api/food/meals/v3/clients/balance")
    fun foodBalance(
        @Query("clientIds") clientIds: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    @GET("api/food/meals/v3/menu/complexes")
    fun foodMenu(
        @Query("clientId") clientId: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("withPreorder") withPreorder: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    @GET("api/food/meals/v3/orders")
    fun foodOrders(
        @Query("clientId") clientId: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("limit") limit: Int,
        @HeaderMap headers: Map<String, String>,
    ): Call<JsonElement>

    @GET("api/family/web/v1/attendance")
    fun attendance(
        @Query("student_id") studentId: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<AttendanceResponse>

    @GET("api/pass/entrances/v1/visit_durations")
    fun visitDurations(
        @Query("personId") personId: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<VisitDurationsResponse>
}
