package ru.mesh.foss.core

/**
 * Единая редакция чувствительных данных для журнала приложения.
 *
 * Раньше это жило только в логе «Москвёнка»; теперь применяется ко всему, что
 * пишет [MeshLog]: заголовки HTTP, ответы, ошибки, состояния экранов. Токены
 * (`Authorization`, `Auth-Token`), cookie, sessionid, OAuth `code`/`state`,
 * `Profile-Id` и ПИИ (email/phone/ФИО/QR-пейлоад) на выход не попадают.
 *
 * Regex компилируются поштучно через [rx]: если ICU на устройстве не примет
 * шаблон, он просто отключится, а не уронит приложение при старте (JVM-тесты
 * такое не ловят). Сам [scrub] дополнительно обёрнут в runCatching, поэтому
 * журнал не может сломать приложение даже на «странной» строке.
 */
object Sensitive {

    /** Скомпилировать шаблон, но не падать, если он несовместим с ICU. */
    private fun rx(pattern: String, ignoreCase: Boolean = true): Regex? =
        runCatching {
            val options: Set<RegexOption> =
                if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            Regex(pattern, options)
        }.getOrNull()

    /** Маска секрета: первые 4 символа + многоточие. */
    fun mask(value: String): String =
        if (value.length <= 4) "***" else value.take(4) + "..."

    // Заголовки. Порядок применения важен: сначала точные заголовки, потом текст.
    private val BEARER = rx("(authorization\\s*:\\s*bearer\\s+)([^\\s,;]+)")
    private val AUTH_TOKEN = rx("((?:auth-token|x-auth-token|auth_token)\\s*[:=]\\s*)([^\\s,;]+)")
    private val PROFILE_HEADER = rx("((?:profile-id|x-mes-profile-id)\\s*[:=]\\s*)([^\\s,;]+)")
    private val COOKIE = rx("(cookie\\s*:\\s*)([^\\r\\n]+)")
    // sessionid/sid в любом виде: sid=..., x-sessionid: ...
    private val SID_ASSIGN = rx("((?:x-)?(?:session[_-]?id|sid)[=:]\\s*)([A-Za-z0-9]+)")
    private val SESSION_HEADER = rx("(x-sessionid\\s*:\\s*)(\\S+)")
    // OAuth и токены в URL/тексте.
    private val OAUTH_PARAM =
        rx("([?&](?:code|state|session_id|token|access_token|auth_token)=)([^&\\s\"'<>]+)")
    private val CALLBACK_SID = rx("(/login_callback/[^/?\\s]*::)([A-Za-z0-9]+)", ignoreCase = false)
    private val PII_KEY = rx(
        "\"(email|phone|lastname|firstname|patronymic|first_name|last_name|middle_name|" +
            "barcode_payload|password|token|auth_token|access_token|refresh_token|session_id)\"\\s*:\\s*\"([^\"]*)\""
    )
    // Экранирование {, [ и ] обязательно: Android-regex (ICU) строже JVM и
    // падает на «голых» скобках — тесты на JVM это не ловят (краш при старте).
    private val PERSONAL_BLOCK = rx("(\"personal\"\\s*:\\s*)\\{[^{}]*\\}", ignoreCase = false)
    private val FIELDS_BLOCK = rx("(\"fields\"\\s*:\\s*)\\[[^\\[\\]]*\\]", ignoreCase = false)
    private val JWT = rx(
        "eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}",
        ignoreCase = false
    )

    /** Применяет все правила; при любой ошибке возвращает исходную строку без потерь. */
    fun scrub(input: String): String = runCatching {
        var out = input
        BEARER?.let { out = it.replace(out) { m -> m.groupValues[1] + "***" } }
        AUTH_TOKEN?.let { out = it.replace(out) { m -> m.groupValues[1] + "***" } }
        PROFILE_HEADER?.let { out = it.replace(out) { m -> m.groupValues[1] + mask(m.groupValues[2]) } }
        COOKIE?.let { out = it.replace(out) { m -> m.groupValues[1] + "***" } }
        SID_ASSIGN?.let { out = it.replace(out) { m -> m.groupValues[1] + mask(m.groupValues[2]) } }
        SESSION_HEADER?.let { out = it.replace(out) { m -> m.groupValues[1] + mask(m.groupValues[2]) } }
        OAUTH_PARAM?.let { out = it.replace(out) { m -> m.groupValues[1] + mask(m.groupValues[2]) } }
        CALLBACK_SID?.let { out = it.replace(out) { m -> m.groupValues[1] + mask(m.groupValues[2]) } }
        PII_KEY?.let { out = it.replace(out) { m ->
            if (m.groupValues[2].isEmpty()) m.value else m.value.replace(m.groupValues[2], "***")
        } }
        PERSONAL_BLOCK?.let { out = it.replace(out) { m -> m.groupValues[1] + "\"***\"" } }
        FIELDS_BLOCK?.let { out = it.replace(out) { m -> m.groupValues[1] + "\"***\"" } }
        JWT?.let { out = it.replace(out) { m -> mask(m.value) } }
        out
    }.getOrDefault(input)
}
