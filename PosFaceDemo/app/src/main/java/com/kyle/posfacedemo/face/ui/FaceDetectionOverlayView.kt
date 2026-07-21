package com.kyle.posfacedemo.face.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class FaceDetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private var faceRect: RectF? = null

    fun setFaceRect(rect: RectF?) {
        faceRect = rect?.let { RectF(it) }
        invalidate()
    }

    fun setQualityPassed(passed: Boolean) {
        paint.color = if (passed) Color.GREEN else Color.YELLOW
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        faceRect?.let { canvas.drawRect(it, paint) }
    }
}