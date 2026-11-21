package ru.mesh.foss.feature.cdz

import ru.mesh.foss.core.MeshLog
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.CdzNativeClient

import android.app.Application
import androidx.compose.runtime.getValue
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

/**
 * Нативный просмотр завершённой попытки ЦДЗ.
 *
 * Источник — `GET .../challenge/{cid}/attempt/{aid}/result` из docs/cdz_api.md §9.
 * Никаких start-attempt / complete_attempt здесь нет: экран только читает.
 */
class CdzResultViewModel(
    app: Application,
    private val resultUrl: String,
) : AndroidViewModel(app) {

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var info by mutableStateOf<JSONObject?>(null)
        private set
    var tasks by mutableStateOf<List<CdzResultTask>>(emptyList())
        private set

    private val match = Regex("/exam/challenge/(\\d+)/attempt/(\\d+)").find(resultUrl)
    private val challenge = match?.groupValues?.getOrNull(1).orEmpty()
    private val attemptId = match?.groupValues?.getOrNull(2)?.toLongOrNull() ?: 0L

    init {
        load()
    }

    fun load() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                if (challenge.isBlank() || attemptId <= 0L) {
                    error = Words.CdzNativeError.text
                    return@launch
                }
                val api = CdzNativeClient.fromSession(getApplication(), resultUrl)
                val refreshed = withContext(Dispatchers.IO) { api.refreshSession() }
                MeshLog.log("CDZ", "result: refresh=$refreshed attempt=$attemptId")
                if (!refreshed) {
                    error = Words.CdzNativeError.text
                    return@launch
                }
                val raw = withContext(Dispatchers.IO) {
                    api.attemptResult(challenge, attemptId)
                }
                val attempt = raw.optJSONObject("attempt_info")
                if (attempt == null) {
                    error = Words.CdzNativeError.text
                    return@launch
                }
                val parsed = parseResultTasks(raw)
                info = attempt
                tasks = parsed
                MeshLog.log(
                    "CDZ",
                    "result: attempt=$attemptId tasks=${parsed.size} " +
                        "score=${attempt.optInt("got_score", -1)}/${attempt.optInt("max_score", -1)}"
                )
            } catch (failure: Exception) {
                MeshLog.error("CDZ", failure)
                error = failure.message ?: failure.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    class Factory(
        private val app: Application,
        private val url: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CdzResultViewModel::class.java))
            return CdzResultViewModel(app, url) as T
        }
    }
}
