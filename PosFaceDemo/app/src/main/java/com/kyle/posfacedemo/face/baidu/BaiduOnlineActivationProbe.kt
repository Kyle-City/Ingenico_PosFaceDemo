package com.kyle.posfacedemo.face.baidu

import android.content.Context
import android.text.TextUtils
import android.util.Log
import android.util.Pair
import com.baidu.idl.main.facesdk.FaceAuth
import com.baidu.liantian.ac.LH
import com.baidu.vis.facecollect.license.AndroidLicenser
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class BaiduOnlineActivationProbe {
    fun activate(context: Context, licenseId: String, onResult: (Result) -> Unit) {
        Log.i(TAG, "activationStart=true")
        try {
            Thread {
                val result = activateInternal(context.applicationContext, licenseId)
                Log.i(TAG, "activationResult=${if (result is Result.Success) "SUCCESS" else "FAILED"}")
                if (result is Result.Failed) {
                    Log.i(TAG, "callbackCode=${result.code}")
                }
                onResult(result)
            }.start()
        } catch (exception: Exception) {
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            onResult(Result.CheckFailed)
        }
    }

    private fun activateInternal(context: Context, licenseId: String): Result {
        val deviceId = getDeviceId(context) ?: return Result.Failed(DEVICE_ID_EMPTY)

        val filesDir = context.filesDir
        val keyFile = File(filesDir, KEY_FILE_NAME)
        val iniFile = File(filesDir, INI_FILE_NAME)
        if (keyFile.canRead() && iniFile.canRead() && keyFile.isFile && iniFile.isFile) {
            val localKey = keyFile.readText()
            if (localKey == licenseId) {
                val localLicense = readLicenseFile(iniFile)
                if (localLicense != null) {
                    val verifyResult = verifyLicense(context, licenseId, localLicense)
                    if (verifyResult is Result.Success) {
                        return verifyResult
                    }
                }
            }
        }

        keyFile.delete()
        iniFile.delete()

        Log.i(TAG, "activationStage=REQUEST")
        val httpResult = requestPost(licenseId, deviceId)
        if (httpResult.code != CODE_SUCCESS || httpResult.response == null) {
            return Result.Failed(httpResult.code)
        }

        Log.i(TAG, "activationStage=PARSE")
        val licenses = when (val parseResult = parseLicense(httpResult.response)) {
            is ParseResult.Success -> parseResult.licenses
            is ParseResult.Failed -> return Result.Failed(parseResult.code)
        }

        Log.i(TAG, "activationStage=SAVE")
        val saveResult = saveLicenseFiles(filesDir, licenseId, licenses)
        if (saveResult is Result.Failed) {
            return saveResult
        }

        Log.i(TAG, "activationStage=VERIFY")
        return verifyLicense(context, licenseId, licenses)
    }

    private fun getDeviceId(context: Context): String? {
        Log.i(TAG, "activationStage=DEVICE_ID")
        return try {
            LH.init(context, false)
            val pair: Pair<String, String>? = LH.getId(context, ID_FLAG)
            if (pair == null || TextUtils.isEmpty(pair.second)) {
                null
            } else {
                pair.second.uppercase()
            }
        } catch (exception: Exception) {
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            null
        }
    }

    private fun requestPost(licenseId: String, deviceId: String): HttpResult {
        return try {
            val jsonObject = JSONObject()
            jsonObject.put("deviceId", deviceId)
            jsonObject.put("key", licenseId)
            jsonObject.put("platformType", 2)
            jsonObject.put("version", 5)
            val postDataBytes = jsonObject.toString().toByteArray(StandardCharsets.UTF_8)
            val connection = (URL(ACTIVATION_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                doInput = true
                requestMethod = "POST"
                useCaches = false
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json")
            }
            connection.connect()
            connection.outputStream.use { outputStream ->
                outputStream.write(postDataBytes)
                outputStream.flush()
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                HttpResult(CODE_SUCCESS, connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            } else {
                HttpResult(CODE_HTTP_ERROR, null)
            }
        } catch (exception: IOException) {
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            HttpResult(CODE_WIFI_ERROR, null)
        } catch (exception: JSONException) {
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            HttpResult(CODE_JSON_ERROR, null)
        }
    }

    private fun parseLicense(response: String): ParseResult {
        return try {
            val json = JSONObject(response)
            val jsonErrorCode = json.optInt("error_code")
            if (jsonErrorCode != 0) {
                return ParseResult.Failed(jsonErrorCode)
            }
            val result = json.optJSONObject("result") ?: return ParseResult.Failed(CODE_HTTP_RESULT_ERROR)
            val license = result.optString("license")
            if (TextUtils.isEmpty(license)) {
                return ParseResult.Failed(CODE_HTTP_RESULT_ERROR)
            }
            val licenses = license.split(",").toTypedArray()
            if (licenses.size == 2) {
                ParseResult.Success(licenses)
            } else {
                ParseResult.Failed(CODE_HTTP_RESULT_ERROR)
            }
        } catch (exception: JSONException) {
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            ParseResult.Failed(CODE_JSON_ERROR)
        }
    }

    private fun saveLicenseFiles(filesDir: File, licenseId: String, licenses: Array<String>): Result {
        return try {
            val keyFile = File(filesDir, KEY_FILE_NAME)
            val iniFile = File(filesDir, INI_FILE_NAME)
            keyFile.delete()
            iniFile.delete()
            keyFile.writeText(licenseId)
            iniFile.writeText(licenses.joinToString("\n"))
            Result.Success
        } catch (exception: IOException) {
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            Result.Failed(CODE_FILE_WRITE_ERROR)
        } catch (exception: SecurityException) {
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            Result.Failed(CODE_FILE_WRITE_ERROR)
        }
    }

    private fun readLicenseFile(file: File): Array<String>? {
        val lines = file.readLines()
        return if (lines.size >= 2) arrayOf(lines[0], lines[1]) else null
    }

    private fun verifyLicense(context: Context, licenseId: String, licenses: Array<String>): Result {
        val licenser = AndroidLicenser.getInstance()
        val fileStatus = licenser.authFromFile(context, licenseId, LICENSE_NAME, false, ALGORITHM_ID)
        if (fileStatus == AndroidLicenser.ErrorCode.SUCCESS) {
            FaceAuth().createInstance()
            return Result.Success
        }
        val memoryStatus = licenser.authFromMemory(context, licenseId, licenses, LICENSE_NAME, ALGORITHM_ID)
        return if (memoryStatus == AndroidLicenser.ErrorCode.SUCCESS) {
            FaceAuth().createInstance()
            Result.Success
        } else {
            Result.Failed(memoryStatus.ordinal)
        }
    }

    sealed class Result {
        data object Success : Result()
        data class Failed(val code: Int) : Result()
        data object CheckFailed : Result()
    }

    private data class HttpResult(val code: Int, val response: String?)

    private sealed class ParseResult {
        data class Success(val licenses: Array<String>) : ParseResult()
        data class Failed(val code: Int) : ParseResult()
    }

    private companion object {
        const val TAG = "POSFACE_BAIDU_ACTIVATION"
        const val ID_FLAG = "1"
        const val ALGORITHM_ID = 3
        const val LICENSE_NAME = "idl-license.face-android"
        const val KEY_FILE_NAME = "license.key"
        const val INI_FILE_NAME = "license.ini"
        const val ACTIVATION_URL = "https://ai.baidu.com/activation/key/activate"
        const val CODE_SUCCESS = 1000
        const val CODE_JSON_ERROR = 1008
        const val CODE_HTTP_ERROR = 1009
        const val CODE_HTTP_RESULT_ERROR = 1010
        const val CODE_WIFI_ERROR = 1011
        const val DEVICE_ID_EMPTY = 1007
        const val CODE_FILE_WRITE_ERROR = 1001
    }
}