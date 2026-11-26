package ru.mesh.foss.data

import org.json.JSONObject

/**
 * Приведение cookie-файлов к плоскому cookie jar'у `name -> value`, который
 * используют [CookieRefresh], QR-вход и WebView.
 *
 * Поддерживаются два вида JSON:
 *  - массив браузерного экспорта (`[{"name":"aupd_token","value":"...","domain":...}]`)
 *    — например `mos_cookies.json`;
 *  - уже плоский объект `{"aupd_token":"..."}` (как хранит SessionStore).
 */
object CookieJson {

    fun parse(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        runCatching {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                val array = org.json.JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                    result[name] = item.optString("value")
                }
            } else {
                val objectJson = JSONObject(trimmed)
                objectJson.keys().forEach { key -> result[key] = objectJson.optString(key) }
            }
        }
        return result
    }

    /** Стабильный ключ аккаунта: по токену/логину, иначе хеш всего набора кук. */
    fun accountKey(raw: String?): String {
        val jar = parse(raw)
        listOf("user_login", "login", "obr_id", "aupd_token").forEach { key ->
            jar[key]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return jar.toSortedMap().entries.joinToString("|") { "${it.key}=${it.value}" }
            .hashCode().toUInt().toString(16)
    }

    fun jarJson(raw: String?): String =
        JSONObject(parse(raw) as Map<*, *>).toString()
}
