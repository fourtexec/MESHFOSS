package ru.mesh.foss.feature.cdz

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em

/** Место для интерактивного пропуска или формулы внутри текста. */
data class CdzInlinePlaceholder(
    val position: Int,
    val id: String,
    val alternateText: String,
    /** Если задано, вместо ответа рисуется нативная формула (корень/дробь). */
    val math: CdzMathNode? = null,
)

/**
 * Собирает `AnnotatedString` из обычного текста и `text_styles`.
 *
 * API присылает формулы как `H2O`, а индексы отдельными диапазонами `SUB`.
 * Здесь текст не переписывается: диапазоны накладываются как `SpanStyle`.
 */
fun buildCdzAnnotatedString(
    text: String,
    spans: List<CdzTextSpan>,
    placeholders: List<CdzInlinePlaceholder> = emptyList(),
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var cursor = 0
    placeholders.sortedBy { it.position }.forEach { placeholder ->
        val at = placeholder.position.coerceIn(cursor, text.length)
        if (at > cursor) appendStyledSegment(builder, text, cursor, at, spans)
        builder.appendInlineContent(
            id = placeholder.id,
            alternateText = placeholder.alternateText.ifBlank { "…" },
        )
        cursor = at
    }
    if (cursor < text.length) appendStyledSegment(builder, text, cursor, text.length, spans)
    return builder.toAnnotatedString()
}

private fun appendStyledSegment(
    builder: AnnotatedString.Builder,
    text: String,
    start: Int,
    end: Int,
    spans: List<CdzTextSpan>,
) {
    if (end <= start) return
    val base = builder.length
    builder.append(text.substring(start, end))
    spans.forEach { span ->
        val styleStart = maxOf(span.start, start)
        val styleEnd = minOf(span.end, end)
        if (styleEnd > styleStart) {
            builder.addStyle(
                style = cdzSpanStyle(span.tokens),
                start = base + (styleStart - start),
                end = base + (styleEnd - start),
            )
        }
    }
}

private fun cdzSpanStyle(tokens: Set<String>): SpanStyle {
    var style = SpanStyle()
    if (tokens.any { it in cdzBoldTokens }) style = style.copy(fontWeight = FontWeight.Bold)
    if (tokens.any { it in cdzItalicTokens }) style = style.copy(fontStyle = FontStyle.Italic)
    if (tokens.any { it in cdzCodeTokens }) {
        style = style.copy(fontFamily = FontFamily.Monospace)
    }

    val decorations = buildList {
        if (tokens.any { it in cdzUnderlineTokens }) add(TextDecoration.Underline)
        if (tokens.any { it in cdzStrikeTokens }) add(TextDecoration.LineThrough)
        if (tokens.any { it in cdzOverlineTokens }) add(TextDecoration.Overline)
    }
    if (decorations.isNotEmpty()) {
        style = style.copy(textDecoration = TextDecoration.combine(decorations))
    }

    // В олимпиадных payload'ах встречается SUPER/SUBSCRIPT, а не только
    // короткие SUP/SUB. Без алиасов кириллические индексы рисовались
    // обычным размером на базовой линии.
    if (tokens.any { it in cdzSubTokens }) {
        style = style.copy(
            baselineShift = BaselineShift.Subscript,
            fontSize = 0.78.em,
        )
    }
    if (tokens.any { it in cdzSupTokens }) {
        style = style.copy(
            baselineShift = BaselineShift.Superscript,
            fontSize = 0.78.em,
        )
    }
    return style
}

private val cdzBoldTokens = setOf("BOLD", "STRONG", "BF")
private val cdzItalicTokens = setOf("ITALIC", "EM", "EMPHASIS", "IT")
private val cdzCodeTokens = setOf("CODE", "MONOSPACE", "TT")
private val cdzUnderlineTokens = setOf("UNDERLINE", "U")
private val cdzStrikeTokens = setOf("STRIKETHROUGH", "STRIKE", "S")
private val cdzOverlineTokens = setOf("OVERLINE")
private val cdzSubTokens = setOf("SUB", "SUBSCRIPT", "LOWER", "INFERIOR")
private val cdzSupTokens = setOf("SUP", "SUPER", "SUPERSCRIPT", "UPPER", "SUPERIOR")

/**
 * Текст задания с сохранением форматирования API.
 *
 * `inlineContent` нужен только для интерактивных пропусков inline-заданий;
 * для обычных формул достаточно `text_styles`.
 */
@Composable
fun CdzRichText(
    text: String,
    spans: List<CdzTextSpan>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
    placeholders: List<CdzInlinePlaceholder> = emptyList(),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val annotated = remember(text, spans, placeholders) {
        buildCdzAnnotatedString(text, spans, placeholders)
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        inlineContent = inlineContent,
        maxLines = maxLines,
        overflow = overflow,
    )
}
