package ru.mesh.foss

import com.google.gson.Gson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mesh.foss.core.MeshCore
import ru.mesh.foss.core.UiLanguage
import ru.mesh.foss.data.model.MarkDetail
import ru.mesh.foss.data.model.MarkHistoryEntry
import ru.mesh.foss.data.model.MarkSnapshot
import ru.mesh.foss.feature.marks.markDateTime
import ru.mesh.foss.feature.marks.markFileExtension
import ru.mesh.foss.feature.marks.markFileIsImage
import ru.mesh.foss.feature.marks.markFileSizeLabel
import ru.mesh.foss.feature.marks.markHistoryChain
import ru.mesh.foss.feature.marks.markTimesDiffer

class MarkFilesTest {

    @Test
    fun parsesGradeAndStudentWorkFiles() {
        val json = """
            {
              "id": 1,
              "value": "5",
              "subject_name": "Test subject",
              "created_at": "2026-10-19T15:43:00",
              "updated_at": "2026-10-19T15:56:00",
              "result_files": [
                {
                  "id": 10,
                  "ordinal": 0,
                  "type": "jpg",
                  "size": "331342",
                  "file_url": "https://example.invalid/grade-file"
                }
              ],
              "student_work_result_files": [
                {
                  "id": 11,
                  "ordinal": 0,
                  "type": "pdf",
                  "size": "2048",
                  "file_url": "https://example.invalid/work-file"
                }
              ],
              "history": [
                {
                  "date": "2026-10-19",
                  "change_date": "2026-10-19T15:56:17",
                  "before": {
                    "id": 1,
                    "value": "4.0",
                    "weight": 1,
                    "is_exam": true,
                    "control_form_name": "Test form",
                    "comment_exists": false,
                    "created_at": "2026-10-19T15:43:15",
                    "updated_at": "2026-10-19T15:43:15"
                  },
                  "after": {
                    "id": 1,
                    "value": "5.0",
                    "weight": 2,
                    "is_exam": true,
                    "control_form_name": "Test form",
                    "comment_exists": true,
                    "created_at": "2026-10-19T15:43:15",
                    "updated_at": "2026-10-19T15:56:17"
                  }
                }
              ]
            }
        """.trimIndent()

        val detail = Gson().fromJson(json, MarkDetail::class.java)

        assertEquals("1", detail.id)
        assertEquals("2026-10-19T15:56:00", detail.updatedAt)
        assertEquals(1, detail.resultFiles.orEmpty().size)
        assertEquals(1, detail.studentWorkResultFiles.orEmpty().size)
        assertEquals("10", detail.resultFiles.orEmpty().single().id)
        assertEquals("jpg", detail.resultFiles.orEmpty().single().type)
        assertEquals("https://example.invalid/work-file", detail.studentWorkResultFiles.orEmpty().single().fileUrl)
        assertEquals(1, detail.history.orEmpty().size)
        assertEquals("4.0", detail.history.orEmpty().single().before?.value)
        assertEquals("5.0", detail.history.orEmpty().single().after?.value)
    }

    @Test
    fun classifiesImageAndDownloadExtensions() {
        assertEquals("jpeg", markFileExtension("image/jpeg"))
        assertEquals("jpg", markFileExtension("JPG"))
        assertEquals("pdf", markFileExtension("application/pdf"))
        assertEquals(
            "docx",
            markFileExtension("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        )
        assertTrue(markFileIsImage("image/png"))
        assertFalse(markFileIsImage("pdf"))
    }

    @Test
    fun formatsFileSize() {
        assertEquals("324 KB", markFileSizeLabel("331342"))
        assertEquals("1.0 MB", markFileSizeLabel("1048576"))
        assertEquals("", markFileSizeLabel("not-a-number"))
    }

    @Test
    fun keepsFilesFlagWhenNormalizingMark() {
        val mark = MeshCore.normalizeMark(
            JSONObject("""{"value":5,"weight":1,"subject_name":"Test","has_files":true}""")
        )
        assertTrue(mark.optBoolean("hasFiles"))
    }

    @Test
    fun formatsMarkDateTimes() {
        UiLanguage.select("ru")
        val created = markDateTime("2026-10-19T15:43:00")
        assertTrue(created.startsWith("19.10.2026"))
        assertTrue(created.endsWith("15:43:00"))
        assertEquals("", markDateTime(null))
    }

    @Test
    fun showsUpdatedTimeOnlyWhenItDiffers() {
        assertTrue(markTimesDiffer("2026-10-19T15:43:00", "2026-10-19T15:56:00"))
        assertFalse(markTimesDiffer("2026-10-19T15:43:00", "2026-10-19T15:43:00"))
        assertFalse(markTimesDiffer(null, null))
    }

    @Test
    fun buildsHistoryChainWithCurrentValueLast() {
        val history = listOf(
            MarkHistoryEntry(
                changeDate = "2026-10-19T15:56:17",
                before = MarkSnapshot(value = "4.0"),
                after = MarkSnapshot(value = "5.0")
            )
        )
        assertEquals(listOf("4", "5"), markHistoryChain(history, "5.0"))
        assertEquals(listOf("4", "5", "3"), markHistoryChain(history, "3"))
    }
}
