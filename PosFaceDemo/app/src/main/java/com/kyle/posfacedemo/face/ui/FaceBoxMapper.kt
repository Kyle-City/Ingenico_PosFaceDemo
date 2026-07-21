package com.kyle.posfacedemo.face.ui

import android.graphics.RectF
import com.kyle.posfacedemo.face.baidu.BaiduFaceDetectionProbe

object FaceBoxMapper {
    fun mapCenterCrop(
        faceBox: BaiduFaceDetectionProbe.FaceBox,
        viewWidth: Float,
        viewHeight: Float,
        mirrorHorizontally: Boolean = false
    ): RectF? {
        if (viewWidth <= 0f || viewHeight <= 0f || faceBox.imageWidth <= 0f || faceBox.imageHeight <= 0f) {
            return null
        }
        val scale: Float
        val dx: Float
        val dy: Float
        if (viewWidth * faceBox.imageHeight > viewHeight * faceBox.imageWidth) {
            scale = viewWidth / faceBox.imageWidth
            dx = 0f
            dy = (viewHeight - faceBox.imageHeight * scale) / 2f
        } else {
            scale = viewHeight / faceBox.imageHeight
            dx = (viewWidth - faceBox.imageWidth * scale) / 2f
            dy = 0f
        }
        val mapped = RectF(
            faceBox.left * scale + dx,
            faceBox.top * scale + dy,
            faceBox.right * scale + dx,
            faceBox.bottom * scale + dy
        )
        if (mirrorHorizontally) {
            val mirroredLeft = viewWidth - mapped.right
            val mirroredRight = viewWidth - mapped.left
            mapped.left = mirroredLeft.coerceIn(0f, viewWidth)
            mapped.right = mirroredRight.coerceIn(0f, viewWidth)
        }
        if (mapped.left >= mapped.right || mapped.top >= mapped.bottom) {
            return null
        }
        return mapped
    }
}