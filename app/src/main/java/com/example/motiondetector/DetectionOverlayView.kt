package com.example.motiondetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Draws bounding boxes + labels for the current frame's detections.
 * Boxes are supplied in the coordinate space of the analyzed bitmap
 * (see [setResults]); this view scales them to its own size.
 */
class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Box(
        val rect: RectF,
        val label: String,
        val score: Float,
        val isPerson: Boolean
    )

    private var boxes: List<Box> = emptyList()
    private var sourceWidth = 1
    private var sourceHeight = 1

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val labelBgPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val labelTextPaint = Paint().apply {
        color = Color.parseColor("#0B0F14")
        textSize = 34f
        isAntiAlias = true
        isFakeBoldText = true
    }

    /** Replace the boxes to draw. [sourceW]/[sourceH] are the pixel size of the analyzed bitmap. */
    fun setResults(newBoxes: List<Box>, sourceW: Int, sourceH: Int) {
        boxes = newBoxes
        sourceWidth = sourceW.coerceAtLeast(1)
        sourceHeight = sourceH.coerceAtLeast(1)
        postInvalidate()
    }

    fun clear() {
        boxes = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (boxes.isEmpty()) return

        val scaleX = width.toFloat() / sourceWidth.toFloat()
        val scaleY = height.toFloat() / sourceHeight.toFloat()

        for (box in boxes) {
            val color = if (box.isPerson) Color.parseColor("#4DA3FF") else Color.parseColor("#FF9F40")
            boxPaint.color = color
            labelBgPaint.color = color

            val left = box.rect.left * scaleX
            val top = box.rect.top * scaleY
            val right = box.rect.right * scaleX
            val bottom = box.rect.bottom * scaleY

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val text = "${box.label} ${(box.score * 100).toInt()}%"
            val textWidth = labelTextPaint.measureText(text)
            val labelTop = (top - 40f).coerceAtLeast(0f)
            canvas.drawRect(left, labelTop, left + textWidth + 20f, labelTop + 40f, labelBgPaint)
            canvas.drawText(text, left + 10f, labelTop + 29f, labelTextPaint)
        }
    }
}
