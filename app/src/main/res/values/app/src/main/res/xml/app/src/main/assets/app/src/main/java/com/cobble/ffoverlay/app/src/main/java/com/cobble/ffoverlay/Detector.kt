package com.cobble.ffoverlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class Detector(context: Context, private val inputSize: Int = 320) {

    private val labels: List<String> = try { loadLabels(context) } catch (e: Throwable) { listOf("head", "body") }
    private val numClasses = labels.size

    private val interpreter: Interpreter? = try {
        Interpreter(loadModel(context), Interpreter.Options().apply {
            setNumThreads(4)
            setUseXNNPACK(true)
        })
    } catch (e: Throwable) { null }

    val ready: Boolean = interpreter != null

    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).order(ByteOrder.nativeOrder())

    private val outputBuffer: ByteBuffer
    private val numAnchors: Int

    private val resizeBitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(resizeBitmap)
    private val paint = Paint().apply { isFilterBitmap = true }

    init {
        if (interpreter != null) {
            val shape = interpreter.getOutputTensor(0).shape()
            numAnchors = shape[2]
            outputBuffer = ByteBuffer.allocateDirect(4 * shape[1] * shape[2]).order(ByteOrder.nativeOrder())
        } else {
            numAnchors = 0
            outputBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        }
    }

    private fun loadModel(context: Context): ByteBuffer {
        val fd = context.assets.openFd("ff_detector.tflite")
        FileInputStream(fd.fileDescriptor).use { fis ->
            return fis.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    private fun loadLabels(context: Context): List<String> =
        context.assets.open("labels.txt").bufferedReader().readLines().filter { it.isNotBlank() }

    fun detect(frame: Bitmap, confThreshold: Float = 0.35f, iouThreshold: Float = 0.45f): List<GameBridge.Target> {
        if (interpreter == null || numAnchors == 0) return emptyList()

        val screenW = frame.width
        val screenH = frame.height

        canvas.drawBitmap(frame, null, RectF(0f, 0f, inputSize.toFloat(), inputSize.toFloat()), paint)

        inputBuffer.rewind()
        val pixels = IntArray(inputSize * inputSize)
        resizeBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            inputBuffer.putFloat((p shr 16 and 0xFF) / 255f)
            inputBuffer.putFloat((p shr 8 and 0xFF) / 255f)
            inputBuffer.putFloat((p and 0xFF) / 255f)
        }
        inputBuffer.rewind()
        outputBuffer.rewind()

        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        val scaleX = screenW.toFloat() / inputSize
        val scaleY = screenH.toFloat() / inputSize

        val raw = ArrayList<RectF>()
        val cls = ArrayList<Int>()
        val scores = ArrayList<Float>()
        val heads = ArrayList<RectF>()
        val bodies = ArrayList<RectF>()

        val anchors = outputBuffer.asFloatBuffer()
        val stride = 4 + numClasses

        for (i in 0 until numAnchors) {
            var bestScore = 0f
            var bestClass = -1
            for (c in 0 until numClasses) {
                val s = anchors.get(i * stride + 4 + c)
                if (s > bestScore) { bestScore = s; bestClass = c }
            }
            if (bestScore < confThreshold) continue

            val cx = anchors.get(i * stride)
            val cy = anchors.get(i * stride + 1)
            val w  = anchors.get(i * stride + 2)
            val h  = anchors.get(i * stride + 3)

            val rect = RectF(
                (cx - w / 2) * scaleX,
                (cy - h / 2) * scaleY,
                (cx + w / 2) * scaleX,
                (cy + h / 2) * scaleY
            )
            raw.add(rect); cls.add(bestClass); scores.add(bestScore)
        }

        val keep = nms(raw, cls, scores, iouThreshold)

        for (idx in keep) {
            val r = raw[idx]
            when (labels[cls[idx]].lowercase()) {
                "head" -> heads.add(r)
                else -> bodies.add(r)
            }
        }

        val targets = ArrayList<GameBridge.Target>()
        for (b in bodies) {
            val head = heads.minByOrNull {
                if (it.centerX() in b.left - b.width() .. b.right + b.width())
                    Math.abs(it.centerY() - b.top) else Float.MAX_VALUE
            }
            targets.add(GameBridge.Target(head, b, 0.8f))
        }
        for (h in heads) {
            if (targets.none { it.head == h }) targets.add(GameBridge.Target(h, null, 0.8f))
        }

        return targets.sortedByDescending { (it.body?.width() ?: 0f) * (it.body?.height() ?: 0f) }
    }

    private fun nms(rects: List<RectF>, classes: List<Int>, scores: List<Float>, iouThresh: Float): List<Int> {
        val order = scores.indices.sortedByDescending { scores[it] }
        val keep = ArrayList<Int>()
        val suppressed = BooleanArray(rects.size)
        for (i in order) {
            if (suppressed[i]) continue
            keep.add(i)
            for (j in order) {
                if (j == i || suppressed[j]) continue
                if (classes[i] == classes[j] && iou(rects[i], rects[j]) > iouThresh) suppressed[j] = true
            }
        }
        return keep
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interL = maxOf(a.left, b.left); val interT = maxOf(a.top, b.top)
        val interR = minOf(a.right, b.right); val interB = minOf(a.bottom, b.bottom)
        val interArea = maxOf(0f, interR - interL) * maxOf(0f, interB - interT)
        if (interArea <= 0f) return 0f
        return interArea / (a.width() * a.height() + b.width() * b.height() - interArea)
    }

    fun close() = interpreter?.close()
}
