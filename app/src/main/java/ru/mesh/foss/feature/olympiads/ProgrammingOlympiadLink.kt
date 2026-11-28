package ru.mesh.foss.feature.olympiads

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

    private val RESULT_RE = Regex(
        """https?://[^"'\s<>]*/vrlabs/informatics/student/olympiad/competition/(\d+)/participation/results/(\d+)[^"'\s<>]*"""
    )

    fun parse(url: String?): ProgrammingOlympiadIds? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return null
        var candidate = raw
        repeat(5) {
            val match = RESULT_RE.find(candidate)
            if (match != null) {
                val pageUrl = match.value
                    .trimEnd(')', ']', ',', ';')
                    .replace("&amp;", "&")
                val ids = RESULT_RE.find(pageUrl) ?: return null
                val competition = ids.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
                val assignment = ids.groupValues.getOrNull(2)?.toLongOrNull() ?: return null
                if (competition <= 0L || assignment <= 0L) return null
                return ProgrammingOlympiadIds(competition, assignment, pageUrl)
            }
            val decoded = runCatching { URLDecoder.decode(candidate, "UTF-8") }.getOrNull()
                ?: return null
            if (decoded == candidate) return null
            candidate = decoded
        }
        return null
    }
}
