package ru.mesh.foss.feature.attendance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mesh.foss.data.MeshClient
import ru.mesh.foss.core.MeshCore
import ru.mesh.foss.data.model.AttendanceResponse
import ru.mesh.foss.data.model.VisitDurationsResponse

/** Один снимок посещаемости: пропуски по урокам и фактические проходы. */
data class AttendanceSnapshot(
    val absences: AttendanceResponse,
    val visits: VisitDurationsResponse,
    val from: String,
    val to: String,
)

/** Контракт загрузки посещаемости — экран не знает, откуда берутся данные. */
interface AttendanceRepository {
    suspend fun load(studentId: String, personId: String, from: String, to: String): AttendanceSnapshot
}

/** Реализация поверх двух подтверждённых endpoint'ов МЭШ. */
class MeshAttendanceRepository(
    private val client: MeshClient,
) : AttendanceRepository {

    override suspend fun load(
        studentId: String,
        personId: String,
        from: String,
        to: String,
    ): AttendanceSnapshot = withContext(Dispatchers.IO) {
        val absences = client.getAttendance(studentId, from, to)
        val visits = client.getVisitDurations(personId, from, to)
        AttendanceSnapshot(absences, visits, from, to)
    }
}

/**
 * Неделя для запроса. МЭШ принимает `from`/`to` включительно; начинаем с
 * понедельника, чтобы ответы совпадали с дневником.
 */
fun attendanceWeekStart(iso: String = MeshCore.today()): String {
    val calendar = MeshCore.parseIso(iso)
    val day = calendar.get(java.util.Calendar.DAY_OF_WEEK)
    val shift = if (day == java.util.Calendar.SUNDAY) -6 else java.util.Calendar.MONDAY - day
    return MeshCore.addDays(iso, shift)
}

fun attendanceWeekEnd(start: String): String = MeshCore.addDays(start, 6)
