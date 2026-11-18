@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import android.view.HapticFeedbackConstants
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Цель перехода из расписания в конкретное ДЗ. */
data class HomeworkTarget(
    val date: String,
    val subjectId: String = "",
    val subjectName: String = "",
    val text: String = ""
)

/** 5.0 → «5», 4.50 → «4.5», 4.37 → «4.37». */
fun formatMark(value: Double?): String {
    if (value == null || value.isNaN() || value.isInfinite()) return "—"
    val rounded = Math.round(value * 100.0) / 100.0
    if (rounded == Math.floor(rounded)) return rounded.toInt().toString()
    return String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
}

@Composable
fun markColor(value: Double?): Color {
    val light = MaterialTheme.colorScheme.surface.luminance() > .179f
    return when {
        value == null -> MaterialTheme.colorScheme.onSurfaceVariant
        value >= 4.5 -> if (light) Color(0xFF006342) else Color(0xFF64DFAD)
        value >= 3.5 -> if (light) Color(0xFF794800) else Color(0xFFFFCD72)
        else -> if (light) Color(0xFFA92938) else Color(0xFFFF939E)
    }
}

/** Оценка/средний балл в аккуратном бейдже справа. */
@Composable
fun GradeBadge(text: String, value: Double?, modifier: Modifier = Modifier) {
    val color = markColor(value)
    Box(
        modifier = modifier
            .widthIn(min = 48.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/** Переключение дня стрелками — одинаковое в расписании и ДЗ. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySelector(
    date: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDateSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = isoToMillisUtc(date))

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(millisToIsoUtc(millis))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().glassPane(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = {
            onPrev()
        }) {
            Text("‹", style = MaterialTheme.typography.headlineMedium)
        }
        // Средняя колонка занимает остаток строки: стрелки всегда остаются
        // в зоне видимости даже на длинной дате («понедельник, 17 ноября»).
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .clickable {
                    showPicker = true
                }
                .padding(horizontal = 4.dp)
        ) {
            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = date,
                transitionSpec = {
                    val forward = targetState >= initialState
                    val slide = motion.defaultSpatialSpec<IntOffset>()
                    val fade = motion.defaultEffectsSpec<Float>()
                    val enterX: (Int) -> Int = { w -> (if (forward) w else -w) / 3 }
                    val exitX: (Int) -> Int = { w -> (if (forward) -w else w) / 3 }
                    (
                        slideInHorizontally(animationSpec = slide, initialOffsetX = enterX) +
                            fadeIn(animationSpec = fade)
                    ) togetherWith (
                        slideOutHorizontally(animationSpec = slide, targetOffsetX = exitX) +
                            fadeOut(animationSpec = fade)
                    )
                }
            ) { shownDate ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        dayLabel(shownDate),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val relative = remember(shownDate) { relativeDayLabel(shownDate) }
                    if (relative != null) {
                        Text(relative, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        IconButton(onClick = {
            onNext()
        }) {
            Text("›", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/**
 * Горизонтальный свайп для листания дней: влево — следующий день, вправо —
 * предыдущий. Вешается на корневую колонку экрана: вертикальный скролл и
 * pull-to-refresh потребляют только свою ось, поэтому горизонтальный жест
 * доходит сюда. Смена дня анимируется тем же AnimatedContent, что и стрелки.
 */
fun Modifier.swipeDays(onPrev: () -> Unit, onNext: () -> Unit): Modifier = composed {
    val prev by rememberUpdatedState(onPrev)
    val next by rememberUpdatedState(onNext)
    pointerInput(Unit) {
        var total = 0f
        var fired = false
        val threshold = 48.dp.toPx()
        detectHorizontalDragGestures(
            onDragEnd = { total = 0f; fired = false },
            onDragCancel = { total = 0f; fired = false }
        ) { change, dragAmount ->
            change.consume()
            if (fired) return@detectHorizontalDragGestures
            total += dragAmount
            when {
                total <= -threshold -> { fired = true; next() }
                total >= threshold -> { fired = true; prev() }
            }
        }
    }
}

/** Дата в DaySelector: без года, если он текущий — строка короче и не обрезается. */
private fun dayLabel(iso: String): String {
    val full = MeshCore.dateRu(iso, weekday = true)
    val year = Calendar.getInstance().get(Calendar.YEAR)
    return if (iso.take(4).toIntOrNull() == year) full.removeSuffix(" $year") else full
}

private fun isoToMillisUtc(iso: String): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    val parsed = MeshCore.parseIso(iso)
    cal.set(Calendar.YEAR, parsed.get(Calendar.YEAR))
    cal.set(Calendar.MONTH, parsed.get(Calendar.MONTH))
    cal.set(Calendar.DAY_OF_MONTH, parsed.get(Calendar.DAY_OF_MONTH))
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun millisToIsoUtc(millis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    return String.format(
        Locale.US,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

/** Оценка с учётом коэффициента: при weight>1 — красная рамка, цифра — цветом самой оценки. */
@Composable
fun WeightedGradeBadge(markText: String, value: Double?, weight: Double) {
    val nonUnit = weight > 1.0
    Box(
        modifier = if (nonUnit) Modifier.border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp)) else Modifier,
        contentAlignment = Alignment.Center
    ) {
        Box {
            GradeBadge(markText, value)
            if (nonUnit) {
                Text(
                    text = formatWeight(weight),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = markColor(value),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp)
                )
            }
        }
    }
}

fun formatWeight(weight: Double): String =
    if (weight == Math.floor(weight)) weight.toInt().toString() else String.format(Locale.US, "%.1f", weight)

/** Подпись относительного дня: «Сегодня», «Завтра», «Послезавтра» и т.п. (±2 дня), иначе null. */
private fun relativeDayLabel(date: String): String? {
    val today = MeshCore.today()
    return when (date) {
        today -> "Сегодня"
        MeshCore.addDays(today, 1) -> "Завтра"
        MeshCore.addDays(today, -1) -> "Вчера"
        MeshCore.addDays(today, 2) -> "Послезавтра"
        MeshCore.addDays(today, -2) -> "Позавчера"
        else -> null
    }
}
