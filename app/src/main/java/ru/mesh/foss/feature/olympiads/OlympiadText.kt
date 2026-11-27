package ru.mesh.foss.feature.olympiads

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.util.Locale
import ru.mesh.foss.core.DateText

/**
 * Чистые преобразования данных олимпиад: описание приходит то HTML-строкой,
 * то JSON-строкой с rich-text блоками, а даты — ISO-строками с часовым поясом.
 * Логика вынесена из Compose, чтобы её можно было проверить JVM-тестами.
 */
object OlympiadText {

    private val TAG_RE = Regex("<[^>]*>")
    private val BR_RE = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val PARAGRAPH_RE = Regex("</p\\s*>", RegexOption.IGNORE_CASE)
    private val SPACES_RE = Regex("[\\t\\x0B\\f\\r ]+")
    private val DECIMAL_ENTITY_RE = Regex("&#(\\d+);")
    private val HEX_ENTITY_RE = Regex("&#x([0-9a-fA-F]+);")

    private val SAFE_KEYS = listOf(
        "text", "content", "title", "description", "body", "label", "caption",
        "paragraph", "items", "children", "blocks", "data", "value"
    )

    /** HTML/JSON rich-text → обычный текст без разметки и служебных полей. */
    fun plainDescription(raw: String?): String {
        val source = raw?.trim().orEmpty()
        if (source.isEmpty()) return ""
        val parsed = runCatching { JsonParser.parseString(source) }.getOrNull()
        val extracted = when {
            parsed == null || parsed.isJsonNull -> ""
            parsed.isJsonPrimitive -> parsed.asString
            else -> collectText(parsed, 0).distinct().joinToString("\n")
        }
        return normalize(if (extracted.isBlank()) stripHtml(source) else stripHtml(extracted))
    }

    fun stripHtml(raw: String): String {
        val withBreaks = raw
            .replace(BR_RE, "\n")
            .replace(PARAGRAPH_RE, "\n")
        val withoutTags = TAG_RE.replace(withBreaks, "")
        val decoded = withoutTags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(DECIMAL_ENTITY_RE) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
            }
            .replace(HEX_ENTITY_RE) { match ->
                match.groupValues[1].toIntOrNull(16)?.toChar()?.toString().orEmpty()
            }
        return normalize(decoded)
    }

    fun formatDateTime(iso: String?, locale: Locale = Locale("ru")): String =
        DateText.formatDateTime(iso, locale)

    fun formatDate(iso: String?, locale: Locale = Locale("ru")): String =
        DateText.formatDate(iso, locale)

    private fun collectText(element: JsonElement, depth: Int): List<String> {
        if (depth > 24) return emptyList()
        if (element.isJsonNull) return emptyList()
        if (element.isJsonPrimitive) {
            return if (element.asJsonPrimitive.isString) {
                listOf(element.asString)
            } else {
                emptyList()
            }
        }
        if (element.isJsonArray) {
            return element.asJsonArray.flatMap { collectText(it, depth + 1) }
        }
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            return SAFE_KEYS.flatMap { key ->
                obj.get(key)?.let { collectText(it, depth + 1) }.orEmpty()
            }
        }
        return emptyList()
    }

    private fun normalize(text: String): String =
        text
            .replace(SPACES_RE, " ")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
}
