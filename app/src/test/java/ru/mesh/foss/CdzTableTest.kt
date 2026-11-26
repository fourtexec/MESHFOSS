package ru.mesh.foss

import ru.mesh.foss.feature.cdz.cdzAbsoluteUrl
import ru.mesh.foss.feature.cdz.gridAnswer
import ru.mesh.foss.feature.cdz.hasAnswer
import ru.mesh.foss.feature.cdz.hasVisibleAnswer
import ru.mesh.foss.feature.cdz.isInlineChoice
import ru.mesh.foss.feature.cdz.parseGrid
import ru.mesh.foss.feature.cdz.parseGridValues
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
    fun `answer table trims header line breaks and expands hidden cells`() {
        val table = JSONObject()
            .put("rows", 1)
            .put("columns", 1)
            .put("columnHeaders", true)
            .put(
                "cells",
                JSONObject()
                    .put(
                        "0",
                        JSONObject().put("0", JSONArray().put("Энергия, Дж\n"))
                    )
                    .put(
                        "1",
                        JSONObject().put("0", JSONArray().put("42"))
                    )
            )
        val parsed = parseTable(
            JSONObject().put(
                "options",
                JSONArray().put(
                    JSONObject().put(
                        "content",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "content/table")
                                .put("table", table)
                        )
                    )
                )
            )
        )
        assertNotNull(parsed)
        parsed ?: return
        assertEquals("Энергия, Дж", parsed.value(0, 0))
        assertEquals("42", parsed.value(1, 0))
        assertEquals(2, parsed.rows)
        assertTrue(parsed.isInput(1, 0))
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
        assertFalse(hasVisibleAnswer(JSONObject().put("cells", JSONArray())))
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
    fun `answer grid reads headers and single select cells`() {
        val grid = parseGrid(gridQuestion())
        assertNotNull(grid)
        grid ?: return
        assertEquals(listOf(1, 2), grid.rows)
        assertEquals(listOf(1, 2), grid.columns)
        assertEquals("Плотность пара в сосуде", grid.cell(1, 1)?.text)
        assertTrue(grid.cell(1, 1)?.header == true)
        assertTrue(grid.cell(2, 1)?.input == true)
        assertEquals(3, grid.cell(2, 1)?.options?.size)
        assertEquals("увеличится", grid.cell(2, 1)?.options?.first()?.text)
    }

    @Test
    fun `answer grid round trips selected option ids`() {
        val grid = parseGrid(gridQuestion())
        val payload = JSONObject().put(
            "cells",
            JSONArray()
                .put(
                    JSONObject()
                        .put("input_type", "cell-input/single-select")
                        .put("row", 2)
                        .put("column", 2)
                        .put("option_id", "second-less")
                )
                .put(
                    JSONObject()
                        .put("input_type", "cell-input/single-select")
                        .put("row", 2)
                        .put("column", 1)
                        .put("option_id", "first-grow")
                )
        )
        val values = parseGridValues(payload)
        assertEquals(listOf("first-grow"), values[2 to 1]?.optionIds)
        assertEquals(listOf("second-less"), values[2 to 2]?.optionIds)

        val saved = gridAnswer("answer/grid", values, grid)
        assertTrue(hasAnswer(saved))
        assertTrue(hasVisibleAnswer(saved))
        val cells = saved.getJSONArray("cells")
        assertEquals(2, cells.length())
        assertEquals(2, cells.getJSONObject(0).getInt("row"))
        assertEquals(1, cells.getJSONObject(0).getInt("column"))
        assertEquals("first-grow", cells.getJSONObject(0).getString("option_id"))
        assertEquals("cell-input/single-select", cells.getJSONObject(0).getString("input_type"))
    }

    private fun gridQuestion(): JSONObject {
        fun text(value: String) = JSONObject()
            .put("type", "content/text")
            .put("text", value)
            .put("content", JSONArray())
            .put("text_styles", JSONArray())
        fun option(id: String, value: String) = JSONObject()
            .put("id", id)
            .put("text", value)
            .put("type", "option_type/default")
            .put("content", JSONArray())
            .put("text_styles", JSONArray())
        fun header(row: Int, column: Int, value: String) = JSONObject()
            .put("row", row)
            .put("column", column)
            .put("is_header", true)
            .put("type", "cell/attachment")
            .put("content", JSONArray().put(text(value)))
        fun select(row: Int, column: Int, prefix: String) = JSONObject()
            .put("row", row)
            .put("column", column)
            .put("type", "cell/input")
            .put("input_type", "cell-input/single-select")
            .put(
                "options",
                JSONArray()
                    .put(option("$prefix-grow", "увеличится"))
                    .put(option("$prefix-less", "уменьшится"))
                    .put(option("$prefix-same", "не изменится"))
            )

        return JSONObject()
            .put("type", "answer/grid")
            .put(
                "cells",
                JSONArray()
                    .put(header(1, 1, "Плотность пара в сосуде"))
                    .put(header(1, 2, "Масса жидкой воды"))
                    .put(select(2, 1, "first"))
                    .put(select(2, 2, "second"))
            )
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
