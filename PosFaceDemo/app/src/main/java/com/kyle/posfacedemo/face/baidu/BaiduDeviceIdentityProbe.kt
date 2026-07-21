package com.kyle.posfacedemo.face.baidu

import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.baidu.liantian.ac.LH

class BaiduDeviceIdentityProbe {
    fun check(context: Context): Boolean {
        return try {
            LH.init(context.applicationContext, false)
            Log.i(TAG, "lhInitSuccess=true")
            val pair = LH.getId(context.applicationContext, "1")
            val pairIsNull = pair == null
            val firstIsEmpty = pair == null || TextUtils.isEmpty(pair.first)
            val secondIsEmpty = pair == null || TextUtils.isEmpty(pair.second)
            Log.i(TAG, "pairIsNull=$pairIsNull")
            Log.i(TAG, "pairFirstIsEmpty=$firstIsEmpty")
            Log.i(TAG, "pairSecondIsEmpty=$secondIsEmpty")
            !pairIsNull && !firstIsEmpty && !secondIsEmpty
        } catch (exception: Exception) {
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            false
        }
    }

    private companion object {
        const val TAG = "POSFACE_BAIDU_DEVICE"
    }
}