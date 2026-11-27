package ru.mesh.foss.data.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * Ответ `webtests/contests/rest/secure/challenge/task/answers`.
 *
 * Структура заданий динамическая (тип ответа, rich-text, таблицы, файлы),
 * поэтому вложенные части остаются Gson-деревьями. Верхний контракт всё
 * равно типизирован: это не `org.json`-разбор сетевого ответа.
 */
data class OlympiadAttemptResponse(
    @SerializedName("attempt_info") val attemptInfo: JsonObject? = null,
    @SerializedName("results") val results: JsonArray? = null,
)
