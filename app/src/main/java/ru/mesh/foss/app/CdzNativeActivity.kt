package ru.mesh.foss.app

import ru.mesh.foss.R
import ru.mesh.foss.core.MeshLog
import ru.mesh.foss.core.UiLanguage
import ru.mesh.foss.core.Words
import ru.mesh.foss.data.AccountRegistry
import ru.mesh.foss.data.CdzNativeClient
import ru.mesh.foss.data.CookieJson
import ru.mesh.foss.data.SessionStore

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.webkit.CookieManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * Нативное прохождение ЦДЗ (эксперимент): задания, ручное сохранение ответов
 * на сервер, локальные черновики без автосохранения и предупреждение перед
 * завершением попытки. По умолчанию所使用的 веб-окно.
 */
class CdzNativeActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, url: String, title: String?): Intent =
            Intent(context, CdzNativeActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var client: CdzNativeClient? = null
    private var draft: JSONObject = JSONObject()
    private var saved: MutableSet<Int> = mutableSetOf()
    private var attemptId: Long = 0L
    private var tasks: List<JSONObject> = emptyList()
    private var current = 0
    private var challenge = ""
    private var finished = false
    private var currentView: View? = null


    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var nav: GridView
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var saveButton: Button
    private lateinit var finishButton: Button

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiLanguage.context(newBase))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
            ?: Words.CdzNativeTitle.text
        setContentView(R.layout.activity_cdz_native)

        titleView = findViewById(R.id.cdzTitle)
        statusView = findViewById(R.id.cdzStatus)
        progress = findViewById(R.id.cdzProgress)
        nav = findViewById(R.id.cdzNav)
        content = findViewById(R.id.cdzContent)
        scroll = findViewById(R.id.cdzScroll)
        saveButton = findViewById(R.id.cdzSave)
        finishButton = findViewById(R.id.cdzFinish)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        MeshLog.log("CDZ", "native: открытие url=${url.take(200)}")
        val store = SessionStore(this, AccountRegistry(this).activeScope())
        val jar = CookieJson.parse(store.authCookies).toMutableMap()
        CookieManager.getInstance().getCookie("https://uchebnik.mos.ru")
            ?.split(';')?.forEach { pair ->
                val key = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=').trim()
                if (key.isNotBlank() && value.isNotBlank()) jar[key] = value
            }
        val token = jar["aupd_token"]?.takeIf { it.isNotBlank() } ?: store.token
        MeshLog.trace("CDZ") {
            "native: аккаунт scope=${AccountRegistry(this).activeScope()} " +
                "studentId=${store.studentId} personId=${store.personId} " +
                "cookies=${jar.size} есть токен=${!token.isNullOrBlank()}"
        }
        saveButton.setOnClickListener { saveCurrent() }
        finishButton.setOnClickListener { confirmFinish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsaved()) confirmLeave() else finish()
            }
        })

        resolvePage(url)
    }

    /**
     * Лаунчер содержит activity_url и все параметры запуска. Собираем страницу
     * ЦДЗ локально — без сетевого редиректа (и без WebView).
     */
    private fun resolvePage(startUrl: String) {
        val api = client ?: return
        val derived = api.derivePageUrl(startUrl)
        MeshLog.log("CDZ", "native: pageUrl=${derived?.take(180).orEmpty()}")
        val id = derived?.let { Regex("/exam/challenge/(\\d+)").find(it)?.groupValues?.get(1) }
            ?.takeIf { it.isNotBlank() }
        if (derived == null || id == null) {
            MeshLog.log("CDZ", "native: не найден activity_url/challenge в launch URL")
            Toast.makeText(this, Words.CdzNativeError.text, Toast.LENGTH_LONG).show()
            return
        }
        client = CdzNativeClient(
            pageUrl = derived,
            token = api.currentToken(),
            profileId = api.currentProfileId(),
            userId = api.currentUserId(),
            personId = api.currentPersonId(),
            cookies = api.currentCookies(),
        )
        startNative(derived, id)
    }

    private fun startNative(finalUrl: String, challengeId: String) {
        // Клиент уже создан с pageUrl=activity_url и параметрами лаунчера.
        challenge = challengeId
        scope.launch {
            progress.visibility = View.VISIBLE
            try {
                val api = client ?: return@launch
                val info = withContext(Dispatchers.IO) { api.checkAttempts(challenge) }
                val attemptCount = info.optInt("number_of_attempt")
                val lastAttempt = info.optString("last_attempt_id")
                MeshLog.log("CDZ", "native: info number_of_attempt=$attemptCount last=$lastAttempt")
                val refreshed = withContext(Dispatchers.IO) { api.refreshSession() }
                MeshLog.log("CDZ", "native: refresh=$refreshed")
                if (!refreshed) {
                    MeshLog.log("CDZ", "native: сессия uchebnik не обновлена")
                    Toast.makeText(this@CdzNativeActivity, Words.CdzNativeError.text, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val attempt = withContext(Dispatchers.IO) { api.startAttempt(challenge) }
                attemptId = attempt.optLong("challenge_attempt_id")
                MeshLog.log("CDZ", "native: attempt=$attemptId keys=${attempt.keys()}")
                tasks = readTasks(attempt)
                MeshLog.log("CDZ", "native: заданий=${tasks.size}")
                if (tasks.isEmpty()) {
                    MeshLog.log("CDZ", "native: пустой список заданий: ${attempt.toString().take(300)}")
                    throw IllegalStateException("no tasks")
                }
                draft = restoreDraft()
                saved = restoreSaved()
                render()
            } catch (error: Exception) {
                MeshLog.error("CDZ", error)
                Toast.makeText(this@CdzNativeActivity, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun draftKey(): String = "cdz_draft_${challenge}_${attemptId}"

    private fun prefs() = getSharedPreferences("cdz_drafts", Context.MODE_PRIVATE)

    private fun restoreDraft(): JSONObject {
        val raw = prefs().getString(draftKey(), null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    private fun restoreSaved(): MutableSet<Int> {
        val raw = prefs().getString("${draftKey()}_saved", null).orEmpty()
        return raw.split(',').mapNotNull { it.toIntOrNull() }.toMutableSet()
    }

    private fun keepDraft() {
        prefs().edit().putString(draftKey(), draft.toString()).apply()
    }

    private fun readTasks(attempt: JSONObject): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        val groups = attempt.optJSONArray("challenge_test_groups") ?: return out
        for (i in 0 until groups.length()) {
            val tasksInGroup = groups.optJSONObject(i)?.optJSONArray("challenge_tasks") ?: continue
            for (j in 0 until tasksInGroup.length()) {
                val task = tasksInGroup.optJSONObject(j) ?: continue
                if (task.optLong("id") > 0L) out += task
            }
        }
        return out.sortedBy { it.optInt("task_order") }
    }

    private fun taskId(index: Int): Long = tasks.getOrNull(index)?.optLong("id") ?: 0L

    private fun isSaved(index: Int): Boolean = saved.contains(index.toInt())

    private fun isAnswered(index: Int): Boolean {
        val answer = draft.optJSONObject(taskId(index).toString()) ?: return false
        return answer.has("id") || answer.has("ids") || answer.has("string") ||
            answer.has("number") || answer.has("match")
    }

    private fun isDone(index: Int): Boolean = isSaved(index) && isAnswered(index)

    private fun hasUnsaved(): Boolean =
        tasks.indices.any { isAnswered(it) && !isSaved(it) }

    private fun savedCount(): Int = tasks.indices.count { isDone(it) }

    private fun render() {
        titleView.text = Words.CdzNativeTitle.text
        val done = savedCount()
        statusView.text = getString(
            R.string.cdz_native_status, done, tasks.size
        ) + if (hasUnsaved()) "  ⚠" else ""
        statusView.setTextColor(
            if (hasUnsaved() || done < tasks.size) {
                resources.getColor(android.R.color.holo_red_dark, theme)
            } else {
                resources.getColor(android.R.color.holo_blue_dark, theme)
            }
        )
        nav.adapter = NavAdapter()
        nav.numColumns = 6
        nav.setOnItemClickListener { _, _, position, _ -> switchTo(position) }
        renderTask()
    }

    private fun switchTo(index: Int) {
        if (index !in tasks.indices || index == current) return
        // Черновик остаётся в приложении: автосохранения на сервер нет.
        keepDraft()
        current = index
        renderTask()
    }

    private fun renderTask() {
        content.removeAllViews()
        val task = tasks.getOrNull(current) ?: return
        val question = task.optJSONArray("question_elements")
        val html = StringBuilder()
        if (question != null) {
            for (i in 0 until question.length()) {
                val block = question.optJSONObject(i) ?: continue
                val type = block.optString("type")
                val text = block.optString("text")
                val contentBlocks = block.optJSONArray("content")
                if (contentBlocks != null) {
                    for (j in 0 until contentBlocks.length()) {
                        val part = contentBlocks.optJSONObject(j) ?: continue
                        val partType = part.optString("type")
                        val url = part.optString("relative_url").ifBlank { part.optString("preview_url") }
                        when {
                            partType == "content/atomic" && part.optString("atomic_type") == "image" && url.isNotBlank() -> {
                                html.append("<br><img src=\"").append(url).append("\" style=\"max-width:100%\"><br>")
                            }
                            partType == "content/text" -> html.append(part.optString("text").escapeHtml())
                        }
                    }
                }
                if (type == "content/text" && text.isNotBlank()) {
                    html.append(text.escapeHtml()).append("<br>")
                }
            }
        }
        val questionView = TextView(this)
        questionView.text = HtmlCompat.fromHtml(
            html.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY,
            { source -> loadImage(source) }, null
        )
        questionView.movementMethod = LinkMovementMethod.getInstance()
        questionView.setTextIsSelectable(true)
        content.addView(questionView)

        val answer = task.optJSONObject("answer") ?: JSONObject()
        val type = answer.optString("answer.type", answer.optString("type"))
        val answerKey = taskId(current).toString()
        val chosen = draft.optJSONObject(answerKey) ?: JSONObject()
        when (type) {
            "answer/single", "answer/multiple" -> renderOptions(answer, chosen, type, answerKey)
            else -> renderTextAnswer(chosen, answerKey)
        }
        scroll.scrollTo(0, 0)
        renderNavState()
    }

    private fun loadImage(source: String): android.graphics.drawable.Drawable {
        val placeholder = resources.getDrawable(android.R.drawable.ic_menu_gallery, theme)
        if (!source.startsWith("http")) return placeholder
        // Html.ImageGetter синхронный: грузим bitmap с таймаутом, чтобы не блокировать надолго.
        return runCatching {
            val connection = URL(source).openConnection()
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val bitmap = connection.getInputStream().use { BitmapFactory.decodeStream(it) }
            android.graphics.drawable.BitmapDrawable(resources, bitmap)
        }.getOrDefault(placeholder)
    }

    private fun renderOptions(answer: JSONObject, chosen: JSONObject, type: String, answerKey: String) {
        val options = answer.optJSONArray("options") ?: return
        val multiple = type == "answer/multiple"
        val selectedIds = chosen.optJSONArray("ids")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        } ?: setOf(chosen.optString("id").takeIf { it.isNotBlank() })
        for (i in 0 until options.length()) {
            val option = options.optJSONObject(i) ?: continue
            val id = option.optString("id")
            val row = CheckBox(this)
            row.isChecked = id in selectedIds
            row.text = option.optString("text").ifBlank { Words.Option.text }
            row.setTextIsSelectable(true)
            row.setOnCheckedChangeListener { _, checked ->
                val currentAnswer = draft.optJSONObject(answerKey) ?: JSONObject()
                if (multiple) {
                    val ids = currentAnswer.optJSONArray("ids")?.let { arr ->
                        (0 until arr.length()).map { arr.optString(it) }.toMutableList()
                    } ?: mutableListOf()
                    ids.remove(id)
                    if (checked) ids.add(id)
                    currentAnswer.put("@answer_type", type).put("ids", ids.toJsonArray())
                } else {
                    if (checked) {
                        currentAnswer.put("@answer_type", type).put("id", id)
                    } else {
                        currentAnswer.remove("id")
                    }
                }
                draft.put(answerKey, currentAnswer)
                keepDraft()
                saved.remove(current)
                renderNavState()
            }
            content.addView(row)
        }
    }

    private fun renderTextAnswer(chosen: JSONObject, answerKey: String) {
        val input = android.widget.EditText(this)
        input.setText(chosen.optString("string"))
        input.setTextIsSelectable(true)
        input.hint = Words.YourAnswer.text
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val value = s?.toString().orEmpty()
                val answer = JSONObject()
                    .put("@answer_type", "answer/string")
                    .put("string", JSONArray().put(value))
                    .put("caseSensitive", false)
                if (value.isBlank()) draft.remove(answerKey) else draft.put(answerKey, answer)
                keepDraft()
                saved.remove(current)
                renderNavState()
            }
        })
        content.addView(input)
    }

    private fun saveCurrent() {
        val answer = draft.optJSONObject(taskId(current).toString())
        if (answer == null || answer.length() <= 1) {
            Toast.makeText(this, Words.CdzAnswerEmpty.text, Toast.LENGTH_SHORT).show()
            return
        }
        saveButton.isEnabled = false
        scope.launch {
            try {
                val api = client ?: return@launch
                api.refreshSession()
                withContext(Dispatchers.IO) { api.sendAnswer(taskId(current), attemptId, answer) }
                saved.add(current)
                keepDraft()
                savedStatusPersist()
                render()
                Toast.makeText(this@CdzNativeActivity, Words.CdzSaved.text, Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                MeshLog.error("CDZ", error)
                Toast.makeText(this@CdzNativeActivity, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
            } finally {
                saveButton.isEnabled = true
            }
        }
    }

    private fun savedStatusPersist() {
        prefs().edit().putString("${draftKey()}_saved", saved.map { it.toString() }.joinToString(",")).apply()
    }

    private fun confirmFinish() {
        val done = savedCount()
        val total = tasks.size
        val unsaved = hasUnsaved()
        val message = buildString {
            append(Words.CdzFinishConfirm(done, total))
            if (done < total || unsaved) {
                append("\n\n")
                append(Words.CdzFinishWarning(done, total))
                append("\n")
                append(Words.CdzFinishUnsaved.text)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(Words.CdzFinishTitle.text)
            .setMessage(message)
            .setPositiveButton(Words.CdzFinishAnyway.text) { _, _ -> finishAttempt() }
            .setNegativeButton(Words.Cancel.text, null)
            .show()
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this)
            .setTitle(Words.CdzLeaveTitle.text)
            .setMessage(Words.CdzLeaveMessage.text)
            .setPositiveButton(Words.CdzLeaveAnyway.text) { _, _ -> finish() }
            .setNegativeButton(Words.Cancel.text, null)
            .show()
    }

    private fun finishAttempt() {
        finishButton.isEnabled = false
        scope.launch {
            try {
                val api = client ?: return@launch
                api.refreshSession()
                withContext(Dispatchers.IO) { api.completeAttempt(challenge) }
                val result = withContext(Dispatchers.IO) { api.attemptResult(challenge, attemptId) }
                finished = true
                val score = result.optJSONObject("attempt_info")?.optInt("got_score", -1) ?: -1
                val max = result.optJSONObject("attempt_info")?.optInt("max_score", -1) ?: -1
                AlertDialog.Builder(this@CdzNativeActivity)
                    .setTitle(Words.CdzResultTitle.text)
                    .setMessage(if (score >= 0) Words.CdzResultScore(score, max) else Words.CdzResultDone.text)
                    .setPositiveButton(Words.Close.text) { _, _ -> finish() }
                    .show()
            } catch (error: Exception) {
                MeshLog.error("CDZ", error)
                Toast.makeText(this@CdzNativeActivity, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
            } finally {
                finishButton.isEnabled = true
            }
        }
    }

    private fun renderNavState() {
        (nav.adapter as? NavAdapter)?.notifyDataSetChanged()
        val done = savedCount()
        statusView.text = getString(R.string.cdz_native_status, done, tasks.size) +
            if (hasUnsaved()) "  ⚠" else ""
        statusView.setTextColor(
            if (hasUnsaved() || done < tasks.size) {
                resources.getColor(android.R.color.holo_red_dark, theme)
            } else {
                resources.getColor(android.R.color.holo_blue_dark, theme)
            }
        )
    }

    private inner class NavAdapter : android.widget.BaseAdapter() {
        override fun getCount(): Int = tasks.size
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_cdz_task, parent, false)
            val box = view.findViewById<TextView>(R.id.cdzTaskNumber)
            box.text = (position + 1).toString()
            val color = when {
                isDone(position) -> android.R.color.holo_blue_dark
                isAnswered(position) -> android.R.color.holo_red_dark
                else -> android.R.color.darker_gray
            }
            box.setBackgroundColor(resources.getColor(color, theme))
            box.alpha = if (position == current) 0.55f else 1f
            return view
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

private fun Collection<String>.toJsonArray(): JSONArray {
    val array = JSONArray()
    forEach { array.put(it) }
    return array
}

private fun String.escapeHtml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
