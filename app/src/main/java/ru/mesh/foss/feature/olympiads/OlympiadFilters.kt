package ru.mesh.foss.feature.olympiads

import java.util.Calendar

/**
 * Фильтры списка олимпиад.
 *
 * Наборы хранятся как `Set`, чтобы чипы не дублировались, а порядок в запросе
 * не зависел от порядка нажатий. Пустой [years] означает «все годы»; по
 * умолчанию выбран текущий год, как в веб-версии.
 */
data class OlympiadFilters(
    val subjectIds: Set<String> = emptySet(),
    val stageCodes: Set<String> = emptySet(),
    val parallelIds: Set<String> = emptySet(),
    val years: Set<Int> = setOf(currentOlympiadYear()),
    val includeCurrent: Boolean = true,
    val includeFuture: Boolean = true,
    val includeCompleted: Boolean = true,
) {
    val activeCount: Int
        get() = subjectIds.size +
            stageCodes.size +
            parallelIds.size +
            years.size +
            (if (includeCurrent) 0 else 1) +
            (if (includeFuture) 0 else 1) +
            (if (includeCompleted) 0 else 1)

    val hasActive: Boolean get() = activeCount > 0

    fun withoutSubject(id: String): OlympiadFilters = copy(subjectIds = subjectIds - id)

    fun withoutStage(code: String): OlympiadFilters = copy(stageCodes = stageCodes - code)

    fun withoutParallel(id: String): OlympiadFilters = copy(parallelIds = parallelIds - id)

    fun withoutYear(year: Int): OlympiadFilters = copy(years = years - year)
}

fun currentOlympiadYear(): Int =
    Calendar.getInstance().get(Calendar.YEAR)
