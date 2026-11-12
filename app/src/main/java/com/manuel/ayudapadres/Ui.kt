package com.manuel.ayudapadres

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Colores de la app: alto contraste para leer sin lentes. */
object Col {
    val FONDO = 0xFFF5F8FA.toInt()
    val BLANCO = 0xFFFFFFFF.toInt()
    val TINTA = 0xFF13202B.toInt()
    val GRIS = 0xFF4B5B69.toInt()
    val LINEA = 0xFFCAD4DC.toInt()
    val AZUL = 0xFF1D5BB8.toInt()
    val MORADO = 0xFF6A3D9A.toInt()
    val VERDE_AZUL = 0xFF0D6B6B.toInt()
    val NARANJA = 0xFFA3500A.toInt()
    val VERDE = 0xFF177A3B.toInt()
    val VERDE_SUAVE = 0xFFDDF1E4.toInt()
    val ROJO = 0xFFC21F1F.toInt()
    val ROJO_SUAVE = 0xFFF9DADA.toInt()
    val AMARILLO = 0xFFA86B0C.toInt()
    val AMARILLO_SUAVE = 0xFFFBEBC9.toInt()
}

fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

fun Context.rounded(fill: Int, radiusDp: Int = 20, stroke: Int? = null, strokeDp: Int = 2): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(fill)
    g.cornerRadius = dp(radiusDp).toFloat()
    if (stroke != null) g.setStroke(dp(strokeDp), stroke)
    return g
}

fun Context.vbox(gapDp: Int = 12): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.VERTICAL
    l.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    val gap = GradientDrawable()
    gap.setSize(1, dp(gapDp))
    gap.setColor(0x00000000)
    l.dividerDrawable = gap
    return l
}

fun Context.hbox(gapDp: Int = 10): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.HORIZONTAL
    l.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    val gap = GradientDrawable()
    gap.setSize(dp(gapDp), 1)
    gap.setColor(0x00000000)
    l.dividerDrawable = gap
    return l
}

fun Context.text(s: CharSequence, sizeSp: Float = 20f, bold: Boolean = false, color: Int = Col.TINTA): TextView {
    val t = TextView(this)
    t.text = s
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    t.setTextColor(color)
    if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    t.setLineSpacing(0f, 1.15f)
    return t
}

fun Context.heading(s: String): TextView = text(s, 30f, true)

/** Botón grande de ancho completo. */
fun Context.bigButton(label: String, bg: Int = Col.AZUL, fg: Int = Col.BLANCO, onClick: () -> Unit): TextView {
    val b = text(label, 22f, true, fg)
    b.gravity = Gravity.CENTER
    b.minHeight = dp(68)
    b.setPadding(dp(16), dp(12), dp(16), dp(12))
    b.background = if (bg == Col.BLANCO) rounded(bg, 18, Col.LINEA) else rounded(bg, 18)
    b.isClickable = true
    b.isFocusable = true
    b.setOnClickListener { onClick() }
    return b
}

/** Fila con emoji, título, subtítulo y un estado opcional (por ejemplo "Prendida"). */
fun Context.rowButton(
    emoji: String, title: String, sub: String, color: Int, state: String? = null, stateOn: Boolean = false,
    onClick: () -> Unit
): LinearLayout {
    val row = hbox(14)
    row.gravity = Gravity.CENTER_VERTICAL
    row.minimumHeight = dp(88)
    row.setPadding(dp(14), dp(12), dp(14), dp(12))
    row.background = rounded(Col.BLANCO, 20, Col.LINEA)
    row.isClickable = true
    row.isFocusable = true
    row.setOnClickListener { onClick() }

    val icon = text(emoji, 30f)
    icon.gravity = Gravity.CENTER
    icon.background = rounded(color, 16)
    row.addView(icon, LinearLayout.LayoutParams(dp(60), dp(60)))

    val col = vbox(2)
    col.addView(text(title, 22f, true))
    if (sub.isNotEmpty()) col.addView(text(sub, 17f, false, Col.GRIS))
    row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

    if (state != null) {
        val st = text(state, 15f, true, if (stateOn) Col.BLANCO else Col.TINTA)
        st.setPadding(dp(10), dp(4), dp(10), dp(4))
        st.background = if (stateOn) rounded(Col.VERDE, 99) else rounded(Col.FONDO, 99, Col.LINEA, 1)
        row.addView(st)
    }
    return row
}

/** Cuadro grande de color para cada categoría de la pantalla principal. */
fun Context.tile(emoji: String, title: String, color: Int, onClick: () -> Unit): LinearLayout {
    val t = vbox(10)
    t.setPadding(dp(18), dp(18), dp(18), dp(18))
    t.minimumHeight = dp(160)
    t.background = rounded(color, 24)
    t.isClickable = true
    t.isFocusable = true
    t.setOnClickListener { onClick() }
    t.addView(text(emoji, 42f))
    val space = View(this)
    t.addView(space, LinearLayout.LayoutParams(1, dp(12)))
    t.addView(text(title, 23f, true, Col.BLANCO))
    return t
}

fun Context.card(fill: Int = Col.BLANCO, stroke: Int? = Col.LINEA): LinearLayout {
    val c = vbox(8)
    c.setPadding(dp(16), dp(14), dp(16), dp(14))
    c.background = rounded(fill, 18, stroke, 1)
    return c
}

fun Context.input(hint: String, value: String = "", numeric: Boolean = false): EditText {
    val e = EditText(this)
    e.hint = hint
    e.setText(value)
    e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
    e.setTextColor(Col.TINTA)
    e.minHeight = dp(60)
    e.setPadding(dp(14), dp(8), dp(14), dp(8))
    e.background = rounded(Col.BLANCO, 14, Col.LINEA)
    e.inputType = if (numeric) InputType.TYPE_CLASS_PHONE else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    return e
}

fun Context.full(): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

/** Pantalla con scroll y márgenes cómodos. Devuelve el contenedor donde se agregan cosas. */
fun Activity.scrollPage(): LinearLayout {
    val scroll = ScrollView(this)
    scroll.setBackgroundColor(Col.FONDO)
    scroll.isFillViewport = true
    val col = vbox(14)
    col.setPadding(dp(18), dp(24), dp(18), dp(32))
    scroll.addView(col, full())
    setContentView(scroll)
    return col
}

/**
 * Hoja de diálogo grande. Recibe una función que llena el contenido;
 * los botones que agregue deben llamar a dismiss() del diálogo que se entrega.
 */
fun Activity.sheet(fill: (LinearLayout, AlertDialog) -> Unit): AlertDialog {
    val scroll = ScrollView(this)
    val col = vbox(14)
    col.setPadding(dp(20), dp(22), dp(20), dp(22))
    scroll.addView(col, full())
    val d = AlertDialog.Builder(this).setView(scroll).create()
    fill(col, d)
    d.window?.setBackgroundDrawable(rounded(Col.BLANCO, 26))
    d.show()
    return d
}
