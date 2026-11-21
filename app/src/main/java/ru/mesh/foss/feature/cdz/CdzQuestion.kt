package ru.mesh.foss.feature.cdz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (index in 0 until elements.length()) {
            val block = elements.optJSONObject(index) ?: continue
            CdzQuestionBlock(block)
        }
    }
}

/** Один блок условия. Отдельно нужен inline-заданиям с пропусками в тексте. */
@Composable
internal fun CdzQuestionBlock(
    block: JSONObject,
    modifier: Modifier = Modifier,
) {
    val type = block.optString("type")
    if (type == "content/atomic") {
        CdzAtomicMedia(block, modifier)
        return
    }

    val text = block.optString("text")
    if (text.isNotBlank()) {
        val spans = remember(block) {
            parseTextSpans(text, block.optJSONArray("text_styles"))
        }
        CdzRichText(
            text = text,
            spans = spans,
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    val content = block.optJSONArray("content") ?: return
    for (partIndex in 0 until content.length()) {
        val part = content.optJSONObject(partIndex) ?: continue
        if (part.optString("type") == "content/atomic" ||
            part.optString("atomic_type").isNotBlank()
        ) {
            CdzAtomicMedia(part, modifier)
            continue
        }
        if (part.optString("type").startsWith("content/text")) {
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

@Composable
private fun CdzAtomicMedia(block: JSONObject, modifier: Modifier = Modifier) {
    when (block.optString("atomic_type")) {
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
