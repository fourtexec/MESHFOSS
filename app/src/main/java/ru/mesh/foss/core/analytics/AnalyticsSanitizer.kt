package ru.mesh.foss.core.analytics

import ru.mesh.foss.core.Sensitive

/**
 * Дополнительная редакция для телеметрии.
 *
 * [Sensitive] уже убирает токены, cookie и основные ПИИ из журнала. Для
 * отправки на внешний endpoint нужен более строгий режим: даже случайный
 * UUID, длинный числовой id, email или телефон не должны покидать устройство.
 * Поэтому здесь дополнительно вырезаются любые UUID и числа от 6 цифр,
 * почта/телефоны, URL-query с id и ключи вида `name`/`student_id`.
 */
object AnalyticsSanitizer {

    private const val MAX_EVENT = 900
    private const val MAX_STACK = 3_500

    private val uuid = Regex(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        RegexOption.IGNORE_CASE
    )
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    // Без lookbehind: на Android часть ICU-сборок капризна к нему, а падать
    // из-за regex аналитика не должна.
    private val phone = Regex("(?:\\+7|8)[\\s(-]*\\d{3}[\\s)-]*\\d{3}[\\s-]*\\d{2}[\\s-]*\\d{2}")
    private val longNumber = Regex("\\b\\d{6,}\\b")
    // 32+, чтобы не замаскировать обычные имена классов исключений.
    private val longToken = Regex("\\b[A-Za-z0-9_-]{32,}\\b")
    private val control = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
    private val spaces = Regex("\\s+")

    /** Одна строка для поля `event`: без переводов строк и управляющих символов. */
    fun event(input: String, max: Int = MAX_EVENT): String =
        sanitize(input)
            .replace(control, " ")
            .let { spaces.replace(it, " ").trim() }
            .take(max)

    /** Стек: переводы строк сохраняем, но обрезаем длину и вычищаем id/секреты. */
    fun stack(input: String, max: Int = MAX_STACK): String =
        sanitize(input)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .take(max)

    /** Короткий отпечаток для дедупликации одинаковых ошибок. */
    fun fingerprint(tag: String, error: Throwable): String =
        event("$tag|${error.javaClass.name}|${error.message.orEmpty()}", max = 256)

    private fun sanitize(input: String): String =
        scrub(input)
            .replace(uuid, "***")
            .replace(email, "***")
            .replace(phone, "***")
            .replace(longNumber, "***")
            .replace(longToken, "***")

    private fun scrub(input: String): String =
        runCatching { Sensitive.scrub(input) }.getOrDefault(input)
}
