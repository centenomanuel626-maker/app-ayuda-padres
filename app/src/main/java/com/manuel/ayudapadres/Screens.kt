package com.manuel.ayudapadres

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import java.util.Locale

// Subpantallas de la app de los padres. Son funciones de MainActivity.

private fun MainActivity.checkRow(c: LinearLayout, name: String, level: SecurityCheck.Level?, detail: String) {
    val row = hbox(12)
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(dp(12), dp(10), dp(12), dp(10))
    row.background = rounded(Col.BLANCO, 14, Col.LINEA, 1)
    val mark = text(
        when (level) {
            SecurityCheck.Level.OK -> "✓"
            SecurityCheck.Level.WARN -> "!"
            SecurityCheck.Level.BAD -> "✕"
            null -> "…"
        }, 20f, true, Col.BLANCO
    )
    mark.gravity = Gravity.CENTER
    mark.background = rounded(
        when (level) {
            SecurityCheck.Level.OK -> Col.VERDE
            SecurityCheck.Level.WARN -> Col.AMARILLO
            SecurityCheck.Level.BAD -> Col.ROJO
            null -> Col.LINEA
        }, 99
    )
    row.addView(mark, LinearLayout.LayoutParams(dp(34), dp(34)))
    val col = vbox(2)
    col.addView(text(name, 19f, true))
    col.addView(text(detail, 16f, false, Col.GRIS))
    row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    c.addView(row, full())
}

// ---------------- No tengo internet ----------------

fun MainActivity.showDiag() {
    screen = "diag"
    val c = page()
    header(c, "No tengo internet") { backToCategory() }
    c.addView(text("Revisando…", 20f, false, Col.GRIS))
    val ctx = applicationContext
    Thread {
        val airplane = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val wifiOn = ctx.getSystemService(WifiManager::class.java).isWifiEnabled
        val dataOn = try {
            ctx.getSystemService(TelephonyManager::class.java).isDataEnabled
        } catch (e: Exception) {
            true
        }
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        Thread.sleep(900)
        runOnUiThread {
            if (screen != "diag") return@runOnUiThread
            val p = page()
            header(p, "No tengo internet") { backToCategory() }
            checkRow(p, "Modo avión", if (airplane) SecurityCheck.Level.BAD else SecurityCheck.Level.OK,
                if (airplane) "Está prendido: corta todo" else "Apagado, bien")
            checkRow(p, "Wifi", if (wifiOn) SecurityCheck.Level.OK else SecurityCheck.Level.WARN,
                if (onWifi) "Conectado" else if (wifiOn) "Prendido, pero sin conectar" else "Apagado")
            checkRow(p, "Datos móviles", if (dataOn) SecurityCheck.Level.OK else SecurityCheck.Level.WARN,
                if (dataOn) "Prendidos" else "Apagados")
            checkRow(p, "Internet", if (online) SecurityCheck.Level.OK else SecurityCheck.Level.BAD,
                if (online) "Funciona" else "No hay conexión")

            val res: Triple<String, String?, (() -> Unit)?> = when {
                online -> Triple("Todo está bien: ya tienes internet.", null, null)
                airplane -> Triple("El modo avión está prendido. Apágalo.", "Apagar el modo avión",
                    { acc.open(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) })
                !wifiOn -> Triple("El wifi está apagado. Si estás en casa, préndelo.", "Prender el wifi", { acc.run("wifi") })
                !onWifi -> Triple("El wifi no está conectado. Toca el wifi de tu casa.", "Elegir el wifi", { acc.run("wifi") })
                !dataOn -> Triple("Los datos están apagados. Préndelos si estás fuera de casa.", "Prender los datos", { acc.run("datos") })
                else -> Triple("El wifi está conectado pero no hay internet. Apaga y prende el módem de la casa.", "Llamar a ${prefs.helperName}", { acc.run("llamar") })
            }
            val (msg, btn, act) = res
            p.addView(text(msg, 22f, true), full())
            if (btn != null && act != null) p.addView(bigButton(btn, Col.AZUL) { act() }, full())
            p.addView(bigButton("Revisar otra vez", Col.BLANCO, Col.TINTA) { showDiag() }, full())
            say(msg, if (online) "ok" else "info")
        }
    }.start()
}

