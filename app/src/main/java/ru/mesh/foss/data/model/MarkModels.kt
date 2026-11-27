package ru.mesh.foss.data.model

import com.google.gson.annotations.SerializedName

/**
 * Подробная информация об оценке.
 *
 * `result_files` — файлы, прикреплённые учителем к самой оценке (например,
 * скан контрольной). `student_work_result_files` — файлы работы ученика.
 * Оба массива приходят только в детальном ответе `/marks/{markId}`, поэтому
 * список оценок показывает лишь индикатор `has_files`.
 */
data class MarkDetail(
    @SerializedName("id") val id: String? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("weight") val weight: Double? = null,
    @SerializedName("subject_name") val subjectName: String? = null,
    @SerializedName("control_form_name") val controlFormName: String? = null,
    @SerializedName("date") val date: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("comment") val comment: String? = null,
    @SerializedName("teacher") val teacher: MarkTeacher? = null,
    @SerializedName("activity") val activity: MarkActivity? = null,
    @SerializedName("class_results") val classResults: MarkClassResults? = null,
    @SerializedName("result_files") val resultFiles: List<MarkFile>? = null,
    @SerializedName("student_work_result_files") val studentWorkResultFiles: List<MarkFile>? = null,
    @SerializedName("history") val history: List<MarkHistoryEntry>? = null,
)

/** Одна запись истории изменения оценки. */
data class MarkHistoryEntry(
    @SerializedName("date") val date: String? = null,
    @SerializedName("change_date") val changeDate: String? = null,
    @SerializedName("before") val before: MarkSnapshot? = null,
    @SerializedName("after") val after: MarkSnapshot? = null,
)

/** Снимок полей оценки до или после изменения. */
data class MarkSnapshot(
    @SerializedName("id") val id: String? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("weight") val weight: Double? = null,
    @SerializedName("is_point") val isPoint: Boolean? = null,
    @SerializedName("is_exam") val isExam: Boolean? = null,
    @SerializedName("control_form_name") val controlFormName: String? = null,
    @SerializedName("comment_exists") val commentExists: Boolean? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class MarkTeacher(
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("middle_name") val middleName: String? = null,
)

data class MarkActivity(
    @SerializedName("lesson_topic") val lessonTopic: String? = null,
)

data class MarkClassResults(
    @SerializedName("total_students") val totalStudents: Int? = null,
    @SerializedName("marks_distributions") val marksDistributions: List<MarkDistribution>? = null,
)

data class MarkDistribution(
    @SerializedName("percentage_of_students") val percentageOfStudents: Int? = null,
    @SerializedName("number_of_students") val numberOfStudents: Int? = null,
    @SerializedName("mark_value") val markValue: MarkDistributionValue? = null,
)

data class MarkDistributionValue(
    @SerializedName("five") val five: Double? = null,
)

/** Файл, прикреплённый к оценке или к работе ученика. */
data class MarkFile(
    @SerializedName("id") val id: String? = null,
    @SerializedName("ordinal") val ordinal: Int? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("size") val size: String? = null,
    @SerializedName("file_url") val fileUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)
