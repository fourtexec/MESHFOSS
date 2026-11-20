@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.homeworks

import ru.mesh.foss.app.CdzActivity

import ru.mesh.foss.core.*
import ru.mesh.foss.data.*
import ru.mesh.foss.ui.*
import ru.mesh.foss.R

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import org.json.JSONObject
import java.util.Locale

@Composable
fun HomeworksScreen(
    store: SessionStore,
    client: MeshClient,
    studentId: String?,
    personId: String? = null,
    target: HomeworkTarget? = null,
    onTargetConsumed: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    var loading by remember { mutableStateOf(studentId != null) }
    // Идёт обновление свайпом «потянуть» — список не прячем, показываем индикатор в шапке.
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var homeworks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    // По умолчанию — ДЗ на завтра; в пятницу/субботу/воскресенье — на понедельник.
    var selectedDate by remember { mutableStateOf(MeshCore.defaultHomeworkDate()) }
    // due — домашнее задание к уроку выбранного дня (как в дневнике);
    // assigned — по дате, когда его задали.
    var mode by remember { mutableStateOf("due") }
    var focusIndex by remember { mutableStateOf<Int?>(null) }
    var launchingMaterialId by remember { mutableStateOf<String?>(null) }
    // Локальные (оптимистичные) отметки «выполнено»: entryStudentId → сделано.
    var doneOverrides by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var savingDone by remember { mutableStateOf<Set<String>>(emptySet()) }
    // ЦДЗ: homeworkEntryId → { uuid: карточка + result } (docs/mesh-cdz-api.md §5–6).
    var cdzInfo by remember { mutableStateOf<Map<String, JSONObject>>(emptyMap()) }
    var cdzLoading by remember { mutableStateOf<Set<String>>(emptySet()) }
    // ДЗ, чьё ЦДЗ открыли последним: при возврате в приложение перечитываем статус.
    var cdzReturnEntry by remember { mutableStateOf<String?>(null) }
    // Подтверждение запуска материала с таймером («Облако Знаний»).
    var confirmLaunch by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    val cdzLimiter = remember { Semaphore(3) }
    val prefs = remember(context) { context.getSharedPreferences("mesh_settings", Context.MODE_PRIVATE) }
    val from = MeshCore.addDays(selectedDate, -45)
    val to = MeshCore.addDays(selectedDate, 45)

    fun load() {
        if (studentId.isNullOrBlank()) {
            loading = false
            refreshing = false
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                homeworks = withContext(Dispatchers.IO) { client.getHomeworks(studentId, from, to) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = MeshCore.describeError(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    /** Статус ЦДЗ одного ДЗ: расширенные карточки и результаты LRS. */
    fun loadCdz(homework: JSONObject, force: Boolean = false) {
        val entryId = homework.optString("homeworkEntryId")
        if (entryId.isBlank() || personId.isNullOrBlank()) return
        if (!hasCdz(homework)) return
        if (!force && (cdzInfo.containsKey(entryId) || entryId in cdzLoading)) return
        cdzLoading = cdzLoading + entryId
        scope.launch {
            try {
                val info = cdzLimiter.withPermit {
                    withContext(Dispatchers.IO) { client.getCdzStatus(homework, personId) }
                }
                cdzInfo = cdzInfo + (entryId to info)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Статус — дополнительная информация: карточка ДЗ остаётся рабочей.
                if (!cdzInfo.containsKey(entryId)) cdzInfo = cdzInfo + (entryId to JSONObject())
            } finally {
                cdzLoading = cdzLoading - entryId
            }
        }
    }

    /**
     * Запуск материала. ЦДЗ открываем только через одноразовую ссылку
     * `partners/v1/homeworks/launch` (§7): прямые ссылки без контекста ДЗ
     * не засчитываются. Ссылку не кэшируем — для каждого открытия новая.
     */
    fun launchMaterial(homeworkEntryId: String, material: JSONObject) {
        val materialId = material.optString("uuid")
            .ifBlank { material.optString("materialId") }
            .trim()
        val directUrl = material.optString("url").trim()
        val viaLaunch = homeworkEntryId.isNotBlank() && materialId.isNotBlank() &&
            (material.optBoolean("isCdz") || directUrl.isBlank())
        if (!viaLaunch) {
            openUrl(uriHandler, directUrl, context)
            return
        }
        launchingMaterialId = materialId
        scope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    client.launchHomeworkMaterial(homeworkEntryId, materialId)
                }
                cdzReturnEntry = homeworkEntryId
                if (prefs.getBoolean("cdz_in_app", true)) {
                    context.startActivity(CdzActivity.intent(context, url, material.optString("title")))
                } else {
                    openUrl(uriHandler, url, context)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, MeshCore.describeError(e), Toast.LENGTH_LONG).show()
            } finally {
                launchingMaterialId = null
            }
        }
    }

    fun openMaterial(homeworkEntryId: String, material: JSONObject) {
        // «Облако Знаний» запускает таймер сразу при открытии — спрашиваем (§8, §12.6).
        if (material.optBoolean("isCdz") && MeshCdz.startsTimer(material.optString("type"))) {
            confirmLaunch = homeworkEntryId to material
        } else {
            launchMaterial(homeworkEntryId, material)
        }
    }

    // Отметка/снятие отметки «выполнено». Сразу меняем UI, затем подтверждаем у МЭШ;
    // при ошибке откатываем и показываем сообщение.
    fun toggleDone(homework: JSONObject, target: Boolean) {
        val entryId = homework.optString("entryStudentId").ifBlank { homework.optString("id") }
        if (entryId.isBlank()) return
        val type = homework.optString("entryType").ifBlank { "OO" }
        val previous = doneOverrides[entryId] ?: homework.optBoolean("isDone")
        if (previous == target) return
        doneOverrides = doneOverrides + (entryId to target)
        savingDone = savingDone + entryId
        scope.launch {
            try {
                withContext(Dispatchers.IO) { client.setHomeworkDone(entryId, type, target) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                doneOverrides = doneOverrides + (entryId to previous)
                Toast.makeText(context, MeshCore.describeError(e), Toast.LENGTH_LONG).show()
            } finally {
                savingDone = savingDone - entryId
            }
        }
    }

    LaunchedEffect(studentId, selectedDate) { load() }

    // Возврат из окна ЦДЗ или браузера: результат мог появиться в LRS.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val entry = cdzReturnEntry
                if (entry != null) {
                    cdzReturnEntry = null
                    homeworks.firstOrNull { it.optString("homeworkEntryId") == entry }
                        ?.let { loadCdz(it, force = true) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    confirmLaunch?.let { (entryId, material) ->
        AlertDialog(
            onDismissRequest = { confirmLaunch = null },
            title = { Text(Words.CdzTimerTitle.text) },
            text = {
                Column {
                    Text(
                        material.optString("title"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(Words.CdzTimerWarning.text, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirmLaunch = null
                    launchMaterial(entryId, material)
                }) { Text(Words.CdzBegin.text) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLaunch = null }) { Text(Words.Cancel.text) }
            }
        )
    }

    LaunchedEffect(target) {
        val destination = target ?: return@LaunchedEffect
        if (mode != "due") mode = "due"
        if (selectedDate != destination.date) selectedDate = destination.date
    }

    val filtered = remember(homeworks, mode, selectedDate) {
        homeworks
            .filter { h ->
                val day = if (mode == "assigned") h.optString("assignedOn") else h.optString("dueDate")
                day == selectedDate
            }
            .sortedWith(compareBy({ it.optString("subjectName") }, { it.optString("dueDate") }))
    }

    LaunchedEffect(target, selectedDate, loading, filtered) {
        val destination = target ?: return@LaunchedEffect
        if (loading || selectedDate != destination.date) return@LaunchedEffect
        val index = findHomeworkIndex(filtered, destination)
        if (index >= 0) {
            listState.animateScrollToItem(index)
            focusIndex = index
            delay(1_000)
            focusIndex = null
        }
        onTargetConsumed()
    }

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .swipeDays(
                    onPrev = { selectedDate = MeshCore.addDays(selectedDate, -1) },
                    onNext = { selectedDate = MeshCore.addDays(selectedDate, 1) }
                )
        ) {
            DaySelector(
            date = selectedDate,
            onPrev = { selectedDate = MeshCore.addDays(selectedDate, -1) },
            onNext = { selectedDate = MeshCore.addDays(selectedDate, 1) },
            onDateSelected = { selectedDate = it }
        )
        // M3E: connected-группа переключателей вместо SegmentedButton.
        ButtonGroup(
            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
            modifier = Modifier.fillMaxWidth()
        ) {
            toggleableItem(
                checked = mode == "due",
                label = Words.DueOnDate.text,
                onCheckedChange = { mode = "due" },
                weight = 1f
            )
            toggleableItem(
                checked = mode == "assigned",
                label = Words.Assigned.text,
                onCheckedChange = { mode = "assigned" },
                weight = 1f
            )
        }
        Spacer(Modifier.height(4.dp))

        when {
            studentId.isNullOrBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(Words.LoadingStudentProfile.text)
                    }
                }
            }
            loading && !refreshing -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            error != null -> {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { load() }) { Text(Words.Retry.text) }
                }
            }
            filtered.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (mode == "due") Words.NoHomeworkDueToday.text else Words.NoHomeworkAssignedOnThisDay.text)
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { refreshing = true; load() },
                    modifier = Modifier.weight(1f)
                ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(filtered) { index, h ->
                        val entryId = h.optString("entryStudentId").ifBlank { h.optString("id") }
                        val homeworkEntryId = h.optString("homeworkEntryId")
                        LaunchedEffect(homeworkEntryId, personId) { loadCdz(h) }
                        HomeworkCard(
                            h = h,
                            uriHandler = uriHandler,
                            context = context,
                            highlighted = focusIndex == index,
                            dimmed = focusIndex != null && focusIndex != index,
                            launchingMaterialId = launchingMaterialId,
                            onOpenMaterial = ::openMaterial,
                            isDone = doneOverrides[entryId] ?: h.optBoolean("isDone"),
                            savingDone = entryId.isNotBlank() && entryId in savingDone,
                            onToggleDone = { target -> toggleDone(h, target) },
                            cdz = cdzInfo[homeworkEntryId],
                            cdzLoading = homeworkEntryId in cdzLoading,
                            onOpenResult = { url -> openUrl(uriHandler, url, context) }
                        )
                    }
                }
                }
            }
        }
    }
}
}

