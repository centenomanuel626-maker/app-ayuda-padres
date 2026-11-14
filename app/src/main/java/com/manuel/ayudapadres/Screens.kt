package com.manuel.ayudapadres

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
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
      try {
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
      } catch (e: Exception) {
        CrashLog.save(ctx, e, "no tengo internet")
        runOnUiThread { if (screen == "diag") say("No pude revisar la conexión. Llama a ${prefs.helperName}.", "warn") }
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
        val items = try {
            SecurityCheck.run(ctx)
        } catch (e: Exception) {
            CrashLog.save(ctx, e, "revisión de seguridad")
            runOnUiThread {
                if (screen == "seguridad") {
                    val p = page()
                    header(p, "Revisión de seguridad") { backToCategory() }
                    p.addView(text("No pude terminar la revisión en este celular. Ya quedó anotado para ${prefs.helperName}.", 21f), full())
                    p.addView(bigButton("Intentar otra vez", Col.AZUL) { runSecurity() }, full())
                }
            }
            return@Thread
        }
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
                    "No entres al banco desde este celular. " +
                        if (Cloud.configured(ctx)) "Ya le avisé a ${prefs.helperName}." else "Llama a ${prefs.helperName}.")
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

/** Tarjeta de una app con su ícono, una línea de detalle y botones. */
private fun MainActivity.appCard(c: LinearLayout, pkg: String, label: String, detail: String, extra: ((LinearLayout) -> Unit)? = null) {
    val box = card()
    val row = hbox(12)
    row.gravity = Gravity.CENTER_VERTICAL
    val iv = ImageView(this)
    try { iv.setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (e: Exception) { }
    row.addView(iv, LinearLayout.LayoutParams(dp(52), dp(52)))
    val col = vbox(2)
    col.addView(text(label, 21f, true))
    if (detail.isNotEmpty()) col.addView(text(detail, 16f, false, Col.GRIS))
    row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    box.addView(row, full())
    val system = AppCleanup.isSystem(this, pkg)
    box.addView(bigButton(if (system) "Desactivar" else "Quitar", Col.BLANCO, Col.ROJO) { acc.uninstall(pkg) }, full())
    extra?.invoke(box)
    c.addView(box, full())
}

fun MainActivity.showUnused() {
    screen = "quitar"
    val c = page(keepScroll = true)
    header(c, "Apps que no usas") { backToCategory() }
    if (RemoveRequest.pending(this).isNotEmpty()) {
        c.addView(bigButton("Ver las apps que ${prefs.helperName} te sugiere quitar", Col.AZUL) { showRemoveRequested() }, full())
    }
    if (!AppCleanup.hasUsageAccess(this)) {
        c.addView(text("Para saber qué apps no usas falta un permiso. ${prefs.helperName} lo tiene que dar una vez en la configuración.", 21f), full())
        return
    }
    val list = AppCleanup.unused(this)
    if (list.isEmpty()) {
        c.addView(text("No hay apps olvidadas. Tu celular está en orden.", 22f, true), full())
        return
    }
    c.addView(text("No las abres hace más de 30 días.", 20f), full())
    for (app in list) {
        appCard(c, app.pkg, app.label, if (app.idleDays >= 999) "Nunca la abriste" else "Sin abrir hace ${app.idleDays} días")
    }
    c.addView(text("WhatsApp, el teléfono, la cámara, TeamViewer y las apps que ${prefs.helperName} aprobó nunca aparecen aquí.", 16f, false, Col.GRIS), full())
}

/** Las apps que Manuel sugirió quitar desde su panel. */
fun MainActivity.showRemoveRequested() {
    screen = "pedidas"
    val c = page(keepScroll = true)
    header(c, "Apps para quitar") { showHome() }
    val left = RemoveRequest.pending(this)
    if (left.isEmpty()) {
        c.addView(text("✅ Listo, ya no queda ninguna. ${prefs.helperName} recibe el aviso.", 22f, true), full())
        Thread { RemoveRequest.finish(applicationContext) }.start()
        return
    }
    c.addView(text("${prefs.helperName} revisó tu celular y te sugiere quitar estas apps: no las usas o gastan batería. Tú decides.", 20f), full())
    c.addView(bigButton("Quitar la primera de la lista", Col.ROJO) { acc.uninstall(left.first()) }, full())
    for (pkg in left) {
        appCard(c, pkg, AppCleanup.label(this, pkg), "") { box ->
            box.addView(bigButton("No, la quiero conservar", Col.BLANCO, Col.TINTA) {
                prefs.keepApps = prefs.keepApps + pkg
                showRemoveRequested()
            }, full())
        }
    }
    say("${prefs.helperName} te sugiere quitar ${left.size} app" + (if (left.size == 1) "" else "s") + ". Toca Quitar y luego Aceptar.", "info")
}

// ---------------- El celular se ve raro (tema o pantalla de inicio) ----------------

fun MainActivity.showTheme() {
    screen = "tema"
    val c = page()
    header(c, "El celular se ve raro") { backToCategory() }
    val home = AppCleanup.impostorHome(this)
    if (home != null) {
        val bad = card(Col.ROJO_SUAVE, Col.ROJO)
        bad.addView(text("“${home.second}” cambió tu pantalla de inicio", 22f, true, Col.ROJO))
        bad.addView(text("Por eso el celular se ve distinto, salen anuncios y se gasta la batería.", 19f))
        bad.addView(bigButton("1. Volver a la pantalla normal", Col.ROJO) {
            val normal = if (AppCleanup.isXiaomi()) "Pantalla de inicio del sistema" else "la de tu celular"
            acc.guide("Toca “Pantalla de inicio” y elige “$normal”. No elijas “${home.second}”.")
            acc.open(Intent(Settings.ACTION_HOME_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        }, full())
        bad.addView(bigButton("2. Quitar “${home.second}”", Col.BLANCO, Col.ROJO) { acc.uninstall(home.first) }, full())
        c.addView(bad, full())
    }
    val t = card()
    t.addView(text(if (home != null) "Después, si los íconos o el fondo siguen raros:" else "Volver al tema de siempre", 22f, true))
    val xiaomi = AppCleanup.isXiaomi()
    val samsung = Build.MANUFACTURER.lowercase(Locale.ROOT).contains("samsung")
    val steps = when {
        xiaomi -> "1. Abajo a la derecha toca “Perfil” (o “Mi”).\n2. Toca “Temas”.\n3. Elige “Predeterminado” (Default).\n4. Toca “Aplicar”."
        samsung -> "1. Toca el menú ☰ y luego “Mis cosas”.\n2. Toca “Temas”.\n3. Elige “Predeterminado”.\n4. Toca “Aplicar”."
        else -> "Busca “Temas” o “Fondo de pantalla y estilo” y elige el que viene con el celular."
    }
    t.addView(text(steps, 20f))
    t.addView(bigButton("Abrir los temas", Col.MORADO) {
        acc.guide(if (xiaomi) "Toca Perfil abajo a la derecha, luego Temas, elige Predeterminado y toca Aplicar."
                  else "Elige el tema Predeterminado y toca Aplicar.")
        val li = listOf("com.android.thememanager", "com.samsung.android.themestore")
            .firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (li != null) acc.open(li)
        else acc.open(Intent("android.settings.WALLPAPER_SETTINGS"), Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }, full())
    t.addView(text("Los fondos que se mueven (“fondo animado”) también gastan batería: mejor una foto quieta.", 16f, false, Col.GRIS))
    c.addView(t, full())
    c.addView(bigButton("Revisar qué más lo pone lento", Col.BLANCO, Col.TINTA) { showSlow() }, full())
    say(if (home != null) "Otra app cambió tu pantalla de inicio. Toca el botón rojo para volver a la normal."
        else "Para volver al tema de siempre, toca Abrir los temas y elige Predeterminado.", if (home != null) "warn" else "info")
}

// ---------------- Lento o se descarga rápido: revisión ----------------

fun MainActivity.showSlow() {
    screen = "lento"
    val c = page()
    header(c, "Lento o se descarga rápido") { backToCategory() }
    c.addView(text("Revisando tu celular…", 22f, true), full())
    val ctx = applicationContext
    Thread {
        val home = AppCleanup.impostorHome(ctx)
        val usage = AppCleanup.hasUsageAccess(ctx)
        val unused = if (usage) AppCleanup.unused(ctx) else emptyList()
        val heavy = if (usage) AppCleanup.heavy(ctx) else emptyList()
        val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val freePct = (st.availableBytes * 100 / maxOf(1L, st.totalBytes)).toInt()
        val total = InstallWatch.launcherApps(ctx).size
        runOnUiThread {
            if (screen != "lento") return@runOnUiThread
            val p = page()
            header(p, "Lento o se descarga rápido") { backToCategory() }
            val said = ArrayList<String>()
            fun finding(bad: Boolean, title: String, detail: String, btn: String?, action: (() -> Unit)?) {
                val box = card(if (bad) Col.AMARILLO_SUAVE else Col.VERDE_SUAVE, if (bad) Col.AMARILLO else Col.VERDE)
                box.addView(text((if (bad) "⚠️ " else "✅ ") + title, 21f, true))
                if (detail.isNotEmpty()) box.addView(text(detail, 18f))
                if (btn != null && action != null) box.addView(bigButton(btn, if (bad) Col.AMARILLO else Col.BLANCO, if (bad) Col.BLANCO else Col.TINTA) { action() }, full())
                p.addView(box, full())
                if (bad) said.add(title)
            }
            if (home != null) finding(true, "“${home.second}” cambió tu pantalla de inicio",
                "Es lo que más batería gasta y llena el celular de anuncios.", "Arreglarlo") { showTheme() }
            else finding(false, "La pantalla de inicio es la normal", "", null, null)
            if (heavy.isNotEmpty()) finding(true, "Estas apps se usan mucho",
                heavy.joinToString("\n") { "• ${it.label}: ${it.weekMinutes / 60} h ${it.weekMinutes % 60} min esta semana" } +
                    "\nMientras están abiertas gastan batería.", null, null)
            if (unused.isNotEmpty()) finding(true, "Tienes ${unused.size} apps que no usas",
                "Ocupan espacio y algunas trabajan escondidas.", "Ver cuáles") { showUnused() }
            else if (usage) finding(false, "No hay apps olvidadas", "Tienes $total apps.", null, null)
            if (freePct < 10) finding(true, "Queda poco espacio ($freePct % libre)", "Con poco espacio todo va lento.", "Liberar espacio") { acc.run("espacio") }
            if (RemoveRequest.pending(this).isNotEmpty()) finding(true, "${prefs.helperName} te sugirió quitar unas apps", "", "Verlas") { showRemoveRequested() }
            finding(false, "Reiniciar una vez por semana ayuda", "Cierra todo lo que quedó trabado.", "Cómo reiniciarlo") { showReboot() }
            if (!usage) p.addView(text("Para revisar el uso de cada app falta un permiso que ${prefs.helperName} da en la configuración.", 16f, false, Col.GRIS), full())
            p.addView(bigButton("Si no se arregla, llamar a ${prefs.helperName}", Col.VERDE) { acc.run("llamar") }, full())
            say(if (said.isEmpty()) "Todo se ve en orden. Si sigue lento, reinícialo."
                else "Encontré ${said.size} cosa" + (if (said.size == 1) "" else "s") + ": " + said.joinToString(". ") + ".",
                if (said.isEmpty()) "ok" else "warn")
        }
    }.start()
}

// ---------------- Celular lento ----------------

fun MainActivity.showReboot() {
    screen = "reiniciar"
    val c = page()
    header(c, "Cómo reiniciarlo") { showSlow() }
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

/** "Aprender a usar la app": recorrido guiado, guías paso a paso y modo práctica. */
@Suppress("UNUSED_PARAMETER")
fun MainActivity.showTutorial(step: Int) {
    screen = "tutorial"
    val c = page()
    header(c, "Aprender") { backToCategory() }
    c.addView(text("Elige qué quieres aprender. La app oscurece la pantalla y hace brillar el botón que tienes que tocar.", 20f), full())
    c.addView(bigButton("👀 Recorrido por la app", Col.AZUL) { startTour() }, full())
    c.addView(text("Guías paso a paso", 22f, true), full())
    for ((id, label) in guides(prefs.helperName)) {
        c.addView(rowButton("👆", label, "", Col.VERDE_AZUL) { startGuide(id) }, full())
    }
    c.addView(text("Practicar sin miedo", 22f, true), full())
    c.addView(bigButton(if (prefs.practice) "Apagar el modo práctica" else "Prender el modo práctica", Col.AMARILLO) {
        prefs.practice = !prefs.practice
        say(if (prefs.practice) "Modo práctica prendido: los botones solo te dicen para qué sirven." else "Listo, los botones ya funcionan de verdad.")
        showTutorial(0)
    }, full())
}

// ---------------- Bloquear anuncios y ventanas ----------------

fun MainActivity.showWebProtection() {
    screen = "web"
    val c = page(keepScroll = true)
    header(c, "Anuncios y ventanas") { backToCategory() }
    c.addView(text("Tres protecciones contra los avisos de “instala esto”, los virus falsos y los anuncios engañosos.", 20f), full())

    // 1. Anuncios: DNS privado
    val dnsOn = WebShield.adBlockActive(this)
    val b1 = card(if (dnsOn) Col.VERDE_SUAVE else Col.AMARILLO_SUAVE, if (dnsOn) Col.VERDE else Col.AMARILLO)
    b1.addView(text((if (dnsOn) "✅ " else "⚠️ ") + "1. Bloqueo de anuncios", 21f, true))
    b1.addView(text(if (dnsOn) "Activo: la mayoría de los anuncios y ventanas engañosas ya no aparecen en páginas ni apps."
                    else "Apagado. Se activa una sola vez en los ajustes del celular (lo mejor es que lo haga ${prefs.helperName}).", 17f))
    if (!dnsOn) b1.addView(bigButton("Activar bloqueo de anuncios", Col.AZUL) {
        try {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DNS", WebShield.DNS))
        } catch (e: Exception) { }
        sheet { s, d ->
            s.addView(heading("Bloqueo de anuncios"))
            s.addView(text("1. Busca “DNS privado” (en Samsung: Conexiones → Más ajustes de conexión → DNS privado; en Xiaomi: Conexión y compartir → DNS privado).\n" +
                "2. Elige “Nombre de host del proveedor”.\n3. Mantén apretado el espacio y toca “Pegar”: ${WebShield.DNS}\n4. Toca “Guardar”.", 19f))
            s.addView(bigButton("Abrir los ajustes", Col.AZUL) {
                d.dismiss()
                acc.open(Intent("android.settings.PRIVATE_DNS_SETTINGS"), Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            }, full())
            s.addView(bigButton("Cerrar", Col.BLANCO, Col.TINTA) { d.dismiss() }, full())
        }
        say("Copié la dirección del bloqueo. Sigue los pasos.", "info")
    }, full())
    c.addView(b1, full())

    // 2. Avisos de páginas web en los navegadores
    val b2 = card()
    b2.addView(text("2. Avisos de páginas web", 21f, true))
    b2.addView(text("Algunas páginas piden “Permitir notificaciones” y después mandan avisos falsos de virus o premios. Apaga los avisos de los sitios en cada navegador.", 17f))
    val list = WebShield.installedBrowsers(this)
    if (list.isEmpty()) b2.addView(text("No encontré navegadores instalados.", 17f, false, Col.GRIS))
    for ((pkg, name) in list) {
        b2.addView(bigButton("Apagar avisos de $name", Col.BLANCO, Col.TINTA) {
            say("Apaga “Sitios” o “Notificaciones de sitios”. Si quieres, apaga todo.", "info")
            acc.open(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            )
        }, full())
    }
    c.addView(b2, full())

    // 3. Instalaciones desde páginas
    val b3 = card()
    b3.addView(text("3. Instalar desde páginas o mensajes", 21f, true))
    b3.addView(text("Si dice “Permitido” en el navegador, WhatsApp o Archivos, cualquier página puede instalar cosas. Déjalo todo en “No permitido”.", 17f))
    b3.addView(bigButton("Revisar quién puede instalar apps", Col.BLANCO, Col.TINTA) {
        say("Toca cada app que diga Permitido y apágala.", "info")
        acc.open(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES), Intent(Settings.ACTION_SECURITY_SETTINGS))
    }, full())
    c.addView(b3, full())

    c.addView(text("Los anuncios dentro de los videos de YouTube no se pueden bloquear así. Si un anuncio te dice que instales algo, no lo hagas: si aparece una app nueva, esta app te pregunta y le avisa a ${prefs.helperName}.", 16f, false, Col.GRIS), full())
}
