package ru.mesh.foss.data.analytics

import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

sealed interface AnalyticsResult {
    data object Success : AnalyticsResult
    data class Retry(val afterMs: Long, val status: Int) : AnalyticsResult
    data class Fatal(val status: Int) : AnalyticsResult
    data object TooLarge : AnalyticsResult
}

interface AnalyticsTransport {
    suspend fun post(body: String): AnalyticsResult
}

/**
 * Транспорт аналитики на Retrofit + Gson — как остальные сетевые клиенты
 * приложения. Тело JSON Lines передаётся готовым RequestBody, потому что
 * Gson-конвертер сериализовал бы строку как JSON-строку, а не как поток строк.
 *
 * Ключ приходит из BuildConfig (GitHub secret) и никогда не пишется в журнал.
 * Тело ответа не читаем и не логируем.
 */
class RetrofitAnalyticsTransport(
    baseUrl: String,
    private val apiKey: String,
    client: OkHttpClient = defaultClient(),
) : AnalyticsTransport {

    private val api: AnalyticsApiService = Retrofit.Builder()
        .baseUrl(baseUrl.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
        .build()
        .create(AnalyticsApiService::class.java)

    override suspend fun post(body: String): AnalyticsResult = withContext(Dispatchers.IO) {
        val requestBody = body.toRequestBody("application/stream+json".toMediaType())
        try {
            val response = api.insert(
                timeField = "timestamp",
                msgField = "event",
                streamFields = "app,version,device,level,event_type,uuid",
                apiKey = apiKey,
                body = requestBody,
            )
            val code = response.code()
            when (code) {
                in 200..299 -> AnalyticsResult.Success
                401, 403, 415 -> AnalyticsResult.Fatal(code)
                413 -> AnalyticsResult.TooLarge
                429 -> AnalyticsResult.Retry(retryAfter(response, code), code)
                in 500..599, 408, 425 -> AnalyticsResult.Retry(60_000L, code)
                else -> AnalyticsResult.Fatal(code)
            }.also {
                response.body()?.close()
                response.errorBody()?.close()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            AnalyticsResult.Retry(60_000L, 0)
        }
    }

    private fun retryAfter(response: Response<ResponseBody>, code: Int): Long {
        val value = response.headers()["Retry-After"]?.trim().orEmpty()
        if (value.isNotEmpty()) {
            value.toLongOrNull()?.let { return (it * 1_000L).coerceIn(1_000L, 15 * 60_000L) }
            val date = runCatching {
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }.parse(value)?.time
            }.getOrNull()
            if (date != null) return (date - System.currentTimeMillis()).coerceIn(1_000L, 15 * 60_000L)
        }
        return when (code) {
            429 -> 60_000L
            else -> 60_000L
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
