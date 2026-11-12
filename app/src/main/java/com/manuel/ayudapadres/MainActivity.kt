package com.manuel.ayudapadres

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pantalla principal del celular de los padres. */
class MainActivity : Activity() {
    lateinit var prefs: Prefs
    lateinit var acc: Acciones
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var banner: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val hideBanner = Runnable { banner.visibility = View.GONE }

    /** Pantalla actual: "home", "cat", o una subpantalla ("diag", "seguridad", "quitar"...). */
    var screen = "home"
    var cat = "internet"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (prefs.role.isEmpty()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        if (prefs.role == "manuel") {
            startActivity(Intent(this, PanelActivity::class.java))
            finish()
            return
        }
        Notif.channels(this)
        Speaker.init(this)
        acc = Acciones(this)
        buildLayout()
        ReportJobService.schedule(this)
        Alarms.scheduleAll(this)
        if (!prefs.tutorialDone) showTutorial(0) else showHome()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::acc.isInitialized) {
            if (intent.hasCategory(Intent.CATEGORY_HOME) && screen != "home") showHome()
            handleIntent(intent)
        }
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        // Solo se aceptan órdenes que vienen de los avisos de esta misma app (llevan la clave interna).
        val token = i.getStringExtra("token")
        i.removeExtra("token")
        if (token == null || token != prefs.intentToken) {
            i.removeExtra("uninstall")
            i.removeExtra("run")
            return
        }
        i.getStringExtra("uninstall")?.let {
            i.removeExtra("uninstall")
            Notif.cancel(this, Ids.newApp(it))
            acc.uninstall(it)
        }
        i.getStringExtra("run")?.let {
            i.removeExtra("run")
            acc.run(it)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::acc.isInitialized) return
        refresh()
        Thread {
            InstallWatch.check(applicationContext)
        }.start()
    }

    /** Vuelve a dibujar la pantalla actual (para actualizar estados como "Prendida"). */
    fun refresh() {
        when (screen) {
            "home" -> showHome()
            "cat" -> showCategory(cat, keepScroll = true)
            "quitar" -> showUnused()
            "medicinas" -> showMeds()
            "familia" -> showFamily()
        }
    }

    private fun buildLayout() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Col.FONDO)

        val frame = FrameLayout(this)
        scroll = ScrollView(this)
        scroll.isFillViewport = true
        content = vbox(14)
        content.setPadding(dp(16), dp(18), dp(16), dp(24))
        scroll.addView(content, full())
        frame.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        banner = text("", 21f, true, Col.BLANCO)
        banner.setPadding(dp(18), dp(16), dp(18), dp(16))
        banner.visibility = View.GONE
        banner.elevation = dp(8).toFloat()
        banner.setOnClickListener { banner.visibility = View.GONE }
        val bl = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        bl.setMargins(dp(12), dp(12), dp(12), 0)
        frame.addView(banner, bl)
        root.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val dock = hbox(10)
        dock.setPadding(dp(14), dp(10), dp(14), dp(14))
        dock.setBackgroundColor(Col.BLANCO)
        val call = bigButton("📞 Llamar a ${prefs.helperName}", Col.VERDE) { acc.run("llamar") }
        val sos = bigButton("🆘 Emergencia", Col.ROJO) { acc.run("emergencia") }
        call.minHeight = dp(76)
        sos.minHeight = dp(76)
        dock.addView(call, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dock.addView(sos, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(dock, full())
        setContentView(root)
    }

    /** Mensaje grande arriba de la pantalla, también leído en voz alta. kind: ok, info, warn. */
    fun say(message: String, kind: String = "ok") {
        banner.text = message
        banner.background = rounded(
            when (kind) {
                "warn" -> Col.AMARILLO
                "info" -> Col.AZUL
                else -> Col.VERDE
            }, 18
        )
        banner.visibility = View.VISIBLE
        handler.removeCallbacks(hideBanner)
        handler.postDelayed(hideBanner, 5000)
        Speaker.say(this, message)
    }

    /** Limpia la pantalla y devuelve el contenedor para dibujar una nueva. */
    fun page(keepScroll: Boolean = false): LinearLayout {
        content.removeAllViews()
        if (!keepScroll) scroll.post { scroll.scrollTo(0, 0) }
        return content
    }

    /** Fila superior con botón "Volver" y título. */
    fun header(c: LinearLayout, titleText: String, onBack: () -> Unit) {
        val row = hbox(12)
        row.gravity = Gravity.CENTER_VERTICAL
        val back = text("‹ Volver", 20f, true)
        back.setPadding(dp(14), dp(10), dp(16), dp(10))
        back.minHeight = dp(56)
        back.gravity = Gravity.CENTER_VERTICAL
        back.background = rounded(Col.BLANCO, 16, Col.LINEA)
        back.setOnClickListener { onBack() }
        row.addView(back)
        val t = text(titleText, 25f, true)
        row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row, full())
    }

    fun backToCategory() {
        if (cat.isNotEmpty()) showCategory(cat) else showHome()
    }

    // ---------------- Inicio ----------------

    fun showHome() {
        screen = "home"
        val c = page()

        val top = hbox(12)
        top.gravity = Gravity.CENTER_VERTICAL
        val hello = vbox(2)
        val date = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Date())
        hello.addView(text(date.replaceFirstChar { it.uppercase() }, 18f, false, Col.GRIS))
        val hi = text("Hola, ${prefs.parentName}", 32f, true)
        hi.setOnLongClickListener {
            acc.openSettings()
            true
        }
        hello.addView(hi)
        top.addView(hello, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val mic = text("🎤", 30f)
        mic.gravity = Gravity.CENTER
        mic.background = rounded(Col.BLANCO, 99, Col.LINEA)
        mic.contentDescription = "Hablarle a la app"
        mic.setOnClickListener { listen() }
        top.addView(mic, LinearLayout.LayoutParams(dp(68), dp(68)))
        c.addView(top, full())

        if (prefs.practice) {
            val pr = card(Col.AMARILLO_SUAVE, Col.AMARILLO)
            pr.addView(text("Modo práctica: los botones no cambian nada.", 19f, true))
            pr.addView(bigButton("Salir del modo práctica", Col.BLANCO, Col.TINTA) {
                prefs.practice = false
                say("Listo, los botones ya funcionan de verdad.")
                showHome()
            })
            c.addView(pr, full())
        }

        if (DateUtils.isToday(prefs.lastEstoyBien)) {
            val ok = card(Col.VERDE_SUAVE, Col.VERDE)
            ok.addView(text("✅ Ya le avisaste a ${prefs.helperName} que estás bien.", 20f))
            c.addView(ok, full())
        } else {
            val ok = card(Col.VERDE_SUAVE, Col.VERDE)
            ok.addView(text("¿Todo bien hoy?", 20f))
            ok.addView(text("Toca aquí: Estoy bien ✅", 22f, true))
            ok.isClickable = true
            ok.setOnClickListener { acc.run("estoybien") }
            c.addView(ok, full())
        }

        val visible = Catalog.cats.filter { it.id !in prefs.hiddenCats }
        var i = 0
        while (i < visible.size) {
            val row = hbox(12)
            val a = visible[i]
            row.addView(tile(a.emoji, a.name, a.color) { showCategory(a.id) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            if (i + 1 < visible.size) {
                val b = visible[i + 1]
                row.addView(tile(b.emoji, b.name, b.color) { showCategory(b.id) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }
            c.addView(row, full())
            i += 2
        }
        if (visible.isEmpty()) {
            c.addView(text("Usa los botones de abajo si necesitas ayuda.", 20f, false, Col.GRIS))
        }

        if (prefs.launcherMode) {
            showMyAppsSection(c)
            val exit = text("Salida de emergencia (PIN de ${prefs.helperName})", 15f, false, Col.GRIS)
            exit.gravity = Gravity.CENTER
            exit.setPadding(0, dp(24), 0, dp(8))
            exit.setOnClickListener { acc.exitLauncher() }
            c.addView(exit, full())
        }
    }

    private fun showMyAppsSection(c: LinearLayout) {
        val apps = prefs.approvedApps.mapNotNull { pkg ->
            try {
                val ai = packageManager.getApplicationInfo(pkg, 0)
                Triple(pkg, packageManager.getApplicationLabel(ai).toString(), packageManager.getApplicationIcon(ai))
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.second.lowercase() }
        if (apps.isEmpty()) return
        c.addView(text("Mis aplicaciones", 24f, true))
        for ((pkg, label, icon) in apps) {
            val row = hbox(14)
            row.gravity = Gravity.CENTER_VERTICAL
            row.minimumHeight = dp(80)
            row.setPadding(dp(14), dp(10), dp(14), dp(10))
            row.background = rounded(Col.BLANCO, 20, Col.LINEA)
            val iv = ImageView(this)
            iv.setImageDrawable(icon)
            row.addView(iv, LinearLayout.LayoutParams(dp(56), dp(56)))
            row.addView(text(label, 22f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener {
                val li = packageManager.getLaunchIntentForPackage(pkg)
                if (li != null) startActivity(li) else say("No pude abrir esa app.", "warn")
            }
            c.addView(row, full())
        }
    }

    // ---------------- Categoría ----------------

    fun showCategory(id: String, keepScroll: Boolean = false) {
        screen = "cat"
        cat = id
        val category = Catalog.cat(id)
        val c = page(keepScroll)
        header(c, category.name) { showHome() }
        for (bid in category.items) {
            val b = Catalog.btn(bid, prefs.helperName)
            val st = acc.state(bid)
            val color = when (bid) {
                "emergencia" -> Col.ROJO
                "llamar", "estoybien" -> Col.VERDE
                else -> category.color
            }
            c.addView(rowButton(b.emoji, b.title, b.sub, color, st?.first, st?.second ?: false) { acc.run(bid) }, full())
        }
    }

    // ---------------- Voz ----------------

    fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Dime qué necesitas")
        try {
            Speaker.stop()
            startActivityForResult(i, REQ_VOZ)
        } catch (e: Exception) {
            say("Este celular no tiene reconocimiento de voz.", "warn")
        }
    }

    @Deprecated("Uso de la API clásica de resultados")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VOZ && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) ?: return
            acc.voiceCommand(results)
        }
    }

    @Deprecated("Uso del botón atrás clásico")
    override fun onBackPressed() {
        when {
            screen == "home" -> if (!prefs.launcherMode) super.onBackPressed()
            screen == "cat" -> showHome()
            else -> backToCategory()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideBanner)
        super.onDestroy()
    }

    companion object {
        const val REQ_VOZ = 7
    }
}
