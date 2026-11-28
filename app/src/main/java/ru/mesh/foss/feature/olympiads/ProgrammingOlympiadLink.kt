package ru.mesh.foss.feature.olympiads

import java.net.URI
import java.net.URLDecoder

/**
 * Ссылка на результат программистской олимпиады `vrlabs/informatics`.
 *
 * Формат отличается от `uchebnik`:
 * `/vrlabs/informatics/student/olympiad/competition/{id}/participation/results/{assignment}`.
 */
data class ProgrammingOlympiadIds(
    val competitionId: Long,
    val assignmentId: Long,
    /** Прямая страница результата; query может содержать launchContext. */
    val pageUrl: String,
)

object ProgrammingOlympiadLink {

    private val RESULT_PATH = Regex(
        """/vrlabs/informatics/student/olympiad/competition/(\d+)/participation/results/(\d+)(?:/|$)"""
    )
    private val URL_START = Regex("""https?://""", RegexOption.IGNORE_CASE)
    private val TRAILING = charArrayOf(')', ']', ',', ';', '"', '\'')

    /**
     * Разбирает прямую ссылку и launcher-обёртки.
     *
     * Launcher может быть вложенным: `activity_url`/`activityId` содержат
     * следующую ссылку, иногда URL-encoded несколько раз. Поэтому идём по
     * очереди кандидатов и разбираем каждый как URL, а не ищем подстроку по
     * всему телу ответа.
     */
    fun parse(url: String?): ProgrammingOlympiadIds? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return null

        val queue = ArrayDeque<String>()
        val seen = mutableSetOf<String>()
        queue.addLast(raw)

        while (queue.isNotEmpty()) {
            val candidate = queue.removeFirst().trim()
            if (candidate.isEmpty() || !seen.add(candidate)) continue

            parseDirect(candidate)?.let { return it }

            val decoded = runCatching { URLDecoder.decode(candidate, "UTF-8") }.getOrNull()
            if (decoded != null && decoded != candidate) queue.addLast(decoded)

            val clean = candidate.replace("&amp;", "&")
            val uri = runCatching { URI(clean) }.getOrNull()
            uri?.rawQuery?.split('&')?.forEach { pair ->
                val value = pair.substringAfter('=', "")
                if (value.isNotBlank()) queue.addLast(value)
            }

            // Ссылка может быть внутри текста/HTML, а не быть всем candidate.
            URL_START.findAll(clean).forEach { match ->
                val tail = clean.substring(match.range.first)
                    .takeWhile { !it.isWhitespace() && it !in "\"'<>" }
                    .trimEnd(*TRAILING)
                if (tail.isNotBlank()) queue.addLast(tail)
            }
        }
        return null
    }

    private fun parseDirect(value: String): ProgrammingOlympiadIds? {
        val clean = value
            .replace("&amp;", "&")
            .trim()
            .trimEnd(*TRAILING)
        val uri = runCatching { URI(clean) }.getOrNull() ?: return null
        val match = RESULT_PATH.find(uri.path.orEmpty()) ?: return null
        val competition = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
        val assignment = match.groupValues.getOrNull(2)?.toLongOrNull() ?: return null
        if (competition <= 0L || assignment <= 0L) return null
        return ProgrammingOlympiadIds(competition, assignment, clean)
    }
}
