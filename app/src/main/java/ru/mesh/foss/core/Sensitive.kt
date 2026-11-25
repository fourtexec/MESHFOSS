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
    // Токен в Authorization без слова Bearer (так его иногда шлёт familyweb/LRS).
    private val AUTHORIZATION_PLAIN = rx("(authorization\\s*:\\s*)(?!bearer\\b)([^\\s,;]+)")
    private val API_KEY_HEADER = rx("((?:x-api-key|api-key)\\s*:\\s*)([^\\s,;]+)")
    private val PROFILE_HEADER =
        rx("((?:profile-id|x-mes-profile-id|x-mes-profileid|x-mes-userid)\\s*[:=]\\s*)([^\\s,;]+)")
    private val COOKIE = rx("(cookie\\s*:\\s*)([^\\r\\n]+)")
    private val SET_COOKIE = rx("(set-cookie\\s*:\\s*)([^\\r\\n]+)")
    // sessionid/sid в любом виде: sid=..., x-sessionid: ...
    private val SID_ASSIGN = rx("((?:x-)?(?:session[_-]?id|sid)[=:]\\s*)([A-Za-z0-9]+)")
    private val SESSION_HEADER = rx("(x-sessionid\\s*:\\s*)(\\S+)")
    // OAuth и токены в URL/тексте.
    private val OAUTH_PARAM =
        rx("([?&](?:code|state|session_id|token|access_token|auth_token)=)([^&\\s\"'<>]+)")
    // Токены/секреты в логах не только в URL, но и в тексте вида token=...
    private val TOKEN_ASSIGN = rx(
        "((?:token|access_token|refresh_token|auth_token|api_key|apikey|secret|password)\\s*[=:]\\s*)" +
            "([^\\s,;\"'&]+)"
    )
    private val CALLBACK_SID = rx("(/login_callback/[^/?\\s]*::)([A-Za-z0-9]+)", ignoreCase = false)
    // Идентификаторы ученика, материала, работы, попытки и оценки в query.
    private val ID_PARAM = rx(
        "([?&](?:student_id|person_id|profile_id|user_id|material_id|work_id|markId|mark_id|" +
            "academic_year_id|challenge|challenge_attempt_id|attempt_id|entry|homework_entry_id|" +
            "entry_student_id|uuid|login|email|phone)=)([^&\\s\"'<>]+)"
    )
    // context/activity_url — это URL-encoded JSON/URL, внутри почти всегда id.
    private val CONTEXT_PARAM = rx("([?&](?:context|activity_url)=)([^&\\s\"'<>]+)")
    // Локальные строки вида entry=..., material=..., attemptId=...
    private val ID_ASSIGN = rx(
        "((?:attemptId|last_attempt_id|person_id|student_id|profile_id|user_id|material_id|" +
            "material|work_id|markId|challenge|entry)\\s*[=:]\\s*)([A-Za-z0-9_-]{2,})"
    )
    // Числовые id в пути: /marks/3340378813, /exam/challenge/150779975.
    private val PATH_ID = rx("(/(?:marks|challenge|homeworks|students|materials|objects|documents)/)(\\d{5,})")
    private val EMAIL = rx("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    // Без lookbehind/lookahead: Android-ICU местами их не любит, а фильтр
    // должен работать на всех устройствах.
    private val PHONE = rx("(?:\\+7|8)[\\s(-]*\\d{3}[\\s)-]*\\d{3}[\\s-]*\\d{2}[\\s-]*\\d{2}")
    private val UUID = rx("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val LONG_NUMERIC_ID = rx("\\b\\d{6,}\\b")
    private val PII_KEY = rx(
        "\"(email|phone|lastname|firstname|patronymic|first_name|last_name|middle_name|" +
            "name|full_name|display_name|student_name|parent_name|class|class_name|school|" +
            "school_name|login|birthdate|birthday|address|snils|inn|passport|" +
            "barcode_payload|password|token|auth_token|access_token|refresh_token|session_id)\"\\s*:\\s*\"([^\"]*)\""
    )
    private val ID_KEY = rx(
        "\"(id|user_id|person_id|student_id|profile_id|uuid|challenge_id|attempt_id|" +
            "challenge_attempt_id|material_id|work_id|mark_id|entry_id|homework_entry_id|login)\"" +
            "\\s*:\\s*(\"[^\"]*\"|\\d+)"
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
        AUTHORIZATION_PLAIN?.let { out = it.replace(out) { m -> m.groupValues[1] + "***" } }
        API_KEY_HEADER?.let { out = it.replace(out) { m -> m.groupValues[1] + "***" } }
        PROFILE_HEADER?.let { out = it.replace(out) { m -> m.groupValues[1] + mask(m.groupValues[2]) } }
        COOKIE?.let { out = it.replace(out) { m -> m.groupValues[1] + "***" } }
        SET_COOKIE?.let { out = it.replace(out) { m -> m.groupValues[1] + "***" } }
        SID_ASSIGN?.let { out = it.replace(out) { m -> m.groupValues[1] + mask(m.groupValues[2]) } }
        SESSION_HEADER?.let { out = it.replace(out) { m -> m.groupValues[1] + mask(m.groupValues[2]) } }
        OAUTH_PARAM?.let { out = it.replace(out) { m -> m.groupValues[1] + mask(m.groupValues[2]) } }
        TOKEN_ASSIGN?.let { out = it.replace(out) { m -> m.groupValues[1] + "***" } }
        CALLBACK_SID?.let { out = it.replace(out) { m -> m.groupValues[1] + mask(m.groupValues[2]) } }
        ID_PARAM?.let { out = it.replace(out) { m -> m.groupValues[1] + "***" } }
        CONTEXT_PARAM?.let { out = it.replace(out) { m -> m.groupValues[1] + "***" } }
        ID_ASSIGN?.let { out = it.replace(out) { m -> m.groupValues[1] + "***" } }
        PATH_ID?.let { out = it.replace(out) { m -> m.groupValues[1] + "***" } }
        EMAIL?.let { out = it.replace(out, "***") }
        PHONE?.let { out = it.replace(out, "***") }
        UUID?.let { out = it.replace(out, "***") }
        LONG_NUMERIC_ID?.let { out = it.replace(out, "***") }
        PII_KEY?.let { out = it.replace(out) { m ->
            if (m.groupValues[2].isEmpty()) m.value else m.value.replace(m.groupValues[2], "***")
        } }
        ID_KEY?.let { out = it.replace(out) { m -> "\"${m.groupValues[1]}\": \"***\"" } }
        PERSONAL_BLOCK?.let { out = it.replace(out) { m -> m.groupValues[1] + "\"***\"" } }
        FIELDS_BLOCK?.let { out = it.replace(out) { m -> m.groupValues[1] + "\"***\"" } }
        JWT?.let { out = it.replace(out) { m -> mask(m.value) } }
        out
    }.getOrDefault(input)
}
