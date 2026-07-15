package com.kyle.posfacedemo

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
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

@Suppress("DEPRECATION")
class CameraPreviewActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private var camera: Camera? = null
    private var previewHolder: SurfaceHolder? = null
    private var surfaceReady = false

    private lateinit var previewView: SurfaceView
    private lateinit var statusText: TextView

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
        statusText = findViewById(R.id.cameraStatusText)
        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

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
                openedCamera.setPreviewDisplay(holder)
                openedCamera.setDisplayOrientation(90)
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
        camera?.run {
            try {
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
    }
}