@Composable
private fun HomeworkCard(
    h: JSONObject,
    uriHandler: UriHandler,
    context: Context,
    highlighted: Boolean,
    dimmed: Boolean,
    launchingMaterialId: String?,
    onOpenMaterial: (String, JSONObject) -> Unit,
    isDone: Boolean,
    savingDone: Boolean,
    onToggleDone: (Boolean) -> Unit,
    cdz: JSONObject? = null,
    cdzLoading: Boolean = false,
    onOpenResult: (String) -> Unit = {}
) {
    val description = h.optString("description").trim().let { if (it == ".") "" else it }
    val clipboardManager = LocalClipboardManager.current
    val assigned = h.optString("assignedOn").takeIf { it.isNotBlank() && it != "null" }
    val due = h.optString("dueDate").takeIf { it.isNotBlank() && it != "null" }
    val materials = h.optJSONArray("materials")
    val attachments = h.optJSONArray("attachments")
    // Для запуска и LRS нужен именно homework_entry_id, а не homework_entry_student_id (§12.1).
    val homeworkEntryId = h.optString("homeworkEntryId")
    val cardAlpha = if (dimmed) 0.45f else 1f
    // M3E-морфинг: у выполненного ДЗ углы пружинисто дорастают от medium (16dp,
    // как у обычной карточки) до 30dp — предел, при котором текст не обрезается.
    val cornerRadius by animateDpAsState(
        targetValue = if (isDone) 30.dp else 16.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "homeworkDoneShape"
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(cardAlpha)
            // У подсвеченного ДЗ своя акцентная рамка — стеклянную кромку не рисуем.
            .then(if (highlighted) Modifier else Modifier.glass(RoundedCornerShape(cornerRadius))),
        shape = RoundedCornerShape(cornerRadius),
        tint = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (highlighted) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isDone,
                    onCheckedChange = { onToggleDone(it) },
                    enabled = !savingDone
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    h.optString("subjectName").ifBlank { Words.Assignment.text },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (savingDone) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                }
                if (due != null) {
                    Text(
                        Words.Due(MeshCore.dateShort(due)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.padding(4.dp)) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(description))
                        Toast.makeText(context, Words.AssignmentCopied.text, Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = Words.Copy.text,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val dates = buildString {
                if (assigned != null) append(Words.AssignedLabel(MeshCore.dateShort(assigned)))
                if (due != null) {
                    if (isNotEmpty()) append(" · ")
                    append(Words.DueLabel(MeshCore.dateShort(due)))
                }
            }
            if (dates.isNotBlank()) {
                Text(
                    dates,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (materials != null && materials.length() > 0) {
                val attachmentTypes = setOf("attachment", "attachments", "file", "files")
                val visible = (0 until materials.length())
                    .mapNotNull { materials.optJSONObject(it) }
                    .filter {
                        it.optString("type").lowercase(Locale.US) !in attachmentTypes &&
                            (it.optString("title").isNotBlank() || it.optString("typeName").isNotBlank())
                    }
                if (visible.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val cdzItems = visible.filter { it.optBoolean("isCdz") }
                    val doneCount = cdzItems.count { item ->
                        cdz?.optJSONObject(item.optString("uuid"))?.optJSONObject("result")?.optBoolean("done") == true
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Words.MaterialsAndDigitalHomework.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (cdzItems.isNotEmpty() && cdz != null && cdz.length() > 0) {
                            Text(
                                Words.CdzProgress(doneCount, cdzItems.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (doneCount == cdzItems.size) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    visible.forEach { material ->
                        val uuid = material.optString("uuid")
                        CdzMaterialRow(
                            material = material,
                            extended = cdz?.optJSONObject(uuid),
                            statusLoading = cdzLoading && material.optBoolean("isCdz"),
                            canLaunch = (homeworkEntryId.isNotBlank() && uuid.isNotBlank()) ||
                                material.optString("url").isNotBlank(),
                            opening = launchingMaterialId != null && launchingMaterialId == uuid,
                            onOpen = { onOpenMaterial(homeworkEntryId, material) },
                            onOpenResult = onOpenResult
                        )
                    }
                }
            }

            if (attachments != null && attachments.length() > 0) {
                Spacer(Modifier.height(8.dp))
                Text(Words.Files.text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                for (i in 0 until attachments.length()) {
                    val attachment = attachments.optJSONObject(i) ?: continue
                    val name = attachment.optString("name").ifBlank { Words.File.text }
                    val url = attachment.optString("url")
                    Text(
                        "📎 $name",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (url.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (url.isNotBlank()) {
                            Modifier.clickable { openUrl(uriHandler, url, context) }
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

private fun findHomeworkIndex(items: List<JSONObject>, target: HomeworkTarget): Int {
    fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    val targetText = normalize(target.text)
    val targetSubjectName = normalize(target.subjectName)
    val targetSubjectId = target.subjectId.trim()
    var bestIndex = -1
    var bestScore = 0

    items.forEachIndexed { index, homework ->
        val subjectId = homework.optString("subjectId")
        val subjectName = normalize(homework.optString("subjectName"))
        val text = normalize(homework.optString("description"))
        val sameId = targetSubjectId.isNotBlank() && subjectId == targetSubjectId
        val sameName = targetSubjectName.isNotBlank() &&
            (subjectName == targetSubjectName ||
                subjectName.contains(targetSubjectName) ||
                targetSubjectName.contains(subjectName))
        val sameText = targetText.isNotBlank() &&
            (text.contains(targetText) || targetText.contains(text))
        if (!sameId && !sameName && !sameText) return@forEachIndexed

        val score = when {
            sameText && (sameId || sameName) -> 4
            sameText -> 3
            sameId -> 2
            sameName -> 1
            else -> 0
        }
        if (score > bestScore) {
            bestIndex = index
            bestScore = score
        }
    }
    return bestIndex
}

private fun openUrl(uriHandler: UriHandler, url: String, context: Context) {
    if (url.isBlank()) return
    runCatching { uriHandler.openUri(url) }.onFailure {
        Toast.makeText(context, Words.CouldNotOpenLink.text, Toast.LENGTH_SHORT).show()
    }
}

private fun hasCdz(homework: JSONObject): Boolean {
    val materials = homework.optJSONArray("materials") ?: return false
    return (0 until materials.length()).any { materials.optJSONObject(it)?.optBoolean("isCdz") == true }
}

/**
 * Строка ЦДЗ в карточке ДЗ: название, тип и издатель, статус выполнения из LRS,
 * баллы и кнопки «Пройти» / «Результат» — как на карточке веб-дневника.
 */
@Composable
private fun CdzMaterialRow(
    material: JSONObject,
    extended: JSONObject?,
    statusLoading: Boolean,
    canLaunch: Boolean,
    opening: Boolean,
    onOpen: () -> Unit,
    onOpenResult: (String) -> Unit
) {
    val isCdz = material.optBoolean("isCdz")
    val title = material.optString("title").ifBlank {
        extended?.optString("title")?.takeIf { it.isNotBlank() }
            ?: material.optString("typeName").ifBlank { Words.Material.text }
    }
    val typeLabel = extended?.optString("typeName")?.takeIf { it.isNotBlank() }
        ?: material.optString("typeName").trim()
    val partner = extended?.optString("partnerName").orEmpty().trim()
    val description = extended?.optString("description").orEmpty().trim()
    val result = extended?.optJSONObject("result")
    val done = result?.optBoolean("done") == true
    val resultUrl = result?.optString("resultUrl").orEmpty()
    val action = material.optString("actionName").trim().ifBlank { Words.CdzStart.text }
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (done) scheme.tertiaryContainer.copy(alpha = 0.35f) else scheme.surfaceContainerHigh.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface
            )
            val meta = listOf(typeLabel, partner).filter { it.isNotBlank() }.distinct().joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            if (description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isCdz) {
                Spacer(Modifier.height(4.dp))
                val status = when {
                    result != null -> MeshCdz.statusText(result)
                    statusLoading -> Words.CdzStatusLoading.text
                    extended?.has("resultError") == true -> Words.CdzStatusFailed.text
                    else -> ""
                }
                if (status.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (statusLoading && result == null) {
                            LoadingIndicator(modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            (if (done) "✓ " else "") + status,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (done) scheme.tertiary else scheme.onSurfaceVariant,
                            fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                val score = MeshCdz.scoreText(result)
                if (score.isNotBlank()) {
                    Text(score, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                }
            }
            if (canLaunch || resultUrl.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (canLaunch) {
                        FilledTonalButton(
                            onClick = onOpen,
                            enabled = !opening,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (opening) {
                                LoadingIndicator(modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(Words.Opening.text)
                            } else {
                                Text(action)
                            }
                        }
                    }
                    if (resultUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onOpenResult(resultUrl) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text(Words.CdzResult.text) }
                    }
                }
            }
        }
    }
}
