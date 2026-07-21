package com.kyle.posfacedemo

import android.Manifest
import android.app.AlertDialog
import android.graphics.ImageFormat
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
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
    private lateinit var baiduDetectStatusText: TextView
    private lateinit var baiduQualityStatusText: TextView
    private lateinit var baiduLivenessStatusText: TextView
    private lateinit var baiduIdentifyStatusText: TextView
    private lateinit var baiduRegisterStatusText: TextView
    private lateinit var testUserCountText: TextView
    private lateinit var registerTestUserButton: Button
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
        baiduDetectStatusText = findViewById(R.id.baiduDetectStatusText)
    baiduQualityStatusText = findViewById(R.id.baiduQualityStatusText)
        baiduLivenessStatusText = findViewById(R.id.baiduLivenessStatusText)
        baiduIdentifyStatusText = findViewById(R.id.baiduIdentifyStatusText)
        baiduRegisterStatusText = findViewById(R.id.baiduRegisterStatusText)
        testUserCountText = findViewById(R.id.testUserCountText)
        registerTestUserButton = findViewById(R.id.registerTestUserButton)
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }
        registerTestUserButton.setOnClickListener {
            val result = BaiduFaceDetectionProbe.requestRegistration()
            updateRegistrationStatus(result)
            if (result.state == RegistrationState.WAITING_FOR_VALID_FACE) {
                registrationTimeoutHandler.removeCallbacks(registrationTimeoutRunnable)
                registrationTimeoutHandler.postDelayed(registrationTimeoutRunnable, REGISTRATION_TIMEOUT_MS)
            }
        }
        findViewById<Button>(R.id.deleteAllTestUsersButton).setOnClickListener {
            confirmDeleteAllTestUsers()
        }
        updateTestUserCount()
        updateIdentificationStatus(BaiduFaceIdentificationService.reloadFromRepository(this), force = true)

        previewHolder = previewView.holder.also { holder ->
            holder.addCallback(this)
        }

        if (hasCameraPermission()) {
            statusText.text = "正在打开前置摄像头"
        } else {
            statusText.text = "需要摄像头权限才能进行设备预览测试"
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }
    }

    override fun onResume() {
        super.onResume()
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
            statusText.text = "正在打开前置摄像头"
            if (surfaceReady) {
                startCameraPreview()
            }
        } else {
            statusText.visibility = View.VISIBLE
            statusText.text = "摄像头权限已拒绝，无法显示预览。请返回首页或在系统设置中允许摄像头权限。"
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
                statusText.text = "未找到前置摄像头"
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
            statusText.text = "无法打开前置摄像头，请返回后重试"
        } catch (exception: Exception) {
            releaseCamera()
            statusText.visibility = View.VISIBLE
            statusText.text = "摄像头预览启动失败，请返回后重试"
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
                    ResultType.FACE_DETECTED -> "检测到人脸"
                    ResultType.NO_FACE -> "未检测到人脸"
                    ResultType.NOT_READY -> "百度模型未就绪"
                    ResultType.ERROR -> "检测异常"
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
            }
        }
    }

    private fun updateRegistrationStatus(result: RegistrationResult) {
        registerTestUserButton.isEnabled = result.state != RegistrationState.WAITING_FOR_VALID_FACE && result.state != RegistrationState.REGISTERING
        if (result.state == RegistrationState.SUCCESS || result.state == RegistrationState.FAILED) {
            registrationTimeoutHandler.removeCallbacks(registrationTimeoutRunnable)
        }
        if (result.state == RegistrationState.SUCCESS) {
            updateIdentificationStatus(BaiduFaceIdentificationService.reloadFromRepository(this), force = true)
        }
        baiduRegisterStatusText.text = when (result.state) {
            RegistrationState.IDLE -> ""
            RegistrationState.WAITING_FOR_VALID_FACE -> "请正对摄像头完成注册"
            RegistrationState.REGISTERING -> "正在注册"
            RegistrationState.SUCCESS -> "测试用户注册成功"
            RegistrationState.FAILED -> registrationFailureText(result.failureReason)
        }
        testUserCountText.text = "测试用户数量：${result.userCount}"
    }

    private fun registrationFailureText(reason: RegistrationFailureReason?): String {
        return when (reason) {
            RegistrationFailureReason.MODEL_NOT_READY -> "注册失败：模型未就绪"
            RegistrationFailureReason.REQUEST_ALREADY_PENDING -> "注册失败：已有注册请求"
            RegistrationFailureReason.TIMEOUT -> "注册失败：等待超时"
            RegistrationFailureReason.NO_FACE -> "注册失败：未检测到人脸"
            RegistrationFailureReason.FACE_TOO_SMALL -> "注册失败：人脸距离过远"
            RegistrationFailureReason.QUALITY_NOT_PASSED -> "注册失败：质量未通过"
            RegistrationFailureReason.LIVENESS_NOT_PASSED -> "注册失败：活体未通过"
            RegistrationFailureReason.LANDMARKS_INVALID -> "注册失败：关键点无效"
            RegistrationFailureReason.FEATURE_EXTRACTION_FAILED -> "注册失败：特征提取失败"
            RegistrationFailureReason.DATABASE_WRITE_FAILED -> "注册失败：本地写入失败"
            RegistrationFailureReason.CANCELLED -> "注册已取消"
            RegistrationFailureReason.EXCEPTION,
            null -> "注册失败"
        }
    }

    private fun confirmDeleteAllTestUsers() {
        AlertDialog.Builder(this)
            .setTitle("删除全部测试用户")
            .setMessage("将删除本机全部测试人脸特征。此操作不会影响百度授权和模型文件。")
            .setPositiveButton("删除") { _, _ ->
                registrationTimeoutHandler.removeCallbacks(registrationTimeoutRunnable)
                BaiduFaceDetectionProbe.cancelRegistration()
                BaiduFaceIdentificationService.clearSearchMemory()
                registerTestUserButton.isEnabled = true
                val repository = LocalFaceRepository(this)
                val deletedCount = try {
                    repository.deleteAll()
                } finally {
                    repository.close()
                }
                testUserCountText.text = "测试用户数量：0"
                baiduRegisterStatusText.text = "已删除 $deletedCount 个测试用户"
                updateIdentificationStatus(BaiduFaceIdentificationService.clearSearchMemory(), force = true)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateTestUserCount() {
        val repository = LocalFaceRepository(this)
        val userCount = try {
            repository.getUserCount()
        } finally {
            repository.close()
        }
        testUserCountText.text = "测试用户数量：$userCount"
    }

    private fun livenessText(result: BaiduFaceDetectionProbe.Result): String {
        if (result.type != ResultType.FACE_DETECTED || !result.quality.qualityPassed) {
            return "活体未检测"
        }
        return when (result.liveness.state) {
            LivenessState.NOT_RUN -> "活体未检测"
            LivenessState.CHECKING -> "活体检测中"
            LivenessState.PASS -> "活体通过 ${formatScore(result.liveness.score)}"
            LivenessState.FAIL -> "活体未通过 ${formatScore(result.liveness.score)}"
            LivenessState.ERROR -> "活体检测异常"
        }
    }

    private fun updateIdentificationStatus(result: com.kyle.posfacedemo.face.baidu.IdentificationResult, force: Boolean = false) {
        if (!force && result.state == IdentificationState.NOT_RUN) return
        baiduIdentifyStatusText.text = when (result.state) {
            IdentificationState.NOT_RUN -> "识别未执行"
            IdentificationState.SEARCHING -> "正在识别"
            IdentificationState.MATCHED -> "已识别测试用户 ${formatIdentifyScore(result.similarityScore)} 阈值 ${formatIdentifyScore(result.threshold)}"
            IdentificationState.NOT_MATCHED -> "未识别 ${formatIdentifyScore(result.similarityScore)} 阈值 ${formatIdentifyScore(result.threshold)}"
            IdentificationState.NO_USERS -> "暂无测试用户"
            IdentificationState.ERROR -> "识别异常"
        }
    }

    private fun formatIdentifyScore(score: Float?): String {
        return score?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: ""
    }

    private fun formatScore(score: Float?): String {
        return score?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: ""
    }

    private fun qualityText(result: BaiduFaceDetectionProbe.Result): String {
        return when (result.type) {
            ResultType.NO_FACE -> "未检测到人脸"
            ResultType.NOT_READY,
            ResultType.ERROR -> "质量检测异常"
            ResultType.FACE_DETECTED -> when (result.quality.failureReason) {
                FailureReason.NONE -> "质量通过"
                FailureReason.POSE -> "请正对摄像头"
                FailureReason.BLUR -> "图像模糊"
                FailureReason.ILLUMINATION -> "光线不足"
                FailureReason.OCCLUSION -> "面部存在遮挡"
                FailureReason.FACE_TOO_SMALL -> "人脸距离过远"
                FailureReason.UNKNOWN -> "质量检测异常"
            }
        }
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
        registrationTimeoutHandler.removeCallbacks(registrationTimeoutRunnable)
        BaiduFaceDetectionProbe.cancelRegistration()
        if (::overlayView.isInitialized) {
            overlayView.setFaceRect(null)
        }
        if (::baiduQualityStatusText.isInitialized) {
            baiduQualityStatusText.text = "未检测到人脸"
        }
        if (::baiduLivenessStatusText.isInitialized) {
            baiduLivenessStatusText.text = "活体未检测"
        }
        if (::baiduIdentifyStatusText.isInitialized) {
            baiduIdentifyStatusText.text = "识别未执行"
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
        private const val DETECT_TAG = "POSFACE_BAIDU_DETECT"
        private const val PREVIEW_MIRRORED = true
    }
}