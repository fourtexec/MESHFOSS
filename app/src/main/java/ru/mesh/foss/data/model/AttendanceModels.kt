package ru.mesh.foss.data.model

import com.google.gson.annotations.SerializedName

/** Ответ `family/web/v1/attendance` — пропуски и причины по дням. */
data class AttendanceResponse(
    @SerializedName("attendance") val attendance: List<AttendanceDay> = emptyList(),
    @SerializedName("days_count") val daysCount: Int = 0,
    @SerializedName("year_description") val yearDescription: String? = null,
)

data class AttendanceDay(
    @SerializedName("date") val date: String = "",
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("notified") val notified: Boolean? = null,
    @SerializedName("lessons") val lessons: List<AttendanceLesson> = emptyList(),
    @SerializedName("description") val description: String? = null,
    @SerializedName("reason_id") val reasonId: Int? = null,
    @SerializedName("is_system") val isSystem: Boolean? = null,
    @SerializedName("parent_profile_id") val parentProfileId: Long? = null,
)

data class AttendanceLesson(
    @SerializedName("subject_id") val subjectId: Long? = null,
    @SerializedName("subject_name") val subjectName: String? = null,
    @SerializedName("bell_id") val bellId: Long? = null,
    @SerializedName("notified") val notified: Boolean? = null,
    @SerializedName("reason_id") val reasonId: Int? = null,
    @SerializedName("schedule_item_id") val scheduleItemId: Long? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("lesson_education_type") val lessonEducationType: String? = null,
    @SerializedName("health_status") val healthStatus: String? = null,
)

/** Ответ `pass/entrances/v1/visit_durations` — фактическое время входа и выхода. */
data class VisitDurationsResponse(
    @SerializedName("payload") val payload: List<VisitDay> = emptyList(),
)

data class VisitDay(
    @SerializedName("date") val date: String = "",
    @SerializedName("visits") val visits: List<VisitEntry> = emptyList(),
)

data class VisitEntry(
    @SerializedName("in") val enteredAt: String? = null,
    @SerializedName("out") val leftAt: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("personIn") val personIn: String? = null,
    @SerializedName("personOut") val personOut: String? = null,
    @SerializedName("kindId") val kindId: Int? = null,
    @SerializedName("kindName") val kindName: String? = null,
    @SerializedName("isIncomplete") val isIncomplete: Boolean? = null,
    @SerializedName("organizationId") val organizationId: Long? = null,
    @SerializedName("organizationName") val organizationName: String? = null,
    @SerializedName("organizationShortName") val organizationShortName: String? = null,
    @SerializedName("organizationAddress") val organizationAddress: String? = null,
)
