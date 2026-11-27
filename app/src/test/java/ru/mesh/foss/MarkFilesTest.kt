package ru.mesh.foss

import com.google.gson.Gson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mesh.foss.core.MeshCore
import ru.mesh.foss.data.model.MarkDetail
import ru.mesh.foss.feature.marks.markFileExtension
import ru.mesh.foss.feature.marks.markFileIsImage
import ru.mesh.foss.feature.marks.markFileSizeLabel

class MarkFilesTest {

    @Test
    fun parsesGradeAndStudentWorkFiles() {
        val json = """
            {
              "id": 1,
              "value": "4",
              "subject_name": "Test subject",
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
              ]
            }
        """.trimIndent()

        val detail = Gson().fromJson(json, MarkDetail::class.java)

        assertEquals("1", detail.id)
        assertEquals(1, detail.resultFiles.orEmpty().size)
        assertEquals(1, detail.studentWorkResultFiles.orEmpty().size)
        assertEquals("10", detail.resultFiles.orEmpty().single().id)
        assertEquals("jpg", detail.resultFiles.orEmpty().single().type)
        assertEquals("https://example.invalid/work-file", detail.studentWorkResultFiles.orEmpty().single().fileUrl)
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
}
