package ru.mesh.foss.data.model

import com.google.gson.annotations.SerializedName

/** Ответ `olympiad/result-page/{competitionId}`. */
data class ProgrammingResultPage(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("instruction") val instruction: String? = null,
    @SerializedName("maxPointsCount") val maxPointsCount: Double? = null,
    @SerializedName("taskCount") val taskCount: Int? = null,
    @SerializedName("timeLimit") val timeLimit: Int? = null,
)

/** Ответ `pass-test/{assignmentId}/state`. */
data class ProgrammingState(
    @SerializedName("assignmentId") val assignmentId: Long? = null,
    @SerializedName("challengeId") val challengeId: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("assignmentStatus") val assignmentStatus: String? = null,
    @SerializedName("assignmentStatusComment") val assignmentStatusComment: String? = null,
    @SerializedName("collectedPoint") val collectedPoint: Double? = null,
    @SerializedName("maxPoint") val maxPoint: Double? = null,
    @SerializedName("completeDate") val completeDate: String? = null,
    @SerializedName("timeLimit") val timeLimit: Int? = null,
    @SerializedName("autoCheckResults") val autoCheckResults: ProgrammingAutoCheck? = null,
    @SerializedName("tasks") val tasks: List<ProgrammingStateTask>? = null,
)

data class ProgrammingAutoCheck(
    @SerializedName("correct") val correct: Int? = null,
    @SerializedName("partCorrect") val partCorrect: Int? = null,
    @SerializedName("notCorrect") val notCorrect: Int? = null,
)

data class ProgrammingStateTask(
    @SerializedName("assignmentTaskId") val assignmentTaskId: Long? = null,
    @SerializedName("checkMethod") val checkMethod: String? = null,
    @SerializedName("checkResult") val checkResult: String? = null,
    @SerializedName("collectedPoint") val collectedPoint: Double? = null,
    @SerializedName("currentTaskAttemptCount") val currentTaskAttemptCount: Int? = null,
    @SerializedName("taskAttemptCountMax") val taskAttemptCountMax: Int? = null,
    @SerializedName("task") val task: ProgrammingStateTaskInfo? = null,
)

data class ProgrammingStateTaskInfo(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("order") val order: Int? = null,
    @SerializedName("maxPoint") val maxPoint: Double? = null,
)

/** Ответ `pass-test/{assignmentId}/task/{taskId}`. */
data class ProgrammingTask(
    @SerializedName("taskId") val taskId: Long? = null,
    @SerializedName("order") val order: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("inputFormat") val inputFormat: String? = null,
    @SerializedName("outputFormat") val outputFormat: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("verificationRecommendations") val verificationRecommendations: String? = null,
    @SerializedName("theory") val theory: String? = null,
    @SerializedName("descriptionFormat") val descriptionFormat: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("maxPoint") val maxPoint: Double? = null,
    @SerializedName("examples") val examples: List<ProgrammingExample>? = null,
    @SerializedName("taskAssignment") val taskAssignment: ProgrammingTaskAssignment? = null,
    @SerializedName("attachments") val attachments: List<ProgrammingAttachment>? = null,
)

data class ProgrammingExample(
    @SerializedName("comment") val comment: String? = null,
    @SerializedName("exampleInput") val exampleInput: String? = null,
    @SerializedName("exampleOutput") val exampleOutput: String? = null,
)

data class ProgrammingTaskAssignment(
    @SerializedName("answersA") val answersA: List<ProgrammingAnswerA>? = null,
    @SerializedName("answersB") val answersB: List<ProgrammingAnswerB>? = null,
    @SerializedName("attachment") val attachment: ProgrammingAttachment? = null,
    @SerializedName("checkMethod") val checkMethod: String? = null,
    @SerializedName("checkResult") val checkResult: String? = null,
    @SerializedName("completed") val completed: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("id") val id: Long? = null,
    @SerializedName("pointCount") val pointCount: Double? = null,
    @SerializedName("started") val started: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("takenAnswerId") val takenAnswerId: Long? = null,
    @SerializedName("teacherComment") val teacherComment: String? = null,
)

data class ProgrammingAnswerA(
    @SerializedName("answer") val answer: String? = null,
    @SerializedName("order") val order: Int? = null,
)

data class ProgrammingAnswerB(
    @SerializedName("answer") val answer: String? = null,
    @SerializedName("order") val order: Int? = null,
)

data class ProgrammingAttachment(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("relative_url") val relativeUrl: String? = null,
)

/** OAuth-обмен: `POST /vrlabs/informatics/api/authentication/token`. */
data class VrlabsTokenRequest(
    @SerializedName("launchContext") val launchContext: VrlabsLaunchContext,
)

data class VrlabsLaunchContext(
    @SerializedName("code") val code: String,
    @SerializedName("state") val state: String,
    @SerializedName("activityId") val activityId: String,
    @SerializedName("actor") val actor: String,
    @SerializedName("authurl") val authurl: String,
    @SerializedName("endpoint") val endpoint: String,
    @SerializedName("fetch") val fetch: String,
    @SerializedName("registration") val registration: String,
    @SerializedName("role") val role: String,
    @SerializedName("redirectUrl") val redirectUrl: String,
)

data class VrlabsTokenResponse(
    @SerializedName("token") val token: String? = null,
)
