package ru.mesh.foss.feature.olympiads

/** ID попытки, извлечённые из пользовательской ссылки `detailsUrl`. */
data class OlympiadAttemptIds(
    val challengeId: Long,
    val attemptId: Long,
)

/**
 * `detailsUrl` олимпиады после `/url-details/link` ведёт на страницу
 * `uchebnik.mos.ru/contests/challenge/{challenge}/attempt/{attempt}`.
 * ID нужны, чтобы открыть тот же результат нативно, без браузера.
 */
object OlympiadAttemptLink {

    private val ATTEMPT_RE = Regex(
        "/(?:contests|exam)/challenge/(\\d+)/attempt/(\\d+)"
    )

    fun parse(url: String?): OlympiadAttemptIds? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val match = ATTEMPT_RE.find(raw) ?: return null
        val challenge = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
        val attempt = match.groupValues.getOrNull(2)?.toLongOrNull() ?: return null
        if (challenge <= 0L || attempt <= 0L) return null
        return OlympiadAttemptIds(challenge, attempt)
    }
}
