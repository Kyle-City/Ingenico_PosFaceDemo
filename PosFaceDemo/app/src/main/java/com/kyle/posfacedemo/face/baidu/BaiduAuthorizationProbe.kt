package com.kyle.posfacedemo.face.baidu

import android.content.Context
import android.util.Log
import com.baidu.vis.facecollect.license.AndroidLicenser

class BaiduAuthorizationProbe {
    fun check(context: Context): Result {
        Log.i(TAG, "checkStart=true")
        return try {
            val status = AndroidLicenser.getInstance().getAuthStatus(ALGORITHM_ID)
            val result = when {
                status == AndroidLicenser.ErrorCode.SUCCESS -> Result.AUTHORIZED
                else -> when (BaiduLocalAuthorizationRestorer.ensureAuthorized(context)) {
                    BaiduLocalAuthorizationRestorer.Result.AUTHORIZED -> Result.AUTHORIZED
                    BaiduLocalAuthorizationRestorer.Result.UNAUTHORIZED -> Result.UNAUTHORIZED
                    BaiduLocalAuthorizationRestorer.Result.FAILED -> Result.FAILED
                }
            }
            Log.i(TAG, "checkResult=$result")
            val finalStatus = AndroidLicenser.getInstance().getAuthStatus(ALGORITHM_ID)
            Log.i(TAG, "officialErrorCode=${finalStatus.ordinal}")
            result
        } catch (exception: Exception) {
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            Result.FAILED
        }
    }

    enum class Result {
        AUTHORIZED,
        UNAUTHORIZED,
        FAILED
    }

    private companion object {
        const val TAG = "POSFACE_BAIDU_AUTH"
        const val ALGORITHM_ID = 3
    }
}