package ru.mesh.foss.feature.cdz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Условие задания ЦДЗ: текстовые блоки и вложенные картинки.
 *
 * В API текст и изображения приходят отдельными `content/text` и
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
            val type = block.optString("type")
            val text = block.optString("text").trim()
            if (type == "content/atomic") {
                CdzAtomicImage(block)
                continue
            }
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            val content = block.optJSONArray("content") ?: continue
            for (partIndex in 0 until content.length()) {
                val part = content.optJSONObject(partIndex) ?: continue
                when {
                    part.optString("atomic_type") == "image" -> CdzAtomicImage(part)
                    part.optString("type") == "content/text" -> {
                        val partText = part.optString("text").trim()
                        if (partText.isNotBlank()) {
                            Text(
                                text = partText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CdzAtomicImage(block: JSONObject) {
    if (block.optString("atomic_type") != "image") return
    val url = block.optString("relative_url")
        .ifBlank { block.optString("preview_url") }
        .trim()
    CdzRemoteImage(
        url = url,
        description = block.optString("description").trim(),
    )
}
