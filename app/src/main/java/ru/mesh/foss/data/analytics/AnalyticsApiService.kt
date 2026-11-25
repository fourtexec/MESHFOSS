package ru.mesh.foss.data.analytics

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Контракт analytics endpoint'а.
 *
 * Тело — JSON Lines, поэтому передаём готовый [RequestBody] с
 * `application/stream+json`, а не сериализуем объект через Gson. Остальные
 * запросы приложения тоже идут через Retrofit; здесь сохраняем тот же стиль.
 */
interface AnalyticsApiService {

    @POST("insert/jsonline")
    suspend fun insert(
        @Query("_time_field") timeField: String,
        @Query("_msg_field") msgField: String,
        @Query("_stream_fields") streamFields: String,
        @Header("X-Api-Key") apiKey: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>
}
