@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.Words

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.json.JSONArray
import org.json.JSONObject

/**
 * `answer/inline/choice/single`: в тексте задания есть пропуски, а варианты
 * выбираются из выпадающего списка прямо на месте пропуска.
 *
 * Позиции приходят в `answer.text_position` и указывают на символ в тексте
 * блока с тем же `text_id`. В отличие от обычного вопроса, выбранные ответы
 * живут не в отдельной карточке, а внутри [CdzRichText] как inline-контент.
 */
@Composable
fun CdzInlineChoiceContent(
    task: CdzTask,
    answer: JSONObject,
    onChanged: (JSONObject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choices = remember(task.answer) { parseInlineChoices(task.answer) }
    if (choices.isEmpty()) {
        Text(
            text = Words.CdzUnsupportedType(task.type),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    val selected = remember(answer) { inlineSelections(answer) }
    val elements = task.question
    val targetIndex = findInlineTextIndex(elements, choices)
    // В одном задании может быть несколько текстовых блоков со своими
    // пропусками: группируем по `text_id`, чтобы не подставлять позиции
    // одного текста в другой.
    val choicesByText = remember(choices) { choices.groupBy { it.textId } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (index in 0 until elements.length()) {
            val block = elements.optJSONObject(index) ?: continue
            val blockChoices = choicesByText[block.optString("text_id")].orEmpty()
            when {
                blockChoices.isNotEmpty() -> {
                    CdzInlineChoiceText(
                        type = task.type,
                        block = block,
                        choices = blockChoices,
                        allChoices = choices,
                        selected = selected,
                        onChanged = onChanged,
                    )
                }
                index == targetIndex -> {
                    // Резервный путь для нестандартного текста без совпадения
                    // `text_id`: показываем пропуски в первом текстовом блоке.
                    CdzInlineChoiceText(
                        type = task.type,
                        block = block,
                        choices = choices,
                        allChoices = choices,
                        selected = selected,
                        onChanged = onChanged,
                    )
                }
                else -> CdzQuestionBlock(block)
            }
        }
    }
}

private fun findInlineTextIndex(elements: JSONArray, choices: List<CdzInlineChoice>): Int {
    val textId = choices.firstOrNull()?.textId.orEmpty()
    if (textId.isNotBlank()) {
        for (index in 0 until elements.length()) {
            val block = elements.optJSONObject(index) ?: continue
            if (block.optString("text_id") == textId) return index
        }
    }
    // На случай нестандартного текста: показываем пропуски в первом текстовом
    // блоке, чтобы задание оставалось доступным, а не превращалось в пустоту.
    for (index in 0 until elements.length()) {
        val block = elements.optJSONObject(index) ?: continue
        if (block.optString("text").isNotBlank()) return index
    }
    return -1
}

@Composable
private fun CdzInlineChoiceText(
    type: String,
    block: JSONObject,
    choices: List<CdzInlineChoice>,
    allChoices: List<CdzInlineChoice>,
    selected: Map<String, String>,
    onChanged: (JSONObject) -> Unit,
) {
    val text = block.optString("text")
    val spans = remember(block) {
        parseTextSpans(text, block.optJSONArray("text_styles"))
    }

    fun choose(choice: CdzInlineChoice, optionId: String) {
        val next = LinkedHashMap(selected)
        next[choice.positionId] = optionId
        val answer = allChoices.mapNotNull { item ->
            val id = next[item.positionId] ?: return@mapNotNull null
            CdzInlineSelection(
                textId = item.textId,
                positionId = item.positionId,
                optionId = id,
            )
        }
        onChanged(inlineAnswer(type, answer))
    }

    val placeholders = choices.mapIndexed { index, choice ->
        CdzInlinePlaceholder(
            position = choice.position,
            id = "inline_${choice.positionId}_$index",
            alternateText = optionLabel(
                choice.options.firstOrNull { it.id == selected[choice.positionId] }
            ).ifBlank { "…" },
        )
    }
    val inlineContent = choices.mapIndexed { index, choice ->
        placeholders[index].id to InlineTextContent(
            placeholder = Placeholder(
                width = 7.0.em,
                height = 2.1.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            CdzInlineChoiceDropdown(
                choice = choice,
                selectedId = selected[choice.positionId],
                onSelected = { optionId -> choose(choice, optionId) },
            )
        }
    }.toMap()

    CdzRichText(
        text = text,
        spans = spans,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        inlineContent = inlineContent,
        placeholders = placeholders,
    )
}

@Composable
private fun CdzInlineChoiceDropdown(
    choice: CdzInlineChoice,
    selectedId: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember(choice.positionId) { mutableStateOf(false) }
    val selected = choice.options.firstOrNull { it.id == selectedId }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.small,
            color = if (selected != null) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (selected != null) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (selected != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CdzRichText(
                    text = optionLabel(selected).ifBlank { Words.CdzChoose.text },
                    spans = selected?.textSpans.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("▾", style = MaterialTheme.typography.labelSmall)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            choice.options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        CdzRichText(
                            text = optionLabel(option),
                            spans = option.textSpans,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option.id)
                    },
                )
            }
        }
    }
}

private fun optionLabel(option: CdzOption?): String {
    if (option == null) return ""
    return option.text.ifBlank { option.description }
}
