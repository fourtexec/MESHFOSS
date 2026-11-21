package ru.mesh.foss

import ru.mesh.foss.feature.cdz.cdzAbsoluteUrl
import ru.mesh.foss.feature.cdz.hasAnswer
import ru.mesh.foss.feature.cdz.hasVisibleAnswer
import ru.mesh.foss.feature.cdz.isInlineChoice
import ru.mesh.foss.feature.cdz.parseInlineChoices
import ru.mesh.foss.feature.cdz.parseOptions
import ru.mesh.foss.feature.cdz.parseTable
import ru.mesh.foss.feature.cdz.parseTableValues
import ru.mesh.foss.feature.cdz.tableAnswer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Таблицы, относительные картинки и gap/match/text из реальных ответов ЦДЗ. */
class CdzTableTest {

    private fun tableQuestion(): JSONObject {
        val table = JSONObject()
            .put("rows", 2)
            .put("columns", 2)
            .put("columnHeaders", true)
            .put("rowHeaders", false)
            .put(
                "cells",
                JSONObject().put(
                    "0",
                    JSONObject()
                        .put("0", JSONArray().put("Жизнеспособность"))
                        .put("1", JSONArray().put("Количество хлорофилла"))
                )
            )
        return JSONObject()
            .put("type", "answer/table")
            .put(
                "options",
                JSONArray().put(
                    JSONObject().put(
                        "id", "table-option"
                    ).put(
                        "content",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "content/table")
                                .put("table", table)
                        )
                    )
                )
            )
            .put(
                "cell_types",
                JSONObject().put(
                    "1",
                    JSONObject()
                        .put("0", "MANUAL_INPUT")
                        .put("1", "MANUAL_INPUT")
                )
            )
    }

    @Test
    fun `answer table reads headers and input cells`() {
        val table = parseTable(tableQuestion())
        assertNotNull(table)
        table ?: return
        assertEquals(2, table.rows)
        assertEquals(2, table.columns)
        assertEquals("Жизнеспособность", table.value(0, 0))
        assertEquals("Количество хлорофилла", table.value(0, 1))
        assertFalse(table.isInput(0, 0))
        assertTrue(table.isInput(1, 0))
        assertTrue(table.isInput(1, 1))
    }

    @Test
    fun `answer table round trips through user cells`() {
        val payload = JSONObject().put(
            "cells",
            JSONObject().put(
                "1",
                JSONObject()
                    .put("0", JSONArray().put("3"))
                    .put("1", JSONArray().put("1"))
            )
        )
        val values = parseTableValues(payload)
        assertEquals("3", values[1 to 0])
        assertEquals("1", values[1 to 1])

        val saved = tableAnswer("answer/table", values)
        assertTrue(hasAnswer(saved))
        assertEquals(
            "3",
            saved.getJSONObject("cells")
                .getJSONObject("1")
                .getJSONArray("0")
                .getString(0)
        )
    }

    @Test
    fun `hidden result answers are not shown as correct`() {
        assertFalse(hasVisibleAnswer(JSONObject().put("id", JSONObject.NULL)))
        assertFalse(hasVisibleAnswer(JSONObject().put("ids", JSONArray())))
        assertFalse(hasVisibleAnswer(JSONObject().put("match", JSONObject())))
        assertFalse(
            hasVisibleAnswer(
                JSONObject().put(
                    "cells",
                    JSONObject().put(
                        "1",
                        JSONObject().put("0", JSONArray().put(""))
                    )
                )
            )
        )
        assertTrue(
            hasVisibleAnswer(
                JSONObject().put("ids", JSONArray().put("option-1"))
            )
        )
    }

    @Test
    fun `relative atomic images use uchebnik origin`() {
        assertEquals(
            "https://uchebnik.mos.ru/system/atomic_objects/files/013/588/568/original/a.png",
            cdzAbsoluteUrl("/system/atomic_objects/files/013/588/568/original/a.png")
        )
        assertEquals(
            "https://uchebnik.mos.ru/cms/system/a.png",
            cdzAbsoluteUrl("https://uchebnik.mos.ru/cms/system/a.png")
        )
    }

    @Test
    fun `relative option image is normalized in parseOptions`() {
        val option = JSONObject()
            .put("id", "image-option")
            .put(
                "content",
                JSONArray().put(
                    JSONObject()
                        .put("atomic_type", "image")
                        .put("relative_url", "/system/a.png")
                )
            )
        val parsed = parseOptions(JSONObject().put("options", JSONArray().put(option))).single()
        assertEquals("https://uchebnik.mos.ru/system/a.png", parsed.imageUrl)
    }

    @Test
    fun `group headers are marked separately from items`() {
        val group = JSONObject()
            .put("id", "group-1")
            .put("text", "Инфузории")
            .put("type", "option_type/group")
        val item = JSONObject()
            .put("id", "item-1")
            .put("text", "Питается бактериями")
            .put("type", "option_type/default")
        val parsed = parseOptions(
            JSONObject().put("options", JSONArray().put(group).put(item))
        )
        assertTrue(parsed[0].isGroup)
        assertFalse(parsed[1].isGroup)
        assertEquals("Инфузории", parsed[0].text)
    }

    @Test
    fun `gap match text uses shared options for every position`() {
        val option = JSONObject().put("id", "option-1").put("text", "топливо")
        val position = JSONObject()
            .put("position_id", "position-1")
            .put("text_id", "text-1")
            .put("position", 4)
        val answer = JSONObject()
            .put("type", "answer/gap/match/text")
            .put("text_position", JSONArray().put(position))
            .put("options", JSONArray().put(option))

        val choices = parseInlineChoices(answer)
        assertEquals(1, choices.size)
        assertEquals("option-1", choices.single().options.single().id)
        assertTrue(isInlineChoice("answer/gap/match/text"))
    }
}
