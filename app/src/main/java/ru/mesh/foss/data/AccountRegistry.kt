package ru.mesh.foss.data

import ru.mesh.foss.core.Words

import android.content.Context

/**
 * Реестр аккаунтов. Каждый аккаунт хранит свой токен и свой кэш в отдельном
 * `SessionStore` (по префиксу файлов); общие настройки приложения остаются
 * в `mesh_settings`.
 */
class AccountRegistry(context: Context) {

    companion object {
        private const val PREFS = "mesh_settings"
        private const val KEY_ACCOUNTS = "accounts_v1"
        private const val KEY_ACTIVE = "active_account_v1"

        /** Легаси-аккаунт до введения мультиаккаунтности: файлы без префикса. */
        const val LEGACY = ""

        fun scopeFor(key: String): String =
            if (key == LEGACY) LEGACY else "a" + key.take(16).map { c ->
                if (c.isLetterOrDigit()) c else '_'
            }.joinToString("")
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Account(val id: String, val label: String)

    fun accounts(): List<Account> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        val result = mutableListOf<Account>()
        runCatching {
            val array = org.json.JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                result += Account(id, item.optString("label").ifBlank { id })
            }
        }
        return result
    }

    fun activeId(): String = prefs.getString(KEY_ACTIVE, null).orEmpty()

    fun activeScope(): String = scopeFor(activeId())

    fun setActive(id: String) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun add(id: String, label: String) {
        if (id.isBlank()) return
        val next = accounts().filter { it.id != id }.toMutableList()
        next += Account(id, label)
        prefs.edit()
            .putString(KEY_ACCOUNTS, org.json.JSONArray().apply {
                next.forEach { put(org.json.JSONObject().put("id", it.id).put("label", it.label)) }
            }.toString())
            .putString(KEY_ACTIVE, id)
            .apply()
    }

    fun remove(id: String) {
        val next = accounts().filter { it.id != id }
        val active = if (activeId() == id) next.firstOrNull()?.id.orEmpty() else activeId()
        prefs.edit()
            .putString(KEY_ACCOUNTS, org.json.JSONArray().apply {
                next.forEach { put(org.json.JSONObject().put("id", it.id).put("label", it.label)) }
            }.toString())
            .putString(KEY_ACTIVE, active)
            .apply()
    }

    /** Первый вход после обновления: легаси-данные остаются активными. */
    fun ensureLegacy() {
        if (accounts().isEmpty()) {
            val legacy = org.json.JSONArray().put(
                org.json.JSONObject().put("id", LEGACY).put("label", Words.Account.text)
            )
            prefs.edit()
                .putString(KEY_ACCOUNTS, legacy.toString())
                .putString(KEY_ACTIVE, LEGACY)
                .apply()
        }
    }
}
