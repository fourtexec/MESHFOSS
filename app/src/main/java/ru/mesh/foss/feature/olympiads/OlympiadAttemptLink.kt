package ru.mesh.foss.feature.olympiads

import java.net.URLDecoder

/** ID попытки, извлечённые из пользовательской ссылки `detailsUrl`. */
data class OlympiadAttemptIds(
    val challengeId: Long,
    val attemptId: Long,
) {
    /** Канонический адрес страницы попытки: он же Referer для uchebnik. */
    val pageUrl: String
        get() = "https://uchebnik.mos.ru/contests/challenge/$challengeId/attempt/$attemptId"
}

/**
 * `detailsUrl` олимпиады после `/url-details/link` может вести как напрямую
 * на страницу `uchebnik.mos.ru/contests/challenge/{challenge}/attempt/{attempt}`,
 * так и на launcher-ссылку school.mos.ru с URL-encoded `activity_url`.
 *
 * ID нужны, чтобы открыть тот же результат нативно, без браузера.
 */
object OlympiadAttemptLink {

    private val ATTEMPT_RE = Regex(
        "/(?:contests|exam)/challenge/(\\d+)/attempt/(\\d+)"
    )

    fun parse(url: String?): OlympiadAttemptIds? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return null

        // launcher-ссылка содержит activity_url=https%3A%2F%2Fuchebnik...
        // Декодируем до трёх раз: некоторые редиректы кодируют ссылку дважды.
        var candidate = raw
        repeat(4) {
            val match = ATTEMPT_RE.find(candidate)
            if (match != null) {
                val challenge = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
                val attempt = match.groupValues.getOrNull(2)?.toLongOrNull() ?: return null
                if (challenge <= 0L || attempt <= 0L) return null
                return OlympiadAttemptIds(challenge, attempt)
            }
            val decoded = runCatching { URLDecoder.decode(candidate, "UTF-8") }.getOrNull()
                ?: return null
            if (decoded == candidate) return null
            candidate = decoded
        }
        return null
    }
}
