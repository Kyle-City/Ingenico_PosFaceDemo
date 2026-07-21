package com.kyle.posfacedemo.face.baidu

data class RegistrationResult(
    val state: RegistrationState,
    val durationMs: Long = 0L,
    val userCount: Int = 0,
    val failureReason: RegistrationFailureReason? = null
) {
    companion object {
        fun idle(userCount: Int = 0) = RegistrationResult(RegistrationState.IDLE, userCount = userCount)
        fun waiting(userCount: Int = 0) = RegistrationResult(RegistrationState.WAITING_FOR_VALID_FACE, userCount = userCount)
        fun success(durationMs: Long, userCount: Int) = RegistrationResult(RegistrationState.SUCCESS, durationMs, userCount)
        fun failed(reason: RegistrationFailureReason, durationMs: Long = 0L, userCount: Int = 0) =
            RegistrationResult(RegistrationState.FAILED, durationMs, userCount, reason)
    }
}

enum class RegistrationState {
    IDLE,
    WAITING_FOR_VALID_FACE,
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
    FEATURE_EXTRACTION_FAILED,
    DATABASE_WRITE_FAILED,
    CANCELLED,
    EXCEPTION
}