package com.kyle.posfacedemo.face.baidu

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.baidu.idl.main.facesdk.FaceInfo
import com.baidu.idl.main.facesdk.FaceLive
import com.baidu.idl.main.facesdk.model.BDFaceImageInstance
import com.baidu.idl.main.facesdk.model.BDFaceDetectListConf
import com.baidu.idl.main.facesdk.model.BDFaceSDKCommon
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object BaiduFaceDetectionProbe {
    private const val TAG = "POSFACE_BAIDU_DETECT"
    private const val REGISTER_TAG = "POSFACE_BAIDU_REGISTER"
    private val processing = AtomicBoolean(false)
    private val pendingRegistrationRequest = AtomicReference<RegistrationRequestState?>(null)
    private val registering = AtomicBoolean(false)

    fun requestRegistration(): RegistrationResult {
        val request = PendingRegistrationRequest(SystemClock.elapsedRealtime())
        if (!pendingRegistrationRequest.compareAndSet(null, request)) {
            return RegistrationResult.failed(RegistrationFailureReason.REQUEST_ALREADY_PENDING)
        }
        return RegistrationResult.waiting()
    }

    fun cancelRegistration(): RegistrationResult {
        val request = pendingRegistrationRequest.getAndSet(null)
        return if (request == null) {
            RegistrationResult.idle()
        } else {
            RegistrationResult.failed(RegistrationFailureReason.CANCELLED)
        }
    }

    fun timeoutRegistration(context: Context): RegistrationResult? {
        val request = pendingRegistrationRequest.get() ?: return null
        if (!request.isTimedOut()) return null
        if (!pendingRegistrationRequest.compareAndSet(request, null)) return null
        return logRegistration(
            RegistrationResult.failed(
                RegistrationFailureReason.TIMEOUT,
                request.elapsedMs(),
                getUserCount(context)
            )
        )
    }

    fun detect(
        context: Context,
        nv21: ByteArray,
        width: Int,
        height: Int,
        direction: Int,
        mirror: Int,
        onResult: (Result) -> Unit
    ) {
        if (!processing.compareAndSet(false, true)) {
            return
        }
        Thread {
            val startTime = System.currentTimeMillis()
            Log.i(TAG, "detectStart=true width=$width height=$height direction=$direction mirror=$mirror")
            var imageInstance: BDFaceImageInstance? = null
            try {
                val faceDetect = BaiduModelInitializationProbe.getRgbFastFaceDetect()
                val accurateFaceDetect = BaiduModelInitializationProbe.getRgbAccurateFaceDetect()
                val faceLive = BaiduModelInitializationProbe.getRgbFaceLive()
                if (faceDetect == null || accurateFaceDetect == null) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "detectResult=NOT_READY")
                    Log.i(TAG, "faceCount=0")
                    logQuality(QualityResult.notReady(), duration)
                    Log.i(TAG, "durationMs=$duration")
                    onResult(Result(ResultType.NOT_READY, 0, null, QualityResult.notReady(), LivenessResult.notRun(), handleRegistration(context, null, null, QualityResult.notReady(), LivenessResult.notRun()), IdentificationResult.notRun()))
                    return@Thread
                }
                imageInstance = BDFaceImageInstance(
                    nv21,
                    height,
                    width,
                    BDFaceSDKCommon.BDFaceImageType.BDFACE_IMAGE_TYPE_YUV_NV21,
                    direction.toFloat(),
                    mirror
                )
                val faceInfos: Array<FaceInfo>? = faceDetect.track(
                    BDFaceSDKCommon.DetectType.DETECT_VIS,
                    BDFaceSDKCommon.AlignType.BDFACE_ALIGN_TYPE_RGB_FAST,
                    imageInstance
                )
                val faceCount = faceInfos?.size ?: 0
                val result = if (faceInfos.isNullOrEmpty()) {
                    Result(ResultType.NO_FACE, 0, null, QualityResult.noFace(), LivenessResult.notRun(), handleRegistration(context, null, null, QualityResult.noFace(), LivenessResult.notRun()), IdentificationResult.notRun())
                } else {
                    val faceInfo = faceInfos.maxBy { it.width * it.height }
                    val qualityFaceInfo = accurateFaceDetect.detect(
                        BDFaceSDKCommon.DetectType.DETECT_VIS,
                        BDFaceSDKCommon.AlignType.BDFACE_ALIGN_TYPE_RGB_ACCURATE,
                        imageInstance,
                        faceInfos,
                        createQualityDetectConfig()
                    )?.maxByOrNull { it.width * it.height } ?: faceInfo
                    val mappedImage = imageInstance.getImage()
                    val faceBox = FaceBox(
                        left = faceInfo.centerX - faceInfo.width / 2f,
                        top = faceInfo.centerY - faceInfo.height / 2f,
                        right = faceInfo.centerX + faceInfo.width / 2f,
                        bottom = faceInfo.centerY + faceInfo.height / 2f,
                        imageWidth = mappedImage.width.toFloat(),
                        imageHeight = mappedImage.height.toFloat()
                    )
                    mappedImage.destory()
                    val quality = evaluateQuality(qualityFaceInfo)
                    val liveness = if (quality.qualityPassed) {
                        runRgbLiveness(faceLive, imageInstance, qualityFaceInfo)
                    } else {
                        logLiveness(LivenessResult.notRun(), quality.qualityPassed)
                        LivenessResult.notRun()
                    }
                    val registration = handleRegistration(context, imageInstance, qualityFaceInfo, quality, liveness)
                    val identification = handleIdentification(imageInstance, qualityFaceInfo, quality, liveness, registration)
                    Result(ResultType.FACE_DETECTED, faceCount, faceBox, quality, liveness, registration, identification)
                }
                val duration = System.currentTimeMillis() - startTime
                Log.i(TAG, "detectResult=${if (result.type == ResultType.FACE_DETECTED) "FACE" else "NO_FACE"}")
                Log.i(TAG, "faceCount=$faceCount")
                logQuality(result.quality, duration)
                Log.i(TAG, "durationMs=$duration")
                onResult(result)
            } catch (exception: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Log.i(TAG, "exceptionType=${exception.javaClass.name}")
                Log.i(TAG, "detectResult=ERROR")
                Log.i(TAG, "faceCount=0")
                logQuality(QualityResult.error(), duration)
                Log.i(TAG, "durationMs=$duration")
                onResult(Result(ResultType.ERROR, 0, null, QualityResult.error(), LivenessResult.error(LivenessFailureReason.EXCEPTION), handleRegistration(context, null, null, QualityResult.error(), LivenessResult.error(LivenessFailureReason.EXCEPTION)), IdentificationResult.notRun()))
            } finally {
                imageInstance?.destory()
                processing.set(false)
            }
        }.start()
    }

    private fun handleRegistration(
        context: Context,
        imageInstance: BDFaceImageInstance?,
        faceInfo: FaceInfo?,
        quality: QualityResult,
        liveness: LivenessResult
    ): RegistrationResult {
        val request = pendingRegistrationRequest.get()
        if (request == null) {
            return RegistrationResult.idle(getUserCount(context))
        }
        if (request is RegisteringRegistrationRequest) {
            return RegistrationResult(RegistrationState.REGISTERING, userCount = getUserCount(context))
        }
        if (request.isTimedOut()) {
            if (pendingRegistrationRequest.compareAndSet(request, null)) {
                return logRegistration(
                    RegistrationResult.failed(
                        RegistrationFailureReason.TIMEOUT,
                        request.elapsedMs(),
                        getUserCount(context)
                    )
                )
            }
            return RegistrationResult.idle(getUserCount(context))
        }
        val gateFailure = when {
            imageInstance == null || faceInfo == null -> RegistrationFailureReason.NO_FACE
            quality.failureReason == FailureReason.FACE_TOO_SMALL -> RegistrationFailureReason.FACE_TOO_SMALL
            !quality.qualityPassed -> RegistrationFailureReason.QUALITY_NOT_PASSED
            liveness.state != LivenessState.PASS -> RegistrationFailureReason.LIVENESS_NOT_PASSED
            faceInfo.landmarks == null || faceInfo.landmarks.isEmpty() -> RegistrationFailureReason.LANDMARKS_INVALID
            BaiduModelInitializationProbe.getLivePhotoFaceFeature() == null -> RegistrationFailureReason.MODEL_NOT_READY
            else -> null
        }
        if (gateFailure != null) {
            return RegistrationResult.waiting(getUserCount(context))
        }
        val validFaceInfo = faceInfo ?: return RegistrationResult.waiting(getUserCount(context))
        val validImageInstance = imageInstance ?: return RegistrationResult.waiting(getUserCount(context))
        val registeringRequest = RegisteringRegistrationRequest(request.startedAtElapsedRealtime)
        if (!pendingRegistrationRequest.compareAndSet(request, registeringRequest)) {
            return RegistrationResult.idle(getUserCount(context))
        }
        if (!registering.compareAndSet(false, true)) {
            return logRegistration(RegistrationResult.failed(RegistrationFailureReason.REQUEST_ALREADY_PENDING, userCount = getUserCount(context)))
        }
        val startTime = System.currentTimeMillis()
        val featureBuffer = ByteArray(FEATURE_BUFFER_SIZE)
        return try {
            val faceFeature = BaiduModelInitializationProbe.getLivePhotoFaceFeature()
                ?: return consumeRegisteringFailure(registeringRequest, RegistrationFailureReason.MODEL_NOT_READY, 0L, context)
            val featureSize = synchronized(faceFeature) {
                faceFeature.feature(
                    BDFaceSDKCommon.FeatureType.BDFACE_FEATURE_TYPE_LIVE_PHOTO,
                    validImageInstance,
                    validFaceInfo.landmarks,
                    featureBuffer
                )
            }
            Log.i(REGISTER_TAG, "featureSizeReturned=$featureSize")
            if (featureSize != FEATURE_SUCCESS_SIZE) {
                return consumeRegisteringFailure(registeringRequest, RegistrationFailureReason.FEATURE_EXTRACTION_FAILED, System.currentTimeMillis() - startTime, context)
            }
            if (!pendingRegistrationRequest.compareAndSet(registeringRequest, null)) {
                return RegistrationResult.idle(getUserCount(context))
            }
            val repository = LocalFaceRepository(context)
            try {
                val saved = repository.saveOrReplaceTestUser(LocalFaceRepository.TEST_USER_ID, featureBuffer.copyOf())
                if (saved) {
                    logRegistration(RegistrationResult.success(System.currentTimeMillis() - startTime, repository.getUserCount()))
                } else {
                    logRegistration(RegistrationResult.failed(RegistrationFailureReason.DATABASE_WRITE_FAILED, System.currentTimeMillis() - startTime, repository.getUserCount()))
                }
            } finally {
                repository.close()
            }
        } catch (exception: Exception) {
            Log.i(REGISTER_TAG, "exceptionType=${exception.javaClass.name}")
            consumeRegisteringFailure(registeringRequest, RegistrationFailureReason.EXCEPTION, System.currentTimeMillis() - startTime, context)
        } finally {
            featureBuffer.fill(0)
            registering.set(false)
        }
    }

    private fun handleIdentification(
        imageInstance: BDFaceImageInstance,
        faceInfo: FaceInfo,
        quality: QualityResult,
        liveness: LivenessResult,
        registration: RegistrationResult
    ): IdentificationResult {
        if (!quality.qualityPassed || liveness.state != LivenessState.PASS) {
            return IdentificationResult.notRun()
        }
        if (registration.state == RegistrationState.SUCCESS || registration.state == RegistrationState.REGISTERING) {
            return IdentificationResult.notRun()
        }
        val landmarks = faceInfo.landmarks ?: return IdentificationResult.error(IdentificationFailureReason.LANDMARKS_INVALID)
        return BaiduFaceIdentificationService.identify(imageInstance, landmarks)
    }

    private fun consumeRegisteringFailure(
        request: RegisteringRegistrationRequest,
        reason: RegistrationFailureReason,
        durationMs: Long,
        context: Context
    ): RegistrationResult {
        if (!pendingRegistrationRequest.compareAndSet(request, null)) {
            return RegistrationResult.idle(getUserCount(context))
        }
        return logRegistration(RegistrationResult.failed(reason, durationMs, getUserCount(context)))
    }

    private fun getUserCount(context: Context): Int {
        val repository = LocalFaceRepository(context)
        return try {
            repository.getUserCount()
        } finally {
            repository.close()
        }
    }

    private fun logRegistration(result: RegistrationResult): RegistrationResult {
        Log.i(REGISTER_TAG, "state=${result.state}")
        Log.i(REGISTER_TAG, "durationMs=${result.durationMs}")
        Log.i(REGISTER_TAG, "userCount=${result.userCount}")
        result.failureReason?.let { Log.i(REGISTER_TAG, "failureReason=$it") }
        return result
    }

    private fun createQualityDetectConfig(): BDFaceDetectListConf {
        return BDFaceDetectListConf().apply {
            usingQuality = true
            usingHeadPose = true
            usingBestImage = true
        }
    }

    private fun runRgbLiveness(faceLive: FaceLive?, imageInstance: BDFaceImageInstance, faceInfo: FaceInfo): LivenessResult {
        val startTime = System.currentTimeMillis()
        Log.i(LIVENESS_TAG, "livenessState=CHECKING")
        Log.i(LIVENESS_TAG, "qualityPassed=true")
        Log.i(LIVENESS_TAG, "livenessThreshold=$RGB_LIVENESS_THRESHOLD")
        if (faceLive == null) {
            val result = LivenessResult.error(LivenessFailureReason.NOT_READY, System.currentTimeMillis() - startTime)
            logLiveness(result, true)
            return result
        }
        val landmarks = faceInfo.landmarks
        if (landmarks == null || landmarks.isEmpty()) {
            val result = LivenessResult.error(LivenessFailureReason.INVALID_LANDMARKS, System.currentTimeMillis() - startTime)
            logLiveness(result, true)
            return result
        }
        return try {
            val score = synchronized(faceLive) {
                faceLive.silentLive(
                    BDFaceSDKCommon.LiveType.BDFACE_SILENT_LIVE_TYPE_RGB,
                    imageInstance,
                    landmarks,
                    RGB_LIVENESS_THRESHOLD
                )
            }
            if (!score.isFinite() || score < 0f) {
                val result = LivenessResult.error(LivenessFailureReason.INVALID_SCORE, System.currentTimeMillis() - startTime)
                logLiveness(result, true)
                result
            } else {
                val state = if (score >= RGB_LIVENESS_THRESHOLD) LivenessState.PASS else LivenessState.FAIL
                val result = LivenessResult(state, score, RGB_LIVENESS_THRESHOLD, System.currentTimeMillis() - startTime, null)
                logLiveness(result, true)
                result
            }
        } catch (exception: Exception) {
            Log.i(LIVENESS_TAG, "exceptionType=${exception.javaClass.name}")
            val result = LivenessResult.error(LivenessFailureReason.EXCEPTION, System.currentTimeMillis() - startTime)
            logLiveness(result, true)
            result
        }
    }

    private fun logLiveness(result: LivenessResult, qualityPassed: Boolean) {
        Log.i(LIVENESS_TAG, "livenessState=${result.state}")
        result.score?.let { Log.i(LIVENESS_TAG, "livenessScore=$it") }
        Log.i(LIVENESS_TAG, "livenessThreshold=${result.threshold}")
        Log.i(LIVENESS_TAG, "qualityPassed=$qualityPassed")
        Log.i(LIVENESS_TAG, "durationMs=${result.durationMs}")
        result.failureReason?.let { Log.i(LIVENESS_TAG, "failureReason=$it") }
    }

    private fun evaluateQuality(faceInfo: FaceInfo): QualityResult {
        val occlusion = faceInfo.occlusion
        val occlusionPassed = occlusion != null
                && occlusion.leftEye <= LEFT_EYE_THRESHOLD
                && occlusion.rightEye <= RIGHT_EYE_THRESHOLD
                && occlusion.nose <= NOSE_THRESHOLD
                && occlusion.mouth <= MOUTH_THRESHOLD
                && occlusion.leftCheek <= LEFT_CHEEK_THRESHOLD
                && occlusion.rightCheek <= RIGHT_CHEEK_THRESHOLD
                && occlusion.chin <= CHIN_THRESHOLD
        val faceSizeCheck = faceInfo.width >= QUALITY_MIN_FACE_WIDTH
        val failureReason = when {
            !faceSizeCheck -> FailureReason.FACE_TOO_SMALL
            kotlin.math.abs(faceInfo.yaw) > GESTURE_THRESHOLD
                    || kotlin.math.abs(faceInfo.roll) > GESTURE_THRESHOLD
                    || kotlin.math.abs(faceInfo.pitch) > GESTURE_THRESHOLD -> FailureReason.POSE
            faceInfo.bluriness > BLUR_THRESHOLD_POC -> FailureReason.BLUR
            faceInfo.illum < ILLUMINATION_THRESHOLD -> FailureReason.ILLUMINATION
            !occlusionPassed -> FailureReason.OCCLUSION
            else -> FailureReason.NONE
        }
        return QualityResult(
            qualityPassed = failureReason == FailureReason.NONE,
            failureReason = failureReason,
            pitch = faceInfo.pitch,
            yaw = faceInfo.yaw,
            roll = faceInfo.roll,
            blur = faceInfo.bluriness,
            illumination = faceInfo.illum,
            occlusionPassed = occlusionPassed,
            faceSizeCheck = faceSizeCheck,
            faceWidth = faceInfo.width
        )
    }

    private fun logQuality(quality: QualityResult, durationMs: Long) {
        Log.i(QUALITY_TAG, "failureReason=${quality.failureReason}")
        Log.i(QUALITY_TAG, "faceSizeCheck=${quality.faceSizeCheck}")
        Log.i(QUALITY_TAG, "faceWidth=${quality.faceWidth}")
        Log.i(QUALITY_TAG, "detectorMinFaceSize=$MIN_FACE_SIZE")
        Log.i(QUALITY_TAG, "qualityMinFaceWidth=$QUALITY_MIN_FACE_WIDTH")
    }

    data class Result(
        val type: ResultType,
        val faceCount: Int,
        val faceBox: FaceBox?,
        val quality: QualityResult,
        val liveness: LivenessResult,
        val registration: RegistrationResult,
        val identification: IdentificationResult
    )

    private sealed interface RegistrationRequestState {
        val startedAtElapsedRealtime: Long
        fun elapsedMs(): Long = SystemClock.elapsedRealtime() - startedAtElapsedRealtime
        fun isTimedOut(): Boolean = elapsedMs() >= REGISTRATION_TIMEOUT_MS
    }

    data class PendingRegistrationRequest(
        override val startedAtElapsedRealtime: Long
    ) : RegistrationRequestState

    private data class RegisteringRegistrationRequest(
        override val startedAtElapsedRealtime: Long
    ) : RegistrationRequestState

    data class FaceBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val imageWidth: Float,
        val imageHeight: Float
    )

    enum class ResultType {
        FACE_DETECTED,
        NO_FACE,
        NOT_READY,
        ERROR
    }

    data class QualityResult(
        val qualityPassed: Boolean,
        val failureReason: FailureReason,
        val pitch: Float,
        val yaw: Float,
        val roll: Float,
        val blur: Float,
        val illumination: Float,
        val occlusionPassed: Boolean,
        val faceSizeCheck: Boolean,
        val faceWidth: Float,
        val logResult: String = if (qualityPassed) "PASS" else "FAIL"
    ) {
        companion object {
            fun noFace() = QualityResult(false, FailureReason.UNKNOWN, 0f, 0f, 0f, 0f, 0f, false, false, 0f, "NOT_READY")
            fun notReady() = QualityResult(false, FailureReason.UNKNOWN, 0f, 0f, 0f, 0f, 0f, false, false, 0f, "NOT_READY")
            fun error() = QualityResult(false, FailureReason.UNKNOWN, 0f, 0f, 0f, 0f, 0f, false, false, 0f, "ERROR")
        }
    }

    enum class FailureReason {
        NONE,
        POSE,
        BLUR,
        ILLUMINATION,
        OCCLUSION,
        FACE_TOO_SMALL,
        UNKNOWN
    }

    data class LivenessResult(
        val state: LivenessState,
        val score: Float?,
        val threshold: Float,
        val durationMs: Long,
        val failureReason: LivenessFailureReason?
    ) {
        companion object {
            fun notRun() = LivenessResult(LivenessState.NOT_RUN, null, RGB_LIVENESS_THRESHOLD, 0L, null)
            fun error(reason: LivenessFailureReason, durationMs: Long = 0L) =
                LivenessResult(LivenessState.ERROR, null, RGB_LIVENESS_THRESHOLD, durationMs, reason)
        }
    }

    enum class LivenessState {
        NOT_RUN,
        CHECKING,
        PASS,
        FAIL,
        ERROR
    }

    enum class LivenessFailureReason {
        NOT_READY,
        INVALID_LANDMARKS,
        INVALID_SCORE,
        EXCEPTION
    }

    private const val QUALITY_TAG = "POSFACE_BAIDU_QUALITY"
    private const val LIVENESS_TAG = "POSFACE_BAIDU_LIVENESS"
    private const val MIN_FACE_SIZE = 60f
    // PoC threshold calibrated from DX8000 old fixed-focus camera with 640x480 input.
    // Normal distance: 285-300, acceptable far: 210-220, too far: 115-120. Do not use in production.
    private const val QUALITY_MIN_FACE_WIDTH = 200f
    private const val GESTURE_THRESHOLD = 30f
    // Only for old DX8000 PoC devices with fixed-focus 640x480 camera. Do not use in production.
    private const val BLUR_THRESHOLD_POC = 0.995f
    private const val ILLUMINATION_THRESHOLD = 0.8f
    private const val LEFT_EYE_THRESHOLD = 0.8f
    private const val RIGHT_EYE_THRESHOLD = 0.8f
    private const val NOSE_THRESHOLD = 0.8f
    private const val MOUTH_THRESHOLD = 0.8f
    private const val LEFT_CHEEK_THRESHOLD = 0.8f
    private const val RIGHT_CHEEK_THRESHOLD = 0.8f
    private const val CHIN_THRESHOLD = 0.8f
    private const val RGB_LIVENESS_THRESHOLD = 0.80f
    private const val REGISTRATION_TIMEOUT_MS = 10_000L
    private const val FEATURE_BUFFER_SIZE = 512
    private const val FEATURE_SUCCESS_SIZE = 128f
}