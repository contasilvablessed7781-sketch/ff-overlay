package com.cobble.ffoverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class OverlayView(context: Context) : View(context) {

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 4f
        color = Color.parseColor("#FF2D55"); isAntiAlias = true
    }
    private val headPaint = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 6f
        color = Color.parseColor("#00FF88"); isAntiAlias = true
    }
    private val linePaint = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
        color = Color.WHITE; isAntiAlias = true
    }

    @Volatile private var targets: List<GameBridge.Target> = emptyList()
    @Volatile private var crossX = 0f
    @Volatile private var crossY = 0f

    fun update(list: List<GameBridge.Target>) {
        targets = list
        postInvalidateOnAnimation()
    }

    fun setCrosshair(x: Float, y: Float) {
        crossX = x; crossY = y
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!GameBridge.espEnabled) return

        if (crossX > 0f) {
            canvas.drawCircle(crossX, crossY, 12f, linePaint)
            canvas.drawLine(crossX - 22f, crossY, crossX - 8f, crossY, linePaint)
            canvas.drawLine(crossX + 8f, crossY, crossX + 22f, crossY, linePaint)
            canvas.drawLine(crossX, crossY - 22f, crossX, crossY - 8f, linePaint)
            canvas.drawLine(crossX, crossY + 8f, crossX, crossY + 22f, linePaint)
        }

        for (t in targets) {
            t.body?.let { canvas.drawRect(it, boxPaint) }
            t.head?.let { canvas.drawRect(it, headPaint) }
            if (t.body != null && crossX > 0f) {
                val tx = t.head?.centerX() ?: t.body.centerX()
                val ty = t.head?.centerY() ?: t.body.centerY()
                canvas.drawLine(crossX, crossY, tx, ty, linePaint)
            }
        }
    }
}
