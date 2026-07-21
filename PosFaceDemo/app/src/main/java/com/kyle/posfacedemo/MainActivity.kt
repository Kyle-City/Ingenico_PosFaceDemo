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
import com.kyle.posfacedemo.face.baidu.BaiduModelInitializationProbe

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<Button>(R.id.startDeviceTestButton).setOnClickListener {
            startActivity(Intent(this, CameraPreviewActivity::class.java))
        }
        findViewById<Button>(R.id.checkBaiduDeviceButton).setOnClickListener {
            val success = BaiduDeviceIdentityProbe().check(this)
            findViewById<TextView>(R.id.baiduDeviceResultText).text = if (success) {
                "检查成功"
            } else {
                "检查失败"
            }
        }
        findViewById<Button>(R.id.checkBaiduAuthButton).setOnClickListener {
            val result = BaiduAuthorizationProbe().check(this)
            findViewById<TextView>(R.id.baiduAuthResultText).text = when (result) {
                BaiduAuthorizationProbe.Result.AUTHORIZED -> "已授权"
                BaiduAuthorizationProbe.Result.UNAUTHORIZED -> "未授权"
                BaiduAuthorizationProbe.Result.FAILED -> "检查失败"
            }
        }
        val activateBaiduButton = findViewById<Button>(R.id.activateBaiduButton)
        val activateBaiduResultText = findViewById<TextView>(R.id.baiduActivationResultText)
        if (isDebuggable()) {
            activateBaiduButton.visibility = View.VISIBLE
            activateBaiduResultText.visibility = View.VISIBLE
            activateBaiduButton.setOnClickListener {
                showBaiduActivationDialog(activateBaiduResultText)
            }
            findViewById<Button>(R.id.initBaiduModelButton).visibility = View.VISIBLE
            findViewById<TextView>(R.id.baiduModelResultText).visibility = View.VISIBLE
            findViewById<Button>(R.id.initBaiduModelButton).setOnClickListener {
                val modelResultText = findViewById<TextView>(R.id.baiduModelResultText)
                modelResultText.text = "初始化中"
                BaiduModelInitializationProbe.initialize(this) { result ->
                    runOnUiThread {
                        modelResultText.text = when (result) {
                            is BaiduModelInitializationProbe.Result.Success -> "初始化成功"
                            is BaiduModelInitializationProbe.Result.Failed -> "初始化失败：${result.code}"
                        }
                    }
                }
            }
        } else {
            activateBaiduButton.visibility = View.GONE
            activateBaiduResultText.visibility = View.GONE
            findViewById<Button>(R.id.initBaiduModelButton).visibility = View.GONE
            findViewById<TextView>(R.id.baiduModelResultText).visibility = View.GONE
        }
    }

    private fun showBaiduActivationDialog(resultText: TextView) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        AlertDialog.Builder(this)
            .setTitle("在线激活百度授权")
            .setView(input)
            .setPositiveButton("激活") { _, _ ->
                val licenseChars = input.text?.toString()?.trim()?.toCharArray()
                input.text?.clear()
                if (licenseChars == null) {
                    resultText.text = "序列号格式错误"
                    return@setPositiveButton
                }
                val currentLicenseId = String(licenseChars)
                licenseChars.fill('\u0000')
                if (!LICENSE_FORMAT.matches(currentLicenseId)) {
                    resultText.text = "序列号格式错误"
                    return@setPositiveButton
                }
                BaiduOnlineActivationProbe().activate(this, currentLicenseId) { result ->
                    runOnUiThread {
                        resultText.text = when (result) {
                            BaiduOnlineActivationProbe.Result.Success -> "激活成功"
                            is BaiduOnlineActivationProbe.Result.Failed -> "激活失败：${result.code}"
                            BaiduOnlineActivationProbe.Result.CheckFailed -> "激活失败：检查失败"
                        }
                    }
                }
            }
            .setNegativeButton("取消") { _, _ ->
                input.text?.clear()
            }
            .show()
    }

    private fun isDebuggable(): Boolean {
        return applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    private companion object {
        val LICENSE_FORMAT = Regex("^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$")
    }
}