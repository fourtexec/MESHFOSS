package ru.mesh.foss.data.analytics

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

/** Состояние отправки: версия, дневная активность, rate-limit и backoff. */
data class AnalyticsState(
    @SerializedName("last_version") val lastVersion: String? = null,
    @SerializedName("last_active_at") val lastActiveAt: Long = 0L,
    @SerializedName("last_request_at") val lastRequestAt: Long = 0L,
    @SerializedName("blocked_until") val blockedUntil: Long = 0L,
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()

        fun fromJson(raw: String): AnalyticsState = runCatching {
            val parsed = gson.fromJson(raw, AnalyticsState::class.java)
            AnalyticsState(
                lastVersion = parsed.lastVersion?.takeIf { it.isNotBlank() },
                lastActiveAt = parsed.lastActiveAt.coerceAtLeast(0L),
                lastRequestAt = parsed.lastRequestAt.coerceAtLeast(0L),
                blockedUntil = parsed.blockedUntil.coerceAtLeast(0L),
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
