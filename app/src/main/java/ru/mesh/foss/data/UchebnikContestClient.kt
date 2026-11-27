package ru.mesh.foss.data

import com.google.gson.GsonBuilder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.mesh.foss.data.api.OlympiadAttemptApiService
import ru.mesh.foss.data.model.OlympiadAttemptResponse

/**
 * Нативное чтение результата олимпиадной попытки на `uchebnik.mos.ru`.
 *
 * Авторизацию и cookie-сессию даёт [CdzNativeClient]; сам запрос идёт через
 * Retrofit + Gson. Это только `GET`: попытка не создаётся и не завершается.
 */
class UchebnikContestClient(
    private val session: CdzNativeClient,
) {

    companion object {
        private const val BASE = "https://uchebnik.mos.ru/webtests/contests/rest/secure/"
    }

    private val gson = GsonBuilder().setLenient().create()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val api: OlympiadAttemptApiService = Retrofit.Builder()
        .baseUrl(BASE)
        .client(http)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(OlympiadAttemptApiService::class.java)

    suspend fun attempt(challengeId: Long, attemptId: Long): OlympiadAttemptResponse =
        withContext(Dispatchers.IO) {
            if (challengeId <= 0L || attemptId <= 0L) {
                throw IllegalArgumentException("bad olympiad attempt")
            }
            if (!session.refreshSession()) {
                throw IllegalStateException("uchebnik session is unavailable")
            }

            fun fetch() = api.answers(
                challengeId = challengeId.toString(),
                attemptId = attemptId.toString(),
                headers = session.webtestsHeaders(),
            ).execute()

            var response = fetch()
            if (response.code() == 401 && session.refreshSession()) {
                response = fetch()
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("olympiad attempt HTTP ${response.code()}")
            }
            response.body() ?: throw IllegalStateException("olympiad attempt is empty")
        }
}
