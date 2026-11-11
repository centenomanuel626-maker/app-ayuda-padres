@file:Suppress("DEPRECATION")

package com.manuel.ayudapadres

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Lupa: usa la cámara de atrás con zoom y linterna para leer letra pequeña
 * (etiquetas de medicinas, menús). Usa la API de cámara clásica porque es la más simple.
 */
class MagnifierActivity : Activity(), SurfaceHolder.Callback {
    private var camera: Camera? = null
    private var holder: SurfaceHolder? = null
    private var zoomStep = 2
    private var light = false
    private var frozen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Col.TINTA)

        val surface = SurfaceView(this)
        surface.holder.addCallback(this)
        val frame = FrameLayout(this)
        frame.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val hint = text("Acerca la cámara a lo que quieres leer", 18f, true, Col.BLANCO)
        hint.setPadding(dp(14), dp(10), dp(14), dp(10))
        hint.background = rounded(0x99000000.toInt(), 14)
        val hl = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        hl.topMargin = dp(16)
        frame.addView(hint, hl)
        root.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = vbox(10)
        bar.setPadding(dp(14), dp(12), dp(14), dp(16))
        bar.setBackgroundColor(Col.BLANCO)
        val zoomRow = hbox(10)
        zoomRow.addView(bigButton("➖ Menos", Col.BLANCO, Col.TINTA) { zoom(-1) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        zoomRow.addView(bigButton("➕ Más", Col.AZUL) { zoom(1) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(zoomRow, full())
        val row2 = hbox(10)
        row2.addView(bigButton("🔦 Luz", Col.NARANJA) { toggleLight() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val freeze = bigButton("⏸ Congelar", Col.MORADO) { }
        freeze.setOnClickListener {
            frozen = !frozen
            if (frozen) camera?.stopPreview() else camera?.startPreview()
            freeze.text = if (frozen) "▶ Seguir" else "⏸ Congelar"
        }
        row2.addView(freeze, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(row2, full())
        bar.addView(bigButton("Cerrar la lupa", Col.BLANCO, Col.TINTA) { finish() }, full())
        root.addView(bar, full())
        setContentView(root)

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) holder?.let { start(it) }
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        holder = h
        start(h)
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) { }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        release()
    }

    private fun start(h: SurfaceHolder) {
        if (camera != null) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        try {
            val cam = Camera.open()
            cam.setDisplayOrientation(90)
            val params = cam.parameters
            if (params.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) {
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }
            cam.parameters = params
            cam.setPreviewDisplay(h)
            cam.startPreview()
            camera = cam
            applyZoom()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "No pude abrir la cámara", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun zoom(delta: Int) {
        zoomStep = (zoomStep + delta).coerceIn(0, 5)
        applyZoom()
    }

    private fun applyZoom() {
        val cam = camera ?: return
        try {
            val params = cam.parameters
            if (params.isZoomSupported) {
                params.zoom = params.maxZoom * zoomStep / 5
                cam.parameters = params
            }
        } catch (e: Exception) { }
    }

    private fun toggleLight() {
        val cam = camera ?: return
        try {
            light = !light
            val params = cam.parameters
            params.flashMode = if (light) Camera.Parameters.FLASH_MODE_TORCH else Camera.Parameters.FLASH_MODE_OFF
            cam.parameters = params
        } catch (e: Exception) { }
    }

    private fun release() {
        camera?.let {
            try {
                it.stopPreview()
                it.release()
            } catch (e: Exception) { }
        }
        camera = null
    }

    override fun onPause() {
        super.onPause()
        release()
    }

    override fun onResume() {
        super.onResume()
        holder?.let { start(it) }
    }
}
