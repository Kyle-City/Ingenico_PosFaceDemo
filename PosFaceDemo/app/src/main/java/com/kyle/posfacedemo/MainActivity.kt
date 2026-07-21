package com.kyle.posfacedemo

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kyle.posfacedemo.face.baidu.BaiduOnlineActivationProbe
import com.kyle.posfacedemo.face.baidu.BaiduAuthorizationProbe
import com.kyle.posfacedemo.face.baidu.BaiduDeviceIdentityProbe
import com.kyle.posfacedemo.face.baidu.LocalFaceRepository
import com.kyle.posfacedemo.face.baidu.BaiduModelInitializationProbe
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private lateinit var debugToolsToggleButton: Button
    private lateinit var debugToolsContainer: View
    private lateinit var baiduAuthResultText: TextView
    private lateinit var baiduModelResultText: TextView
    private lateinit var testUserCountValueText: TextView
    private lateinit var debugFeedbackText: TextView
    private lateinit var startFaceRecognitionButton: Button
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        activeActivity = WeakReference(this)
        startFaceRecognitionButton = findViewById(R.id.startDeviceTestButton)
        startFaceRecognitionButton.isEnabled = false
        startFaceRecognitionButton.setOnClickListener {
            startActivity(Intent(this, CameraPreviewActivity::class.java))
        }
        debugToolsToggleButton = findViewById(R.id.debugToolsToggleButton)
        debugToolsContainer = findViewById(R.id.debugToolsContainer)
        baiduAuthResultText = findViewById(R.id.baiduAuthResultText)
        baiduModelResultText = findViewById(R.id.baiduModelResultText)
        testUserCountValueText = findViewById(R.id.testUserCountValueText)
        debugFeedbackText = findViewById(R.id.debugFeedbackText)
        updateHomeStatus()
        startAutomaticInitializationIfNeeded()
        findViewById<Button>(R.id.checkBaiduDeviceButton).setOnClickListener {
            setDebugFeedback(getString(R.string.debug_device_checking), R.color.poc_primary)
            val success = BaiduDeviceIdentityProbe().check(this)
            if (success) {
                setDebugFeedback(getString(R.string.debug_device_check_success), R.color.poc_success)
            } else {
                setDebugFeedback(getString(R.string.debug_device_check_failed), R.color.poc_error)
            }
        }
        findViewById<Button>(R.id.checkBaiduAuthButton).setOnClickListener {
            setDebugFeedback(getString(R.string.debug_auth_checking), R.color.poc_primary)
            val result = BaiduAuthorizationProbe().check(this)
            updateAuthorizationStatus(result)
            when (result) {
                BaiduAuthorizationProbe.Result.AUTHORIZED -> setDebugFeedback(getString(R.string.debug_auth_ready), R.color.poc_success)
                BaiduAuthorizationProbe.Result.UNAUTHORIZED -> setDebugFeedback(getString(R.string.debug_auth_not_ready), R.color.poc_warning)
                BaiduAuthorizationProbe.Result.FAILED -> setDebugFeedback(getString(R.string.debug_auth_check_failed), R.color.poc_error)
            }
        }
        val activateBaiduButton = findViewById<Button>(R.id.activateBaiduButton)
        if (isDebuggable()) {
            debugToolsToggleButton.visibility = View.VISIBLE
            debugToolsToggleButton.setOnClickListener { toggleDebugTools() }
            activateBaiduButton.setOnClickListener {
                showBaiduActivationDialog()
            }
            findViewById<Button>(R.id.initBaiduModelButton).setOnClickListener {
                restartModelInitializationForDebug()
            }
        } else {
            debugToolsToggleButton.visibility = View.GONE
            debugToolsContainer.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        activeActivity = WeakReference(this)
        if (::testUserCountValueText.isInitialized) {
            updateHomeStatus()
        }
    }

    override fun onDestroy() {
        destroyed = true
        if (activeActivity.get() === this) {
            activeActivity = WeakReference(null)
        }
        super.onDestroy()
    }

    private fun updateHomeStatus() {
        if (BaiduModelInitializationProbe.getLivePhotoFaceFeature() != null) {
            autoInitState = AutoInitState.READY
        }
        renderAutoInitState(autoInitState)
        val repository = LocalFaceRepository(this)
        val userCount = try {
            repository.getUserCount()
        } finally {
            repository.close()
        }
        testUserCountValueText.text = getString(R.string.status_user_count_format, userCount)
        testUserCountValueText.setTextColor(getColor(if (userCount > 0) R.color.poc_success else R.color.poc_neutral))
    }

    private fun startAutomaticInitializationIfNeeded() {
        if (BaiduModelInitializationProbe.getLivePhotoFaceFeature() != null) {
            autoInitState = AutoInitState.READY
            renderAutoInitState(autoInitState)
            return
        }
        if (!autoInitSubmitted.compareAndSet(false, true)) {
            renderAutoInitState(autoInitState)
            return
        }
        autoInitState = AutoInitState.CHECKING_AUTH
        renderAutoInitState(autoInitState)
        Thread {
            val authResult = BaiduAuthorizationProbe().check(applicationContext)
            if (authResult != BaiduAuthorizationProbe.Result.AUTHORIZED) {
                autoInitState = AutoInitState.UNAUTHORIZED
                renderActiveAutoInitState()
                return@Thread
            }
            autoInitState = AutoInitState.INITIALIZING
            renderActiveAutoInitState()
            BaiduModelInitializationProbe.initialize(applicationContext) { result ->
                autoInitState = when (result) {
                    is BaiduModelInitializationProbe.Result.Success -> AutoInitState.READY
                    is BaiduModelInitializationProbe.Result.Failed -> AutoInitState.FAILED
                }
                renderActiveAutoInitState()
            }
        }.start()
    }

    private fun restartModelInitializationForDebug() {
        setDebugFeedback(getString(R.string.debug_model_init_checking), R.color.poc_primary)
        autoInitState = AutoInitState.INITIALIZING
        renderAutoInitState(autoInitState)
        BaiduModelInitializationProbe.initialize(applicationContext) { result ->
            autoInitState = when (result) {
                is BaiduModelInitializationProbe.Result.Success -> AutoInitState.READY
                is BaiduModelInitializationProbe.Result.Failed -> AutoInitState.FAILED
            }
            renderActiveAutoInitState()
            activeActivity.get()?.safeRunOnUiThread {
                when (result) {
                    is BaiduModelInitializationProbe.Result.Success -> setDebugFeedback(getString(R.string.debug_model_init_success), R.color.poc_success)
                    is BaiduModelInitializationProbe.Result.Failed -> setDebugFeedback(getString(R.string.debug_model_init_failed), R.color.poc_error)
                }
            }
        }
    }

    private fun renderActiveAutoInitState() {
        activeActivity.get()?.safeRunOnUiThread {
            renderAutoInitState(autoInitState)
        }
    }

    private fun renderAutoInitState(state: AutoInitState) {
        if (!::startFaceRecognitionButton.isInitialized) return
        when (state) {
            AutoInitState.NOT_STARTED -> {
                setStatus(baiduAuthResultText, getString(R.string.status_not_ready), R.color.poc_neutral)
                setStatus(baiduModelResultText, getString(R.string.status_model_not_initialized), R.color.poc_neutral)
                startFaceRecognitionButton.isEnabled = false
            }
            AutoInitState.CHECKING_AUTH -> {
                setStatus(baiduAuthResultText, getString(R.string.status_checking), R.color.poc_primary)
                setStatus(baiduModelResultText, getString(R.string.status_model_not_initialized), R.color.poc_neutral)
                startFaceRecognitionButton.isEnabled = false
            }
            AutoInitState.UNAUTHORIZED -> {
                setStatus(baiduAuthResultText, getString(R.string.status_not_ready), R.color.poc_warning)
                setStatus(baiduModelResultText, getString(R.string.status_model_not_initialized), R.color.poc_neutral)
                startFaceRecognitionButton.isEnabled = false
            }
            AutoInitState.INITIALIZING -> {
                setStatus(baiduAuthResultText, getString(R.string.status_ready), R.color.poc_success)
                setStatus(baiduModelResultText, getString(R.string.status_checking), R.color.poc_primary)
                startFaceRecognitionButton.isEnabled = false
            }
            AutoInitState.READY -> {
                setStatus(baiduAuthResultText, getString(R.string.status_ready), R.color.poc_success)
                setStatus(baiduModelResultText, getString(R.string.status_ready), R.color.poc_success)
                startFaceRecognitionButton.isEnabled = true
            }
            AutoInitState.FAILED -> {
                setStatus(baiduAuthResultText, getString(R.string.status_ready), R.color.poc_success)
                setStatus(baiduModelResultText, getString(R.string.model_init_failed_user), R.color.poc_error)
                startFaceRecognitionButton.isEnabled = false
            }
        }
    }

    private fun updateAuthorizationStatus(result: BaiduAuthorizationProbe.Result) {
        when (result) {
            BaiduAuthorizationProbe.Result.AUTHORIZED -> {
                setStatus(baiduAuthResultText, getString(R.string.status_ready), R.color.poc_success)
                if (autoInitState == AutoInitState.UNAUTHORIZED) {
                    autoInitSubmitted.set(false)
                    startAutomaticInitializationIfNeeded()
                }
            }
            BaiduAuthorizationProbe.Result.UNAUTHORIZED -> setStatus(baiduAuthResultText, getString(R.string.status_not_ready), R.color.poc_warning)
            BaiduAuthorizationProbe.Result.FAILED -> setStatus(baiduAuthResultText, getString(R.string.debug_check_failed), R.color.poc_error)
        }
    }

    private fun safeRunOnUiThread(action: () -> Unit) {
        if (destroyed || isFinishing) return
        runOnUiThread {
            if (!destroyed && !isFinishing) {
                action()
            }
        }
    }

    private fun setStatus(textView: TextView, text: String, colorRes: Int) {
        textView.text = text
        textView.setTextColor(getColor(colorRes))
    }

    private fun setDebugFeedback(text: String, colorRes: Int) {
        if (!::debugFeedbackText.isInitialized) return
        debugFeedbackText.text = text
        debugFeedbackText.setTextColor(getColor(colorRes))
        debugFeedbackText.visibility = View.VISIBLE
    }

    private fun toggleDebugTools() {
        val show = debugToolsContainer.visibility != View.VISIBLE
        debugToolsContainer.visibility = if (show) View.VISIBLE else View.GONE
        debugToolsToggleButton.text = getString(if (show) R.string.debug_tools_hide else R.string.debug_tools_show)
    }

    private fun showBaiduActivationDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.debug_activation_title)
            .setView(input)
            .setPositiveButton(R.string.debug_activation_action) { _, _ ->
                val licenseChars = input.text?.toString()?.trim()?.toCharArray()
                input.text?.clear()
                if (licenseChars == null) {
                    setDebugFeedback(getString(R.string.debug_license_format_error), R.color.poc_error)
                    return@setPositiveButton
                }
                val currentLicenseId = String(licenseChars)
                licenseChars.fill('\u0000')
                if (!LICENSE_FORMAT.matches(currentLicenseId)) {
                    setDebugFeedback(getString(R.string.debug_license_format_error), R.color.poc_error)
                    return@setPositiveButton
                }
                setDebugFeedback(getString(R.string.debug_activation_checking), R.color.poc_primary)
                BaiduOnlineActivationProbe().activate(this, currentLicenseId) { result ->
                    runOnUiThread {
                        when (result) {
                            BaiduOnlineActivationProbe.Result.Success -> setDebugFeedback(getString(R.string.debug_activation_success), R.color.poc_success)
                            is BaiduOnlineActivationProbe.Result.Failed -> setDebugFeedback(getString(R.string.debug_activation_failed), R.color.poc_error)
                            BaiduOnlineActivationProbe.Result.CheckFailed -> setDebugFeedback(getString(R.string.debug_activation_failed), R.color.poc_error)
                        }
                    }
                }
            }
            .setNegativeButton(R.string.common_cancel) { _, _ ->
                input.text?.clear()
            }
            .show()
    }

    private fun isDebuggable(): Boolean {
        return applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    private companion object {
        val LICENSE_FORMAT = Regex("^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$")
        private val autoInitSubmitted = AtomicBoolean(false)
        @Volatile
        private var autoInitState = AutoInitState.NOT_STARTED
        @Volatile
        private var activeActivity = WeakReference<MainActivity?>(null)
    }

    private enum class AutoInitState {
        NOT_STARTED,
        CHECKING_AUTH,
        UNAUTHORIZED,
        INITIALIZING,
        READY,
        FAILED
    }
}