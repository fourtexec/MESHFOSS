package ru.mesh.foss.feature.portfolio

import ru.mesh.foss.core.DateText
import ru.mesh.foss.data.model.PortfolioEvent
import ru.mesh.foss.data.model.PortfolioReward
import java.util.Locale

/** Чистые преобразования данных портфолио — без Compose и Android. */
object PortfolioText {

    fun date(iso: String?, locale: Locale = Locale("ru")): String =
        DateText.formatDate(iso, locale)

    fun number(value: Double?, maxFractionDigits: Int = 2): String {
        if (value == null || value.isNaN() || value.isInfinite()) return "—"
        if (value % 1.0 == 0.0) return value.toInt().toString()
        val text = String.format(Locale.US, "%.${maxFractionDigits}f", value)
        return text.trimEnd('0').trimEnd('.')
    }

    fun percent(value: Double?): String =
        if (value == null || value.isNaN() || value.isInfinite()) "—"
        else "${number(value, 1)}%"

    /**
     * Результат события: `result` приходит строкой, `maxScore` — числом.
     * Если результат не число (например, «Победитель»), показываем его как есть.
     */
    fun score(result: String?, maxScore: Double?): String? {
        val raw = result?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val mark = raw.toDoubleOrNull()
        val max = maxScore?.takeIf { it > 0.0 }
        return when {
            mark == null -> raw
            max == null -> number(mark)
            else -> "${number(mark)} / ${number(max)}"
        }
    }

    fun subjectNames(subjects: List<ru.mesh.foss.data.model.PortfolioDictionary>?): String =
        subjects.orEmpty()
            .mapNotNull { it.value?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .distinct()
            .joinToString(", ")

    /**
     * Награда сама по себе часто называется просто «Победитель». Контекст
     * («за что») берём из события, на которое указывает `entityId`.
     */
    fun rewardTitle(reward: PortfolioReward, event: PortfolioEvent?): String =
        event?.name?.takeIf { it.isNotBlank() }
            ?: reward.description?.takeIf { it.isNotBlank() }
            ?: reward.name?.takeIf { it.isNotBlank() }
            ?: reward.rewardType?.value.orEmpty()

    fun rewardMeta(
        reward: PortfolioReward,
        event: PortfolioEvent?,
        locale: Locale = Locale("ru"),
    ): String {
        val subject = subjectNames(event?.subjects)
            .ifBlank { reward.category?.value.orEmpty() }
        return listOfNotNull(
            subject.takeIf { it.isNotBlank() },
            event?.stageEvent?.takeIf { it.isNotBlank() },
            reward.statusReward?.value?.takeIf { it.isNotBlank() },
            reward.date?.let { date(it, locale) }?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    fun initials(name: String?): String =
        name.orEmpty()
            .split(Regex("\\s+"))
            .mapNotNull { part -> part.firstOrNull()?.uppercaseChar()?.toString() }
            .take(2)
            .joinToString("")
            .ifBlank { "•" }
}
