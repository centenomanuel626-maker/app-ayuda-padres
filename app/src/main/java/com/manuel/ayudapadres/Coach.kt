package com.manuel.ayudapadres

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Tutorial guiado: oscurece la pantalla, deja brillando el botón que hay que tocar,
 * muestra un dedo que lo señala y explica en voz alta qué hacer.
 *
 * Cada paso busca su botón por la etiqueta (tag) que le pone MainActivity:
 * "cat:internet", "btn:wifi", "dock:call", "mic", "gear", "estoybien"...
 */
class Coach(
    private val a: MainActivity,
    private val steps: List<Step>,
    private val onFinish: () -> Unit
) {
    /**
     * tag: botón a señalar (null = mensaje en el centro, sin botón).
     * tap: true si la persona tiene que tocar el botón para seguir; false si se sigue con "Siguiente".
     * before: algo que hacer antes del paso (por ejemplo, volver al inicio).
     */
    data class Step(val tag: String?, val text: String, val tap: Boolean = false, val before: (() -> Unit)? = null)

    private val handler = Handler(Looper.getMainLooper())
    private val decor = a.window.decorView as ViewGroup
    private val overlay = FrameLayout(a)
    private val spot = SpotView(a)
    private val bubble = a.vbox(12)
    private val msg = a.text("", 23f, true)
    private val hint = a.text("", 18f, true, Col.AZUL)
    private val buttons = a.hbox(10)
    private val nextBtn: TextView
    private val exitBtn: TextView
    private var index = -1
    private var target: View? = null
    private var hole: RectF? = null
    private var busy = false
    private val pulse = ValueAnimator.ofFloat(0f, 1f)

    init {
        overlay.isClickable = true
        overlay.addView(spot, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        bubble.setPadding(a.dp(20), a.dp(18), a.dp(20), a.dp(18))
        bubble.background = a.rounded(Col.BLANCO, 24)
        bubble.elevation = a.dp(10).toFloat()
        bubble.addView(msg, a.full())
        bubble.addView(hint, a.full())
        exitBtn = a.bigButton("Salir", Col.BLANCO, Col.TINTA) { close() }
        exitBtn.minHeight = a.dp(56)
        nextBtn = a.bigButton("Siguiente ›", Col.AZUL) { show(index + 1) }
        nextBtn.minHeight = a.dp(56)
        buttons.addView(exitBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(nextBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
        bubble.addView(buttons, a.full())
        overlay.addView(bubble, bubbleParams(top = true, margin = 0))

        // Toques fuera de la burbuja: si es sobre el botón que brilla, se toca de verdad.
        overlay.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_UP) onTap(ev.x, ev.y)
            true
        }

        pulse.duration = 900
        pulse.repeatMode = ValueAnimator.REVERSE
        pulse.repeatCount = ValueAnimator.INFINITE
        pulse.addUpdateListener {
            spot.pulse = it.animatedValue as Float
            spot.invalidate()
        }
    }

    fun start() {
        decor.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pulse.start()
        show(0)
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        pulse.cancel()
        Speaker.stop()
        decor.removeView(overlay)
        a.coach = null
        onFinish()
    }

    private fun bubbleParams(top: Boolean, margin: Int): FrameLayout.LayoutParams {
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            if (top) Gravity.TOP else Gravity.BOTTOM)
        lp.leftMargin = a.dp(16)
        lp.rightMargin = a.dp(16)
        if (top) lp.topMargin = margin else lp.bottomMargin = margin
        return lp
    }

    private fun show(i: Int) {
        if (i >= steps.size) {
            close()
            return
        }
        index = i
        busy = true
        val step = steps[i]
        step.before?.invoke()
        bubble.visibility = View.INVISIBLE
        spot.hole = null
        spot.invalidate()
        // Esperar a que la pantalla se dibuje (por ejemplo, después de abrir una categoría).
        handler.postDelayed({ locate(step) }, 380)
    }

    private fun locate(step: Step) {
        val t = if (step.tag == null) null else decor.findViewWithTag<View>(step.tag)
        if (step.tag != null && t == null) {
            // El botón no está en esta pantalla (por ejemplo, una categoría oculta): se salta el paso.
            show(index + 1)
            return
        }
        target = t
        if (t != null) {
            t.requestRectangleOnScreen(Rect(0, 0, t.width, t.height), false)
            handler.postDelayed({ place(step) }, 320)
        } else {
            place(step)
        }
    }

    private fun place(step: Step) {
        val t = target
        val ol = IntArray(2)
        overlay.getLocationOnScreen(ol)
        val h = if (t != null) {
            val tl = IntArray(2)
            t.getLocationOnScreen(tl)
            val pad = a.dp(8).toFloat()
            RectF(tl[0] - ol[0] - pad, tl[1] - ol[1] - pad, tl[0] - ol[0] + t.width + pad, tl[1] - ol[1] + t.height + pad)
        } else null
        hole = h
        spot.hole = h

        msg.text = step.text
        hint.text = if (step.tap) "👆 Toca lo que está brillando" else ""
        hint.visibility = if (step.tap) View.VISIBLE else View.GONE
        nextBtn.visibility = if (step.tap) View.GONE else View.VISIBLE
        nextBtn.text = if (index == steps.size - 1) "Terminar ✓" else "Siguiente ›"

        // La burbuja va del lado contrario al botón para no taparlo.
        val oh = overlay.height
        val lp = when {
            h == null -> {
                val p = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                p.leftMargin = a.dp(16)
                p.rightMargin = a.dp(16)
                p
            }
            h.centerY() > oh / 2f -> bubbleParams(top = true, margin = a.dp(48))
            else -> bubbleParams(top = false, margin = a.dp(48))
        }
        bubble.layoutParams = lp
        bubble.visibility = View.VISIBLE
        spot.invalidate()
        busy = false
        Speaker.say(a, step.text + if (step.tap) ". Toca lo que está brillando." else "")
    }

    private fun onTap(x: Float, y: Float) {
        if (busy) return
        val step = steps.getOrNull(index) ?: return
        val h = hole
        if (step.tap && h != null && h.contains(x, y)) {
            val t = target
            busy = true
            spot.hole = null
            bubble.visibility = View.INVISIBLE
            spot.invalidate()
            t?.performClick()
            // Si el botón abrió otra app (por ejemplo, el panel del wifi), el tutorial termina ahí.
            handler.postDelayed({ show(index + 1) }, 450)
        } else if (step.tap) {
            Speaker.say(a, "Toca lo que está brillando.")
        }
    }

    /** Capa oscura con un hueco brillante alrededor del botón y un dedo que lo señala. */
    class SpotView(ctx: Context) : View(ctx) {
        var hole: RectF? = null
        var pulse = 0f
        private val density = ctx.resources.displayMetrics.density
        private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9000000.toInt() }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFFD54F.toInt()
        }
        private val finger = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 46f, ctx.resources.displayMetrics)
            textAlign = Paint.Align.CENTER
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val h = hole
            path.reset()
            path.fillType = Path.FillType.EVEN_ODD
            path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            val r = 22 * density
            if (h != null) path.addRoundRect(h, r, r, Path.Direction.CW)
            canvas.drawPath(path, dim)
            if (h == null) return

            // Brillo que late alrededor del botón.
            for (k in 0..2) {
                val grow = (4 + k * 6 + pulse * 8) * density
                glow.strokeWidth = (5 - k * 1.5f) * density
                glow.alpha = ((1f - k * 0.3f) * (155 + 100 * pulse)).toInt().coerceIn(0, 255)
                val g = RectF(h.left - grow, h.top - grow, h.right + grow, h.bottom + grow)
                canvas.drawRoundRect(g, r + grow, r + grow, glow)
            }

            // Dedo que señala, del lado donde hay más espacio.
            val bob = pulse * 10 * density
            val below = h.centerY() < height / 2f
            val fy = if (below) h.bottom + 56 * density + bob else h.top - 18 * density - bob
            canvas.drawText(if (below) "👆" else "👇", h.centerX(), fy, finger)
        }
    }
}
