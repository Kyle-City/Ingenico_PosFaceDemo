package com.kyle.posfacedemo

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.graphics.ImageFormat
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kyle.posfacedemo.face.baidu.BaiduFaceDetectionProbe
import com.kyle.posfacedemo.face.baidu.BaiduFaceIdentificationService
import com.kyle.posfacedemo.face.baidu.BaiduFaceDetectionProbe.FailureReason
import com.kyle.posfacedemo.face.baidu.BaiduFaceDetectionProbe.LivenessState
import com.kyle.posfacedemo.face.baidu.BaiduFaceDetectionProbe.ResultType
import com.kyle.posfacedemo.face.baidu.IdentificationResult
import com.kyle.posfacedemo.face.baidu.IdentificationState
import com.kyle.posfacedemo.face.baidu.LocalFaceRepository
import com.kyle.posfacedemo.face.baidu.RegistrationFailureReason
import com.kyle.posfacedemo.face.baidu.RegistrationResult
import com.kyle.posfacedemo.face.baidu.RegistrationState
import com.kyle.posfacedemo.face.ui.FaceBoxMapper
import com.kyle.posfacedemo.face.ui.FaceDetectionOverlayView

@Suppress("DEPRECATION")
class CameraPreviewActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private var camera: Camera? = null
    private var previewHolder: SurfaceHolder? = null
    private var surfaceReady = false
    private var acceptingFrames = false
    private var lastDetectTimeMs = 0L

    private lateinit var previewView: SurfaceView
    private lateinit var overlayView: FaceDetectionOverlayView
    private lateinit var statusText: TextView
    private lateinit var mainGuidanceText: TextView
    private lateinit var baiduDetectStatusText: TextView
    private lateinit var baiduQualityStatusText: TextView
    private lateinit var baiduLivenessStatusText: TextView
    private lateinit var baiduIdentifyStatusText: TextView
    private lateinit var baiduRegisterStatusText: TextView
    private lateinit var testUserCountText: TextView
    private lateinit var registerTestUserButton: Button
    private var recentMatchedDisplayName: String? = null
    private var recentSimilarityScore: Float? = null
    private val registrationTimeoutHandler = Handler(Looper.getMainLooper())
    private val registrationTimeoutRunnable = Runnable {
        val result = BaiduFaceDetectionProbe.timeoutRegistration(this)
        if (result != null && acceptingFrames) {
            updateRegistrationStatus(result)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_camera_preview)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.cameraPreviewRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        previewView = findViewById(R.id.cameraPreviewView)
        overlayView = findViewById(R.id.faceDetectionOverlayView)
        statusText = findViewById(R.id.cameraStatusText)
        mainGuidanceText = findViewById(R.id.mainGuidanceText)
        baiduDetectStatusText = findViewById(R.id.baiduDetectStatusText)
        baiduQualityStatusText = findViewById(R.id.baiduQualityStatusText)
        baiduLivenessStatusText = findViewById(R.id.baiduLivenessStatusText)
        baiduIdentifyStatusText = findViewById(R.id.baiduIdentifyStatusText)
        baiduRegisterStatusText = findViewById(R.id.baiduRegisterStatusText)
        testUserCountText = findViewById(R.id.testUserCountText)
        registerTestUserButton = findViewById(R.id.registerTestUserButton)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        registerTestUserButton.setOnClickListener {
            showRegisterNameDialog()
        }
        findViewById<Button>(R.id.deleteAllTestUsersButton).setOnClickListener {
            startActivity(Intent(this, FaceLibraryActivity::class.java))
        }
        updateTestUserCount()
        updateIdentificationStatus(BaiduFaceIdentificationService.reloadFromRepository(this), force = true)

        previewHolder = previewView.holder.also { holder ->
            holder.addCallback(this)
        }

        if (hasCameraPermission()) {
            statusText.text = getString(R.string.camera_opening)
        } else {
            statusText.text = getString(R.string.camera_permission_required)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::testUserCountText.isInitialized) {
            updateTestUserCount()
            updateIdentificationStatus(BaiduFaceIdentificationService.reloadFromRepository(this), force = true)
        }
        if (hasCameraPermission() && surfaceReady) {
            startCameraPreview()
        }
    }

    override fun onPause() {
        releaseCamera()
        super.onPause()
    }

    override fun onDestroy() {
        previewHolder?.removeCallback(this)
        releaseCamera()
        super.onDestroy()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        if (hasCameraPermission()) {
            startCameraPreview()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (hasCameraPermission() && surfaceReady) {
            startCameraPreview()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        releaseCamera()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CAMERA_PERMISSION_REQUEST) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            statusText.text = getString(R.string.camera_opening)
            if (surfaceReady) {
                startCameraPreview()
            }
        } else {
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.camera_permission_denied)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCameraPreview() {
        val holder = previewHolder ?: return
        releaseCamera()

        try {
            val cameraId = findFrontCameraId()
            if (cameraId == null) {
                statusText.visibility = View.VISIBLE
                statusText.text = getString(R.string.camera_not_found)
                return
            }

            camera = Camera.open(cameraId).also { openedCamera ->
                val parameters = openedCamera.parameters
                parameters.previewFormat = ImageFormat.NV21
                val previewSize = parameters.supportedPreviewSizes.firstOrNull {
                    it.width == PREVIEW_WIDTH && it.height == PREVIEW_HEIGHT
                }
                if (previewSize != null) {
                    parameters.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
                }
                openedCamera.parameters = parameters
                openedCamera.setPreviewDisplay(holder)
                openedCamera.setDisplayOrientation(90)
                acceptingFrames = true
                openedCamera.setPreviewCallback { data, _ ->
                    onPreviewFrame(data)
                }
                openedCamera.startPreview()
            }
            statusText.visibility = View.GONE
        } catch (exception: RuntimeException) {
            releaseCamera()
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.camera_open_failed)
        } catch (exception: Exception) {
            releaseCamera()
            statusText.visibility = View.VISIBLE
            statusText.text = getString(R.string.camera_open_failed)
        }
    }

    private fun onPreviewFrame(data: ByteArray?) {
        if (!acceptingFrames || data == null) return
        val now = System.currentTimeMillis()
        if (now - lastDetectTimeMs < DETECT_INTERVAL_MS) return
        lastDetectTimeMs = now
        val frame = data.copyOf()
        BaiduFaceDetectionProbe.detect(
            this,
            frame,
            PREVIEW_WIDTH,
            PREVIEW_HEIGHT,
            BAIDU_DETECT_DIRECTION,
            BAIDU_DETECT_MIRROR
        ) { result ->
            runOnUiThread {
                if (!acceptingFrames) return@runOnUiThread
                baiduDetectStatusText.text = when (result.type) {
                    ResultType.FACE_DETECTED -> getString(R.string.camera_title)
                    ResultType.NO_FACE -> getString(R.string.main_prompt_no_face)
                    ResultType.NOT_READY -> getString(R.string.main_prompt_model_not_ready)
                    ResultType.ERROR -> getString(R.string.main_prompt_detect_error)
                }
                val mappedRect = result.faceBox?.let {
                    FaceBoxMapper.mapCenterCrop(
                        it,
                        overlayView.width.toFloat(),
                        overlayView.height.toFloat(),
                        PREVIEW_MIRRORED
                    )
                }
                android.util.Log.i(DETECT_TAG, "previewMirrored=$PREVIEW_MIRRORED")
                android.util.Log.i(DETECT_TAG, "overlayMirrorApplied=${PREVIEW_MIRRORED && mappedRect != null}")
                android.util.Log.i(DETECT_TAG, "mappedRectValid=${mappedRect != null}")
                overlayView.setQualityPassed(result.quality.qualityPassed)
                overlayView.setFaceRect(mappedRect)
                baiduQualityStatusText.text = qualityText(result)
                baiduLivenessStatusText.text = livenessText(result)
                updateRegistrationStatus(result.registration)
                updateIdentificationStatus(result.identification, force = result.type != ResultType.FACE_DETECTED || !result.quality.qualityPassed || result.liveness.state != LivenessState.PASS)
                updateMainGuidance(result)
                updateStatusColors(result)
            }
        }
    }

    private fun updateRegistrationStatus(result: RegistrationResult) {
        registerTestUserButton.isEnabled = result.state != RegistrationState.PREPARING
                && result.state != RegistrationState.WAITING_FOR_VALID_FACE
                && result.state != RegistrationState.COLLECTING
                && result.state != RegistrationState.REGISTERING
        registerTestUserButton.text = when (result.state) {
            RegistrationState.PREPARING -> getString(R.string.register_button_waiting)
            RegistrationState.WAITING_FOR_VALID_FACE -> getString(R.string.register_button_waiting)
            RegistrationState.COLLECTING -> getString(R.string.register_button_waiting)
            RegistrationState.REGISTERING -> getString(R.string.register_button_running)
            RegistrationState.SUCCESS -> getString(R.string.register_button_again)
            else -> getString(if (result.userCount > 0) R.string.register_button_again else R.string.register_button_idle)
        }
        if (result.state == RegistrationState.SUCCESS || result.state == RegistrationState.FAILED) {
            registrationTimeoutHandler.removeCallbacks(registrationTimeoutRunnable)
        }
        if (result.state == RegistrationState.SUCCESS) {
            updateIdentificationStatus(BaiduFaceIdentificationService.reloadFromRepository(this), force = true)
        }
        when (result.state) {
            RegistrationState.PREPARING -> mainGuidanceText.text = getString(R.string.register_prepare_prompt)
            RegistrationState.WAITING_FOR_VALID_FACE -> mainGuidanceText.text = getString(R.string.main_prompt_no_face)
            RegistrationState.COLLECTING -> mainGuidanceText.text = getString(R.string.register_collecting_progress, result.collectedFrameCount, result.requiredFrameCount)
            RegistrationState.REGISTERING -> mainGuidanceText.text = getString(R.string.register_button_running)
            RegistrationState.SUCCESS -> mainGuidanceText.text = registrationSuccessText(result)
            RegistrationState.FAILED -> mainGuidanceText.text = registrationFailureText(result.failureReason)
            RegistrationState.IDLE -> Unit
        }
        baiduRegisterStatusText.text = when (result.state) {
            RegistrationState.IDLE -> ""
            RegistrationState.PREPARING -> getString(R.string.register_prepare_prompt)
            RegistrationState.WAITING_FOR_VALID_FACE -> getString(R.string.register_button_waiting)
            RegistrationState.COLLECTING -> getString(R.string.register_collecting_progress, result.collectedFrameCount, result.requiredFrameCount)
            RegistrationState.REGISTERING -> getString(R.string.register_button_running)
            RegistrationState.SUCCESS -> registrationSuccessText(result)
            RegistrationState.FAILED -> registrationFailureText(result.failureReason)
        }
        baiduRegisterStatusText.visibility = if (result.state == RegistrationState.IDLE) View.GONE else View.VISIBLE
        testUserCountText.text = getString(R.string.test_user_count_format, result.userCount)
    }

    private fun showRegisterNameDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.register_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(MAX_DISPLAY_NAME_LENGTH))
            isSingleLine = true
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.register_name_title)
            .setView(input)
            .setNegativeButton(R.string.common_cancel) { _, _ ->
                input.text?.clear()
            }
            .setPositiveButton(R.string.register_name_start, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val displayName = input.text?.toString()?.trim().orEmpty()
                if (displayName.isEmpty()) {
                    input.error = getString(R.string.register_name_required)
                    return@setOnClickListener
                }
                val repository = LocalFaceRepository(this)
                val exists = try {
                    repository.isDisplayNameExists(displayName)
                } finally {
                    repository.close()
                }
                if (exists) {
                    input.error = getString(R.string.register_name_duplicate)
                    return@setOnClickListener
                }
                input.text?.clear()
                dialog.dismiss()
                startRegistration(displayName)
            }
        }
        dialog.show()
    }

    private fun startRegistration(displayName: String) {
        val result = BaiduFaceDetectionProbe.requestRegistration(displayName)
        updateRegistrationStatus(result)
        if (result.state == RegistrationState.PREPARING
            || result.state == RegistrationState.WAITING_FOR_VALID_FACE
            || result.state == RegistrationState.COLLECTING
        ) {
            registrationTimeoutHandler.removeCallbacks(registrationTimeoutRunnable)
            registrationTimeoutHandler.postDelayed(registrationTimeoutRunnable, REGISTRATION_TIMEOUT_MS)
        }
    }

    private fun registrationFailureText(reason: RegistrationFailureReason?): String {
        return when (reason) {
            RegistrationFailureReason.TIMEOUT -> getString(R.string.register_timeout)
            RegistrationFailureReason.CANCELLED -> getString(R.string.register_cancelled)
            RegistrationFailureReason.EXCEPTION,
            RegistrationFailureReason.MODEL_NOT_READY,
            RegistrationFailureReason.REQUEST_ALREADY_PENDING,
            RegistrationFailureReason.NO_FACE,
            RegistrationFailureReason.FACE_TOO_SMALL,
            RegistrationFailureReason.QUALITY_NOT_PASSED,
            RegistrationFailureReason.LIVENESS_NOT_PASSED,
            RegistrationFailureReason.LANDMARKS_INVALID,
            RegistrationFailureReason.INVALID_DISPLAY_NAME,
            RegistrationFailureReason.FEATURE_EXTRACTION_FAILED,
            RegistrationFailureReason.DATABASE_CONSTRAINT_FAILED,
            RegistrationFailureReason.DATABASE_WRITE_FAILED,
            null -> getString(R.string.register_failed_retry)
        }
    }

    private fun registrationSuccessText(result: RegistrationResult): String {
        return getString(R.string.register_success, result.displayName ?: getString(R.string.test_user_generic))
    }

    private fun updateTestUserCount() {
        val repository = LocalFaceRepository(this)
        val userCount = try {
            repository.getUserCount()
        } finally {
            repository.close()
        }
        testUserCountText.text = getString(R.string.test_user_count_format, userCount)
    }

    private fun livenessText(result: BaiduFaceDetectionProbe.Result): String {
        if (result.type != ResultType.FACE_DETECTED || !result.quality.qualityPassed) {
            return getString(R.string.liveness_label) + "：" + getString(R.string.liveness_not_run)
        }
        return when (result.liveness.state) {
            LivenessState.NOT_RUN -> getString(R.string.liveness_label) + "：" + getString(R.string.liveness_not_run)
            LivenessState.CHECKING -> getString(R.string.liveness_label) + "：" + getString(R.string.status_checking)
            LivenessState.PASS -> getString(R.string.liveness_label) + "：" + getString(R.string.liveness_passed)
            LivenessState.FAIL -> getString(R.string.liveness_label) + "：" + getString(R.string.liveness_failed)
            LivenessState.ERROR -> getString(R.string.liveness_label) + "：" + getString(R.string.liveness_error)
        }
    }

    private fun updateIdentificationStatus(result: IdentificationResult, force: Boolean = false) {
        if (!force && result.state == IdentificationState.NOT_RUN) return
        if (shouldClearRecentMatch(result)) {
            clearRecentMatch()
        }
        baiduIdentifyStatusText.text = when (result.state) {
            IdentificationState.NOT_RUN -> getString(R.string.identify_not_run)
            IdentificationState.SEARCHING -> getString(R.string.identify_searching)
            IdentificationState.MATCHED -> {
                recentMatchedDisplayName = resolveMatchedDisplayName(result)
                recentSimilarityScore = result.similarityScore
                result.similarityScore?.let { score ->
                    getString(R.string.identify_status_success_with_score, score)
                } ?: getString(R.string.identify_matched)
            }
            IdentificationState.NOT_MATCHED -> getString(R.string.identify_not_matched)
            IdentificationState.NO_USERS -> getString(R.string.identify_no_users)
            IdentificationState.ERROR -> getString(R.string.identify_error)
        }
    }

    private fun shouldClearRecentMatch(result: IdentificationResult): Boolean {
        return result.state == IdentificationState.NOT_MATCHED
                || result.state == IdentificationState.NO_USERS
                || result.state == IdentificationState.ERROR
    }

    private fun resolveMatchedDisplayName(result: IdentificationResult): String {
        return result.matchedLocalId?.let { localId ->
            val repository = LocalFaceRepository(this)
            try {
                repository.getUserSummary(localId)?.displayName
            } finally {
                repository.close()
            }
        } ?: getString(R.string.identify_matched)
    }

    private fun clearRecentMatch() {
        recentMatchedDisplayName = null
        recentSimilarityScore = null
    }

    private fun qualityText(result: BaiduFaceDetectionProbe.Result): String {
        return when (result.type) {
            ResultType.NO_FACE -> getString(R.string.quality_label) + "：" + getString(R.string.quality_not_run)
            ResultType.NOT_READY,
            ResultType.ERROR -> getString(R.string.quality_label) + "：" + getString(R.string.quality_failed)
            ResultType.FACE_DETECTED -> when (result.quality.failureReason) {
                FailureReason.NONE -> getString(R.string.quality_label) + "：" + getString(R.string.quality_passed)
                else -> getString(R.string.quality_label) + "：" + getString(R.string.quality_failed)
            }
        }
    }

    private fun updateStatusColors(result: BaiduFaceDetectionProbe.Result) {
        baiduQualityStatusText.setTextColor(
            getColor(
                when {
                    result.type == ResultType.NO_FACE -> R.color.poc_neutral
                    result.type == ResultType.ERROR || result.type == ResultType.NOT_READY -> R.color.poc_error
                    result.quality.qualityPassed -> R.color.poc_success
                    else -> R.color.poc_warning
                }
            )
        )
        baiduLivenessStatusText.setTextColor(
            getColor(
                when (result.liveness.state) {
                    LivenessState.PASS -> R.color.poc_success
                    LivenessState.FAIL,
                    LivenessState.ERROR -> R.color.poc_error
                    LivenessState.CHECKING -> R.color.poc_primary
                    LivenessState.NOT_RUN -> R.color.poc_neutral
                }
            )
        )
        baiduIdentifyStatusText.setTextColor(
            getColor(
                when (result.identification.state) {
                    IdentificationState.MATCHED -> R.color.poc_success
                    IdentificationState.NOT_MATCHED -> R.color.poc_warning
                    IdentificationState.ERROR -> R.color.poc_error
                    IdentificationState.SEARCHING -> R.color.poc_primary
                    IdentificationState.NO_USERS,
                    IdentificationState.NOT_RUN -> R.color.poc_neutral
                }
            )
        )
        mainGuidanceText.setTextColor(
            getColor(
                when {
                    recentMatchedDisplayName != null || result.identification.state == IdentificationState.MATCHED || result.registration.state == RegistrationState.SUCCESS -> R.color.poc_success
                    result.liveness.state == LivenessState.FAIL || result.identification.state == IdentificationState.ERROR -> R.color.poc_error
                    result.type == ResultType.FACE_DETECTED && !result.quality.qualityPassed -> R.color.poc_warning
                    else -> R.color.white
                }
            )
        )
    }

    private fun updateMainGuidance(result: BaiduFaceDetectionProbe.Result) {
        when (result.registration.state) {
            RegistrationState.PREPARING -> {
                mainGuidanceText.text = getString(R.string.register_prepare_prompt)
                return
            }
            RegistrationState.COLLECTING -> {
                mainGuidanceText.text = getString(R.string.register_collecting_progress, result.registration.collectedFrameCount, result.registration.requiredFrameCount)
                return
            }
            RegistrationState.REGISTERING -> {
                mainGuidanceText.text = getString(R.string.register_button_running)
                return
            }
            RegistrationState.IDLE,
            RegistrationState.WAITING_FOR_VALID_FACE,
            RegistrationState.SUCCESS,
            RegistrationState.FAILED -> Unit
        }
        if (result.registration.state == RegistrationState.SUCCESS) {
            mainGuidanceText.text = registrationSuccessText(result.registration)
            return
        }
        if (shouldClearRecentMatchForFrame(result)) {
            clearRecentMatch()
        }
        recentMatchedDisplayName?.let { displayName ->
            if (result.identification.state == IdentificationState.NOT_RUN || result.identification.state == IdentificationState.SEARCHING) {
                mainGuidanceText.text = getString(R.string.welcome_identified_user, displayName)
                return
            }
        }
        if (result.identification.state == IdentificationState.MATCHED) {
            val displayName = recentMatchedDisplayName ?: resolveMatchedDisplayName(result.identification)
            mainGuidanceText.text = getString(R.string.welcome_identified_user, displayName)
            return
        }
        val textRes = when {
            result.registration.state == RegistrationState.FAILED && result.registration.failureReason == RegistrationFailureReason.TIMEOUT -> R.string.register_timeout
            result.type == ResultType.NOT_READY -> R.string.main_prompt_model_not_ready
            result.type == ResultType.ERROR -> R.string.main_prompt_detect_error
            result.type == ResultType.NO_FACE -> R.string.main_prompt_no_face
            result.type == ResultType.FACE_DETECTED && result.quality.failureReason == FailureReason.FACE_TOO_SMALL -> R.string.main_prompt_face_too_small
            result.type == ResultType.FACE_DETECTED && result.quality.failureReason == FailureReason.POSE -> R.string.main_prompt_pose
            result.type == ResultType.FACE_DETECTED && result.quality.failureReason == FailureReason.OCCLUSION -> R.string.main_prompt_occlusion
            result.type == ResultType.FACE_DETECTED && result.quality.failureReason == FailureReason.BLUR -> R.string.main_prompt_blur
            result.type == ResultType.FACE_DETECTED && result.quality.failureReason == FailureReason.ILLUMINATION -> R.string.main_prompt_illumination
            result.liveness.state == LivenessState.FAIL -> R.string.main_prompt_liveness_failed
            result.liveness.state == LivenessState.ERROR -> R.string.liveness_error
            result.identification.state == IdentificationState.NO_USERS -> R.string.main_prompt_no_users
            result.identification.state == IdentificationState.MATCHED -> R.string.main_prompt_matched
            result.identification.state == IdentificationState.NOT_MATCHED -> R.string.main_prompt_not_matched
            result.identification.state == IdentificationState.ERROR -> R.string.identify_error
            else -> R.string.main_prompt_liveness_checking
        }
        mainGuidanceText.text = getString(textRes)
    }

    private fun shouldClearRecentMatchForFrame(result: BaiduFaceDetectionProbe.Result): Boolean {
        return result.type == ResultType.NOT_READY
                || result.type == ResultType.ERROR
                || result.type == ResultType.NO_FACE
                || (result.type == ResultType.FACE_DETECTED && !result.quality.qualityPassed)
                || result.liveness.state == LivenessState.FAIL
                || result.liveness.state == LivenessState.ERROR
                || result.identification.state == IdentificationState.NOT_MATCHED
                || result.identification.state == IdentificationState.NO_USERS
                || result.identification.state == IdentificationState.ERROR
    }

    private fun findFrontCameraId(): Int? {
        val cameraInfo = Camera.CameraInfo()
        for (cameraId in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(cameraId, cameraInfo)
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return cameraId
            }
        }
        return null
    }

    private fun releaseCamera() {
        acceptingFrames = false
        clearRecentMatch()
        registrationTimeoutHandler.removeCallbacks(registrationTimeoutRunnable)
        BaiduFaceDetectionProbe.cancelRegistration()
        if (::overlayView.isInitialized) {
            overlayView.setFaceRect(null)
        }
        if (::baiduQualityStatusText.isInitialized) {
            baiduQualityStatusText.text = getString(R.string.quality_label) + "：" + getString(R.string.quality_not_run)
        }
        if (::baiduLivenessStatusText.isInitialized) {
            baiduLivenessStatusText.text = getString(R.string.liveness_label) + "：" + getString(R.string.liveness_not_run)
        }
        if (::baiduIdentifyStatusText.isInitialized) {
            baiduIdentifyStatusText.text = getString(R.string.identify_not_run)
        }
        if (::baiduRegisterStatusText.isInitialized) {
            baiduRegisterStatusText.text = ""
        }
        camera?.run {
            try {
                setPreviewCallback(null)
                stopPreview()
            } catch (exception: RuntimeException) {
                // Preview may already be stopped by the HAL.
            }
            release()
        }
        camera = null
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
        private const val PREVIEW_WIDTH = 640
        private const val PREVIEW_HEIGHT = 480
        private const val BAIDU_DETECT_DIRECTION = 270
        private const val BAIDU_DETECT_MIRROR = 0
        private const val DETECT_INTERVAL_MS = 300L
        private const val REGISTRATION_TIMEOUT_MS = 10_000L
        private const val MAX_DISPLAY_NAME_LENGTH = 20
        private const val DETECT_TAG = "POSFACE_BAIDU_DETECT"
        private const val PREVIEW_MIRRORED = true
    }
}