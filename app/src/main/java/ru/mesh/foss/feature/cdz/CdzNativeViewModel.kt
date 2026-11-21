package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.MeshLog
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.AccountRegistry
import ru.mesh.foss.data.CdzNativeClient

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class CdzPhase { Loading, Choice, Game }

data class CdzResult(val score: Int, val max: Int)

/** Завершённая попытка для списка истории. */
data class CdzHistoryEntry(
    val attemptId: Long,
    val number: Int,
    val startedAt: String,
    val completedAt: String,
    val score: Double,
    val maxScore: Double,
)

/**
 * Состояние нативного прохождения ЦДЗ.
 *
 * Сеть, черновики и навигация по заданиям живут здесь; Compose-экран только
 * рисует состояние и вызывает команды. Автосоздания попытки нет — старт и
 * продолжение всегда являются явным действием пользователя.
 */
class CdzNativeViewModel(
    app: Application,
    private val startUrl: String,
) : AndroidViewModel(app) {

    var phase by mutableStateOf(CdzPhase.Loading)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    var attemptCount by mutableIntStateOf(0)
        private set
    var lastAttemptId by mutableStateOf<String?>(null)
        private set

    var historyVisible by mutableStateOf(false)
        private set
    var historyLoading by mutableStateOf(false)
        private set
    var historyAttempts by mutableStateOf<List<CdzHistoryEntry>>(emptyList())
        private set
    var historyError by mutableStateOf<String?>(null)
        private set
    val challengeId: String get() = challenge

    var tasks by mutableStateOf<List<CdzTask>>(emptyList())
        private set
    var attemptId by mutableLongStateOf(0L)
        private set
    var current by mutableIntStateOf(0)
        private set

    var saved by mutableStateOf<Set<Long>>(emptySet())
        private set
    var draft by mutableStateOf<Map<Long, JSONObject>>(emptyMap())
        private set

    var result by mutableStateOf<CdzResult?>(null)
        private set
    private var client: CdzNativeClient? = null
    private var challenge = ""
    private var attemptStartedAt = ""
    private val prefs = app.getSharedPreferences("cdz_drafts", Context.MODE_PRIVATE)

    val total: Int get() = tasks.size
    val currentTask: CdzTask? get() = tasks.getOrNull(current)
    val currentAnswer: JSONObject get() = currentTask?.let { answerOf(it.id) } ?: JSONObject()
    val solvedCount: Int get() = tasks.count { isDone(it.id) }
    val hasUnsaved: Boolean get() = tasks.any { isAnswered(it.id) && it.id !in saved }
    init {
        openChallenge()
    }

    fun answerOf(taskId: Long): JSONObject = draft[taskId] ?: JSONObject()

    fun isAnswered(taskId: Long): Boolean = hasAnswer(answerOf(taskId))

    fun isSaved(taskId: Long): Boolean = taskId in saved

    fun isDone(taskId: Long): Boolean = isSaved(taskId) && isAnswered(taskId)

    fun openChallenge() {
        phase = CdzPhase.Loading
        error = null
        viewModelScope.launch {
            busy = true
            try {
                val api = createClient()
                val derived = api.derivePageUrl(startUrl)
                    ?: startUrl.takeIf { it.contains("/exam/challenge/") }
                if (derived.isNullOrBlank()) {
                    error = Words.CdzNativeError.text
                    return@launch
                }
                val id = Regex("/exam/challenge/(\\d+)")
                    .find(derived)?.groupValues?.getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
                if (id == null) {
                    MeshLog.log("CDZ", "native: в launch URL нет challenge")
                    error = Words.CdzNativeError.text
                    return@launch
                }
                challenge = id
                val active = CdzNativeClient(
                    pageUrl = derived,
                    token = api.currentToken(),
                    profileId = api.currentProfileId(),
                    userId = api.currentUserId(),
                    personId = api.currentPersonId(),
                    cookies = api.currentCookies(),
                )
                client = active
                val refreshed = withContext(Dispatchers.IO) { active.refreshSession() }
                MeshLog.log("CDZ", "native: refresh=$refreshed")
                if (!refreshed) {
                    error = Words.CdzNativeError.text
                    return@launch
                }
                loadAttempts()
            } catch (failure: Exception) {
                MeshLog.error("CDZ", failure)
                error = failure.message ?: failure.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    private suspend fun loadAttempts() {
        val api = client ?: return
        val info = withContext(Dispatchers.IO) { api.checkAttempts(challenge) }
        // API иногда обновляет счётчик с задержкой: локальная история уже
        // знает о только что завершённых попытках, поэтому берём максимум.
        attemptCount = maxOf(info.optInt("number_of_attempt", 0), readLocalAttempts().size)
        lastAttemptId = info.optString("last_attempt_id")
            .takeIf { it.isNotBlank() && it != "null" }
        phase = CdzPhase.Choice
        MeshLog.log("CDZ", "попытки: count=$attemptCount last=$lastAttemptId")
    }

    /** Старт и продолжение — один и тот же серверный вызов, но только по кнопке. */
    fun beginAttempt() {
        if (busy) return
        val api = client ?: return
        error = null
        viewModelScope.launch {
            busy = true
            try {
                val attempt = withContext(Dispatchers.IO) { api.startAttempt(challenge) }
                attemptId = attempt.optLong("challenge_attempt_id")
                attemptStartedAt = attempt.optString("challenge_attempt_started_at")
                val parsed = parseTasks(attempt)
                if (parsed.isEmpty()) throw IllegalStateException("В попытке нет заданий")
                tasks = parsed
                current = 0
                restoreDraft()
                phase = CdzPhase.Game
                MeshLog.log(
                    "CDZ",
                    "попытка $attemptId: заданий=${parsed.size} порядок=" +
                        parsed.joinToString(",") { "g${it.group}o${it.order}" }
                )
            } catch (failure: Exception) {
                MeshLog.error("CDZ", failure)
                message = failure.message ?: failure.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    fun selectTask(index: Int) {
        if (index in tasks.indices) current = index
    }

    /**
     * Черновик хранится только в приложении: на сервер уходит лишь [saveCurrent].
     * Пустой ответ удаляет задание из черновика.
     */
    fun updateAnswer(taskId: Long, answer: JSONObject) {
        draft = if (!hasAnswer(answer)) {
            draft - taskId
        } else {
            draft + (taskId to answer)
        }
        if (taskId in saved) saved = saved - taskId
        persistDraft()
    }

    fun saveCurrent() {
        val task = currentTask ?: return
        if (!isAnswered(task.id)) {
            message = Words.CdzAnswerEmpty.text
            return
        }
        if (busy) return
        val api = client ?: return
        val answer = answerOf(task.id)
        viewModelScope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { api.refreshSession() }
                withContext(Dispatchers.IO) { api.sendAnswer(task.id, attemptId, answer) }
                saved = saved + task.id
                persistDraft()
                message = Words.CdzSaved.text
            } catch (failure: Exception) {
                MeshLog.error("CDZ", failure)
                message = failure.message ?: failure.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    fun showHistory() {
        historyVisible = true
        if (historyLoading) return
        val api = client ?: return
        historyLoading = true
        historyError = null
        historyAttempts = historyEntriesFor(readLocalAttempts())
        viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.IO) { api.attemptHistory(challenge) }
                val apiAttempts = history.optJSONArray("attempts")
                val remote = mutableListOf<JSONObject>()
                if (apiAttempts != null) {
                    for (i in 0 until apiAttempts.length()) {
                        apiAttempts.optJSONObject(i)?.let { remote += it }
                    }
                }
                // API отдаёт завершённые попытки с задержкой. Сначала показываем
                // локально сохранённые, затем заменяем их серверными по attempt_id.
                val merged = mergeAttempts(remote)
                writeLocalAttempts(merged)
                historyAttempts = historyEntriesFor(merged)
                MeshLog.log(
                    "CDZ",
                    "history: api=${remote.size} local=${readLocalAttempts().size} merged=${merged.size}"
                )
            } catch (failure: Exception) {
                MeshLog.error("CDZ", failure)
                val local = readLocalAttempts()
                if (local.isEmpty()) {
                    historyError = failure.message ?: failure.javaClass.simpleName
                } else {
                    MeshLog.log("CDZ", "history: API недоступен, показываю локальные ${local.size}")
                    historyAttempts = historyEntriesFor(local)
                }
            } finally {
                historyLoading = false
            }
        }
    }

    fun dismissHistory() {
        historyVisible = false
    }

    fun finishAttempt() {
        if (busy) return
        val api = client ?: return
        viewModelScope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { api.refreshSession() }
                withContext(Dispatchers.IO) { api.completeAttempt(challenge) }
                // Сразу кладём попытку в локальную историю: серверный список
                // завершённых попыток обновляется с задержкой. Баллы уточним
                // из result, но даже при его ошибке попытка уже не потеряется.
                storeLocalAttempt(
                    JSONObject()
                        .put("attempt_id", attemptId)
                        .put("attempt_num", readLocalAttempts().size + 1)
                        .put("start_attempt", attemptStartedAt)
                        .put("complete_attempt", "")
                        .put("got_score", -1)
                        .put("max_score", -1)
                )
                attemptCount = maxOf(attemptCount, readLocalAttempts().size)
                lastAttemptId = null
                val raw = try {
                    withContext(Dispatchers.IO) { api.attemptResult(challenge, attemptId) }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    MeshLog.error("CDZ", failure)
                    JSONObject()
                }
                val info = raw.optJSONObject("attempt_info")
                if (info != null) storeLocalAttempt(info)
                val score = info?.optInt("got_score", -1) ?: -1
                val max = info?.optInt("max_score", -1) ?: -1
                result = CdzResult(score = score, max = max)
            } catch (failure: Exception) {
                MeshLog.error("CDZ", failure)
                message = failure.message ?: failure.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    fun dismissResult() {
        result = null
    }

    fun consumeMessage() {
        message = null
    }

    private fun createClient(): CdzNativeClient =
        CdzNativeClient.fromSession(getApplication(), startUrl)

    private fun historyKey(): String =
        "cdz_history_${AccountRegistry(getApplication()).activeScope()}_$challenge"

    private fun attemptIdOf(item: JSONObject): Long =
        item.optLong("attempt_id", 0L)

    private fun readLocalAttempts(): List<JSONObject> {
        val raw = prefs.getString(historyKey(), null).orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun writeLocalAttempts(attempts: List<JSONObject>) {
        val array = JSONArray()
        attempts.forEach { array.put(it) }
        prefs.edit().putString(historyKey(), array.toString()).apply()
    }

    /** Локальная копия завершённой попытки; API подхватит её позже. */
    private fun storeLocalAttempt(info: JSONObject) {
        val id = attemptIdOf(info)
        if (id <= 0L) return
        val attempts = readLocalAttempts().toMutableList()
        val index = attempts.indexOfFirst { attemptIdOf(it) == id }
        if (index >= 0) attempts[index] = info else attempts += info
        writeLocalAttempts(attempts)
        MeshLog.log("CDZ", "history: локально сохранена попытка $id, всего=${attempts.size}")
    }

    /**
     * Соотносит локальную историю с API: серверные записи идут как есть,
     * отсутствующие локальные (только что завершённые) добавляются в конец.
     * Одинаковые `attempt_id` не дублируются — побеждает серверная версия.
     */
    private fun mergeAttempts(remote: List<JSONObject>): List<JSONObject> {
        val merged = mutableListOf<JSONObject>()
        val seen = mutableSetOf<Long>()
        remote.forEach { item ->
            val id = attemptIdOf(item)
            if (id == 0L || seen.add(id)) merged += item
        }
        readLocalAttempts().forEach { item ->
            val id = attemptIdOf(item)
            if (id == 0L || seen.add(id)) merged += item
        }
        return merged
    }

    private fun historyEntriesFor(attempts: List<JSONObject>): List<CdzHistoryEntry> =
        attempts.mapNotNull { item ->
            val attemptId = attemptIdOf(item)
            if (attemptId <= 0L) return@mapNotNull null
            CdzHistoryEntry(
                attemptId = attemptId,
                number = item.optInt("attempt_num", 0),
                startedAt = item.optString("start_attempt"),
                completedAt = item.optString("complete_attempt"),
                score = item.optDouble("got_score", -1.0),
                maxScore = item.optDouble("max_score", -1.0),
            )
        }

    private fun draftKey(): String =
        "cdz_draft_${AccountRegistry(getApplication()).activeScope()}_${challenge}_$attemptId"

    private fun restoreDraft() {
        val raw = prefs.getString(draftKey(), null)
        val local = runCatching { JSONObject(raw.orEmpty()) }.getOrDefault(JSONObject())
        val localSaved = prefs.getString("${draftKey()}_saved", null)
            .orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }
            .toSet()

        val answers = LinkedHashMap<Long, JSONObject>()
        val savedIds = linkedSetOf<Long>()
        tasks.forEach { task ->
            val localAnswer = local.optJSONObject(task.id.toString())
            when {
                localAnswer != null -> {
                    answers[task.id] = localAnswer
                    if (task.id in localSaved) savedIds += task.id
                }
                task.serverAnswer != null -> {
                    answers[task.id] = task.serverAnswer
                    savedIds += task.id
                }
            }
        }
        draft = answers
        saved = savedIds
    }

    private fun persistDraft() {
        val json = JSONObject()
        draft.forEach { (taskId, answer) -> json.put(taskId.toString(), answer) }
        prefs.edit()
            .putString(draftKey(), json.toString())
            .putString("${draftKey()}_saved", saved.joinToString(","))
            .apply()
    }

    class Factory(
        private val app: Application,
        private val url: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CdzNativeViewModel::class.java))
            return CdzNativeViewModel(app, url) as T
        }
    }
}
