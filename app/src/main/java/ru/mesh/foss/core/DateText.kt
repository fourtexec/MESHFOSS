package ru.mesh.foss.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Форматирование ISO-дат, которые приходят из API МЭШ.
 *
 * Вынесено в `core`, чтобы экраны «Олимпиады» и «Портфолио» не зависели
 * друг от друга. Даты без часового пояса трактуются как локальные — так же,
 * как в веб-интерфейсе МЭШ.
 */
object DateText {

    private val INPUT_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd",
    )

    fun formatDateTime(iso: String?, locale: Locale = Locale("ru")): String {
        val date = parse(iso) ?: return iso?.trim().orEmpty().ifBlank { "—" }
        val pattern = if (locale.language == "ru") "dd.MM.yyyy HH:mm" else "d MMM yyyy, HH:mm"
        return SimpleDateFormat(pattern, locale).format(date)
    }

    fun formatDate(iso: String?, locale: Locale = Locale("ru")): String {
        val date = parse(iso) ?: return iso?.trim().orEmpty().ifBlank { "—" }
        val pattern = if (locale.language == "ru") "dd.MM.yyyy" else "d MMM yyyy"
        return SimpleDateFormat(pattern, locale).format(date)
    }

    private fun parse(iso: String?): Date? {
        val text = iso?.trim().orEmpty()
        if (text.isEmpty()) return null
        INPUT_PATTERNS.forEach { pattern ->
            val format = SimpleDateFormat(pattern, Locale.US)
            format.isLenient = false
            val parsed = runCatching { format.parse(text) }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }
}
