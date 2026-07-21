package com.kyle.posfacedemo.face.baidu

data class RegistrationResult(
    val state: RegistrationState,
    val durationMs: Long = 0L,
    val userCount: Int = 0,
    val failureReason: RegistrationFailureReason? = null,
    val displayName: String? = null,
    val collectedFrameCount: Int = 0,
    val requiredFrameCount: Int = 0,
    val registrationStage: RegistrationStage = RegistrationStage.IDLE
) {
    companion object {
        fun idle(userCount: Int = 0) = RegistrationResult(RegistrationState.IDLE, userCount = userCount)
        fun preparing(userCount: Int = 0, requiredFrameCount: Int = 0) = RegistrationResult(
            RegistrationState.PREPARING,
            userCount = userCount,
            requiredFrameCount = requiredFrameCount,
            registrationStage = RegistrationStage.PREPARING
        )
        fun waiting(userCount: Int = 0, requiredFrameCount: Int = 0) = RegistrationResult(
            RegistrationState.WAITING_FOR_VALID_FACE,
            userCount = userCount,
            requiredFrameCount = requiredFrameCount,
            registrationStage = RegistrationStage.WAITING
        )
        fun collecting(collectedFrameCount: Int, requiredFrameCount: Int, userCount: Int = 0) = RegistrationResult(
            RegistrationState.COLLECTING,
            userCount = userCount,
            collectedFrameCount = collectedFrameCount,
            requiredFrameCount = requiredFrameCount,
            registrationStage = RegistrationStage.COLLECTING
        )
        fun success(durationMs: Long, userCount: Int, displayName: String? = null) = RegistrationResult(RegistrationState.SUCCESS, durationMs, userCount, displayName = displayName, registrationStage = RegistrationStage.SUCCESS)
        fun failed(reason: RegistrationFailureReason, durationMs: Long = 0L, userCount: Int = 0) =
            RegistrationResult(RegistrationState.FAILED, durationMs, userCount, reason, registrationStage = RegistrationStage.FAILED)
    }
}

enum class RegistrationState {
    IDLE,
    PREPARING,
    WAITING_FOR_VALID_FACE,
    COLLECTING,
    REGISTERING,
    SUCCESS,
    FAILED
}

enum class RegistrationStage {
    IDLE,
    PREPARING,
    WAITING,
    COLLECTING,
    REGISTERING,
    SUCCESS,
    FAILED
}

enum class RegistrationFailureReason {
    MODEL_NOT_READY,
    REQUEST_ALREADY_PENDING,
    TIMEOUT,
    NO_FACE,
    FACE_TOO_SMALL,
    QUALITY_NOT_PASSED,
    LIVENESS_NOT_PASSED,
    LANDMARKS_INVALID,
    INVALID_DISPLAY_NAME,
    FEATURE_EXTRACTION_FAILED,
    DATABASE_CONSTRAINT_FAILED,
    DATABASE_WRITE_FAILED,
    CANCELLED,
    EXCEPTION
}