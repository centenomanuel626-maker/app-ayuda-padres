package com.manuel.ayudapadres

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Asistente de un toque para Xiaomi / Redmi / POCO (HyperOS y MIUI).
 * Xiaomi cierra las apps en segundo plano: sin estos ajustes no suenan las medicinas
 * ni llegan los reportes al panel. Cada paso abre la pantalla exacta y una manito señala qué tocar.
 */
object XiaomiSetup {

    /** check: comprobación real (null si el celular no deja comprobarlo: se marca al tocarlo). */
    data class Step(val id: String, val emoji: String, val title: String, val sub: String, val check: ((Context) -> Boolean)?)

    val steps = listOf(
        Step("notif", "🔔", "Dejar que Ayuda muestre avisos", "Para las medicinas y los recordatorios") { ctx ->
            Notif.canPost(ctx) && ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        },
        Step("bateria", "🔋", "Quitarle el ahorro de batería a Ayuda", "Toca “Permitir”") { ctx ->
            ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        },
        Step("auto", "🚀", "Inicio automático", "Busca “Ayuda” y prende el interruptor", null),
        Step("sinrestr", "⚡", "Ahorro de batería: “Sin restricciones”", "Elige “Sin restricciones”", null),
        Step("ventanas", "🪟", "Ventanas en segundo plano", "“Permitir siempre” en las dos opciones de ventanas", null),
        Step("candado", "🔒", "Candado en apps recientes", "Para que Xiaomi no la cierre al limpiar", null)
    )

    fun done(ctx: Context, s: Step): Boolean =
        (try { s.check?.invoke(ctx) } catch (e: Exception) { null }) ?: (s.id in Prefs(ctx).xiaomiDone)

    fun missing(ctx: Context): List<Step> = steps.filter { !done(ctx, it) }

    /** Resumen para el panel de Manuel. */
    fun summary(ctx: Context): String {
        val m = missing(ctx)
        return if (m.isEmpty()) "Listo" else "Faltan: " + m.joinToString(", ") { it.title }
    }
}

private val xiaomiCoached = HashSet<String>()

fun MainActivity.showXiaomiSetup() {
    screen = "xiaomi"
    val c = page(keepScroll = true)
    header(c, "Ajustar este Xiaomi") { showHome() }
    c.addView(text("Xiaomi cierra las apps para ahorrar batería. Con estos pasos Ayuda sigue funcionando: medicinas, avisos y el panel de ${prefs.helperName}.", 19f), full())
    val pkg = packageName
    var next: XiaomiSetup.Step? = null
    XiaomiSetup.steps.forEachIndexed { i, s ->
        val ok = XiaomiSetup.done(this, s)
        if (!ok && next == null) next = s
        val row = rowButton(s.emoji, "${i + 1}. ${s.title}", s.sub, if (ok) Col.VERDE else Col.NARANJA, if (ok) "Hecho" else "Falta", ok) {
            runXiaomiStep(s, pkg)
        }
        row.tag = "xi:" + s.id
        c.addView(row, full())
    }
    if (next == null) {
        val done = card(Col.VERDE_SUAVE, Col.VERDE)
        done.addView(text("✅ Este Xiaomi ya está ajustado.", 22f, true, Col.VERDE))
        c.addView(done, full())
    }
    c.addView(bigButton("Empezar de nuevo", Col.BLANCO, Col.TINTA) {
        prefs.xiaomiDone = emptySet()
        xiaomiCoached.clear()
        showXiaomiSetup()
    }, full())
    // La manito señala el siguiente paso (una vez por paso, para no insistir si la persona sale).
    val n = next
    if (n != null && coach == null && n.id !in xiaomiCoached) {
        xiaomiCoached.add(n.id)
        scroll.post {
            if (screen != "xiaomi" || coach != null) return@post
            coach = Coach(this, listOf(Coach.Step("xi:" + n.id, "Toca aquí: ${n.title}. ${n.sub}.", tap = true))) { }
            coach?.start()
        }
    }
}

private fun MainActivity.runXiaomiStep(s: XiaomiSetup.Step, pkg: String) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
    if (s.check == null) prefs.xiaomiDone = prefs.xiaomiDone + s.id
    when (s.id) {
        "notif" -> {
            acc.guide("Prende “Mostrar notificaciones”.")
            acc.open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg), details)
        }
        "bateria" -> {
            acc.guide("Toca “Permitir”.")
            acc.open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), details)
        }
        "auto" -> {
            acc.guide("Busca “Ayuda” en la lista y prende el interruptor.")
            acc.open(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                details
            )
        }
        "sinrestr" -> {
            acc.guide("Elige “Sin restricciones”. Si no aparece, toca “Ahorro de batería” primero.")
            acc.open(
                Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                    .putExtra("package_name", pkg).putExtra("package_label", getString(R.string.app_name)),
                details
            )
        }
        "ventanas" -> {
            acc.guide("Toca “Mostrar ventanas emergentes mientras se ejecuta en segundo plano” y elige “Permitir siempre”. Haz lo mismo con “Mostrar en pantalla de bloqueo”.")
            acc.open(
                Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    .putExtra("extra_pkgname", pkg),
                Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg),
                details
            )
        }
        "candado" -> sheet { c, d ->
            c.addView(heading("Poner el candado"))
            c.addView(text("1. Desliza el dedo desde abajo hacia arriba y déjalo quieto un momento: aparecen las apps abiertas.\n2. Mantén apretada la tarjeta de “Ayuda”.\n3. Toca el candado 🔒.\n\nAsí, aunque limpies las apps, Ayuda no se cierra.", 20f))
            c.addView(bigButton("Entendido", Col.VERDE) {
                d.dismiss()
                showXiaomiSetup()
            }, full())
            Speaker.say(this, "Abre las apps recientes, mantén apretada la tarjeta de Ayuda y toca el candado.")
        }
    }
}

/** Para la configuración: abre el asistente en la pantalla principal (con la clave interna). */
fun Context.openXiaomiSetup() {
    startActivity(
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("run", "xiaomi").putExtra("token", Prefs(this).intentToken)
    )
}
