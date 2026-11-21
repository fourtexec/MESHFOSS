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
import android.widget.EditText
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
    private var currentAttemptCount = 0
    private var currentView: View? = null


    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var nav: GridView
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var saveButton: Button
    private lateinit var finishButton: Button
    private lateinit var attemptPanel: View
    private lateinit var gamePanel: View
    private lateinit var attemptHint: TextView
    private lateinit var continueButton: Button
    private lateinit var startButton: Button
    private lateinit var historyButton: Button

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
        attemptPanel = findViewById(R.id.cdzAttemptPanel)
        gamePanel = findViewById(R.id.cdzGamePanel)
        attemptHint = findViewById(R.id.cdzAttemptHint)
        continueButton = findViewById(R.id.cdzContinue)
        startButton = findViewById(R.id.cdzStart)
        historyButton = findViewById(R.id.cdzHistory)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        MeshLog.log("CDZ", "native: открытие url=${url.take(200)}")
        val store = SessionStore(this, AccountRegistry(this).activeScope())
        val jar = CookieJson.parse(store.authCookies).toMutableMap()
        // webview-сессия может содержать свежие Session-Id-External/Profile — берём их тоже.
        CookieManager.getInstance().getCookie("https://uchebnik.mos.ru")
            ?.split(';')?.forEach { pair ->
                val key = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=').trim()
                if (key.isNotBlank() && value.isNotBlank()) jar[key] = value
            }
        MeshLog.trace("CDZ") { "cookie names=${jar.keys}" }
        val token = jar["aupd_token"]?.takeIf { it.isNotBlank() } ?: store.token
        MeshLog.trace("CDZ") {
            "native: аккаунт scope=${AccountRegistry(this).activeScope()} " +
                "studentId=${store.studentId} personId=${store.personId} " +
                "cookies=${jar.size} есть токен=${!token.isNullOrBlank()}"
        }
        client = CdzNativeClient(
            pageUrl = url,
            token = token,
            profileId = store.studentId,
            userId = jar["obr_id"],
            personId = store.personId,
            cookies = jar,
        )
        MeshLog.log("CDZ", "native: клиент создан")
        saveButton.setOnClickListener { saveCurrent() }
        finishButton.setOnClickListener { confirmFinish() }
        continueButton.setOnClickListener { beginAttempt() }
        startButton.setOnClickListener { confirmNewAttempt(currentAttemptCount) }
        historyButton.setOnClickListener { showHistory() }
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
        challenge = challengeId
        attemptPanel.visibility = View.VISIBLE
        gamePanel.visibility = View.GONE
        attemptHint.text = Words.CdzAttemptLoading.text
        continueButton.isEnabled = false
        startButton.isEnabled = false
        historyButton.isEnabled = false
        scope.launch {
            progress.visibility = View.VISIBLE
            try {
                val api = client ?: return@launch
                val refreshed = withContext(Dispatchers.IO) { api.refreshSession() }
                MeshLog.log("CDZ", "native: refresh=$refreshed")
                if (!refreshed) {
                    MeshLog.log("CDZ", "native: сессия uchebnik не обновлена")
                    Toast.makeText(this@CdzNativeActivity, Words.CdzNativeError.text, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val info = withContext(Dispatchers.IO) { api.checkAttempts(challenge) }
                val count = info.optInt("number_of_attempt")
                val last = info.optString("last_attempt_id").takeIf { it.isNotBlank() && it != "null" }
                showAttemptChooser(count, last)
            } catch (error: Exception) {
                MeshLog.error("CDZ", error)
                Toast.makeText(this@CdzNativeActivity, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    /** Никакого автостарта: пользователь сам выбирает кнопкой на экране. */
    private fun showAttemptChooser(count: Int, lastAttempt: String?) {
        currentAttemptCount = count
        attemptHint.text = Words.CdzAttemptHint.text
        continueButton.isEnabled = true
        startButton.isEnabled = true
        historyButton.isEnabled = true
        attemptPanel.visibility = View.VISIBLE
        gamePanel.visibility = View.GONE
        nav.visibility = View.GONE
        continueButton.visibility = if (lastAttempt != null) View.VISIBLE else View.GONE
        if (lastAttempt != null) {
            continueButton.text = Words.CdzContinueAttempt(lastAttempt)
        }
        startButton.text = if (count > 0) Words.CdzStartNewAttempt.text else Words.CdzStartAttempt.text
        statusView.text = Words.CdzAttemptStatus(count, lastAttempt ?: "—")
        MeshLog.log("CDZ", "панель попыток: count=$count last=$lastAttempt")
    }

    private fun confirmNewAttempt(count: Int) {
        AlertDialog.Builder(this)
            .setTitle(Words.CdzStartAttempt.text)
            .setMessage(if (count > 0) Words.CdzNewAttemptWarning(count) else Words.CdzFirstAttemptWarning.text)
            .setPositiveButton(Words.CdzStartAnyway.text) { _, _ -> beginAttempt() }
            .setNegativeButton(Words.Cancel.text, null)
            .show()
    }

    private fun beginAttempt() {
        scope.launch {
            progress.visibility = View.VISIBLE
            try {
                val api = client ?: return@launch
                val attempt = withContext(Dispatchers.IO) { api.startAttempt(challenge) }
                attemptId = attempt.optLong("challenge_attempt_id")
                MeshLog.log("CDZ", "start-attempt выбран вручную: attempt=$attemptId")
                tasks = readTasks(attempt)
                MeshLog.log("CDZ", "native: заданий=${tasks.size}")
                if (tasks.isEmpty()) throw IllegalStateException("no tasks")
                draft = restoreDraft()
                saved = restoreSaved()
                attemptPanel.visibility = View.GONE
                gamePanel.visibility = View.VISIBLE
                nav.visibility = View.VISIBLE
                render()
            } catch (error: Exception) {
                MeshLog.error("CDZ", error)
                Toast.makeText(this@CdzNativeActivity, error.message ?: error.javaClass.simpleName, Toast.LENGTH_LONG).show()
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun showHistory() {
        scope.launch {
            progress.visibility = View.VISIBLE
            try {
                val api = client ?: return@launch
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
                            item.optInt("max_score")
                        )
                    }
                }
                AlertDialog.Builder(this@CdzNativeActivity)
                    .setTitle(Words.CdzAttemptHistory.text)
                    .setItems(lines.toTypedArray(), null)
                    .setPositiveButton(Words.Close.text, null)
                    .show()
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
        return answer.length() > 1
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
            "answer/match" -> renderMatch(answer, chosen, answerKey)
            "answer/number" -> renderNumber(chosen, answerKey)
            "answer/number/interval" -> renderNumberInterval(chosen, answerKey)
            "answer/string", "answer/free" -> renderTextAnswer(chosen, answerKey, type)
            "answer/order" -> renderOrder(answer, chosen, answerKey)
            "answer/groups" -> renderGroups(answer, chosen, answerKey)
            "answer/grid" -> renderGrid(answer, chosen, answerKey)
            else -> renderTextAnswer(chosen, answerKey, type)
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

    private fun renderTextAnswer(chosen: JSONObject, answerKey: String, type: String) {
        val input = android.widget.EditText(this)
        val values = chosen.optJSONArray("string")
        val initial = if (values != null && values.length() > 0) {
            (0 until values.length()).joinToString(", ") { values.optString(it) }
        } else chosen.optString("string")
        input.setText(initial)
        input.setTextIsSelectable(true)
        input.hint = Words.YourAnswer.text
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val value = s?.toString().orEmpty().trim()
                val answer = if (type == "answer/free") {
                    JSONObject().put("@answer_type", type).put("string", value).put("files", JSONArray())
                } else {
                    JSONObject()
                        .put("@answer_type", type)
                        .put("string", JSONArray().put(value))
                        .put("caseSensitive", false)
                }
                if (value.isBlank()) draft.remove(answerKey) else draft.put(answerKey, answer)
                keepDraft()
                saved.remove(current)
                renderNavState()
            }
        })
        content.addView(input)
    }

    private fun optionView(option: JSONObject, onClick: () -> Unit): TextView {
        val view = TextView(this)
        val text = option.optString("text")
        val parts = mutableListOf<String>()
        if (text.isNotBlank()) parts += text
        val attachments = option.optJSONArray("content")
        if (attachments != null) {
            for (i in 0 until attachments.length()) {
                val a = attachments.optJSONObject(i) ?: continue
                if (a.optString("type") == "content/atomic" && a.optString("atomic_type") == "image") {
                    parts += "🖼 " + a.optString("description").ifBlank { a.optString("atomic_id") }
                }
            }
        }
        view.text = parts.joinToString("\n").ifBlank { Words.Option.text }
        view.setTextIsSelectable(true)
        view.setBackgroundResource(android.R.drawable.btn_default)
        view.setPadding(12, 12, 12, 12)
        view.setOnClickListener { onClick() }
        return view
    }

    /** answer/match — Compose + M3 Expressive: верхний/нижний ряды и цветные линии. */
    private fun renderMatch(answer: JSONObject, chosen: JSONObject, answerKey: String) {
        val composeView = androidx.compose.ui.platform.ComposeView(this)
        composeView.setContent {
            MeshRankerTheme {
                CdzMatchScreen(
                    answer = answer,
                    savedMatch = chosen.optJSONObject("match") ?: JSONObject(),
                    onChanged = { json ->
                        draft.put(answerKey, JSONObject().put("@answer_type", "answer/match").put("match", json))
                        keepDraft(); saved.remove(current); renderNavState()
                    },
                )
            }
        }
        content.addView(composeView)
    }

    private fun renderNumber(chosen: JSONObject, answerKey: String) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val arr = chosen.optJSONArray("number")
        input.setText(if (arr != null && arr.length() > 0) arr.optString(0) else "")
        input.hint = Words.YourAnswer.text
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val value = s?.toString().orEmpty().trim()
                if (value.isBlank()) draft.remove(answerKey)
                else draft.put(answerKey, JSONObject().put("@answer_type", "answer/number").put("number", JSONArray().put(value)))
                keepDraft(); saved.remove(current); renderNavState()
            }
        })
        content.addView(input)
    }

    private fun renderNumberInterval(chosen: JSONObject, answerKey: String) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT
        input.setText(chosen.optString("number"))
        input.hint = Words.YourAnswer.text
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val value = s?.toString().orEmpty().trim()
                if (value.isBlank()) draft.remove(answerKey)
                else draft.put(answerKey, JSONObject().put("@answer_type", "answer/number/interval").put("number", value))
                keepDraft(); saved.remove(current); renderNavState()
            }
        })
        content.addView(input)
    }

    private fun renderOrder(answer: JSONObject, chosen: JSONObject, answerKey: String) {
        val options = answer.optJSONArray("options") ?: return
        val selected = (chosen.optJSONArray("ids_order")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        } ?: (0 until options.length()).map { options.optJSONObject(it)?.optString("id").orEmpty() }).toMutableList()
        fun commit() {
            draft.put(answerKey, JSONObject().put("@answer_type", "answer/order").put("ids_order", selected.toJsonArray()))
            keepDraft(); saved.remove(current); renderNavState(); renderTask()
        }
        for (i in selected.indices) {
            val option = (0 until options.length()).mapNotNull { options.optJSONObject(it) }
                .firstOrNull { it.optString("id") == selected[i] } ?: continue
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(optionView(option) { if (i > 0) { val t = selected[i-1]; selected[i-1]=selected[i]; selected[i]=t; commit() } })
            row.addView(Button(this).apply { text = "↓"; setOnClickListener { if (i < selected.lastIndex) { val t = selected[i+1]; selected[i+1]=selected[i]; selected[i]=t; commit() } } })
            content.addView(row)
        }
    }

    private fun renderGroups(answer: JSONObject, chosen: JSONObject, answerKey: String) {
        val options = answer.optJSONArray("options") ?: return
        val groups = mutableMapOf<String, MutableList<String>>()
        val chosenGroups = chosen.optJSONArray("groups")
        if (chosenGroups != null) {
            for (i in 0 until chosenGroups.length()) {
                val g = chosenGroups.optJSONObject(i) ?: continue
                val id = g.optString("group_id")
                val ids = g.optJSONArray("options_ids") ?: continue
                groups[id] = (0 until ids.length()).map { ids.optString(it) }.toMutableList()
            }
        }
        val groupIds = (0 until options.length()).mapNotNull { options.optJSONObject(it)?.optString("group_id")?.takeIf(String::isNotBlank) }.distinct()
        groupIds.forEach { gid ->
            content.addView(TextView(this).apply { text = gid; textSize = 16f; setTextIsSelectable(true) })
            options.arrayObjects().filter { it.optString("group_id") == gid }.forEach { option ->
                val oid = option.optString("id")
                val selected = groups[gid]?.contains(oid) == true
                val box = CheckBox(this)
                box.text = option.optString("text").ifBlank { Words.Option.text }
                box.isChecked = selected
                box.setTextIsSelectable(true)
                box.setOnCheckedChangeListener { _, checked ->
                    val list = groups.getOrPut(gid) { mutableListOf() }
                    if (checked) list.add(oid) else list.remove(oid)
                    val out = JSONArray()
                    groups.forEach { (key, values) ->
                        out.put(JSONObject().put("group_id", key).put("options_ids", values.toJsonArray()))
                    }
                    draft.put(answerKey, JSONObject().put("@answer_type", "answer/groups").put("groups", out))
                    keepDraft(); saved.remove(current); renderNavState()
                }
                content.addView(box)
            }
        }
    }

    private fun renderGrid(answer: JSONObject, chosen: JSONObject, answerKey: String) {
        val cells = chosen.optJSONArray("cells")
        val count = maxOf(3, (cells?.length() ?: 0) + 1)
        for (i in 0 until count) {
            val input = EditText(this)
            input.setText(cells?.optJSONObject(i)?.optString("answer").orEmpty())
            input.hint = Words.YourAnswer.text
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val out = JSONArray()
                    for (j in 0 until content.childCount) {
                        val child = content.getChildAt(j)
                        if (child is EditText) out.put(JSONObject().put("input_type", "MANUAL_INPUT").put("answer", child.text.toString()))
                    }
                    draft.put(answerKey, JSONObject().put("@answer_type", "answer/grid").put("cells", out))
                    keepDraft(); saved.remove(current); renderNavState()
                }
            })
            content.addView(input)
        }
    }

    private fun JSONArray.arrayObjects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

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
