package com.kyle.posfacedemo.face.baidu

import android.content.Context
import android.util.Log
import com.baidu.idl.main.facesdk.FaceAuth
import com.baidu.vis.facecollect.license.AndroidLicenser
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

object BaiduLocalAuthorizationRestorer {
    private const val TAG = "POSFACE_BAIDU_AUTH"
    private const val ALGORITHM_ID = 3
    private const val LICENSE_NAME = "idl-license.face-android"
    private const val KEY_FILE_NAME = "license.key"
    private const val INI_FILE_NAME = "license.ini"
    private const val FILE_NOT_FOUND = 1005
    private const val FILE_READ_ERROR = 1001

    fun ensureAuthorized(context: Context): Result {
        val licenser = AndroidLicenser.getInstance()
        val currentStatus = licenser.getAuthStatus(ALGORITHM_ID)
        if (currentStatus == AndroidLicenser.ErrorCode.SUCCESS) {
            return Result.AUTHORIZED
        }
        return restore(context.applicationContext)
    }

    private fun restore(context: Context): Result {
        Log.i(TAG, "localRestoreStart=true")
        val keyFile = File(context.filesDir, KEY_FILE_NAME)
        val iniFile = File(context.filesDir, INI_FILE_NAME)
        val filesPresent = keyFile.canRead() && iniFile.canRead() && keyFile.isFile && iniFile.isFile
        Log.i(TAG, "licenseFilesPresent=$filesPresent")
        if (!filesPresent) {
            Log.i(TAG, "localRestoreResult=FAILED")
            Log.i(TAG, "callbackCode=$FILE_NOT_FOUND")
            return Result.UNAUTHORIZED
        }

        var keyBytes: ByteArray? = null
        return try {
            keyBytes = keyFile.readBytes()
            val key = String(keyBytes, StandardCharsets.UTF_8)
            val licenseValues = readLicenseValues(iniFile)
            val licenser = AndroidLicenser.getInstance()
            val fileStatus = licenser.authFromFile(context, key, LICENSE_NAME, false, ALGORITHM_ID)
            val status = if (fileStatus == AndroidLicenser.ErrorCode.SUCCESS) {
                fileStatus
            } else if (licenseValues != null) {
                licenser.authFromMemory(context, key, licenseValues, LICENSE_NAME, ALGORITHM_ID)
            } else {
                fileStatus
            }
            if (status == AndroidLicenser.ErrorCode.SUCCESS) {
                FaceAuth().createInstance()
                Log.i(TAG, "localRestoreResult=SUCCESS")
                Log.i(TAG, "callbackCode=0")
                Result.AUTHORIZED
            } else {
                Log.i(TAG, "localRestoreResult=FAILED")
                Log.i(TAG, "callbackCode=${status.ordinal}")
                Result.UNAUTHORIZED
            }
        } catch (exception: IOException) {
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            Log.i(TAG, "localRestoreResult=FAILED")
            Log.i(TAG, "callbackCode=$FILE_READ_ERROR")
            Result.FAILED
        } catch (exception: SecurityException) {
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            Log.i(TAG, "localRestoreResult=FAILED")
            Log.i(TAG, "callbackCode=$FILE_READ_ERROR")
            Result.FAILED
        } finally {
            keyBytes?.fill(0)
        }
    }

    private fun readLicenseValues(file: File): Array<String>? {
        val lines = file.readLines(StandardCharsets.UTF_8)
        return if (lines.size >= 2) arrayOf(lines[0], lines[1]) else null
    }

    enum class Result {
        AUTHORIZED,
        UNAUTHORIZED,
        FAILED
    }
}