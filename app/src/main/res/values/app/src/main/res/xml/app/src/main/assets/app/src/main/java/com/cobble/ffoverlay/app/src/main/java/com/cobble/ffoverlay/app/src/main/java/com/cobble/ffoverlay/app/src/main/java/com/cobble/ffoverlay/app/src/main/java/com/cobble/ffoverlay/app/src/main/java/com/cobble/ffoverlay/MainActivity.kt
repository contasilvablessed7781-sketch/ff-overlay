package com.cobble.ffoverlay

import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK && res.data != null) {
                val i = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, res.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, res.data)
                }
                startForegroundService(i)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#0E0E12"))
        }

        fun label(t: String) = TextView(this).apply {
            text = t; setTextColor(Color.WHITE); textSize = 15f
            setPadding(0, pad / 2, 0, pad / 4)
        }

        root.addView(label("FF Overlay — cobble"))

        root.addView(Button(this).apply {
            text = "1. Permitir sobreposição"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })

        root.addView(Button(this).apply {
            text = "2. Ativar serviço de mira"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        root.addView(Button(this).apply {
            text = "3. Iniciar captura + overlay"
            setOnClickListener {
                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                captureLauncher.launch(mpm.createScreenCaptureIntent())
            }
        })

        root.addView(Switch(this).apply {
            text = "ESP (caixas)"; setTextColor(Color.WHITE); isChecked = true
            setOnCheckedChangeListener { _, v -> GameBridge.espEnabled = v }
        })

        root.addView(Switch(this).apply {
            text = "Aimbot"; setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, v -> GameBridge.aimEnabled = v }
        })

        root.addView(Switch(this).apply {
            text = "Auto-fire"; setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, v -> GameBridge.autoFire = v }
        })

        root.addView(label("Suavização da mira (ms)"))
        root.addView(SeekBar(this).apply {
            max = 360; progress = 80
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    GameBridge.aimSmoothingMs = (p + 40).toLong()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        val fireLabel = label("toque para calibrar botão de tiro")
        root.addView(fireLabel)
        root.addView(Button(this).apply {
            text = "Calibrar botão de tiro"
            setOnClickListener {
                val m = resources.displayMetrics
                GameBridge.fireTapX = m.widthPixels * 0.86f
                GameBridge.fireTapY = m.heightPixels * 0.72f
                fireLabel.text = "X=${GameBridge.fireTapX.toInt()}  Y=${GameBridge.fireTapY.toInt()}"
            }
        })

        setContentView(root)
    }
}
