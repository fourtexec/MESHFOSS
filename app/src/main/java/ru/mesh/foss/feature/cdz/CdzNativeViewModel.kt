package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.MeshLog
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.AccountRegistry
import ru.mesh.foss.data.CdzNativeClient
import ru.mesh.foss.data.CookieJson
import ru.mesh.foss.data.SessionStore

import android.app.Application
import android.content.Context
import android.webkit.CookieManager
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
import org.json.JSONObject

enum class CdzPhase { Loading, Choice, Game }

data class CdzResult(val score: Int, val max: Int)

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
    var historyLines by mutableStateOf<List<String>>(emptyList())
        private set

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
        attemptCount = info.optInt("number_of_attempt", 0)
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
                val parsed = parseTasks(attempt)
                if (parsed.isEmpty()) throw IllegalStateException("В попытке нет заданий")
                tasks = parsed
                current = 0
                restoreDraft()
                phase = CdzPhase.Game
                MeshLog.log(
                    "CDZ",
                    "попытка $attemptId: заданий=${parsed.size} порядок=" +
                        parsed.joinToString(",") { it.order.toString() }
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
        historyLines = emptyList()
        viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.IO) { api.attemptHistory(challenge) }
                val attempts = history.optJSONArray("attempts")
                val lines = mutableListOf<String>()
                if (attempts == null || attempts.length() == 0) {
                    lines += Words.CdzNoAttempts.text
                } else {
                    for (i in 0 until attempts.length()) {
                        val item = attempts.optJSONObject(i) ?: continue
                        lines += Words.CdzAttemptRow(
                            item.optInt("attempt_num"),
                            item.optString("start_attempt").take(16),
                            item.optInt("got_score"),
                            item.optInt("max_score"),
                        )
                    }
                }
                historyLines = lines
            } catch (failure: Exception) {
                MeshLog.error("CDZ", failure)
                historyLines = listOf(failure.message ?: failure.javaClass.simpleName)
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
                val raw = withContext(Dispatchers.IO) { api.attemptResult(challenge, attemptId) }
                val info = raw.optJSONObject("attempt_info")
                result = CdzResult(
                    score = info?.optInt("got_score", -1) ?: -1,
                    max = info?.optInt("max_score", -1) ?: -1,
                )
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

    private fun createClient(): CdzNativeClient {
        val app = getApplication<Application>()
        val store = SessionStore(app, AccountRegistry(app).activeScope())
        val jar = CookieJson.parse(store.authCookies).toMutableMap()
        CookieManager.getInstance().getCookie("https://uchebnik.mos.ru")
            ?.split(';')?.forEach { pair ->
                val key = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=').trim()
                if (key.isNotBlank() && value.isNotBlank()) jar[key] = value
            }
        val token = jar["aupd_token"]?.takeIf { it.isNotBlank() } ?: store.token
        MeshLog.log(
            "CDZ",
            "native: аккаунт=${AccountRegistry(app).activeScope()} cookies=${jar.size} " +
                "есть токен=${!token.isNullOrBlank()}"
        )
        return CdzNativeClient(
            pageUrl = startUrl,
            token = token,
            profileId = store.studentId,
            userId = jar["obr_id"],
            personId = store.personId,
            cookies = jar,
        )
    }

    private fun draftKey(): String = "cdz_draft_${challenge}_${attemptId}"

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
