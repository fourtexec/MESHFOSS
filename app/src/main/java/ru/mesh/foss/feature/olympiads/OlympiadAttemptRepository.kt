package ru.mesh.foss.feature.olympiads

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.mesh.foss.data.CdzNativeClient
import ru.mesh.foss.data.UchebnikContestClient
import ru.mesh.foss.feature.cdz.CdzResultTask
import ru.mesh.foss.feature.cdz.parseResultTasks

/** Готовый нативный результат попытки: сводка и задания. */
data class OlympiadAttemptData(
    val info: JSONObject,
    val tasks: List<CdzResultTask>,
)

/**
 * Загружает результат олимпиадной попытки по ссылке из `detailsUrl`.
 *
 * Ссылка сначала приходит из contest API, затем разворачивается в страницу
 * `uchebnik.mos.ru/contests/...` или launcher-ссылку с `activity_url`.
 * Из неё берём ID и читаем JSON попытки.
 */
interface OlympiadAttemptRepository {
    suspend fun load(link: String): OlympiadAttemptData
}

class UchebnikOlympiadAttemptRepository(
    context: Context,
) : OlympiadAttemptRepository {

    private val appContext = context.applicationContext
    private val gson = Gson()

    override suspend fun load(link: String): OlympiadAttemptData = withContext(Dispatchers.IO) {
        val ids = OlympiadAttemptLink.parse(link)
            ?: throw IllegalArgumentException("bad olympiad details link")
        // Для launcher-ссылки school.mos.ru Referer должен быть страницей
        // uchebnik, иначе refresh/webtests могут ответить 401. Если ссылка
        // уже прямая, сохраняем её query (registration/fetch).
        val session = CdzNativeClient.fromSession(appContext, pageUrlFor(link, ids))
        val response = UchebnikContestClient(session).attempt(ids.challengeId, ids.attemptId)

        // Gson уже разобрал сетевой ответ. JSONObject здесь — только мост
        // к общему рендеру результата ЦДЗ, а не сетевой парсер.
        val raw = JSONObject(gson.toJson(response))
        val info = raw.optJSONObject("attempt_info")
            ?: throw IllegalStateException("olympiad attempt has no attempt_info")
        val tasks = parseResultTasks(raw)
        if (tasks.isEmpty()) {
            throw IllegalStateException("olympiad attempt has no tasks")
        }
        OlympiadAttemptData(info = info, tasks = tasks)
    }
}

/** Демо-результат для предпросмотра: не ходит в uchebnik и не требует токена. */
class PreviewOlympiadAttemptRepository : OlympiadAttemptRepository {

    override suspend fun load(link: String): OlympiadAttemptData {
        val payload = JSONObject()
            .put(
                "attempt_info",
                JSONObject()
                    .put("attempt_id", 200000001L)
                    .put("attempt_num", 1)
                    .put("got_score", 5)
                    .put("max_score", 10)
                    .put("time_duration", 1_800_000)
                    .put("amount_correct_answer", 1)
                    .put("amount_partially_correct_answer", 0)
                    .put("amount_wrong_answer", 0)
                    .put("amount_missed_answer", 1)
                    .put("amount_waiting_review_answer", 0)
            )
            .put(
                "results",
                JSONArray().put(
                    JSONObject()
                        .put("test_group_num", 0)
                        .put(
                            "challenge_tasks",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("id", 300000001L)
                                        .put("task_order", 0)
                                        .put("score", 5)
                                        .put("max_score", 5)
                                        .put("status", "DONE")
                                        .put(
                                            "question_elements",
                                            JSONArray().put(
                                                JSONObject()
                                                    .put("type", "content/text")
                                                    .put("text", "Демо-задание олимпиады: запишите ответ.")
                                            )
                                        )
                                        .put(
                                            "answer",
                                            JSONObject()
                                                .put("type", "answer/string")
                                                .put("text_input_rules", "nolimits")
                                        )
                                        .put(
                                            "user_answer",
                                            JSONObject()
                                                .put("@answer_type", "answer/string")
                                                .put("string", JSONArray().put("demo"))
                                        )
                                        .put(
                                            "answer_feedback",
                                            JSONObject()
                                                .put("correctness", "CORRECT")
                                                .put(
                                                    "correct_answer",
                                                    JSONObject().put("string", JSONArray().put("demo"))
                                                )
                                        )
                                )
                                .put(
                                    JSONObject()
                                        .put("id", 300000002L)
                                        .put("task_order", 1)
                                        .put("score", 0)
                                        .put("max_score", 5)
                                        .put("status", "MISSED")
                                        .put(
                                            "question_elements",
                                            JSONArray().put(
                                                JSONObject()
                                                    .put("type", "content/text")
                                                    .put("text", "Демо-задание без ответа.")
                                            )
                                        )
                                        .put(
                                            "answer",
                                            JSONObject()
                                                .put("type", "answer/string")
                                                .put("text_input_rules", "nolimits")
                                        )
                                        .put(
                                            "answer_feedback",
                                            JSONObject()
                                                .put("correctness", "INCORRECT")
                                                .put(
                                                    "correct_answer",
                                                    JSONObject().put("string", JSONArray().put("ответ"))
                                                )
                                        )
                                )
                        )
                )
            )
        val info = payload.getJSONObject("attempt_info")
        val tasks = parseResultTasks(payload)
        return OlympiadAttemptData(info = info, tasks = tasks)
    }
}

private fun pageUrlFor(link: String, ids: OlympiadAttemptIds): String {
    val direct = link.contains("uchebnik.mos.ru/contests/challenge/", ignoreCase = true) ||
        link.contains("uchebnik.mos.ru/exam/challenge/", ignoreCase = true)
    return if (direct) link else ids.pageUrl
}
