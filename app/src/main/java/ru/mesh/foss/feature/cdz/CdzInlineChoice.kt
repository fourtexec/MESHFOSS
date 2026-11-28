@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.Words

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
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

    // Не кэшируем: ответ может обновиться асинхронной подстановкой, пока
    // задание уже открыто. Выбранные варианты должны появиться сразу.
    val selected = inlineSelections(answer)
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
    val blockStarts = remember(text, choices) {
        inlineTextBlockStarts(text.length, choices.map { it.position })
    }
    val spans = remember(block, blockStarts) {
        parseTextSpans(text, block.optJSONArray("text_styles"), blockStarts)
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

    // Ширина пропуска зависит от выбранного слова, а не от фиксированных
    // 7em: короткие ответы больше не растягивают строку, а длинные не
    // превращаются в «…» — поле получает полную ширину варианта.
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodyLarge
    val placeholderData = choices.map { choice ->
        val selectedOption = choice.options.firstOrNull { it.id == selected[choice.positionId] }
        val label = optionLabel(selectedOption).ifBlank { "…" }
        InlinePlaceholderData(
            choice = choice,
            label = label,
            width = inlinePlaceholderWidth(
                label = label,
                style = labelStyle,
                measurer = measurer,
                density = density,
            ),
        )
    }
    val placeholders = placeholderData.mapIndexed { index, data ->
        CdzInlinePlaceholder(
            position = data.choice.position,
            id = "inline_${data.choice.positionId}_$index",
            alternateText = data.label,
        )
    }
    val inlineContent = placeholderData.mapIndexed { index, data ->
        placeholders[index].id to InlineTextContent(
            placeholder = Placeholder(
                width = data.width,
                // Высота строки bodyLarge: плашка не раздвигает строку и не
                // сдвигает соседние буквы вниз. TextCenter держит её на
                // текстовой оси, а не на всей высоте линии.
                height = 1.5.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            CdzInlineChoiceDropdown(
                choice = data.choice,
                selectedId = selected[data.choice.positionId],
                onSelected = { optionId -> choose(data.choice, optionId) },
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

private data class InlinePlaceholderData(
    val choice: CdzInlineChoice,
    val label: String,
    val width: TextUnit,
)

/**
 * Ширина inline-поля по реальному тексту варианта.
 *
 * `Placeholder.width` измеряется в единицах текста, поэтому переводим
 * измеренные пиксели в `sp` через текущую density/fontScale. В ширину входят
 * горизонтальные отступы и стрелка; минимум не даёт пустому полю стать
 * микроскопическим, а верхнего предела нет — иначе длинный вариант
 * обрезался бы многоточием.
 */
internal fun inlinePlaceholderWidth(
    label: String,
    style: TextStyle,
    measurer: TextMeasurer,
    density: Density,
): TextUnit {
    val minWidth = with(density) { 36.dp.toPx() }
    val chromeWidth = with(density) { 30.dp.toPx() }
    val textWidth = if (label.isBlank()) {
        0f
    } else {
        measurer.measure(
            text = AnnotatedString(label),
            style = style,
            maxLines = 1,
            softWrap = false,
        ).size.width.toFloat()
    }
    return with(density) {
        (textWidth + chromeWidth).coerceAtLeast(minWidth).toSp()
    }
}

@Composable
private fun CdzInlineChoiceDropdown(
    choice: CdzInlineChoice,
    selectedId: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember(choice.positionId) { mutableStateOf(false) }
    val selected = choice.options.firstOrNull { it.id == selectedId }
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(
        targetValue = if (selected != null) 16.dp else 10.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "inlineChoiceCorner",
    )
    val shape = RoundedCornerShape(corner)
    val container by animateColorAsState(
        targetValue = if (selected != null) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "inlineChoiceContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected != null) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "inlineChoiceContent",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected != null) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "inlineChoiceBorder",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Не используем clickable-Surface: M3 добавляет ему минимальный
        // touch-target 48dp, из-за чего компактное inline-поле снова
        // выходило бы за высоту строки. Клик вешаем на обычный Surface.
        Surface(
            shape = shape,
            color = container,
            contentColor = contentColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CdzRichText(
                    text = optionLabel(selected).ifBlank { "…" },
                    spans = selected?.textSpans.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
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
