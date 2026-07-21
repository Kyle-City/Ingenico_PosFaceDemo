package com.kyle.posfacedemo.face.baidu

import android.content.Context
import android.util.Log
import com.baidu.idl.main.facesdk.FaceCrop
import com.baidu.idl.main.facesdk.FaceDarkEnhance
import com.baidu.idl.main.facesdk.FaceDetect
import com.baidu.idl.main.facesdk.FaceFeature
import com.baidu.idl.main.facesdk.FaceGaze
import com.baidu.idl.main.facesdk.FaceLive
import com.baidu.idl.main.facesdk.FaceMouthMask
import com.baidu.idl.main.facesdk.FaceSafetyHat
import com.baidu.idl.main.facesdk.FaceSearch
import com.baidu.idl.main.facesdk.callback.Callback
import com.baidu.idl.main.facesdk.model.BDFaceInstance
import com.baidu.idl.main.facesdk.model.BDFaceSDKCommon
import com.baidu.idl.main.facesdk.model.BDFaceSDKConfig
import com.baidu.vis.facecollect.license.AndroidLicenser
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object BaiduModelInitializationProbe {
    private const val TAG = "POSFACE_BAIDU_MODEL"
    private const val ALGORITHM_ID = 3
    private const val MODEL_INIT_BLOCKED_UNAUTHORIZED = -7001
    private const val MODEL_INIT_IN_PROGRESS = -7002
    private const val MODEL_INIT_TIMEOUT = -7003
    private const val MODEL_INIT_EXCEPTION = -7004
    private const val CALLBACK_COUNT = 17

    private val initializing = AtomicBoolean(false)
    @Volatile
    private var initialized = false
    @Volatile
    private var modelHolder: ModelHolder? = null

    fun initialize(context: Context, onResult: (Result) -> Unit) {
        if (initialized) {
            onResult(Result.Success(0L))
            return
        }
        if (!initializing.compareAndSet(false, true)) {
            onResult(Result.Failed(MODEL_INIT_IN_PROGRESS, 0L))
            return
        }
        Thread {
            val startTime = System.currentTimeMillis()
            Log.i(TAG, "modelInitStart=true")
            try {
                if (BaiduLocalAuthorizationRestorer.ensureAuthorized(context.applicationContext)
                    != BaiduLocalAuthorizationRestorer.Result.AUTHORIZED
                ) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.i(TAG, "modelInitResult=FAILED")
                    Log.i(TAG, "callbackCode=$MODEL_INIT_BLOCKED_UNAUTHORIZED")
                    Log.i(TAG, "durationMs=$duration")
                    onResult(Result.Failed(MODEL_INIT_BLOCKED_UNAUTHORIZED, duration))
                    return@Thread
                }

                val holder = ModelHolder()
                val firstErrorCode = AtomicInteger(0)
                val latch = CountDownLatch(CALLBACK_COUNT)
                val callback = Callback { code, _ ->
                    if (code != 0) {
                        firstErrorCode.compareAndSet(0, code)
                    }
                    latch.countDown()
                }
                holder.init(createConfig(), context.applicationContext, callback)
                val completed = latch.await(120, TimeUnit.SECONDS)
                val duration = System.currentTimeMillis() - startTime
                val code = when {
                    !completed -> MODEL_INIT_TIMEOUT
                    firstErrorCode.get() != 0 -> firstErrorCode.get()
                    else -> 0
                }
                if (code == 0) {
                    modelHolder = holder
                    initialized = true
                    Log.i(TAG, "modelInitResult=SUCCESS")
                    Log.i(TAG, "callbackCode=0")
                    Log.i(TAG, "durationMs=$duration")
                    onResult(Result.Success(duration))
                } else {
                    Log.i(TAG, "modelInitResult=FAILED")
                    Log.i(TAG, "callbackCode=$code")
                    Log.i(TAG, "durationMs=$duration")
                    onResult(Result.Failed(code, duration))
                }
            } catch (exception: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Log.i(TAG, "exceptionType=${exception.javaClass.name}")
                Log.i(TAG, "modelInitResult=FAILED")
                Log.i(TAG, "callbackCode=$MODEL_INIT_EXCEPTION")
                Log.i(TAG, "durationMs=$duration")
                onResult(Result.Failed(MODEL_INIT_EXCEPTION, duration))
            } finally {
                initializing.set(false)
            }
        }.start()
    }

    private fun createConfig(): BDFaceSDKConfig {
        return BDFaceSDKConfig().apply {
            maxDetectNum = 2
            trackInterval = Int.MAX_VALUE.toFloat()
            minFaceSize = 60
            notRGBFaceThreshold = 0.5f
            notNIRFaceThreshold = 0.5f
            isAttribute = false
            isCheckBlur = true
            isOcclusion = true
            isIllumination = true
            isHeadPose = true
        }
    }

    sealed class Result {
        data class Success(val durationMs: Long) : Result()
        data class Failed(val code: Int, val durationMs: Long) : Result()
    }

    fun getRgbFastFaceDetect(): FaceDetect? {
        return if (initialized) modelHolder?.rgbFastFaceDetect else null
    }

    fun getRgbAccurateFaceDetect(): FaceDetect? {
        return if (initialized) modelHolder?.rgbAccurateFaceDetect else null
    }

    fun getRgbFaceLive(): FaceLive? {
        return if (initialized) modelHolder?.rgbFaceLive else null
    }

    fun getLivePhotoFaceFeature(): FaceFeature? {
        return if (initialized) modelHolder?.livePhotoFaceFeature else null
    }

    fun getLivePhotoFaceSearch(): FaceSearch? {
        return if (initialized) modelHolder?.livePhotoFaceSearch else null
    }

    private class ModelHolder {
        private val trackInstance = BDFaceInstance().apply { creatInstance() }
        private val detectInstance = BDFaceInstance().apply { creatInstance() }
        private val detectQualityInstance = BDFaceInstance().apply { creatInstance() }
        private val cropInstance = BDFaceInstance().apply { creatInstance() }

        private val trackDetect = FaceDetect(trackInstance)
        private val detect = FaceDetect(detectInstance)
        private val detectQuality = FaceDetect(detectQualityInstance)
        private val crop = FaceCrop(cropInstance)
        private val nirDetect = FaceDetect(detectInstance)
        private val darkEnhance = FaceDarkEnhance()
        private val live = FaceLive()
        private val featurePerson = FaceFeature(detectQualityInstance)
        private val feature = FaceFeature()
        private val faceSearch = FaceSearch()
        private val mouthMask = FaceMouthMask()
        private val safetyHat = FaceSafetyHat()
        private val gaze = FaceGaze()

        val rgbFastFaceDetect: FaceDetect
            get() = trackDetect
        val rgbAccurateFaceDetect: FaceDetect
            get() = detect
        val rgbFaceLive: FaceLive
            get() = live
        val livePhotoFaceFeature: FaceFeature
            get() = feature
        val livePhotoFaceSearch: FaceSearch
            get() = faceSearch

        fun init(config: BDFaceSDKConfig, context: Context, callback: Callback) {
            trackDetect.loadConfig(config)
            detect.loadConfig(config)
            detectQuality.loadConfig(config)

            gaze.initModel(context, GAZE_MODEL, callback)
            mouthMask.initModel(context, MOUTH_MASK_MODEL, callback)
            safetyHat.initModel(context, SAFETY_HAT_MODEL, callback)
            crop.initFaceCrop(callback)
            trackDetect.initModel(
                context,
                DETECT_VIS_MODEL,
                ALIGN_TRACK_MODEL,
                BDFaceSDKCommon.DetectType.DETECT_VIS,
                BDFaceSDKCommon.AlignType.BDFACE_ALIGN_TYPE_RGB_FAST,
                callback
            )
            detect.initModel(
                context,
                DETECT_VIS_MODEL,
                ALIGN_TRACK_MODEL,
                BDFaceSDKCommon.DetectType.DETECT_VIS,
                BDFaceSDKCommon.AlignType.BDFACE_ALIGN_TYPE_RGB_FAST,
                callback
            )
            detect.initModel(
                context,
                DETECT_VIS_MODEL,
                ALIGN_RGB_MODEL,
                BDFaceSDKCommon.DetectType.DETECT_VIS,
                BDFaceSDKCommon.AlignType.BDFACE_ALIGN_TYPE_RGB_ACCURATE,
                callback
            )
            detect.initQuality(context, BLUR_MODEL, OCCLUSION_MODEL, callback)
            detect.initAttrbute(context, ATTRIBUTE_MODEL, callback)
            detect.initBestImage(context, BEST_IMAGE_MODEL, callback)
            detectQuality.initModel(
                context,
                DETECT_VIS_MODEL,
                ALIGN_RGB_MODEL,
                BDFaceSDKCommon.DetectType.DETECT_VIS,
                BDFaceSDKCommon.AlignType.BDFACE_ALIGN_TYPE_RGB_ACCURATE,
                callback
            )
            detectQuality.initQuality(context, BLUR_MODEL, OCCLUSION_MODEL, callback)
            nirDetect.initModel(
                context,
                DETECT_NIR_MODEL,
                ALIGN_NIR_MODEL,
                BDFaceSDKCommon.DetectType.DETECT_NIR,
                BDFaceSDKCommon.AlignType.BDFACE_ALIGN_TYPE_NIR_ACCURATE,
                callback
            )
            darkEnhance.initFaceDarkEnhance(context, DARK_ENHANCE_MODEL, callback)
            live.initModel(context, LIVE_VIS_MODEL, "", "", "", LIVE_NIR_MODEL, LIVE_DEPTH_MODEL, callback)
            featurePerson.initModel(context, RECOGNIZE_IDPHOTO_MODEL, RECOGNIZE_VIS_MODEL, RECOGNIZE_NIR_MODEL, "", callback)
            feature.initModel(context, RECOGNIZE_IDPHOTO_MODEL, RECOGNIZE_VIS_MODEL, RECOGNIZE_NIR_MODEL, "", callback)
            faceSearch.setMaxUpdateSize(0)
            faceSearch.setInputDBIntervalTime(0)
            faceSearch.setRegisterCompareThreshold(90f)
            faceSearch.setUpdateCompareThreshold(0.9f)
            faceSearch.setInputDBThreshold(0.92f)
        }
    }

    private const val DETECT_VIS_MODEL = "face-sdk-models/detect/detect_rgb-customized-pa-192.model.float32-0.0.18.1"
    private const val DETECT_NIR_MODEL = "face-sdk-models/detect/detect_rgb-customized-pa-192.model.float32-0.0.18.1"
    private const val ALIGN_RGB_MODEL = "face-sdk-models/align/align_rgb-customized-pa-80.model.float32-6.4.14.4"
    private const val ALIGN_NIR_MODEL = "face-sdk-models/align/align_rgb-customized-pa-80.model.float32-6.4.14.4"
    private const val ALIGN_TRACK_MODEL = "face-sdk-models/align/align_rgb-customized-pa-fast.model.float32-0.7.5.5"
    private const val LIVE_VIS_MODEL = "face-sdk-models/silent_live/liveness_rgb-customized-pa-DCQsdk80.model.float32-1.1.82.1"
    private const val LIVE_NIR_MODEL = "face-sdk-models/silent_live/liveness_nir-customized-pa-DCQ_80.model.float32-1.1.78.1"
    private const val LIVE_DEPTH_MODEL = "face-sdk-models/silent_live/liveness_depth-customized-pa-paddle_60.model.float32-1.1.13.2"
    private const val RECOGNIZE_VIS_MODEL = "face-sdk-models/feature/feature_live-mnasnet-pa-attention_v4.model.int8-2.0.239.1"
    private const val RECOGNIZE_IDPHOTO_MODEL = "face-sdk-models/feature/feature_live-mnasnet-pa-attention_v4.model.int8-2.0.239.1"
    private const val RECOGNIZE_NIR_MODEL = "face-sdk-models/feature/feature_nir-mnasnet-pa-foreign.model.int8-2.0.189.1"
    private const val OCCLUSION_MODEL = "face-sdk-models/occlusion/occlusion-customized-pa-paddle.model.float32-2.0.7.3"
    private const val BLUR_MODEL = "face-sdk-models/blur/blur-customized-pa-addcloud_quant_e19.model.float32-3.0.13.3"
    private const val ATTRIBUTE_MODEL = "face-sdk-models/attribute/attribute-customized-pa-mobile.model.float32-1.0.9.5"
    private const val DARK_ENHANCE_MODEL = "face-sdk-models/dark_enhance/dark_enhance-customized-pa-zero_depthwise.model.float32-1.0.2.2"
    private const val GAZE_MODEL = "face-sdk-models/gaze/gaze-customized-pa-mobile.model.float32-1.0.3.4"
    private const val MOUTH_MASK_MODEL = "face-sdk-models/mouth_mask/mouth_mask-customized-pa-faceocc_3classes.model.float32-1.0.9.2"
    private const val BEST_IMAGE_MODEL = "face-sdk-models/best_image/best_image-mobilenet-pa-dcqe449_live_e51_relu_128.model.float32-1.0.3.1"
    private const val SAFETY_HAT_MODEL = "face-sdk-models/safetyhat/attribute-customized-pa-anquanmao2023_v1.model.float32-1.0.73.1"
}