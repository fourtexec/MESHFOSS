package ru.mesh.foss.feature.cdz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/** Блок нативно разобранного HTML из программистской олимпиады. */
sealed interface CdzHtmlBlock {
    data class Rich(val content: CdzMergedText) : CdzHtmlBlock
    data class Image(val url: String, val description: String) : CdzHtmlBlock
    data class Math(val node: CdzMathNode) : CdzHtmlBlock
}

data class CdzHtmlDocument(val blocks: List<CdzHtmlBlock>)

/**
 * Разбирает HTML описания задачи.
 *
 * Это не полноценный HTML-движок, а безопасный рендер разрешённых тегов из
 * web-бандла: `p`, `div`, `span`, `br`, `li`, `ul`, `ol`, `img`, `center`.
 * Скрытые `invisibleText` и элементы нулевого размера пропускаются.
 */
fun parseCdzHtml(html: String?): CdzHtmlDocument {
    val source = html?.trim().orEmpty()
    if (source.isEmpty()) return CdzHtmlDocument(emptyList())
    return CdzHtmlParser(source).parse()
}

@Composable
fun CdzHtmlContent(
    html: String?,
    modifier: Modifier = Modifier,
    referer: String = "https://school.mos.ru/",
) {
    val document = remember(html) { parseCdzHtml(html) }
    if (document.blocks.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        document.blocks.forEach { block ->
            when (block) {
                is CdzHtmlBlock.Rich -> CdzHtmlRich(block.content)
                is CdzHtmlBlock.Image -> CdzRemoteImage(
                    url = block.url,
                    description = block.description,
                    referer = referer,
                )
                is CdzHtmlBlock.Math -> CdzMathInline(
                    node = block.node,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CdzHtmlRich(content: CdzMergedText) {
    if (content.text.isBlank() && content.placeholders.isEmpty()) return
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodyLarge
    val inlineContent = content.placeholders.associate { placeholder ->
        val math = placeholder.math
        placeholder.id to InlineTextContent(
            placeholder = Placeholder(
                width = if (math != null) {
                    mathPlaceholderWidthEm(math).em
                } else {
                    inlinePlaceholderWidth(
                        label = placeholder.alternateText,
                        style = labelStyle,
                        measurer = measurer,
                        density = density,
                    )
                },
                height = if (math != null) {
                    mathPlaceholderHeightEm(math).em
                } else {
                    1.5.em
                },
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            if (math != null) {
                CdzMathInline(
                    node = math,
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                androidx.compose.material3.Text(placeholder.alternateText)
            }
        }
    }
    CdzRichText(
        text = content.text,
        spans = content.spans,
        style = labelStyle,
        color = MaterialTheme.colorScheme.onSurface,
        inlineContent = inlineContent,
        placeholders = content.placeholders,
    )
}

private class CdzHtmlParser(private val source: String) {
    private val blocks = mutableListOf<CdzHtmlBlock>()
    private val text = StringBuilder()
    private val spans = mutableListOf<CdzTextSpan>()
    private val placeholders = mutableListOf<CdzInlinePlaceholder>()
    private val spanStack = ArrayDeque<SpanFrame>()
    private val listStack = ArrayDeque<ListFrame>()
    private val hiddenTagStack = ArrayDeque<String>()
    private var hiddenDepth = 0
    private var mathCaptureDepth = 0
    private val mathCapture = StringBuilder()

    private data class SpanFrame(
        val tokens: Set<String>,
        val hidden: Boolean,
        val math: Boolean,
        val void: Boolean = false,
    )

    private data class ListFrame(val ordered: Boolean, var counter: Int = 0)

    fun parse(): CdzHtmlDocument {
        var cursor = 0
        while (cursor < source.length) {
            val tagStart = source.indexOf('<', cursor)
            if (tagStart < 0) {
                appendText(source.substring(cursor))
                break
            }
            if (tagStart > cursor) appendText(source.substring(cursor, tagStart))
            val tagEnd = findTagEnd(source, tagStart + 1)
            if (tagEnd < 0) {
                appendText(source.substring(tagStart))
                break
            }
            handleTag(source.substring(tagStart + 1, tagEnd))
            cursor = tagEnd + 1
        }
        flush()
        return CdzHtmlDocument(blocks)
    }

    private fun handleTag(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("!")) return
        val closing = trimmed.startsWith("/")
        val selfClosing = !closing && trimmed.endsWith("/")
        val body = if (closing) {
            trimmed.substring(1).trim()
        } else {
            trimmed.removeSuffix("/").trim()
        }
        val name = body.takeWhile { !it.isWhitespace() }.lowercase()
        val attrs = parseAttributes(body.drop(name.length))
        if (name == "span") {
            if (closing) {
                closeSpan()
            } else {
                openSpan(attrs, selfClosing)
            }
            return
        }
        // `invisibleText` встречается не только на span, но и на p/div.
        if (!closing && !selfClosing && isHiddenAttrs(attrs)) {
            flush()
            hiddenDepth++
            hiddenTagStack.addLast(name)
            return
        }
        if (closing && hiddenTagStack.lastOrNull() == name) {
            hiddenDepth = (hiddenDepth - 1).coerceAtLeast(0)
            hiddenTagStack.removeLast()
            flush()
            return
        }
        if (hiddenDepth > 0) return
        when (name) {
            "br" -> text.append('\n')
            "p", "div", "center", "legend", "note", "input-specification",
            "output-specification", "scoring", "section", "article", "pre",
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                if (closing) {
                    flush()
                } else {
                    flush()
                }
            }
            "ul", "ol" -> {
                if (closing) {
                    flush()
                    listStack.removeLastOrNull()
                } else {
                    flush()
                    listStack.addLast(ListFrame(ordered = name == "ol"))
                }
            }
            "li" -> {
                if (closing) {
                    flush()
                } else {
                    flush()
                    val list = listStack.lastOrNull()
                    val prefix = if (list?.ordered == true) {
                        list.counter++
                        "${list.counter}. "
                    } else {
                        "• "
                    }
                    text.append(prefix)
                }
            }
            "img" -> {
                if (closing) return
                flush()
                val src = attrs["src"].orEmpty()
                val unsafe = src.startsWith("data:", ignoreCase = true) ||
                    src.startsWith("javascript:", ignoreCase = true) ||
                    src.startsWith("blob:", ignoreCase = true)
                if (src.isNotBlank() && !unsafe) {
                    blocks += CdzHtmlBlock.Image(
                        url = htmlAbsoluteUrl(src),
                        description = decodeEntities(attrs["alt"].orEmpty()),
                    )
                }
            }
            else -> Unit
        }
    }

    private fun openSpan(attrs: Map<String, String>, selfClosing: Boolean) {
        // Внутри скрытого блока нельзя рисовать даже формулы: иначе
        // prompt-injection текст из `invisibleText` может просочиться на экран.
        if (hiddenDepth > 0) {
            if (!selfClosing) {
                spanStack.addLast(SpanFrame(emptySet(), hidden = true, math = false, void = true))
            }
            return
        }
        val classes = attrs["class"].orEmpty().split(Regex("\\s+")).toSet()
        val editorType = attrs["data-w-e-type"].orEmpty()
        if (isHiddenAttrs(attrs)) {
            if (!selfClosing) {
                hiddenDepth++
                spanStack.addLast(SpanFrame(emptySet(), hidden = true, math = false))
            }
            return
        }
        if (editorType == "formula" || editorType == "inline-math") {
            val value = attrs["data-value"].orEmpty()
                .ifBlank { attrs["data-latex"].orEmpty() }
            appendMath(value)
            if (!selfClosing) {
                spanStack.addLast(SpanFrame(emptySet(), hidden = false, math = false, void = true))
            }
            return
        }
        if ("math-tex" in classes) {
            if (!selfClosing) {
                mathCaptureDepth++
                spanStack.addLast(SpanFrame(emptySet(), hidden = false, math = true))
            }
            return
        }
        val tokens = buildSet {
            if ("tex-font-style-bf" in classes || "bold" in classes) add("BOLD")
            if ("tex-font-style-it" in classes || "italic" in classes) add("ITALIC")
            if ("tex-font-style-underline" in classes) add("UNDERLINE")
            if ("tex-font-style-tt" in classes) add("CODE")
        }
        if (!selfClosing) {
            spanStack.addLast(SpanFrame(tokens, hidden = false, math = false))
        }
    }

    private fun closeSpan() {
        val frame = spanStack.removeLastOrNull() ?: return
        if (frame.void) return
        if (frame.hidden) {
            if (hiddenDepth > 0) hiddenDepth--
            return
        }
        if (frame.math) {
            if (mathCaptureDepth > 0) mathCaptureDepth--
            if (mathCaptureDepth == 0) {
                val raw = mathCapture.toString()
                mathCapture.clear()
                appendMath(stripMathDelimiters(raw))
            }
            return
        }
    }

    private fun appendText(raw: String) {
        if (hiddenDepth > 0 || raw.isEmpty()) return
        if (mathCaptureDepth > 0) {
            mathCapture.append(raw)
            return
        }
        var cursor = 0
        mathRegex.findAll(raw).forEach { match ->
            if (match.range.first > cursor) appendPlain(raw.substring(cursor, match.range.first))
            val math = match.groups.drop(1).firstOrNull { it != null }?.value.orEmpty()
            appendMath(math)
            cursor = match.range.last + 1
        }
        if (cursor < raw.length) appendPlain(raw.substring(cursor))
    }

    private fun appendPlain(raw: String) {
        val value = decodeEntities(raw)
        if (value.isEmpty()) return
        val start = text.length
        text.append(value)
        activeTokens().forEach { tokens ->
            if (tokens.isNotEmpty()) {
                spans += CdzTextSpan(start, text.length, tokens)
            }
        }
    }

    private fun activeTokens(): List<Set<String>> = buildList {
        spanStack.forEach { frame ->
            if (!frame.hidden && !frame.math && frame.tokens.isNotEmpty()) add(frame.tokens)
        }
    }

    private fun appendMath(raw: String) {
        val math = parseMath(decodeEntities(raw))
        if (math.text.isBlank() && math.inlines.isEmpty()) return
        val start = text.length
        text.append(math.text)
        math.spans.forEach { span ->
            spans += span.copy(start = start + span.start, end = start + span.end)
        }
        math.inlines.forEachIndexed { index, inline ->
            placeholders += CdzInlinePlaceholder(
                position = start + inline.position,
                id = "html_math_${placeholders.size}_$index",
                alternateText = inline.alternateText,
                math = inline.node,
            )
        }
    }

    private fun flush() {
        if (text.isBlank() && placeholders.isEmpty()) {
            text.clear()
            spans.clear()
            placeholders.clear()
            return
        }
        val merged = normalizeMergedText(
            CdzMergedText(
                text = text.toString(),
                spans = spans.toList(),
                placeholders = placeholders.toList(),
            )
        )
        if (merged.text.isNotBlank() || merged.placeholders.isNotEmpty()) {
            blocks += CdzHtmlBlock.Rich(merged)
        }
        text.clear()
        spans.clear()
        placeholders.clear()
    }
}

// Веб-бандл использует `$$$...$$$`, `\(...\)`, `\[...\]` и editor formula.
// Одиночный `$...$` не поддерживаем: он слишком часто оказывается валютой.
private val mathRegex = Regex(
    """\$\$\$(.+?)\$\$\$|\\\((.+?)\\\)|\\\[(.+?)\\\]""",
    setOf(RegexOption.DOT_MATCHES_ALL),
)

private fun stripMathDelimiters(raw: String): String {
    var value = raw.trim()
    if (value.startsWith("$$$") && value.endsWith("$$$")) {
        value = value.substring(3, value.length - 3)
    }
    if (value.startsWith("\\(") && value.endsWith("\\)")) {
        value = value.substring(2, value.length - 2)
    }
    if (value.startsWith("\\[") && value.endsWith("\\]")) {
        value = value.substring(2, value.length - 2)
    }
    return value.trim()
}

private fun isHiddenAttrs(attrs: Map<String, String>): Boolean {
    val editorType = attrs["data-w-e-type"].orEmpty()
    if (editorType.equals("invisibleText", ignoreCase = true)) return true

    val style = parseStyle(attrs["style"].orEmpty())
    if (style["display"]?.equals("none", ignoreCase = true) == true) return true
    if (style["visibility"]?.equals("hidden", ignoreCase = true) == true) return true
    if (style["opacity"]?.toDoubleOrNull() == 0.0) return true

    val fontSize = style["font-size"].orEmpty()
        .removeSuffix("px").removeSuffix("em").removeSuffix("rem").removeSuffix("%")
        .trim().toDoubleOrNull()
    if (fontSize == 0.0) return true

    val color = style["color"].orEmpty().lowercase().replace(" ", "")
    return color == "white" ||
        color == "#fff" ||
        color == "#ffffff" ||
        color == "rgb(255,255,255)" ||
        color == "rgba(255,255,255,1)" ||
        color == "rgba(255,255,255,1.0)"
}

private fun parseStyle(raw: String): Map<String, String> = raw
    .split(';')
    .mapNotNull { declaration ->
        val colon = declaration.indexOf(':')
        if (colon <= 0) return@mapNotNull null
        val name = declaration.substring(0, colon).trim().lowercase()
        val value = declaration.substring(colon + 1)
            .substringBefore('!')
            .trim()
            .lowercase()
        if (name.isBlank() || value.isBlank()) null else name to value
    }
    .toMap()

/** Ищет `>` вне quoted-атрибутов: `data-value="x > 0"` не должен ломать тег. */
private fun findTagEnd(source: String, start: Int): Int {
    var quote: Char? = null
    var index = start
    while (index < source.length) {
        val char = source[index]
        if (quote != null) {
            if (char == quote) quote = null
        } else {
            when (char) {
                '"', '\'' -> quote = char
                '>' -> return index
            }
        }
        index++
    }
    return -1
}

private fun parseAttributes(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    val result = LinkedHashMap<String, String>()
    val regex = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""")
    regex.findAll(raw).forEach { match ->
        val name = match.groupValues[1].lowercase()
        val value = match.groupValues[3]
            .ifBlank { match.groupValues[4] }
            .ifBlank { match.groupValues[5] }
        result[name] = decodeEntities(value)
    }
    return result
}

private fun decodeEntities(raw: String): String {
    if ('&' !in raw) return raw
    val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"",
        "apos" to "'", "nbsp" to " ", "mdash" to "—", "ndash" to "–",
        "laquo" to "«", "raquo" to "»", "hellip" to "…", "middot" to "·",
        "times" to "×", "divide" to "÷", "le" to "≤", "ge" to "≥",
        "ne" to "≠", "minus" to "−", "plusmn" to "±", "deg" to "°",
    )
    return Regex("""&(#x?[0-9A-Fa-f]+|[A-Za-z]+);""").replace(raw) { match ->
        val body = match.groupValues[1]
        if (body.startsWith("#x") || body.startsWith("#X")) {
            body.substring(2).toIntOrNull(16)?.let { codePointToString(it) } ?: match.value
        } else if (body.startsWith("#")) {
            body.substring(1).toIntOrNull()?.let { codePointToString(it) } ?: match.value
        } else {
            named[body] ?: match.value
        }
    }
}

private fun codePointToString(code: Int): String? =
    runCatching { String(Character.toChars(code)) }.getOrNull()

internal fun htmlAbsoluteUrl(raw: String): String {
    val value = decodeEntities(raw).trim()
    if (value.isBlank()) return ""
    if (value.startsWith("//")) return "https:$value"
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    if (value.startsWith("/")) return "https://school.mos.ru$value"
    return "https://school.mos.ru/$value"
}
