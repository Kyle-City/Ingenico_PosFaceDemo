package com.kyle.posfacedemo.face.baidu

data class IdentificationResult(
    val state: IdentificationState,
    val matchedLocalId: Int? = null,
    val similarityScore: Float? = null,
    val threshold: Float = IDENTIFY_THRESHOLD,
    val durationMs: Long = 0L,
    val userCount: Int = 0,
    val candidateCount: Int = 0,
    val failureReason: IdentificationFailureReason? = null
) {
    companion object {
        const val IDENTIFY_THRESHOLD = 80f

        fun notRun(userCount: Int = 0) = IdentificationResult(IdentificationState.NOT_RUN, userCount = userCount)
        fun noUsers() = IdentificationResult(IdentificationState.NO_USERS, userCount = 0)
        fun error(reason: IdentificationFailureReason, durationMs: Long = 0L, userCount: Int = 0) =
            IdentificationResult(IdentificationState.ERROR, durationMs = durationMs, userCount = userCount, failureReason = reason)
    }
}

enum class IdentificationState {
    NOT_RUN,
    SEARCHING,
    MATCHED,
    NOT_MATCHED,
    NO_USERS,
    ERROR
}

enum class IdentificationFailureReason {
    MODEL_NOT_READY,
    NO_USERS,
    COOLDOWN,
    SEARCH_IN_PROGRESS,
    LANDMARKS_INVALID,
    FEATURE_EXTRACTION_FAILED,
    DATABASE_LOAD_FAILED,
    EXCEPTION
}