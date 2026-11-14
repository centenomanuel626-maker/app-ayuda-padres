package com.manuel.ayudapadres

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** Panel de Manuel: estado de los celulares de los padres, avisos y categorías a distancia. */
class PanelActivity : Activity() {
    private lateinit var prefs: Prefs
    private var devices: JSONArray? = null
    private var alerts: JSONArray? = null
    private var loading = false
    /** Última versión publicada en GitHub (null si no se pudo revisar). */
    private var latest: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Notif.channels(this)
        ReportJobService.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        render()
        load()
    }

    private fun load() {
        if (loading || !Cloud.configured(this)) return
        loading = true
        val ctx = applicationContext
        Thread {
            val d = Cloud.devices(ctx)
            Updater.latest()?.let { latest = it }
            val a = Cloud.alerts(ctx, 0, 50)
            if (a != null && a.length() > 0) {
                val newest = a.getJSONObject(0).getLong("id")
                if (newest > prefs.lastAlertId) prefs.lastAlertId = newest
            }
            if (a != null) prefs.alertsReady = true
            runOnUiThread {
                loading = false
                if (d == null && a == null) Toast.makeText(this, "No pude conectarme. Revisa el internet.", Toast.LENGTH_LONG).show()
                if (d != null) devices = d
                if (a != null) alerts = a
                render()
            }
        }.start()
    }

    private fun millis(iso: String): Long? = try {
        if (iso.isEmpty() || iso == "null") null else OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }

    private fun ago(iso: String): String {
        val m = millis(iso) ?: return "nunca"
        return DateUtils.getRelativeTimeSpanString(m, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    }

    private fun pill(c: LinearLayout, label: String, value: String, color: Int, soft: Int) {
        val row = hbox(10)
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(text(label, 16f, false, Col.GRIS), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val v = text(value, 16f, true, color)
        v.setPadding(dp(10), dp(3), dp(10), dp(3))
        v.background = rounded(soft, 99)
        row.addView(v)
        c.addView(row, full())
    }

    private fun render() {
        val c = scrollPage()
        val head = hbox(10)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(heading("Mi panel"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val settings = text("⚙️", 26f)
        settings.setPadding(dp(12), dp(6), dp(12), dp(6))
        settings.background = rounded(Col.BLANCO, 14, Col.LINEA)
        settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        head.addView(settings)
        c.addView(head, full())
        c.addView(text("Código de familia: ${prefs.familyCode}", 16f, false, Col.GRIS), full())
        if (prefs.familyCode.isNotEmpty() && !FamilyCode.isStrong(prefs.familyCode)) {
            val weak = card(Col.ROJO_SUAVE, Col.ROJO)
            weak.addView(text("🔴 Tu código de familia es muy fácil de adivinar. Cualquiera que lo adivine vería las emergencias y ubicaciones.", 17f, true, Col.ROJO))
            c.addView(weak, full())
        }
        c.addView(rowButton("🔑", "Cambiar el código de familia", "Los celulares de tus padres lo reciben solos", Col.VERDE_AZUL) {
            changeCode()
        }, full())
        c.addView(rowButton("🛡️", "Revisar este celular", "¿Alguien espía o alteró mi propio celular?", Col.NARANJA) {
            checkThisPhone()
        }, full())

        if (!Cloud.configured(this)) {
            val w = card(Col.AMARILLO_SUAVE, Col.AMARILLO)
            w.addView(text(
                if (BuildConfig.SUPABASE_URL.isBlank()) "Esta versión de la app no tiene Supabase configurado. Sigue la guía de instalación (secretos SUPABASE_URL y SUPABASE_ANON_KEY en GitHub) y vuelve a instalar."
                else "Falta el código de familia. Ponlo en la configuración (⚙️).", 18f))
            c.addView(w, full())
            return
        }

        c.addView(bigButton(if (loading) "Cargando…" else "🔄 Recargar el panel", Col.AZUL) { load() }, full())
        val newest = latest
        if (newest != null && newest > Updater.current) {
            c.addView(bigButton("⬇️ Actualizar mi app a la versión 1.0.$newest", Col.BLANCO, Col.TINTA) {
                Updater.interactive(this) { msg, _ -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
            }, full())
        }

        // ---------- Celulares ----------
        val d = devices
        if (d == null) {
            c.addView(text("Cargando…", 18f, false, Col.GRIS))
        } else if (d.length() == 0) {
            val none = card(Col.AMARILLO_SUAVE, Col.AMARILLO)
            none.addView(text("No encuentro ningún celular con tu código.", 19f, true))
            none.addView(text("Revisa que en los celulares de tus padres esté exactamente el mismo código: ${prefs.familyCode}\n(⚙️ → PIN → Código de familia).", 16f))
            c.addView(none, full())
        } else {
            for (i in 0 until d.length()) deviceCard(c, d.getJSONObject(i))
        }

        // ---------- Avisos ----------
        val t = text("Avisos", 24f, true)
        t.setPadding(0, dp(12), 0, 0)
        c.addView(t, full())
        val a = alerts
        if (a == null || a.length() == 0) {
            c.addView(text("No hay avisos.", 18f, false, Col.GRIS))
        } else {
            for (i in 0 until a.length()) alertRow(c, a.getJSONObject(i))
        }
    }

    /** Genera un código nuevo y fuerte; los celulares de la familia lo adoptan en su siguiente reporte. */
    private fun changeCode() {
        android.app.AlertDialog.Builder(this)
            .setTitle("¿Cambiar el código de familia?")
            .setMessage("Se crea un código nuevo y difícil de adivinar. Los celulares de tus padres lo toman solos en los próximos 15 minutos (necesitan la versión 1.0.26 o más nueva).")
            .setPositiveButton("Cambiar") { _, _ ->
                val nuevo = FamilyCode.generate()
                Thread {
                    val ok = Cloud.rotateCode(applicationContext, nuevo)
                    runOnUiThread {
                        if (!ok) {
                            Toast.makeText(this, "No se pudo cambiar. Revisa el internet.", Toast.LENGTH_LONG).show()
                            return@runOnUiThread
                        }
                        prefs.familyCode = nuevo
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Código nuevo")
                            .setMessage("$nuevo\n\nGuárdalo. Los celulares de tus padres lo toman solos; si alguno estaba apagado, lo toma cuando vuelva a tener internet.")
                            .setPositiveButton("Listo", null)
                            .show()
                        load()
                    }
                }.start()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Revisión de seguridad del propio celular de Manuel. */
    private fun checkThisPhone() {
        Toast.makeText(this, "Revisando… espera unos segundos", Toast.LENGTH_SHORT).show()
        Thread {
            val items = SecurityCheck.run(applicationContext)
            val overall = SecurityCheck.overall(items)
            runOnUiThread {
                sheet { s, d ->
                    val (title, col, soft) = when (overall) {
                        "rojo" -> Triple("🔴 Tu celular podría estar alterado", Col.ROJO, Col.ROJO_SUAVE)
                        "amarillo" -> Triple("🟡 Hay cosas para revisar", Col.AMARILLO, Col.AMARILLO_SUAVE)
                        else -> Triple("🟢 Todo se ve bien", Col.VERDE, Col.VERDE_SUAVE)
                    }
                    val box = card(soft, col)
                    box.addView(text(title, 22f, true))
                    s.addView(box, full())
                    for (item in items) {
                        val mark = when (item.level) {
                            SecurityCheck.Level.OK -> "✅"
                            SecurityCheck.Level.WARN -> "⚠️"
                            SecurityCheck.Level.BAD -> "❌"
                        }
                        val row = card()
                        row.addView(text("$mark ${item.name}", 18f, true))
                        row.addView(text(item.detail, 16f, false, Col.GRIS))
                        s.addView(row, full())
                    }
                    s.addView(text("Revisa también a mano: WhatsApp → Dispositivos vinculados, y tu cuenta de Google en myaccount.google.com/device-activity.", 16f, false, Col.GRIS), full())
                    s.addView(bigButton("Cerrar", Col.BLANCO, Col.TINTA) { d.dismiss() }, full())
                }
            }
        }.start()
    }

    private fun deviceCard(c: LinearLayout, d: JSONObject) {
        val box = card()
        val top = hbox(8)
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(text(d.optString("label"), 24f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text(d.optString("model"), 15f, false, Col.GRIS))
        box.addView(top, full())

        val battery = if (d.isNull("battery")) -1 else d.optInt("battery", -1)
        if (battery >= 0) {
            val charging = d.optBoolean("charging", false)
            val (col, soft) = when {
                battery <= 20 -> Col.ROJO to Col.ROJO_SUAVE
                battery <= 40 -> Col.AMARILLO to Col.AMARILLO_SUAVE
                else -> Col.VERDE to Col.VERDE_SUAVE
            }
            pill(box, "Batería", "$battery %" + if (charging) " · cargando" else "", col, soft)
        }
        val net = d.optString("network", "")
        if (net.isNotEmpty() && net != "null") {
            pill(box, "Conexión", net, if (net == "Sin conexión") Col.ROJO else Col.VERDE,
                if (net == "Sin conexión") Col.ROJO_SUAVE else Col.VERDE_SUAVE)
        }
        val okMillis = millis(d.optString("last_ok", ""))
        val okToday = okMillis != null &&
            java.time.Instant.ofEpochMilli(okMillis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
        pill(box, "Estoy bien", if (okToday) "Hoy, " + ago(d.optString("last_ok")) else "Hoy no todavía",
            if (okToday) Col.VERDE else Col.AMARILLO, if (okToday) Col.VERDE_SUAVE else Col.AMARILLO_SUAVE)
        val sec = d.optString("security", "")
        val (sc, ss, sl) = when (sec) {
            "verde" -> Triple(Col.VERDE, Col.VERDE_SUAVE, "Verde")
            "amarillo" -> Triple(Col.AMARILLO, Col.AMARILLO_SUAVE, "Amarillo")
            "rojo" -> Triple(Col.ROJO, Col.ROJO_SUAVE, "Rojo")
            else -> Triple(Col.GRIS, Col.FONDO, "Sin revisar")
        }
        pill(box, "Seguridad", sl, sc, ss)
        if (d.has("adblock") && !d.isNull("adblock")) {
            val ad = d.optBoolean("adblock", false)
            pill(box, "Bloqueo de anuncios", if (ad) "Activo" else "Apagado",
                if (ad) Col.VERDE else Col.AMARILLO, if (ad) Col.VERDE_SUAVE else Col.AMARILLO_SUAVE)
        }
        val missing = d.optString("missing", "")
        if (missing.isNotEmpty() && missing != "null") {
            pill(box, "Permisos", "Faltan", Col.ROJO, Col.ROJO_SUAVE)
            box.addView(text("Falta: $missing. Sin esto la emergencia o los avisos pueden fallar. Entra con TeamViewer y actívalo en la configuración.", 15f, false, Col.ROJO), full())
        } else if (d.has("missing")) {
            pill(box, "Permisos", "Completos", Col.VERDE, Col.VERDE_SUAVE)
        }
        val ringer = d.optString("ringer", "")
        if (ringer.isNotEmpty() && ringer != "null") pill(box, "Sonido", ringer, Col.TINTA, Col.FONDO)
        val seen = millis(d.optString("last_seen"))
        val late = seen == null || System.currentTimeMillis() - seen > 60 * 60 * 1000L
        val ver = d.optString("version", "")
        val verCode = ver.substringAfterLast('.').toIntOrNull() ?: 0
        val newest = latest
        val pedido = d.optInt("update_to", 0)
        if (ver.isNotEmpty() && ver != "null") {
            val old = newest != null && newest > verCode
            pill(box, "Versión de la app", if (old) "$ver · hay 1.0.$newest" else "$ver ✓",
                if (old) Col.AMARILLO else Col.VERDE, if (old) Col.AMARILLO_SUAVE else Col.VERDE_SUAVE)
        }
        if (newest != null && newest > verCode) {
            if (pedido >= newest) {
                box.addView(text("⏳ Actualización pedida. Se instala en los próximos 15 minutos; si su celular pide confirmar, le sale un aviso que dice “Actualizar”.", 15f, false, Col.AMARILLO), full())
            }
            box.addView(bigButton("⬇️ Actualizar su celular a la versión 1.0.$newest", Col.AZUL) {
                Thread {
                    val ok = Cloud.requestUpdate(applicationContext, d.optString("id"), newest)
                    runOnUiThread {
                        Toast.makeText(this,
                            if (ok) "Listo: se actualizará en los próximos 15 minutos." else "No se pudo pedir. Revisa el internet.",
                            Toast.LENGTH_LONG).show()
                        load()
                    }
                }.start()
            }, full())
        }
        // Pantalla de inicio impostora (tema/launcher que llegó por un anuncio).
        val homeApp = d.optString("home_app", "")
        if (homeApp.isNotEmpty() && homeApp != "null") {
            pill(box, "Pantalla de inicio", homeApp, Col.ROJO, Col.ROJO_SUAVE)
            box.addView(text("“$homeApp” se adueñó de su pantalla de inicio: por eso se ve raro, salen anuncios y gasta batería. En su celular le sale un aviso para arreglarlo (Sonido y pantalla → El celular se ve raro).", 15f, false, Col.ROJO), full())
        } else if (d.has("home_app") && !d.isNull("home_app")) {
            pill(box, "Pantalla de inicio", "Normal", Col.VERDE, Col.VERDE_SUAVE)
        }
        if (d.has("keep_alive") && !d.isNull("keep_alive")) {
            val ka = d.optBoolean("keep_alive", false)
            pill(box, "Aviso fijo “activa”", if (ka) "Funcionando" else "Apagado", if (ka) Col.VERDE else Col.AMARILLO,
                if (ka) Col.VERDE_SUAVE else Col.AMARILLO_SUAVE)
        }
        val setup = d.optString("phone_setup", "")
        if (setup.isNotEmpty() && setup != "null") {
            val ok = setup == "Listo"
            pill(box, "Ajustes de Xiaomi", if (ok) "Listos" else "Incompletos", if (ok) Col.VERDE else Col.AMARILLO, if (ok) Col.VERDE_SUAVE else Col.AMARILLO_SUAVE)
            if (!ok) box.addView(text("$setup. Entra con TeamViewer: ⚙️ → “Ajustar este Xiaomi”.", 15f, false, Col.AMARILLO), full())
        }
        // Apps del celular y pedido de quitar.
        val apps = d.optJSONArray("apps")
        val asked = d.optJSONArray("remove_apps")
        if (asked != null && asked.length() > 0) {
            val names = (0 until asked.length()).map { j -> appName(apps, asked.getString(j)) }
            box.addView(text("⏳ Le pediste quitar: " + names.joinToString(", ") + ". Le llegó un aviso; tú recibes otro cuando termine.", 15f, false, Col.AMARILLO), full())
        }
        if (apps != null && apps.length() > 0) {
            box.addView(bigButton("📱 Ver sus apps (${apps.length()}) y elegir cuáles quitar", Col.AZUL) { showApps(d) }, full())
        }

        val diag = d.optString("diagnostic", "")
        if (diag.isNotEmpty() && diag != "null") {
            val ok = diag.contains("Todo funciona")
            box.addView(text("🧪 Probar todo: " + diag.substringAfter(": "), 15f, !ok, if (ok) Col.VERDE else Col.AMARILLO), full())
        }
        pill(box, "Último reporte", ago(d.optString("last_seen")), if (late) Col.ROJO else Col.TINTA, if (late) Col.ROJO_SUAVE else Col.FONDO)
        if (late) {
            box.addView(text("Hace más de una hora que no reporta. Puede estar apagado, sin internet, o el celular cerró la app (en Xiaomi: revisa “Inicio automático”).", 15f, false, Col.ROJO), full())
        }

        // Categorías visibles a distancia
        val hidden = HashSet<String>()
        d.optJSONArray("hidden_categories")?.let { arr -> for (j in 0 until arr.length()) hidden.add(arr.getString(j)) }
        box.addView(text("Categorías en su pantalla (toca para cambiar)", 16f, true), full())
        val deviceId = d.optString("id")
        for (cat in Catalog.cats) {
            val on = cat.id !in hidden
            box.addView(rowButton(cat.emoji, cat.name, "", if (on) cat.color else Col.GRIS, if (on) "Visible" else "Oculta", on) {
                val next = if (on) hidden + cat.id else hidden - cat.id
                Toast.makeText(this, "Guardando… se aplica en su celular en unos minutos", Toast.LENGTH_SHORT).show()
                Thread {
                    val ok = Cloud.setHiddenCategories(applicationContext, deviceId, next)
                    runOnUiThread {
                        if (!ok) Toast.makeText(this, "No se pudo guardar", Toast.LENGTH_LONG).show()
                        load()
                    }
                }.start()
            }, full())
        }
        box.addView(bigButton("Quitar este celular del panel", Col.BLANCO, Col.ROJO) {
            android.app.AlertDialog.Builder(this)
                .setMessage("¿Quitar “${d.optString("label")}” del panel? Si la app sigue instalada en modo padres, volverá a aparecer en 15 minutos.")
                .setPositiveButton("Quitar") { _, _ ->
                    Thread {
                        Cloud.removeDevice(applicationContext, deviceId)
                        runOnUiThread { load() }
                    }.start()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }, full())
        box.addView(bigButton("Entrar con TeamViewer", Col.BLANCO, Col.TINTA) {
            val li = packageManager.getLaunchIntentForPackage("com.teamviewer.teamviewer.market.mobile")
            if (li != null) startActivity(li)
            else try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.teamviewer.teamviewer.market.mobile")))
            } catch (e: Exception) { }
        }, full())
        c.addView(box, full())
    }

    private fun appName(apps: JSONArray?, pkg: String): String {
        if (apps != null) for (j in 0 until apps.length()) {
            val o = apps.getJSONObject(j)
            if (o.optString("p") == pkg) return o.optString("n", pkg)
        }
        return pkg
    }

    /** Lista de apps del celular de mamá o papá: Manuel marca las que sobran y se las sugiere quitar. */
    private fun showApps(d: JSONObject) {
        val apps = d.optJSONArray("apps") ?: return
        val deviceId = d.optString("id")
        data class A(val pkg: String, val name: String, val idle: Int, val mins: Int, val system: Boolean, val keep: Boolean, val installDays: Int)
        val list = (0 until apps.length()).map { j ->
            val o = apps.getJSONObject(j)
            A(o.optString("p"), o.optString("n"), o.optInt("d", -1), o.optInt("m", -1), o.optBoolean("s"), o.optBoolean("k"), o.optInt("i", 0))
        }
        val removable = list.filter { !it.keep }
        // Primero las olvidadas, luego las que más se usan (gastan batería), luego el resto.
        val sorted = removable.sortedWith(compareByDescending<A> { it.idle >= 30 }.thenByDescending { it.mins }.thenByDescending { it.idle })
        val chosen = LinkedHashSet<String>()
        val already = HashSet<String>()
        d.optJSONArray("remove_apps")?.let { arr -> for (j in 0 until arr.length()) already.add(arr.getString(j)) }
        chosen.addAll(already)
        sheet { c, dlg ->
            c.addView(heading("Apps de ${d.optString("label")}"))
            val unusedCount = removable.count { it.idle >= 30 }
            c.addView(text("${list.size} apps con ícono. $unusedCount sin abrir hace más de un mes. " +
                "Marca las que sobran: a ${d.optString("label").lowercase()} le llega un aviso y confirma cada una (Android lo exige).", 16f, false, Col.GRIS))
            if (list.all { it.idle < 0 }) c.addView(text("Falta el permiso de “uso de apps” en su celular: no sé cuáles no usa.", 15f, false, Col.AMARILLO))
            val send = bigButton("Pedir que quite las marcadas (${chosen.size})", Col.ROJO) { }
            fun refreshSend() {
                send.text = "Pedir que quite las marcadas (${chosen.size})"
            }
            c.addView(send, full())
            for (a in sorted) {
                val detail = buildString {
                    append(when {
                        a.idle < 0 -> ""
                        a.idle >= 999 -> "Nunca la abrió"
                        a.idle == 0 -> "La usó hoy"
                        else -> "Sin abrir hace ${a.idle} días"
                    })
                    if (a.mins >= 60) append(" · ${a.mins / 60} h ${a.mins % 60} min esta semana")
                    else if (a.mins > 0) append(" · ${a.mins} min esta semana")
                    if (a.installDays <= 7) append(" · instalada hace ${a.installDays} días")
                    if (a.system) append(" · vino con el celular (solo se desactiva)")
                }
                val color = when {
                    a.idle >= 30 -> Col.AMARILLO
                    a.mins >= 180 -> Col.ROJO
                    else -> Col.GRIS
                }
                lateinit var row: LinearLayout
                fun draw(on: Boolean): LinearLayout = rowButton(if (on) "☑️" else "⬜", a.name, detail, if (on) Col.ROJO_SUAVE else Col.FONDO,
                    if (on) "Quitar" else null, on) {
                    if (a.pkg in chosen) chosen.remove(a.pkg) else chosen.add(a.pkg)
                    val idx = c.indexOfChild(row)
                    c.removeView(row)
                    row = draw(a.pkg in chosen)
                    c.addView(row, idx, full())
                    refreshSend()
                }
                row = draw(a.pkg in chosen)
                if (color != Col.GRIS && a.pkg !in chosen) row.background = rounded(Col.BLANCO, 20, color)
                c.addView(row, full())
            }
            send.setOnClickListener {
                val pick = chosen.toList()
                Thread {
                    val ok = Cloud.requestRemoveApps(applicationContext, deviceId, pick)
                    runOnUiThread {
                        Toast.makeText(this,
                            if (!ok) "No se pudo pedir. Revisa el internet."
                            else if (pick.isEmpty()) "Listo, cancelé el pedido."
                            else "Listo: en unos minutos le llega el aviso para quitar ${pick.size} app" + (if (pick.size == 1) "." else "s."),
                            Toast.LENGTH_LONG).show()
                        if (ok) dlg.dismiss()
                        load()
                    }
                }.start()
            }
            c.addView(text("WhatsApp, teléfono, cámara, TeamViewer, la Play Store y las que ella o él decidió conservar no aparecen.", 14f, false, Col.GRIS))
            c.addView(bigButton("Cerrar", Col.BLANCO, Col.TINTA) { dlg.dismiss() }, full())
        }
    }

    private fun alertRow(c: LinearLayout, a: JSONObject) {
        val kind = a.optString("kind")
        val resolved = a.optBoolean("resolved", false)
        val (col, soft) = when (kind) {
            "emergencia", "bad" -> Col.ROJO to Col.ROJO_SUAVE
            "warn" -> Col.AMARILLO to Col.AMARILLO_SUAVE
            "ok" -> Col.VERDE to Col.VERDE_SUAVE
            else -> Col.AZUL to Col.FONDO
        }
        val box = card(if (resolved) Col.BLANCO else soft, col)
        val title = (if (kind == "emergencia") "🆘 " else "") + a.optString("device_label") + " · " + ago(a.optString("created_at"))
        box.addView(text(title, 15f, true, if (resolved) Col.GRIS else col))
        box.addView(text(a.optString("text"), 18f, false, if (resolved) Col.GRIS else Col.TINTA))
        if (!a.isNull("lat") && !a.isNull("lng")) {
            val lat = a.optDouble("lat")
            val lng = a.optDouble("lng")
            box.addView(bigButton("Ver en el mapa", Col.ROJO) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng")))
                }
            }, full())
        }
        if (!resolved && kind != "ok" && kind != "info") {
            val id = a.optLong("id")
            box.addView(bigButton("Marcar como resuelto", Col.BLANCO, Col.TINTA) {
                Thread {
                    Cloud.resolveAlert(applicationContext, id)
                    runOnUiThread { load() }
                }.start()
            }, full())
        }
        c.addView(box, full())
    }
}
