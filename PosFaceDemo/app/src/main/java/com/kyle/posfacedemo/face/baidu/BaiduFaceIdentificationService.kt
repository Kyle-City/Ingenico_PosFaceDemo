package com.kyle.posfacedemo.face.baidu

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.baidu.idl.main.facesdk.model.BDFaceImageInstance
import com.baidu.idl.main.facesdk.model.BDFaceSDKCommon
import com.baidu.idl.main.facesdk.model.Feature
import java.util.concurrent.atomic.AtomicBoolean

object BaiduFaceIdentificationService {
    private const val TAG = "POSFACE_BAIDU_IDENTIFY"
    private const val TOP_NUM = 1
    private const val FEATURE_BUFFER_SIZE = 512
    private const val FEATURE_SUCCESS_SIZE = 128f
    private const val IDENTIFICATION_COOLDOWN_MS = 2_000L

    private val searching = AtomicBoolean(false)
    private var lastSearchStartedElapsedRealtime = 0L

    @Volatile
    private var loadedUserCount = 0

    fun reloadFromRepository(context: Context): IdentificationResult {
        val faceSearch = BaiduModelInitializationProbe.getLivePhotoFaceSearch()
            ?: return IdentificationResult.error(IdentificationFailureReason.MODEL_NOT_READY, userCount = loadedUserCount)
        val repository = LocalFaceRepository(context)
        return try {
            val users = repository.getAllTestUsers()
            synchronized(faceSearch) {
                faceSearch.featureClear()
                users.forEach { user ->
                    faceSearch.pushPersonById(user.localId, user.feature)
                }
            }
            loadedUserCount = users.size
            if (loadedUserCount == 0) IdentificationResult.noUsers() else IdentificationResult.notRun(loadedUserCount)
        } catch (exception: Exception) {
            synchronized(faceSearch) {
                faceSearch.featureClear()
            }
            loadedUserCount = 0
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            logResult(IdentificationResult.error(IdentificationFailureReason.DATABASE_LOAD_FAILED))
        } finally {
            repository.close()
        }
    }

    fun clearSearchMemory(): IdentificationResult {
        val faceSearch = BaiduModelInitializationProbe.getLivePhotoFaceSearch()
        if (faceSearch != null) {
            synchronized(faceSearch) {
                faceSearch.featureClear()
            }
        }
        loadedUserCount = 0
        return IdentificationResult.noUsers()
    }

    fun identify(imageInstance: BDFaceImageInstance, landmarks: FloatArray): IdentificationResult {
        val userCount = loadedUserCount
        if (userCount <= 0) {
            return logResult(IdentificationResult.noUsers())
        }
        if (landmarks.isEmpty()) {
            return logResult(IdentificationResult.error(IdentificationFailureReason.LANDMARKS_INVALID, userCount = userCount))
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastSearchStartedElapsedRealtime < IDENTIFICATION_COOLDOWN_MS) {
            return IdentificationResult.notRun(userCount)
        }
        if (!searching.compareAndSet(false, true)) {
            return IdentificationResult.notRun(userCount)
        }
        lastSearchStartedElapsedRealtime = now
        val startTime = System.currentTimeMillis()
        val featureBuffer = ByteArray(FEATURE_BUFFER_SIZE)
        return try {
            val faceFeature = BaiduModelInitializationProbe.getLivePhotoFaceFeature()
                ?: return logResult(IdentificationResult.error(IdentificationFailureReason.MODEL_NOT_READY, userCount = userCount))
            val faceSearch = BaiduModelInitializationProbe.getLivePhotoFaceSearch()
                ?: return logResult(IdentificationResult.error(IdentificationFailureReason.MODEL_NOT_READY, userCount = userCount))
            val featureSize = synchronized(faceFeature) {
                faceFeature.feature(
                    BDFaceSDKCommon.FeatureType.BDFACE_FEATURE_TYPE_LIVE_PHOTO,
                    imageInstance,
                    landmarks,
                    featureBuffer
                )
            }
            Log.i(TAG, "featureSizeReturned=$featureSize")
            val durationMs = System.currentTimeMillis() - startTime
            if (featureSize != FEATURE_SUCCESS_SIZE) {
                return logResult(
                    IdentificationResult.error(
                        IdentificationFailureReason.FEATURE_EXTRACTION_FAILED,
                        durationMs,
                        userCount
                    )
                )
            }
            val candidates = synchronized(faceSearch) {
                faceSearch.search(
                    BDFaceSDKCommon.FeatureType.BDFACE_FEATURE_TYPE_LIVE_PHOTO,
                    IdentificationResult.IDENTIFY_THRESHOLD,
                    TOP_NUM,
                    featureBuffer
                )
            }
            toResult(candidates, System.currentTimeMillis() - startTime, userCount)
        } catch (exception: Exception) {
            Log.i(TAG, "exceptionType=${exception.javaClass.name}")
            logResult(IdentificationResult.error(IdentificationFailureReason.EXCEPTION, System.currentTimeMillis() - startTime, userCount))
        } finally {
            featureBuffer.fill(0)
            searching.set(false)
        }
    }

    private fun toResult(candidates: List<Feature>?, durationMs: Long, userCount: Int): IdentificationResult {
        val candidateCount = candidates?.size ?: 0
        val topFeature = candidates?.firstOrNull()
        val score = topFeature?.score
        val result = if (topFeature != null && score != null && score > IdentificationResult.IDENTIFY_THRESHOLD) {
            IdentificationResult(
                state = IdentificationState.MATCHED,
                matchedLocalId = topFeature.id,
                similarityScore = score,
                durationMs = durationMs,
                userCount = userCount,
                candidateCount = candidateCount
            )
        } else {
            IdentificationResult(
                state = IdentificationState.NOT_MATCHED,
                similarityScore = score,
                durationMs = durationMs,
                userCount = userCount,
                candidateCount = candidateCount
            )
        }
        return logResult(result)
    }

    private fun logResult(result: IdentificationResult): IdentificationResult {
        Log.i(TAG, "state=${result.state}")
        Log.i(TAG, "durationMs=${result.durationMs}")
        Log.i(TAG, "userCount=${result.userCount}")
        Log.i(TAG, "candidateCount=${result.candidateCount}")
        result.similarityScore?.let { Log.i(TAG, "similarityScore=$it") }
        Log.i(TAG, "threshold=${result.threshold}")
        result.failureReason?.let { Log.i(TAG, "failureReason=$it") }
        return result
    }
}