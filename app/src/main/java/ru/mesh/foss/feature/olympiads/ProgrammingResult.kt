package ru.mesh.foss.feature.olympiads

import ru.mesh.foss.data.ProgrammingAttemptPayload

/** Готовый результат программистской олимпиады для нативного экрана. */
data class ProgrammingResultData(
    val title: String,
    val description: String?,
    val instruction: String?,
    val score: Double?,
    val maxScore: Double?,
    val completedAt: String?,
    val timeLimitSeconds: Int?,
    val status: String?,
    val correct: Int,
    val partial: Int,
    val incorrect: Int,
    val tasks: List<ProgrammingResultTask>,
)

data class ProgrammingResultTask(
    val number: Int,
    val name: String,
    val type: String,
    val maxScore: Double?,
    val score: Double?,
    val checkResult: String?,
    val status: String?,
    val description: String?,
    val inputFormat: String?,
    val outputFormat: String?,
    val notes: String?,
    val theory: String?,
    val examples: List<ProgrammingResultExample>,
    val answer: String?,
    val sourceCode: String?,
    val teacherComment: String?,
)

data class ProgrammingResultExample(
    val input: String,
    val output: String,
    val comment: String?,
)

fun ProgrammingAttemptPayload.toResultData(): ProgrammingResultData {
    val stateTasks = state.tasks.orEmpty().sortedBy { it.task?.order ?: Int.MAX_VALUE }
    val tasksById = tasks.associateBy { it.taskId }
    val uiTasks = stateTasks.mapIndexedNotNull { index, stateTask ->
        val taskId = stateTask.task?.id ?: return@mapIndexedNotNull null
        val task = tasksById[taskId]
        val assignment = task?.taskAssignment
        val answers = assignment?.answersB.orEmpty()
            .sortedBy { it.order ?: Int.MAX_VALUE }
            .mapNotNull { item -> item.answer?.trim()?.takeIf { it.isNotBlank() } }
        val sourceCode = assignment?.content?.trim()?.takeIf { it.isNotBlank() }
        val answer = if (task?.type == "C") {
            null
        } else {
            answers.joinToString("\n").takeIf { it.isNotBlank() }
        }
        ProgrammingResultTask(
            number = stateTask.task?.order ?: task?.order ?: (index + 1),
            name = task?.name?.trim().orEmpty()
                .ifBlank { stateTask.task?.name.orEmpty().trim() },
            type = task?.type?.trim().orEmpty()
                .ifBlank { stateTask.task?.type.orEmpty().trim() },
            maxScore = stateTask.task?.maxPoint ?: task?.maxPoint,
            score = stateTask.collectedPoint ?: assignment?.pointCount,
            checkResult = stateTask.checkResult ?: assignment?.checkResult,
            status = assignment?.status,
            description = task?.description,
            inputFormat = task?.inputFormat,
            outputFormat = task?.outputFormat,
            notes = task?.notes,
            theory = task?.theory,
            examples = task?.examples.orEmpty().map { example ->
                ProgrammingResultExample(
                    input = example.exampleInput.orEmpty(),
                    output = example.exampleOutput.orEmpty(),
                    comment = example.comment,
                )
            },
            answer = answer,
            sourceCode = sourceCode,
            teacherComment = assignment?.teacherComment,
        )
    }
    val auto = state.autoCheckResults
    val correct = auto?.correct ?: uiTasks.count {
        it.checkResult.equals("CORRECT", ignoreCase = true)
    }
    val partial = auto?.partCorrect ?: uiTasks.count {
        it.checkResult.equals("PART_CORRECT", ignoreCase = true) ||
            it.checkResult.equals("PARTIALLY_CORRECT", ignoreCase = true)
    }
    val incorrect = auto?.notCorrect ?: uiTasks.count {
        it.checkResult.equals("INCORRECT", ignoreCase = true) ||
            it.checkResult.equals("NOT_CORRECT", ignoreCase = true)
    }
    return ProgrammingResultData(
        title = resultPage?.name?.trim().orEmpty()
            .ifBlank { state.name.orEmpty().trim() },
        description = resultPage?.description?.takeIf { it.isNotBlank() }
            ?: state.description?.takeIf { it.isNotBlank() },
        instruction = resultPage?.instruction?.takeIf { it.isNotBlank() },
        score = state.collectedPoint,
        maxScore = state.maxPoint ?: resultPage?.maxPointsCount,
        completedAt = state.completeDate,
        timeLimitSeconds = state.timeLimit ?: resultPage?.timeLimit,
        status = state.assignmentStatus,
        correct = correct,
        partial = partial,
        incorrect = incorrect,
        tasks = uiTasks,
    )
}
