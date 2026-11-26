package ru.mesh.foss.data.analytics

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.io.File

private const val ANALYTICS_STATE_SCHEMA = 2

/** Состояние отправки: версия, heartbeat, rate-limit и backoff. */
data class AnalyticsState(
    @SerializedName("last_version") val lastVersion: String? = null,
    @SerializedName("last_active_at") val lastActiveAt: Long = 0L,
    @SerializedName("last_request_at") val lastRequestAt: Long = 0L,
    @SerializedName("blocked_until") val blockedUntil: Long = 0L,
    @SerializedName("schema_version") val schemaVersion: Int = ANALYTICS_STATE_SCHEMA,
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        fun fromJson(raw: String): AnalyticsState = runCatching {
            val json = JsonParser.parseString(raw).asJsonObject
            val parsed = gson.fromJson(json, AnalyticsState::class.java)
            // Отсутствие поля — это состояние 2.5.x, даже если Kotlin
            // подставил default при создании объекта через Gson.
            val schema = if (json.has("schema_version")) {
                parsed.schemaVersion.coerceAtLeast(0)
            } else {
                0
            }
            AnalyticsState(
                lastVersion = parsed.lastVersion?.takeIf { it.isNotBlank() },
                lastActiveAt = parsed.lastActiveAt.coerceAtLeast(0L),
                lastRequestAt = parsed.lastRequestAt.coerceAtLeast(0L),
                // В 2.5.x после 401/403/415 лежал суточный блок. В 2.6 это
                // короткий backoff, поэтому старый блок сбрасываем.
                blockedUntil = if (schema >= ANALYTICS_STATE_SCHEMA) {
                    parsed.blockedUntil.coerceAtLeast(0L)
                } else {
                    0L
                },
                schemaVersion = ANALYTICS_STATE_SCHEMA,
            )
        }.getOrDefault(AnalyticsState())
    }
}

class AnalyticsStateStore(private val file: File) {

    @Synchronized
    fun load(): AnalyticsState {
        val raw = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()
            ?: return AnalyticsState()
        return AnalyticsState.fromJson(raw)
    }

    @Synchronized
    fun save(state: AnalyticsState) {
        runCatching {
            file.parentFile?.mkdirs()
            val text = state.toJson()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }
}
