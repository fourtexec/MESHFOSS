package ru.mesh.foss

import ru.mesh.foss.feature.cdz.CdzHtmlBlock
import ru.mesh.foss.feature.cdz.CdzInlineSelection
import ru.mesh.foss.feature.cdz.cdzAtomicMediaType
import ru.mesh.foss.feature.cdz.gapAnswers
import ru.mesh.foss.feature.cdz.hasAnswer
import ru.mesh.foss.feature.cdz.imageGapPositions
import ru.mesh.foss.feature.cdz.imageGapSelection
import ru.mesh.foss.feature.cdz.hasVisibleAnswer
import ru.mesh.foss.feature.cdz.inlineAnswer
import ru.mesh.foss.feature.cdz.inlineSelections
import ru.mesh.foss.feature.cdz.inlineTextBlockStarts
import ru.mesh.foss.feature.cdz.isInlineChoice
import ru.mesh.foss.feature.cdz.mergeQuestionText
import ru.mesh.foss.feature.cdz.parseCdzHtml
import ru.mesh.foss.feature.cdz.parseContextInfo
import ru.mesh.foss.feature.cdz.parseInlineChoices
import ru.mesh.foss.feature.cdz.parseMath
import ru.mesh.foss.feature.cdz.parseMathContent
import ru.mesh.foss.feature.cdz.parseOptions
import ru.mesh.foss.feature.cdz.parseTasks
import ru.mesh.foss.feature.cdz.parseTextSpans
import ru.mesh.foss.feature.cdz.textAnswers
import ru.mesh.foss.feature.cdz.textInputAnswers

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
    fun `inline styles use their own text block offset`() {
        val text = "Работа Ап — энергия Qн."
        val styles = JSONArray()
            .put(
                JSONObject()
                    .put("text_block_index", 0)
                    .put("position", 8)
                    .put("offset", 1)
                    .put("content", JSONArray().put("SUB"))
            )
            .put(
                JSONObject()
                    .put("text_block_index", 1)
                    .put("position", 9)
                    .put("offset", 1)
                    .put("content", JSONArray().put("SUB"))
            )

        val spans = parseTextSpans(text, styles, listOf(0, 12, text.length))

        assertEquals(listOf(8, 21), spans.map { it.start })
        assertEquals(listOf("п", "н"), spans.map { text.substring(it.start, it.end) })
        assertTrue(spans.all { it.tokens == setOf("SUB") })
    }

    @Test
    fun `inline block starts follow gap positions`() {
        assertEquals(listOf(0, 63, 74, 358), inlineTextBlockStarts(358, listOf(74, 63, 74)))
        assertEquals(listOf(0, 10), inlineTextBlockStarts(10, emptyList()))
    }

    @Test
    fun `SUPER style uses draft block index after math insertion`() {
        val content = JSONArray().put(
            JSONObject()
                .put("type", "content/math")
                .put("position", 1)
                .put("content", "x")
        )
        val styles = JSONArray().put(
            JSONObject()
                .put("text_block_index", 1)
                .put("position", 0)
                .put("offset", 2)
                .put("content", JSONArray().put("SUPER"))
        )

        val merged = mergeQuestionText("abc", content, styles)
        val span = merged.spans.single()

        assertEquals("a x bc", merged.text)
        assertEquals("bc", merged.text.substring(span.start, span.end))
        assertEquals(setOf("SUPER"), span.tokens)
    }

    @Test
    fun `math content renders chemical subscripts`() {
        assertEquals("H₂S, SO₂", parseMathContent("{H_2S} , {SO_2}"))
        assertEquals(
            "Na₂O - NO - B₂O₃",
            parseMathContent("{Na_2O} - {NO} - {B_2O_3}")
        )
        assertEquals("x² + H₂O", parseMathContent("x^{2} + H_2O"))
    }

    @Test
    fun `math parser keeps cyrillic scripts as baseline spans`() {
        val math = parseMath("p_{мат} + v_{М0}")

        assertEquals("pмат + vМ0", math.text)
        val subscripts = math.spans.filter { "SUB" in it.tokens }
        assertEquals(
            listOf("мат", "М0"),
            subscripts.map { math.text.substring(it.start, it.end) },
        )
        assertTrue(math.inlines.isEmpty())
    }

    @Test
    fun `html renderer skips hidden text and renders editor formulas`() {
        val document = parseCdzHtml(
            """
            <p>Ответ: <span data-w-e-type="formula" data-value="x^2"></span></p>
            <p data-w-e-type="invisibleText" style="font-size: 0px; color: white;">СЕКРЕТ</p>
            """.trimIndent()
        )
        val text = document.blocks
            .filterIsInstance<CdzHtmlBlock.Rich>()
            .joinToString("\n") { it.content.text }
            .trim()

        assertEquals("Ответ: x²", text)
    }

    @Test
    fun `context info keeps math inside the text block`() {
        val result = JSONObject().put(
            "groups_context_info",
            JSONObject().put(
                "0",
                JSONObject()
                    .put("caption", "Общее условие")
                    .put(
                        "content",
                        JSONObject()
                            .put("text", "Дано: ")
                            .put(
                                "content",
                                JSONArray().put(
                                    JSONObject()
                                        .put("type", "content/math")
                                        .put("position", 6)
                                        .put("content", "v_{М0}")
                                )
                            )
                    )
            )
        )

        val contexts = parseContextInfo(result)
        assertEquals(1, contexts.size)
        assertEquals("Общее условие", contexts.single().caption)
        val textBlock = contexts.single().elements.getJSONObject(0)
        assertEquals(1, textBlock.getJSONArray("content").length())
    }

    @Test
    fun `math content renders angle prime and leq symbols`() {
        assertEquals("∠", parseMathContent("\\angle"))
        assertEquals("′", parseMathContent("\\prime"))
        assertEquals("x ≤ 5", parseMathContent("x \\leq 5"))
        assertEquals("1 × 1: 2", parseMathContent("1 \\times 1 : 2"))
        assertEquals("100%", parseMathContent("100\\%"))
    }

    @Test
    fun `math content is inserted at positions instead of appended`() {
        val content = JSONArray()
            .put(
                JSONObject()
                    .put("type", "content/math")
                    .put("position", 5)
                    .put("content", "x")
            )
            .put(
                JSONObject()
                    .put("type", "content/math")
                    .put("position", 16)
                    .put("content", "x^3 + 4x = 8.")
            )
            .put(
                JSONObject()
                    .put("type", "content/math")
                    .put("position", 23)
                    .put("content", "x^7 + 64x^2 + 7.")
            )

        val merged = mergeQuestionText("Числотаково, чтоНайдите", content)

        assertEquals(
            "Число x таково, что x³ + 4x = 8. Найдите x⁷ + 64x² + 7.",
            merged.text,
        )
        assertTrue(merged.spans.isEmpty())
    }

    @Test
    fun `merged text removes spaces before punctuation and keeps ellipsis`() {
        assertEquals(
            "Число. Ответ: да, нет",
            mergeQuestionText("Число . Ответ : да ,  нет", null).text,
        )
        assertEquals(
            "Слово ...",
            mergeQuestionText("Слово ...", null).text,
        )
    }

    @Test
    fun `merged text normalization shifts text styles`() {
        val styles = JSONArray().put(
            JSONObject()
                .put("position", 5)
                .put("offset", 3)
                .put("content", JSONArray().put("BOLD"))
        )

        val merged = mergeQuestionText("да , нет", null, styles)
        val span = merged.spans.single()

        assertEquals("да, нет", merged.text)
        assertEquals("нет", merged.text.substring(span.start, span.end))
    }

    @Test
    fun `math insertion keeps existing spaces and shifts text styles`() {
        val content = JSONArray().put(
            JSONObject()
                .put("type", "content/math")
                .put("position", 5)
                .put("content", "x")
        )
        val styles = JSONArray().put(
            JSONObject()
                .put("position", 0)
                .put("offset", 5)
                .put("content", JSONArray().put("BOLD"))
        )

        val merged = mergeQuestionText("Число таково", content, styles)

        assertEquals("Число x таково", merged.text)
        assertEquals(0, merged.spans.single().start)
        assertEquals(5, merged.spans.single().end)
    }

    @Test
    fun `gap text input answers are readable and visible`() {
        val answer = JSONObject()
            .put("@answer_type", "answer/gap/text/input")
            .put(
                "text_input_answer",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("position_id", "p1")
                            .put("input_strings", JSONArray().put("12"))
                    )
                    .put(
                        JSONObject()
                            .put("position_id", "p2")
                            .put("input_strings", JSONArray().put("34"))
                    )
                    .put(
                        JSONObject()
                            .put("position_id", "p1")
                            .put("input_strings", JSONArray().put("12"))
                    )
            )

        assertEquals(listOf("12", "34"), textInputAnswers(answer))
        assertTrue(hasVisibleAnswer(answer))
        assertTrue(hasAnswer(answer))
    }

    @Test
    fun `gap answers follow question position order and keep variants`() {
        fun item(positionId: String, vararg values: String): JSONObject {
            val strings = JSONArray()
            values.forEach { strings.put(it) }
            return JSONObject()
                .put("position_id", positionId)
                .put("input_strings", strings)
        }

        val positions = JSONArray()
            .put(JSONObject().put("position_id", "p1"))
            .put(JSONObject().put("position_id", "p2"))
            .put(JSONObject().put("position_id", "p3"))
            .put(JSONObject().put("position_id", "p4"))
        val answer = JSONObject()
            .put("@answer_type", "answer/gap/text/input")
            .put(
                "text_input_answer",
                JSONArray()
                    .put(item("p3", "30"))
                    .put(item("p1", "10"))
                    .put(item("p2", "20", "21"))
            )

        val gaps = gapAnswers(answer, positions)

        assertEquals(listOf(0, 1, 2, 3), gaps.map { it.index })
        assertEquals(listOf("10"), gaps[0].values)
        assertEquals(listOf("20", "21"), gaps[1].values)
        assertEquals(listOf("30"), gaps[2].values)
        assertTrue(gaps[3].values.isEmpty())
    }

    @Test
    fun `gap answers are inserted into question text at raw positions`() {
        val text = "Первый? Второй?"
        val positions = JSONArray()
            .put(
                JSONObject()
                    .put("position_id", "p1")
                    .put("text_id", "t1")
                    .put("position", 7)
            )
            .put(
                JSONObject()
                    .put("position_id", "p2")
                    .put("text_id", "t1")
                    .put("position", text.length)
            )
        val payload = JSONObject()
            .put(
                "text_input_answer",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("position_id", "p2")
                            .put("input_strings", JSONArray().put("2,4"))
                    )
                    .put(
                        JSONObject()
                            .put("position_id", "p1")
                            .put("input_strings", JSONArray().put("1000"))
                    )
            )

        val gaps = gapAnswers(payload, positions)
        val merged = mergeQuestionText(text, null, null, gaps)

        assertEquals(text, merged.text)
        assertEquals(listOf(7, text.length), merged.placeholders.map { it.position })
        assertEquals(listOf("1000", "2,4"), merged.placeholders.map { it.alternateText })
        assertEquals(listOf("p1", "p2"), gaps.map { it.positionId })
        assertEquals(listOf("t1", "t1"), gaps.map { it.textId })
    }

    @Test
    fun `text answers keep all acceptable variants`() {
        val payload = JSONObject()
            .put("number", JSONArray().put("10").put("10.0"))

        assertEquals(listOf("10", "10.0"), textAnswers(payload))
    }

    @Test
    fun `image selection positions use background order and stay visible`() {
        val answer = JSONObject()
            .put(
                "image_gaps_positions",
                JSONArray()
                    .put(JSONObject().put("position_id", "a"))
                    .put(JSONObject().put("position_id", "b"))
                    .put(JSONObject().put("position_id", "c"))
            )
        val payload = JSONObject()
            .put("@answer_type", "answer/gap/match/background/image/selection")
            .put("position_ids", JSONArray().put("c").put("a"))

        val positions = imageGapPositions(answer)

        assertEquals(listOf(1, 3), imageGapSelection(payload, positions))
        assertTrue(hasVisibleAnswer(payload))
        assertTrue(hasAnswer(payload))
    }

    @Test
    fun `option math content is not rendered as a bare Variant`() {
        val option = JSONObject()
            .put("id", "math-option")
            .put(
                "content",
                JSONArray().put(
                    JSONObject()
                        .put("type", "content/math")
                        .put("content", "{Na_2S}")
                )
            )
        val parsed = parseOptions(
            JSONObject().put("options", JSONArray().put(option))
        ).single()
        assertEquals("Na₂S", parsed.text)
    }

    @Test
    fun `option text keeps math at its position`() {
        val option = JSONObject()
            .put("id", "math-option")
            .put("text", "Куб")
            .put(
                "content",
                JSONArray().put(
                    JSONObject()
                        .put("type", "content/math")
                        .put("position", 3)
                        .put("content", "11 \\times 11")
                )
            )

        val parsed = parseOptions(
            JSONObject().put("options", JSONArray().put(option))
        ).single()

        assertEquals("Куб 11 × 11", parsed.text)
    }

    @Test
    fun `atomic content without type is treated as photo when it has an image url`() {
        val photo = JSONObject()
            .put("type", "content/atomic")
            .put("relative_url", "/system/atomic_objects/files/table.png")

        assertEquals("image", cdzAtomicMediaType(photo))
    }

    @Test
    fun `atomic video stays video`() {
        val video = JSONObject()
            .put("type", "content/atomic")
            .put("atomic_type", "video")
            .put("relative_url", "/video/lesson.mp4")

        assertEquals("video", cdzAtomicMediaType(video))
    }

    @Test
    fun `option photo is parsed as image and not as a video`() {
        val option = JSONObject()
            .put("id", "photo-option")
            .put(
                "content",
                JSONArray().put(
                    JSONObject()
                        .put("type", "content/atomic")
                        .put("relative_url", "/system/atomic_objects/files/photo.png")
                )
            )
        val parsed = parseOptions(
            JSONObject().put("options", JSONArray().put(option))
        ).single()

        assertEquals("", parsed.text)
        assertTrue(parsed.imageUrl.endsWith("photo.png"))
        assertEquals(null, parsed.video)
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
