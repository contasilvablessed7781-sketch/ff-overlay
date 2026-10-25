package com.cobble.ffoverlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlin.math.*

class AimService : AccessibilityService() {

    companion object {
        @Volatile var instance: AimService? = null
        private const val MAX_SWIPE = 260f
        private const val FOV_RADIUS = 420f
    }

    private var lastAimAt = 0L
    private var lastFireAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun feed(targets: List<GameBridge.Target>) {
        if (!GameBridge.aimEnabled || targets.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastAimAt < GameBridge.aimCooldownMs) return

        val cx = GameBridge.crosshairX
        val cy = GameBridge.crosshairY

        val target = targets
            .map { it to hypot(it.aimPointX - cx, it.aimPointY - cy) }
            .filter { it.second <= FOV_RADIUS }
            .minByOrNull { it.second }?.first ?: return

        val dx = target.aimPointX - cx
        val dy = target.aimPointY - cy
        if (hypot(dx, dy) < 8f) {
            if (GameBridge.autoFire && now - lastFireAt > 140L) { lastFireAt = now; tapFire() }
            return
        }

        val dist = hypot(dx, dy)
        val clamped = minOf(dist, MAX_SWIPE)
        val angle = atan2(dy, dx)
        val endX = cx + cos(angle) * clamped
        val endY = cy + sin(angle) * clamped

        val path = Path().apply { moveTo(cx, cy); lineTo(endX, endY) }
        val duration = GameBridge.aimSmoothingMs.coerceIn(40L, 400L)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()

        lastAimAt = now
        dispatchGesture(gesture, null, null)
    }

    private fun tapFire() {
        val x = GameBridge.fireTapX
        val y = GameBridge.fireTapY
        if (x < 0 || y < 0) return
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 40L))
            .build()
        dispatchGesture(gesture, null, null)
    }
}
