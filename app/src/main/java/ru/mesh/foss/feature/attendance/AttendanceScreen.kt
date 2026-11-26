@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import ru.mesh.foss.ui.GlassCard
import ru.mesh.foss.ui.LocalLiquidBottomPadding
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.core.MeshCore
import ru.mesh.foss.R
import ru.mesh.foss.core.UiLanguage
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.model.AttendanceDay
import ru.mesh.foss.data.model.VisitDay
import ru.mesh.foss.data.model.VisitEntry

/**
 * Посещаемость МЭШ: пропуски по урокам (`family/web/v1/attendance`) и
 * фактические проходы (`pass/entrances/v1/visit_durations`) на одной неделе.
 */
@Composable
fun AttendanceScreen(
    client: MeshClient,
    studentId: String?,
    personId: String?,
    modifier: Modifier = Modifier,
) {
    val vm: AttendanceViewModel = viewModel(
        key = "attendance:${studentId.orEmpty()}:${personId.orEmpty()}",
        factory = AttendanceViewModel.factory(client, studentId, personId),
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        WeekHeader(
            state = state,
            onPrevious = vm::previousWeek,
            onNext = vm::nextWeek,
            onRefresh = vm::refresh,
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            state.error != null -> ErrorCard(state.error!!, vm::refresh)
            else -> AttendanceBody(state.snapshot)
        }
    }
}

@Composable
private fun WeekHeader(
    state: AttendanceUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Text("‹", style = MaterialTheme.typography.headlineMedium)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Words.Attendance.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${MeshCore.dateShort(state.from)} — ${MeshCore.dateShort(state.to)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onNext) {
                Text("›", style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                if (state.refreshing) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        painterResource(R.drawable.ic_refresh),
                        contentDescription = Words.Refresh.text,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendanceBody(snapshot: AttendanceSnapshot?) {
    if (snapshot == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(Words.NoData.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val absences = remember(snapshot) { snapshot.absences.attendance.associateBy { it.date } }
    val visits = remember(snapshot) { snapshot.visits.payload.associateBy { it.date } }
    val dates = remember(absences, visits) {
        (absences.keys + visits.keys).sortedDescending()
    }
    val absenceCount = snapshot.absences.attendance.sumOf { it.lessons.size }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + LocalLiquidBottomPadding.current),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    label = Words.Absences.text,
                    value = absenceCount.toString(),
                    accent = absenceCount > 0,
                    modifier = Modifier.weight(1f),
                )
                SummaryCard(
                    label = Words.Visits.text,
                    value = snapshot.visits.payload.sumOf { it.visits.size }.toString(),
                    accent = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (dates.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        Words.NoAttendanceForWeek.text,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(dates, key = { it }) { date ->
                DayCard(
                    date = date,
                    absence = absences[date],
                    visit = visits[date],
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, accent: Boolean, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DayCard(date: String, absence: AttendanceDay?, visit: VisitDay?) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                dateLabel(date),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            absence?.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Spacer(Modifier.height(2.dp))
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            absence?.lessons?.forEach { lesson ->
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        "•",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            lesson.subjectName.orEmpty().ifBlank { Words.Lesson.text },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val reason = lesson.description
                            ?: lesson.healthStatus
                            ?: lesson.reasonId?.let { "${Words.Reason.text}: $it" }
                        if (!reason.isNullOrBlank()) {
                            Text(
                                reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            visit?.visits?.forEach { entry ->
                Spacer(Modifier.height(8.dp))
                VisitRow(entry)
            }
            if ((absence?.lessons?.isEmpty() != false) && visit?.visits.isNullOrEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    Words.NoDataForThisDate.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VisitRow(entry: VisitEntry) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.organizationShortName ?: entry.kindName ?: Words.School.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${Words.Entry.text}: ${entry.enteredAt ?: "—"}  ·  " +
                    "${Words.Exit.text}: ${entry.leftAt ?: if (entry.isIncomplete == true) Words.StillInside.text else "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entry.duration?.takeIf { it != "-" }?.let { duration ->
            Text(
                duration,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) { Text(Words.Retry.text) }
        }
    }
}

private fun dateLabel(iso: String): String {
    if (UiLanguage.code == "ru") return MeshCore.dateRu(iso, weekday = true)
    val calendar = MeshCore.parseIso(iso)
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(UiLanguage.locale, "EEEE d MMMM")
    return SimpleDateFormat(pattern, UiLanguage.locale).format(calendar.time)
}
