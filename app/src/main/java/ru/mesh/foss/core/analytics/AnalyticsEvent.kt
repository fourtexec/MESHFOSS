package ru.mesh.foss.core.analytics

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

/**
 * Одна строка JSON Lines для analytics endpoint.
 *
 * Обязательные поля: event, timestamp, app, version, device, level, event_type,
 * uuid. Дополнительные `tag`, `error_type`, `stack` и `os` помогают разложить
 * ошибки по экранам/модулям в Grafana, не превращая их в stream-метки.
 */
data class AnalyticsEvent(
    @SerializedName("event") val event: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("app") val app: String,
    @SerializedName("version") val version: String,
    @SerializedName("device") val device: String,
    @SerializedName("level") val level: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("uuid") val uuid: String,
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("error_type") val errorType: String? = null,
    @SerializedName("stack") val stack: String? = null,
    @SerializedName("os") val os: String? = null,
) {

    fun toJsonLine(): String = gson.toJson(this)

    companion object {
        const val INSTALL = "app_install"
        const val UPDATE = "app_update"
        const val ACTIVE = "app_active"
        const val ERROR = "error"

        private val gson = Gson()

        fun fromJsonLine(line: String): AnalyticsEvent? = runCatching {
            val json = JsonParser.parseString(line).asJsonObject
            fun text(name: String): String? =
                json.get(name)
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString
                    ?.takeIf { it.isNotBlank() }

            AnalyticsEvent(
                event = text("event") ?: return null,
                timestamp = text("timestamp") ?: return null,
                app = text("app") ?: return null,
                version = text("version") ?: return null,
                device = text("device") ?: return null,
                level = text("level") ?: return null,
                eventType = text("event_type") ?: return null,
                uuid = text("uuid") ?: return null,
                tag = text("tag"),
                errorType = text("error_type"),
                stack = text("stack"),
                os = text("os"),
            )
        }.getOrNull()
    }
}
