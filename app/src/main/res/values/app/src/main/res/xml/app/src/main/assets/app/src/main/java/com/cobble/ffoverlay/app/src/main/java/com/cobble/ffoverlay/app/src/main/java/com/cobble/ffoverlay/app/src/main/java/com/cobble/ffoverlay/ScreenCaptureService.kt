package com.cobble.ffoverlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    companion object {
        private const val CHANNEL_ID = "ff_overlay_channel"
        private const val NOTIF_ID = 4711
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var detector: Detector? = null
    private var overlay: OverlayView? = null

    private val workerThread = HandlerThread("infer").apply { start() }
    private val worker = Handler(workerThread.looper)
    private val busy = AtomicBoolean(false)

    private lateinit var wm: WindowManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        detector = Detector(applicationContext)
        addOverlayWindow()
    }

    private fun addOverlayWindow() {
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        GameBridge.screenW = metrics.widthPixels
        GameBridge.screenH = metrics.heightPixels
        GameBridge.crosshairX = metrics.widthPixels / 2f
        GameBridge.crosshairY = metrics.heightPixels / 2f

        val view = OverlayView(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(view, params)
        view.setCrosshair(GameBridge.crosshairX, GameBridge.crosshairY)
        overlay = view
        GameBridge.onTargets = { list -> overlay?.update(list) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (code < 0 || data == null) { stopSelf(); return START_NOT_STICKY }
        startCapture(code, data)
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        if (projection != null) return
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data) ?: return

        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val dpi = metrics.densityDpi

        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        imageReader!!.setOnImageAvailableListener({ reader -> onFrame(reader, w, h) }, worker)

        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, worker)

        virtualDisplay = projection!!.createVirtualDisplay(
            "ffcap", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, worker
        )
    }

    private fun onFrame(reader: ImageReader, w: Int, h: Int) {
        if (!busy.compareAndSet(false, true)) { reader.acquireLatestImage()?.close(); return }
        try {
            val image = reader.acquireLatestImage() ?: return
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * w
            val bmp = Bitmap.createBitmap(w + rowPadding / plane.pixelStride, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(plane.buffer)
            val cropped = Bitmap.createBitmap(bmp, 0, 0, w, h)
            bmp.recycle()
            image.close()

            val det = detector ?: return
            val targets = det.detect(cropped)
            cropped.recycle()

            GameBridge.onTargets?.invoke(targets)
            AimService.instance?.feed(targets)
        } catch (_: Throwable) {} finally { busy.set(false) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Overlay", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return b.setContentTitle("FF Overlay ativo")
            .setContentText("ESP e mira em execução")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true).build()
    }

    override fun onDestroy() {
        GameBridge.onTargets = null
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        detector?.close()
        super.onDestroy()
        workerThread.quitSafely()
    }
}
