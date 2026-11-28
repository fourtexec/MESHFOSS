package ru.mesh.foss.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Path
import ru.mesh.foss.data.model.ProgrammingResultPage
import ru.mesh.foss.data.model.ProgrammingState
import ru.mesh.foss.data.model.ProgrammingTask
import ru.mesh.foss.data.model.VrlabsTokenRequest
import ru.mesh.foss.data.model.VrlabsTokenResponse

/**
 * Read-only контракт программистской олимпиады `vrlabs/informatics`.
 *
 * Никаких start/answer/complete-вызовов: только результат, состояние попытки
 * и описания задач.
 */
interface ProgrammingApiService {

    @POST("authentication/token")
    suspend fun token(
        @Body request: VrlabsTokenRequest,
        @HeaderMap headers: Map<String, String>,
    ): VrlabsTokenResponse

    @GET("olympiad/result-page/{competitionId}")
    suspend fun resultPage(
        @Path("competitionId") competitionId: Long,
        @HeaderMap headers: Map<String, String>,
    ): ProgrammingResultPage

    @GET("pass-test/{assignmentId}/state")
    suspend fun state(
        @Path("assignmentId") assignmentId: Long,
        @HeaderMap headers: Map<String, String>,
    ): ProgrammingState

    @GET("pass-test/{assignmentId}/task/{taskId}")
    suspend fun task(
        @Path("assignmentId") assignmentId: Long,
        @Path("taskId") taskId: Long,
        @HeaderMap headers: Map<String, String>,
    ): ProgrammingTask
}
