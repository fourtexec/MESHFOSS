package ru.mesh.foss

import ru.mesh.foss.feature.cdz.CdzInlineSelection
import ru.mesh.foss.feature.cdz.hasAnswer
import ru.mesh.foss.feature.cdz.inlineAnswer
import ru.mesh.foss.feature.cdz.inlineSelections
import ru.mesh.foss.feature.cdz.isInlineChoice
import ru.mesh.foss.feature.cdz.parseInlineChoices
import ru.mesh.foss.feature.cdz.parseOptions
import ru.mesh.foss.feature.cdz.parseTasks
import ru.mesh.foss.feature.cdz.parseTextSpans

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Химические формулы и inline-задания: чистый разбор без Compose. */
class CdzRichTextTest {

    @Test
    fun `SUB span trims trailing space and keeps formula text`() {
        val styles = JSONArray().put(
            JSONObject()
                .put("position", 7)
                .put("offset", 2)
                .put("content", JSONArray().put("SUB"))
        )

        val spans = parseTextSpans("4P + 5O2 →", styles)

        assertEquals(1, spans.size)
        assertEquals(7, spans[0].start)
        assertEquals(8, spans[0].end)
        assertEquals(setOf("SUB"), spans[0].tokens)
    }

    @Test
    fun `option styles shift after outer spaces are trimmed`() {
        val option = JSONObject()
            .put("id", "formula")
            .put("text", " H2O ")
            .put(
                "text_styles",
                JSONArray().put(
                    JSONObject()
                        .put("position", 2)
                        .put("offset", 1)
                        .put("content", JSONArray().put("SUB"))
                )
            )
        val answer = JSONObject().put("options", JSONArray().put(option))

        val parsed = parseOptions(answer).single()

        assertEquals("H2O", parsed.text)
        assertEquals(1, parsed.textSpans.single().start)
        assertEquals(setOf("SUB"), parsed.textSpans.single().tokens)
    }

    @Test
    fun `inline choices round trip through server answer`() {
        val inline = JSONObject()
            .put("position_id", "position-1")
            .put("text_id", "text-1")
            .put("position", 4)
            .put(
                "options",
                JSONArray().put(
                    JSONObject()
                        .put("id", "option-1")
                        .put("text", "медуза")
                )
            )
        val answer = JSONObject()
            .put("type", "answer/inline/choice/single")
            .put("text_position", JSONArray().put(inline))

        val choices = parseInlineChoices(answer)
        assertEquals(1, choices.size)
        assertEquals("position-1", choices.single().positionId)

        val body = inlineAnswer(
            "answer/inline/choice/single",
            listOf(
                CdzInlineSelection(
                    textId = choices.single().textId,
                    positionId = choices.single().positionId,
                    optionId = choices.single().options.single().id,
                )
            ),
        )
        val saved = body.getJSONArray("text_position_answer").getJSONObject(0)

        assertEquals("text-1", saved.getString("text_id"))
        assertEquals("position-1", saved.getString("position_id"))
        assertEquals("option-1", saved.getString("id"))
        assertEquals("option-1", inlineSelections(body)["position-1"])
        assertTrue(hasAnswer(body))
        assertTrue(isInlineChoice("answer/inline/choice/single"))
    }

    @Test
    fun `tasks keep group order and task_order`() {
        fun task(id: Long, order: Int): JSONObject = JSONObject()
            .put("id", id)
            .put("task_order", order)
            .put("answer", JSONObject().put("type", "answer/string"))
            .put("question_elements", JSONArray())

        val attempt = JSONObject().put(
            "challenge_test_groups",
            JSONArray()
                .put(
                    JSONObject()
                        .put("test_group_num", 0)
                        .put(
                            "challenge_tasks",
                            JSONArray()
                                .put(task(10, 2))
                                .put(task(11, 0))
                        )
                )
                .put(
                    JSONObject()
                        .put("test_group_num", 1)
                        .put("challenge_tasks", JSONArray().put(task(12, 0)))
                )
        )

        assertEquals(listOf(11L, 10L, 12L), parseTasks(attempt).map { it.id })
    }
}
