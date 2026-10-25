package com.cobble.ffoverlay

import android.graphics.RectF

object GameBridge {

    data class Target(
        val head: RectF?,
        val body: RectF?,
        val confidence: Float
    ) {
        val aimPointX: Float
            get() = head?.centerX() ?: body?.centerX() ?: 0f
        val aimPointY: Float
            get() = head?.centerY() ?: body?.centerY() ?: 0f
    }

    @Volatile var espEnabled: Boolean = true
    @Volatile var aimEnabled: Boolean = false
    @Volatile var aimSmoothingMs: Long = 120L
    @Volatile var fireTapX: Float = -1f
    @Volatile var fireTapY: Float = -1f
    @Volatile var autoFire: Boolean = false
    @Volatile var aimCooldownMs: Long = 220L

    @Volatile var screenW: Int = 0
    @Volatile var screenH: Int = 0
    @Volatile var crosshairX: Float = 0f
    @Volatile var crosshairY: Float = 0f

    @Volatile var onTargets: ((List<Target>) -> Unit)? = null
}