// ---------------- Revisión de seguridad ----------------

fun MainActivity.showSecurity() {
    screen = "seguridad"
    val c = page()
    header(c, "Revisión de seguridad") { backToCategory() }
    c.addView(text("Reviso si alguien cambió el sistema de tu celular o si hay señales de espionaje. No cambio nada.", 21f), full())
    c.addView(bigButton("Empezar la revisión", Col.AZUL) { runSecurity() }, full())
    if (prefs.lastSecurity.isNotEmpty()) c.addView(text("Última revisión: ${prefs.lastSecurity}", 17f, false, Col.GRIS))
}

private fun MainActivity.runSecurity() {
    val c = page()
    header(c, "Revisión de seguridad") { backToCategory() }
    c.addView(text("Revisando… espera unos segundos.", 21f), full())
    say("Revisando tu celular. Espera unos segundos.", "info")
    val ctx = applicationContext
    Thread {
        val items = SecurityCheck.run(ctx)
        val overall = SecurityCheck.overall(items)
        prefs.lastSecurity = overall
        if (Cloud.configured(ctx)) {
            Cloud.reportStatus(ctx, org.json.JSONObject().put("security", overall))
            if (overall != "verde") {
                val problems = items.filter { it.level != SecurityCheck.Level.OK }.joinToString("; ") { it.name + ": " + it.detail }
                Cloud.alert(ctx, if (overall == "rojo") "bad" else "warn", "Revisión de seguridad en $overall. $problems")
            }
        }
        runOnUiThread {
            if (screen != "seguridad") return@runOnUiThread
            val p = page()
            header(p, "Revisión de seguridad") { backToCategory() }
            val problems = items.count { it.level != SecurityCheck.Level.OK }
            val (color, soft, title, sub) = when (overall) {
                "rojo" -> Quad(Col.ROJO, Col.ROJO_SUAVE, "🔴 Tu celular podría estar alterado",
                    "No entres al banco desde este celular. Ya le avisé a ${prefs.helperName}.")
                "amarillo" -> Quad(Col.AMARILLO, Col.AMARILLO_SUAVE, "🟡 Hay $problems cosa(s) para revisar",
                    "No es grave. ${prefs.helperName} lo puede revisar.")
                else -> Quad(Col.VERDE, Col.VERDE_SUAVE, "🟢 Todo se ve bien", "No encontré señales de espionaje.")
            }
            val box = card(soft, color)
            box.addView(text(title, 24f, true))
            box.addView(text(sub, 19f))
            p.addView(box, full())
            say(title.drop(2).trim() + ". " + sub, if (overall == "verde") "ok" else "warn")
            for (item in items) checkRow(p, item.name, item.level, item.detail)

            p.addView(text("Mira también estas dos cosas, que la app no puede revisar sola:", 19f, true), full())
            p.addView(rowButton("💬", "WhatsApp en otras computadoras", "Dispositivos vinculados", Col.VERDE_AZUL) {
                sheet { s, d ->
                    s.addView(heading("WhatsApp"))
                    s.addView(text("1. Abre WhatsApp.\n2. Toca los tres puntitos ⋮ arriba.\n3. Toca “Dispositivos vinculados”.\n4. Si hay una computadora que no conoces, tócala y elige “Cerrar sesión”.\n5. Avísale a ${prefs.helperName}.", 20f))
                    s.addView(bigButton("Abrir WhatsApp", Col.VERDE) {
                        d.dismiss()
                        val li = packageManager.getLaunchIntentForPackage("com.whatsapp")
                        if (li != null) acc.open(li) else say("No encontré WhatsApp.", "warn")
                    }, full())
                    s.addView(bigButton("Cerrar", Col.BLANCO, Col.TINTA) { d.dismiss() }, full())
                }
            }, full())
            p.addView(rowButton("🔑", "Tu cuenta de Google", "En qué celulares está abierta", Col.AZUL) {
                say("Mira si hay un celular o computadora que no es tuyo. Si lo hay, llama a ${prefs.helperName}.", "info")
                acc.open(Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/device-activity")))
            }, full())
            p.addView(text("Una revisión en verde baja la sospecha, pero un espionaje muy profesional puede esconderse de cualquier app.", 16f, false, Col.GRIS), full())
            p.addView(bigButton("Revisar otra vez", Col.BLANCO, Col.TINTA) { runSecurity() }, full())
        }
    }.start()
}

private data class Quad(val color: Int, val soft: Int, val title: String, val sub: String)

// ---------------- Quitar apps que no uso ----------------

private val protectedApps = setOf(
    "com.whatsapp", "com.whatsapp.w4b", "com.teamviewer.quicksupport.market", "com.teamviewer.host.market", "com.android.vending",
    "com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer", "com.android.contacts",
    "com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms",
    "com.android.settings", "com.sec.android.app.camera", "com.android.camera", "com.google.android.GoogleCamera",
    "com.sec.android.gallery3d", "com.miui.gallery", "com.google.android.apps.photos"
)

private fun MainActivity.hasUsageAccess(): Boolean {
    val ops = getSystemService(AppOpsManager::class.java)
    @Suppress("DEPRECATION")
    val mode = if (Build.VERSION.SDK_INT >= 29)
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
    else
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun MainActivity.showUnused() {
    screen = "quitar"
    val c = page(keepScroll = true)
    header(c, "Apps que no usas") { backToCategory() }
    if (!hasUsageAccess()) {
        c.addView(text("Para saber qué apps no usas falta un permiso. ${prefs.helperName} lo tiene que dar una vez en la configuración.", 21f), full())
        return
    }
    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    val usm = getSystemService(UsageStatsManager::class.java)
    val usage = usm.queryAndAggregateUsageStats(now - 120 * day, now)
    val apps = InstallWatch.launcherApps(this)
    val list = apps.mapNotNull { (pkg, label) ->
        if (pkg in protectedApps || pkg in prefs.approvedApps) return@mapNotNull null
        val info = try { packageManager.getPackageInfo(pkg, 0) } catch (e: Exception) { return@mapNotNull null }
        if (now - info.firstInstallTime < 7 * day) return@mapNotNull null
        val last = usage[pkg]?.lastTimeUsed ?: 0L
        val days = if (last > 0) ((now - last) / day).toInt() else 999
        if (days < 30) null else Triple(pkg, label, days)
    }.sortedByDescending { it.third }

    if (list.isEmpty()) {
        c.addView(text("No hay apps olvidadas. Tu celular está en orden.", 22f, true), full())
        return
    }
    c.addView(text("No las abres hace más de 30 días.", 20f), full())
    for ((pkg, label, days) in list) {
        val box = card()
        val row = hbox(12)
        row.gravity = Gravity.CENTER_VERTICAL
        val iv = ImageView(this)
        try { iv.setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (e: Exception) { }
        row.addView(iv, LinearLayout.LayoutParams(dp(52), dp(52)))
        val col = vbox(2)
        col.addView(text(label, 21f, true))
        col.addView(text(if (days >= 999) "Nunca la abriste" else "Sin abrir hace $days días", 16f, false, Col.GRIS))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row, full())
        val system = try {
            (packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) { false }
        box.addView(bigButton(if (system) "Desactivar" else "Quitar", Col.BLANCO, Col.ROJO) { acc.uninstall(pkg) }, full())
        c.addView(box, full())
    }
    c.addView(text("WhatsApp, el teléfono, la cámara, TeamViewer y las apps que ${prefs.helperName} aprobó nunca aparecen aquí.", 16f, false, Col.GRIS), full())
}

// ---------------- Celular lento ----------------

fun MainActivity.showReboot() {
    screen = "lento"
    val c = page()
    header(c, "Celular lento o trabado") { backToCategory() }
    c.addView(text("Reiniciarlo casi siempre lo arregla.", 22f, true), full())
    val brand = Build.MANUFACTURER.lowercase(Locale.ROOT)
    val steps = if (brand.contains("samsung")) listOf(
        "Mantén apretados al mismo tiempo el botón del lado derecho y el de bajar volumen.",
        "Cuando salga el menú, toca “Reiniciar”.",
        "Toca otra vez “Reiniciar”.",
        "Espera un minuto hasta que vuelva a prender."
    ) else listOf(
        "Mantén apretado el botón de prender (al lado derecho) unos 3 segundos.",
        "Cuando salga el menú, toca “Reiniciar”.",
        "Si no sale nada, mantenlo apretado 15 segundos hasta que se apague y prenda solo.",
        "Espera un minuto hasta que vuelva a prender."
    )
    steps.forEachIndexed { i, s ->
        val row = hbox(14)
        val n = text("${i + 1}", 24f, true, Col.BLANCO)
        n.gravity = Gravity.CENTER
        n.background = rounded(Col.NARANJA, 99)
        row.addView(n, LinearLayout.LayoutParams(dp(46), dp(46)))
        row.addView(text(s, 21f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row, full())
    }
    c.addView(phoneDrawing(brand.contains("samsung")), LinearLayout.LayoutParams(dp(170), dp(260)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
    c.addView(bigButton("Si no se arregla, llamar a ${prefs.helperName}", Col.VERDE) { acc.run("llamar") }, full())
    say("Reiniciarlo casi siempre lo arregla. " + steps.joinToString(" "), "info")
}

/** Dibujo simple de un celular con los botones que hay que apretar en rojo. */
private fun MainActivity.phoneDrawing(samsung: Boolean): View {
    val frame = android.widget.FrameLayout(this)
    val body = View(this)
    body.background = rounded(Col.TINTA, 22)
    frame.addView(body, android.widget.FrameLayout.LayoutParams(dp(130), dp(240), Gravity.CENTER_VERTICAL))
    val screenV = View(this)
    screenV.background = rounded(Col.FONDO, 12)
    val sl = android.widget.FrameLayout.LayoutParams(dp(112), dp(210), Gravity.CENTER_VERTICAL)
    sl.leftMargin = dp(9)
    frame.addView(screenV, sl)
    fun sideButton(top: Int, height: Int, red: Boolean) {
        val b = View(this)
        val g = GradientDrawable()
        g.setColor(if (red) Col.ROJO else Col.GRIS)
        g.cornerRadius = dp(3).toFloat()
        b.background = g
        val lp = android.widget.FrameLayout.LayoutParams(dp(10), dp(height))
        lp.leftMargin = dp(128)
        lp.topMargin = dp(top)
        frame.addView(b, lp)
    }
    // Arriba: volumen (subir y bajar). Abajo: botón de prender.
    sideButton(40, 46, false)
    sideButton(90, 30, samsung)
    sideButton(140, 40, true)
    return frame
}

// ---------------- Mis medicinas ----------------

fun MainActivity.showMeds() {
    screen = "medicinas"
    val c = page(keepScroll = true)
    header(c, "Mis medicinas") { backToCategory() }
    val meds = prefs.meds
    if (meds.isEmpty()) {
        c.addView(text("Todavía no hay medicinas. ${prefs.helperName} las agrega en la configuración.", 21f), full())
        return
    }
    val next = Alarms.nextMed(prefs)
    for (m in meds) {
        val box = card(if (m == next) Col.AMARILLO_SUAVE else Col.BLANCO, if (m == next) Col.AMARILLO else Col.LINEA)
        box.addView(text(String.format(Locale.US, "%d:%02d", m.hour, m.minute), 30f, true))
        box.addView(text(m.name, 24f))
        if (m == next) box.addView(text("Es la siguiente", 17f, true, Col.AMARILLO))
        c.addView(box, full())
    }
    c.addView(text("A cada hora suena una alarma grande con el nombre de la medicina.", 17f, false, Col.GRIS), full())
}

// ---------------- Mi familia (contactos con foto) ----------------

fun MainActivity.showFamily() {
    screen = "familia"
    val c = page(keepScroll = true)
    header(c, "Mi familia") { backToCategory() }
    val people = prefs.people
    if (people.isEmpty()) {
        c.addView(text("Todavía no hay contactos. ${prefs.helperName} los agrega en la configuración.", 21f), full())
        return
    }
    var i = 0
    while (i < people.size) {
        val row = hbox(12)
        for (j in i until minOf(i + 2, people.size)) {
            val person = people[j]
            val box = vbox(8)
            box.gravity = Gravity.CENTER_HORIZONTAL
            box.setPadding(dp(10), dp(14), dp(10), dp(14))
            box.background = rounded(Col.BLANCO, 22, Col.LINEA)
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            var hasPhoto = false
            if (person.photo.isNotEmpty()) {
                try {
                    iv.setImageURI(Uri.parse(person.photo))
                    hasPhoto = iv.drawable != null
                } catch (e: Exception) { }
            }
            if (hasPhoto) {
                iv.clipToOutline = true
                iv.background = rounded(Col.FONDO, 99)
                box.addView(iv, LinearLayout.LayoutParams(dp(120), dp(120)))
            } else {
                val initial = text(person.name.take(1).uppercase(), 52f, true, Col.BLANCO)
                initial.gravity = Gravity.CENTER
                initial.background = rounded(Col.VERDE_AZUL, 99)
                box.addView(initial, LinearLayout.LayoutParams(dp(120), dp(120)))
            }
            val name = text(person.name, 22f, true)
            name.gravity = Gravity.CENTER
            box.addView(name, full())
            val callLabel = text("📞 Llamar", 18f, true, Col.VERDE)
            callLabel.gravity = Gravity.CENTER
            box.addView(callLabel, full())
            box.setOnClickListener {
                if (prefs.practice) say("Modo práctica: aquí llamarías a ${person.name}.", "info")
                else acc.call(person.phone, person.name)
            }
            row.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        if (i + 1 >= people.size) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        c.addView(row, full())
        i += 2
    }
}

// ---------------- Aprender (tutorial y modo práctica) ----------------

fun MainActivity.showTutorial(step: Int) {
    screen = "tutorial"
    val h = prefs.helperName
    val pages = listOf(
        "👋" to "Esta app te ayuda con tu celular. Toca un color para ver sus botones.",
        "📞" to "Abajo siempre están “Llamar a $h”, en verde, y “Emergencia”, en rojo.",
        "😌" to "No te preocupes: con esta app no puedes dañar nada. Si algo no funciona, llama a $h.",
        "🎤" to "Puedes hablarle a la app: toca el micrófono y di, por ejemplo, “no tengo internet”.",
        "🚫" to "Nunca instales apps ni “actualizaciones” que te manden por mensaje, aunque digan que son de $h. $h las instala él mismo.",
        "🎓" to "Para practicar sin cambiar nada, prende el modo práctica. Los botones solo te dirán para qué sirven."
    )
    val s = step.coerceIn(0, pages.size - 1)
    val c = page()
    if (prefs.tutorialDone) header(c, "Aprender") { backToCategory() }
    c.addView(text("Paso ${s + 1} de ${pages.size}", 18f, false, Col.GRIS))
    val emoji = text(pages[s].first, 90f)
    emoji.gravity = Gravity.CENTER
    c.addView(emoji, full())
    c.addView(text(pages[s].second, 26f, true), full())
    Speaker.say(this, pages[s].second)
    if (s < pages.size - 1) {
        c.addView(bigButton("Siguiente", Col.AZUL) { showTutorial(s + 1) }, full())
    } else {
        c.addView(bigButton(if (prefs.practice) "Apagar el modo práctica" else "Prender el modo práctica", Col.AMARILLO) {
            prefs.practice = !prefs.practice
            prefs.tutorialDone = true
            showHome()
            say(if (prefs.practice) "Modo práctica prendido. Toca lo que quieras." else "Listo, los botones ya funcionan de verdad.")
        }, full())
        c.addView(bigButton("Terminar", Col.VERDE) {
            prefs.tutorialDone = true
            showHome()
        }, full())
    }
    if (s > 0) c.addView(bigButton("Anterior", Col.BLANCO, Col.TINTA) { showTutorial(s - 1) }, full())
}
