package ru.mesh.foss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mesh.foss.core.MeshCore
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.data.PreviewMockInterceptor

/**
 * JVM-тесты автономного предпросмотра (без сети и аккаунта МЭШ).
 */
class PreviewModeTest {

    private fun previewClient() = MeshClient(
        baseUrl = MeshCore.HOST,
        token = PreviewMockInterceptor.PREVIEW_TOKEN,
        profileId = PreviewMockInterceptor.PREVIEW_STUDENT_ID,
        previewMode = true
    )

    @Test
    fun previewClientReturnsProfileAndScheduleOffline() {
        val client = previewClient()
        val profile = client.fetchProfile()
        val child = profile.getJSONObject("defaultChild")
        assertEquals(PreviewMockInterceptor.PREVIEW_STUDENT_ID, child.getString("id"))
        assertEquals(PreviewMockInterceptor.PREVIEW_PERSON_ID, child.getString("personId"))

        val schedule = client.getSchedule(PreviewMockInterceptor.PREVIEW_STUDENT_ID)
        assertTrue(schedule.length() >= 5)
        assertEquals(PreviewMockInterceptor.PREVIEW_TOKEN, client.refreshToken())
    }

    @Test
    fun previewClientReturnsRankingMarksHomeworksAttendanceAndFood() {
        val client = previewClient()
        val today = MeshCore.today()

        val subjects = client.getSubjects(PreviewMockInterceptor.PREVIEW_PERSON_ID, today)
        assertTrue(subjects.length() >= 5)

        val rankBoard = client.getRankBoard(PreviewMockInterceptor.PREVIEW_PERSON_ID, today)
        assertTrue(rankBoard.getJSONArray("rows").length() >= 5)

        val marks = client.getMarks(PreviewMockInterceptor.PREVIEW_STUDENT_ID, MeshCore.addDays(today, -14), today)
        assertTrue(marks.isNotEmpty())
        assertTrue(client.getSubjectMarksDetailed(PreviewMockInterceptor.PREVIEW_STUDENT_ID).length() >= 5)

        val years = client.getAcademicYears()
        assertTrue(years.length() >= 1)
        assertTrue(
            client.getFinalMarks(
                PreviewMockInterceptor.PREVIEW_PERSON_ID,
                PreviewMockInterceptor.PREVIEW_STUDENT_ID,
                years.getJSONObject(0).getString("id")
            ).length() >= 5
        )

        val homeworks = client.getHomeworks(PreviewMockInterceptor.PREVIEW_STUDENT_ID, MeshCore.addDays(today, -7), today)
        assertTrue(homeworks.isNotEmpty())
        assertTrue(client.setHomeworkDone("pv-hw-1", "OO", true))

        val attendance = client.getAttendance(PreviewMockInterceptor.PREVIEW_STUDENT_ID, MeshCore.addDays(today, -7), today)
        assertFalse(attendance.attendance.isEmpty())
        val visits = client.getVisitDurations(PreviewMockInterceptor.PREVIEW_PERSON_ID, MeshCore.addDays(today, -7), today)
        assertFalse(visits.payload.isEmpty())

        val foodState = client.getFoodState(PreviewMockInterceptor.PREVIEW_PERSON_ID)
        assertTrue(foodState.optBoolean("preorderAllowed"))
        val balances = client.getFoodBalance(PreviewMockInterceptor.PREVIEW_PERSON_ID)
        assertTrue(balances.length() >= 1)
        val menu = client.getFoodMenu(PreviewMockInterceptor.PREVIEW_PERSON_ID, today, today)
        assertTrue(menu.length() >= 1)
    }

    @Test
    fun previewClientReturnsOlympiadsOffline() {
        val client = previewClient()

        val overview = client.getOlympiadOverview(showParticipantContests = true)
        assertTrue(overview.page.content.size >= 2)
        assertEquals(
            PreviewMockInterceptor.PREVIEW_OLYMPIAD_ID,
            overview.page.content.first().resolvedContestId
        )

        val detail = client.getOlympiadDetail(PreviewMockInterceptor.PREVIEW_OLYMPIAD_PAST_ID)
        assertEquals("Демо-олимпиада по информатике", detail.contestName)

        val result = client.getOlympiadResult(PreviewMockInterceptor.PREVIEW_OLYMPIAD_PAST_ID)
        assertTrue(result?.hasResult == true)
        assertEquals(18.0, result?.resultModel?.score?.mark ?: 0.0, 0.001)
    }
}
