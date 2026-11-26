package ru.mesh.foss

import ru.mesh.foss.feature.cdz.CdzResultTask
import ru.mesh.foss.feature.cdz.CdzTask
import ru.mesh.foss.feature.cdz.prefillAnswers

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Подстановка верных ответов из прошлых попыток — только в локальный черновик. */
class CdzPrefillTest {

    private fun task(id: Long, question: String = "Вопрос $id"): CdzTask {
        val questionJson = JSONArray().put(
            JSONObject()
                .put("type", "content/text")
                .put("text", question)
        )
        val answer = JSONObject()
            .put("type", "answer/single")
            .put("answer.type", "answer/single")
            .put(
                "options",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "opt-$id")
                            .put("text", "Вариант $id")
                    )
                    .put(
                        JSONObject()
                            .put("id", "extra-$id")
                            .put("text", "Дополнительный вариант $id")
                    )
            )
            .put("right_answer", JSONObject().put("id", "opt-$id"))
        return CdzTask(
            id = id,
            group = 0,
            order = id.toInt(),
            type = "answer/single",
            question = questionJson,
            answer = answer,
            serverAnswer = null,
        )
    }

    private fun answer(id: Long): JSONObject = JSONObject()
        .put("@answer_type", "answer/single")
        .put("id", "opt-$id")

    private fun result(
        source: CdzTask,
        userAnswer: JSONObject = answer(source.id),
        correctness: String = "CORRECT",
        question: JSONArray = JSONArray(source.question.toString()),
        status: String = "DONE",
    ): CdzResultTask {
        // В результате сервер скрывает right_answer — сравнение не должно
        // из-за этого считать задание другим.
        val answer = JSONObject(source.answer.toString()).apply { remove("right_answer") }
        return CdzResultTask(
            id = source.id,
            group = source.group,
            order = source.order,
            question = question,
            answer = answer,
            userAnswer = userAnswer,
            feedback = JSONObject().put("correctness", correctness),
            score = 1.0,
            maxScore = 1.0,
            status = status,
        )
    }

    @Test
    fun `подставляет только верные и полностью одинаковые задания`() {
        val first = task(1)
        val second = task(2)
        val third = task(3)
        val changedQuestion = JSONArray().put(
            JSONObject().put("type", "content/text").put("text", "Другой вопрос")
        )

        val prefilled = prefillAnswers(
            tasks = listOf(first, second, third),
            previous = listOf(
                result(first),
                result(second, correctness = "INCORRECT"),
                result(third, question = changedQuestion),
            ),
            draft = emptyMap(),
            saved = emptySet(),
        )

        assertEquals(setOf(1L), prefilled.keys)
        assertEquals("opt-1", prefilled.getValue(1L).getString("id"))
    }

    @Test
    fun `не перезаписывает черновик и уже сохранённые ответы`() {
        val first = task(1)
        val second = task(2)
        val third = task(3)
        val previous = listOf(result(first), result(second), result(third))

        val prefilled = prefillAnswers(
            tasks = listOf(first, second, third),
            previous = previous,
            draft = mapOf(1L to answer(99)),
            saved = setOf(2L),
        )

        assertFalse(prefilled.containsKey(1L))
        assertFalse(prefilled.containsKey(2L))
        assertTrue(prefilled.containsKey(3L))
    }

    @Test
    fun `при нескольких верных ответах побеждает более новая попытка`() {
        val source = task(1)
        val newer = answer(1).put("id", "newer")
        val older = answer(1).put("id", "older")

        val prefilled = prefillAnswers(
            tasks = listOf(source),
            previous = listOf(result(source, userAnswer = newer), result(source, userAnswer = older)),
            draft = emptyMap(),
            saved = emptySet(),
        )

        assertEquals("newer", prefilled.getValue(1L).getString("id"))
    }

    @Test
    fun `ответ на проверке учителя не считается верным`() {
        val source = task(1)

        val prefilled = prefillAnswers(
            tasks = listOf(source),
            previous = listOf(result(source, status = "AWAIT_FOR_REVIEW")),
            draft = emptyMap(),
            saved = emptySet(),
        )

        assertTrue(prefilled.isEmpty())
    }

    @Test
    fun `допускает другой порядок вариантов ответа`() {
        val source = task(1)
        val previous = result(source)
        val reordered = JSONObject(previous.answer.toString()).apply {
            val options = getJSONArray("options")
            put(
                "options",
                JSONArray()
                    .put(options.getJSONObject(1))
                    .put(options.getJSONObject(0))
            )
        }

        val prefilled = prefillAnswers(
            tasks = listOf(source),
            previous = listOf(previous.copy(answer = reordered)),
            draft = emptyMap(),
            saved = emptySet(),
        )

        assertEquals(setOf(1L), prefilled.keys)
    }

    @Test
    fun `при продолжении попытки заполняет только пустые задания`() {
        val answered = task(1).copy(serverAnswer = answer(1))
        val empty = task(2)

        val prefilled = prefillAnswers(
            tasks = listOf(answered, empty),
            previous = listOf(result(task(1)), result(task(2))),
            draft = mapOf(1L to answer(1)),
            saved = setOf(1L),
        )

        assertEquals(setOf(2L), prefilled.keys)
    }

    @Test
    fun `не подставляет задание с изменённым вариантом ответа`() {
        val source = task(1)
        val previous = result(source)
        val changed = JSONObject(previous.answer.toString()).apply {
            getJSONArray("options").getJSONObject(0).put("text", "Изменённый вариант")
        }

        val prefilled = prefillAnswers(
            tasks = listOf(source),
            previous = listOf(previous.copy(answer = changed)),
            draft = emptyMap(),
            saved = emptySet(),
        )

        assertTrue(prefilled.isEmpty())
    }
}
