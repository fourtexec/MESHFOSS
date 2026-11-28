package ru.mesh.foss.feature.cdz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.json.JSONArray
import org.json.JSONObject

/**
 * Условие задания ЦДЗ: стилизованные текстовые блоки и медиа.
 *
 * В API текст и изображения/видео приходят отдельными `content/text` и
 * `content/atomic` блоками; здесь они собираются в один Compose-поток.
 */
@Composable
fun CdzQuestionContent(
    elements: JSONArray,
    modifier: Modifier = Modifier,
    inlineAnswers: List<CdzGapAnswer> = emptyList(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (index in 0 until elements.length()) {
            val block = elements.optJSONObject(index) ?: continue
            val textId = block.optString("text_id").trim()
            val blockAnswers = if (textId.isBlank()) {
                inlineAnswers.filter { it.textId.isBlank() }
            } else {
                inlineAnswers.filter { it.textId == textId }
            }
            CdzQuestionBlock(block, inlineAnswers = blockAnswers)
        }
    }
}

/** Один блок условия. Отдельно нужен inline-заданиям с пропусками в тексте. */
@Composable
internal fun CdzQuestionBlock(
    block: JSONObject,
    modifier: Modifier = Modifier,
    inlineAnswers: List<CdzGapAnswer> = emptyList(),
) {
    val type = block.optString("type")
    if (type == "content/atomic") {
        CdzAtomicMedia(block, modifier)
        return
    }
    if (type.startsWith("content/math") || block.optString("atomic_type") == "math") {
        val math = parseMathNodes(
            block.optString("content").ifBlank { block.optString("text") }
        )
        CdzMathInline(
            node = math,
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        return
    }

    val text = block.optString("text")
    val content = block.optJSONArray("content")
    val merged = remember(block, inlineAnswers) {
        mergeQuestionText(text, content, block.optJSONArray("text_styles"), inlineAnswers)
    }
    if (merged.text.isNotBlank() || merged.placeholders.isNotEmpty()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.bodyLarge
        val inlineContent = merged.placeholders.associate { placeholder ->
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
                    CdzInlineAnswer(placeholder.alternateText)
                }
            }
        }
        CdzRichText(
            text = merged.text,
            spans = merged.spans,
            modifier = modifier,
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurface,
            inlineContent = inlineContent,
            placeholders = merged.placeholders,
        )
    }

    if (content == null) return
    for (partIndex in 0 until content.length()) {
        val part = content.optJSONObject(partIndex) ?: continue
        val partType = part.optString("type")
        if (partType.startsWith("content/math") ||
            part.optString("atomic_type") == "math"
        ) {
            // Math already inserted into [merged] at its `position`.
            continue
        }
        if (cdzAtomicMediaType(part).isNotBlank()) {
            CdzAtomicMedia(part, modifier)
            continue
        }
        if (partType.startsWith("content/text")) {
            val partText = part.optString("text")
            if (partText.isNotBlank()) {
                val spans = remember(part) {
                    parseTextSpans(partText, part.optJSONArray("text_styles"))
                }
                CdzRichText(
                    text = partText,
                    spans = spans,
                    modifier = modifier,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Ответ ученика, вставленный в текст задания на месте пропуска. */
@Composable
private fun CdzInlineAnswer(value: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = value.ifBlank { "—" },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CdzAtomicMedia(block: JSONObject, modifier: Modifier = Modifier) {
    when (cdzAtomicMediaType(block)) {
        "image" -> CdzAtomicImage(block, modifier)
        "video" -> CdzRemoteVideo(block, modifier)
    }
}

@Composable
private fun CdzAtomicImage(block: JSONObject, modifier: Modifier = Modifier) {
    val url = cdzAbsoluteUrl(
        block.optString("relative_url")
            .ifBlank { block.optString("preview_url") }
    )
    CdzRemoteImage(
        url = url,
        description = block.optString("description").trim(),
        modifier = modifier,
    )
}
