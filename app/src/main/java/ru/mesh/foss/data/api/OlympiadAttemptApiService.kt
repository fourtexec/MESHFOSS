package ru.mesh.foss.data.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Query
import ru.mesh.foss.data.model.OlympiadAttemptResponse

/**
 * Read-only результат попытки олимпиады на `uchebnik.mos.ru`.
 *
 * Это тот же `webtests`-контур, что и ЦДЗ, но раздел `contests`.
 * Никаких `start-attempt`, ответов или `complete_attempt` здесь нет.
 */
interface OlympiadAttemptApiService {

    @GET("challenge/task/answers")
    fun answers(
        @Query("challenge_id") challengeId: String,
        @Query("challenge_attempt_id") attemptId: String,
        @HeaderMap headers: Map<String, String>,
    ): Call<OlympiadAttemptResponse>
}
