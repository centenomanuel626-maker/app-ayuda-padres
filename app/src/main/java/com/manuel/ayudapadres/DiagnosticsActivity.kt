package com.manuel.ayudapadres

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.text.format.DateUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * "Probar todo": comprueba en el celular real cada cosa de la que depende la app
 * y deja el resultado en el panel de Manuel. Es la prueba que hay que hacer en cada
 * celular antes de dárselo a mamá o papá.
 */
class DiagnosticsActivity : Activity() {
    private lateinit var prefs: Prefs
    private enum class Lv { OK, WARN, BAD }
    private data class Row(val name: String, val r: Lv, val detail: String, val fix: String? = null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Speaker.init(this)
        start()
    }

    private fun has(perm: String) = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun installed(pkg: String) = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun start() {
        val c = scrollPage()
        c.addView(heading("Probar todo"))
        c.addView(text("Estoy probando cada función en este celular. Tarda unos 10 segundos.", 20f), full())
        val ctx = applicationContext
        // La ubicación es lo único que tarda: se pide primero y el resto se revisa mientras tanto.
        Locate.get(this) { loc ->
            Thread {
                val rows = try {
                    collect(loc != null, loc?.accuracy)
                } catch (e: Exception) {
                    CrashLog.save(ctx, e, "probar todo")
                    listOf(Row("Diagnóstico", Lv.BAD, "No pude terminar: " + (e.message ?: "error")))
                }
                val online = if (Cloud.configured(ctx)) Cloud.reportStatus(ctx, org.json.JSONObject()) else false
                val all = rows.toMutableList()
                all.add(0, when {
                    BuildConfig.SUPABASE_URL.isBlank() -> Row("Conexión con el panel", Lv.BAD, "Esta versión no tiene Supabase configurado")
                    prefs.familyCode.length < 8 -> Row("Conexión con el panel", Lv.BAD, "Falta el código de familia", "settings")
                    online -> Row("Conexión con el panel", Lv.OK, "Conectado con el código de familia")
                    else -> Row("Conexión con el panel", Lv.BAD, "No se pudo conectar: revisa el internet y el código de familia")
                })
                val bad = all.count { it.r == Lv.BAD }
                val warn = all.count { it.r == Lv.WARN }
                val summary = "${BuildConfig.VERSION_NAME}: " + when {
                    bad > 0 -> "$bad falla(s), $warn aviso(s): " + all.filter { it.r != Lv.OK }.joinToString("; ") { it.name }
                    warn > 0 -> "Bien, con $warn aviso(s): " + all.filter { it.r == Lv.WARN }.joinToString("; ") { it.name }
                    else -> "Todo funciona"
                }
                if (online) {
                    Cloud.reportDiagnostic(ctx, summary.take(500))
                    try { CrashLog.flush(ctx) } catch (e: Exception) { }
                }
                runOnUiThread { show(all, bad, warn, online) }
            }.start()
        }
    }

    private fun collect(gotLocation: Boolean, accuracy: Float?): List<Row> {
        val out = ArrayList<Row>()
        val padres = prefs.role == "padres"
        val nm = getSystemService(NotificationManager::class.java)

        // ---- Común a los dos celulares ----
        out += if (Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS))
            Row("Notificaciones", Lv.OK, "Permitidas") else Row("Notificaciones", Lv.BAD, "Bloqueadas: no llegará ningún aviso", "perm")
        val bgOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        out += if (bgOk) Row("Segundo plano", Lv.OK, "La app puede trabajar con la pantalla apagada")
               else Row("Segundo plano", Lv.BAD, "El celular puede dormir la app: no habrá reportes ni avisos", "perm")
        if (Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }) {
            out += Row("Inicio automático (Xiaomi)", Lv.WARN, "No se puede comprobar solo: revisa que esté activado", "settings")
        }
        val js = getSystemService(JobScheduler::class.java)
        out += if (js.getPendingJob(77) != null) Row("Reporte automático", Lv.OK, "Programado cada 15 minutos")
               else Row("Reporte automático", Lv.BAD, "No está programado").also { ReportJobService.schedule(this) }
        val errs = CrashLog.pendingCount(this)
        out += if (errs == 0) Row("Errores guardados", Lv.OK, "Ninguno")
               else Row("Errores guardados", Lv.WARN, "$errs error(es) esperando para enviarse")

        if (!padres) {
            out += if (prefs.alertsReady) Row("Avisos del panel", Lv.OK, "Listos para avisarte de cada emergencia")
                   else Row("Avisos del panel", Lv.WARN, "Abre el panel una vez para activarlos")
            return out
        }

        // ---- Celular de los padres ----
        val phoneDigits = prefs.helperPhone.filter { it.isDigit() }
        out += if (phoneDigits.length >= 8) Row("Teléfono de ${prefs.helperName}", Lv.OK, prefs.helperPhone)
               else Row("Teléfono de ${prefs.helperName}", Lv.BAD, "Falta o está incompleto", "settings")
        val wa = installed("com.whatsapp") || installed("com.whatsapp.w4b")
        out += if (wa) Row("WhatsApp", Lv.OK, "Instalado") else Row("WhatsApp", Lv.BAD, "No está instalado: llamadas y emergencia irán por la línea normal")
        out += when {
            !has(Manifest.permission.READ_CONTACTS) -> Row("Llamada directa por WhatsApp", Lv.BAD, "Falta el permiso de Contactos", "perm")
            !wa -> Row("Llamada directa por WhatsApp", Lv.WARN, "Sin WhatsApp")
            whatsappContact(phoneDigits) -> Row("Llamada directa por WhatsApp", Lv.OK, "${prefs.helperName} está en los contactos con WhatsApp")
            else -> Row("Llamada directa por WhatsApp", Lv.BAD,
                "${prefs.helperName} no está guardado en los contactos con ese número (o WhatsApp aún no lo sincronizó)")
        }
        out += when {
            !has(Manifest.permission.ACCESS_FINE_LOCATION) && !has(Manifest.permission.ACCESS_COARSE_LOCATION) ->
                Row("Ubicación para emergencias", Lv.BAD, "Falta el permiso", "perm")
            gotLocation -> Row("Ubicación para emergencias", Lv.OK,
                if (accuracy != null) "Funciona (precisión de unos ${accuracy.toInt()} metros)" else "Funciona")
            else -> Row("Ubicación para emergencias", Lv.BAD, "No se pudo obtener: revisa que la ubicación (GPS) esté prendida")
        }
        out += if (has(Manifest.permission.CALL_PHONE)) Row("Llamadas", Lv.OK, "Permitidas")
               else Row("Llamadas", Lv.WARN, "Sin permiso: cada llamada pedirá tocar “Llamar”", "perm")
        out += if (Speaker.isReady()) Row("Voz", Lv.OK, "La app puede leer en voz alta")
               else Row("Voz", Lv.WARN, "La voz no está lista: instala o actualiza “Speech Services by Google”")
        val canListen = packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
        out += if (canListen) Row("Micrófono (órdenes por voz)", Lv.OK, "Disponible")
               else Row("Micrófono (órdenes por voz)", Lv.WARN, "Este celular no tiene reconocimiento de voz")
        val medsOn = nm.getNotificationChannel(Notif.MEDICINAS)?.importance != NotificationManager.IMPORTANCE_NONE
        out += if (medsOn) Row("Avisos de medicinas", Lv.OK, "Activados (${prefs.meds.size} medicina(s))")
               else Row("Avisos de medicinas", Lv.BAD, "El canal “Medicinas” está apagado en los ajustes de notificaciones")
        if (Build.VERSION.SDK_INT >= 31) {
            val exact = getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            out += if (exact) Row("Alarmas a la hora exacta", Lv.OK, "Permitidas")
                   else Row("Alarmas a la hora exacta", Lv.WARN, "Las medicinas pueden sonar unos minutos tarde", "perm")
        }
        if (Build.VERSION.SDK_INT >= 34) {
            out += if (nm.canUseFullScreenIntent()) Row("Alarma en pantalla completa", Lv.OK, "Permitida")
                   else Row("Alarma en pantalla completa", Lv.WARN, "Con el celular bloqueado solo saldrá una notificación", "perm")
        }
        val cm = getSystemService(CameraManager::class.java)
        val torch = try {
            cm.cameraIdList.any { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        } catch (e: Exception) { false }
        out += if (torch) Row("Linterna", Lv.OK, "Disponible") else Row("Linterna", Lv.WARN, "Este celular no tiene flash")
        out += if (android.provider.Settings.System.canWrite(this)) Row("Cambiar brillo, letra y pantalla", Lv.OK, "Permitido")
               else Row("Cambiar brillo, letra y pantalla", Lv.WARN, "Falta el permiso “Modificar ajustes del sistema”", "perm")
        out += if (installed("com.teamviewer.quicksupport.market")) Row("TeamViewer QuickSupport", Lv.OK, "Instalado")
               else Row("TeamViewer QuickSupport", Lv.WARN, "No está instalado: no podrás entrar a ayudar a distancia", "perm")
        if (Build.VERSION.SDK_INT >= 29) {
            val role = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            out += if (role) Row("Alerta contra estafas", Lv.OK, "Activa")
                   else Row("Alerta contra estafas", Lv.WARN, "Falta elegir la app como identificador de llamadas", "perm")
        }
        out += if (WebShield.adBlockActive(this)) Row("Bloqueo de anuncios", Lv.OK, "Activo")
               else Row("Bloqueo de anuncios", Lv.WARN, "Apagado: ver Internet → Bloquear anuncios y ventanas")
        out += when {
            prefs.lastReport == 0L -> Row("Último reporte al panel", Lv.WARN, "Todavía ninguno")
            System.currentTimeMillis() - prefs.lastReport < 60 * 60 * 1000L ->
                Row("Último reporte al panel", Lv.OK, DateUtils.getRelativeTimeSpanString(prefs.lastReport).toString())
            else -> Row("Último reporte al panel", Lv.BAD,
                "Fue " + DateUtils.getRelativeTimeSpanString(prefs.lastReport) + ": el celular está durmiendo la app")
        }
        out += if (prefs.pin.length >= 4) Row("PIN de configuración", Lv.OK, "Puesto")
               else Row("PIN de configuración", Lv.BAD, "Sin PIN: cualquiera puede cambiar la configuración", "settings")
        return out
    }

    /** ¿Está Manuel en los contactos con una fila de llamada de WhatsApp? (lo que usa la llamada directa) */
    private fun whatsappContact(digits: String): Boolean {
        if (digits.length < 7) return false
        val tail = digits.takeLast(10)
        return try {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI, arrayOf("data1"), "mimetype = ? OR mimetype = ?",
                arrayOf("vnd.android.cursor.item/vnd.com.whatsapp.voip.call", "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call"),
                null
            )?.use { cur ->
                var found = false
                while (cur.moveToNext()) {
                    if ((cur.getString(0) ?: "").filter { it.isDigit() }.endsWith(tail)) {
                        found = true
                        break
                    }
                }
                found
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun show(rows: List<Row>, bad: Int, warn: Int, online: Boolean) {
        val c = scrollPage()
        val head = hbox(12)
        head.gravity = Gravity.CENTER_VERTICAL
        val back = text("‹ Volver", 20f, true)
        back.setPadding(dp(14), dp(10), dp(16), dp(10))
        back.background = rounded(Col.BLANCO, 16, Col.LINEA)
        back.setOnClickListener { finish() }
        head.addView(back)
        head.addView(text("Probar todo", 26f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(head, full())

        val (title, color, soft) = when {
            bad > 0 -> Triple("🔴 $bad cosa(s) no funcionan", Col.ROJO, Col.ROJO_SUAVE)
            warn > 0 -> Triple("🟡 Funciona, con $warn aviso(s)", Col.AMARILLO, Col.AMARILLO_SUAVE)
            else -> Triple("🟢 Todo funciona", Col.VERDE, Col.VERDE_SUAVE)
        }
        val box = card(soft, color)
        box.addView(text(title, 24f, true))
        box.addView(text("Versión ${BuildConfig.VERSION_NAME}. " +
            if (online) "El resultado ya está en el panel de ${prefs.helperName}." else "No se pudo enviar al panel.", 17f))
        c.addView(box, full())
        Speaker.say(this, title.drop(2).trim())

        // Primero lo que falla, después los avisos, al final lo que está bien.
        for (row in rows.sortedBy { it.r.ordinal * -1 }) {
            val rowBox = card(
                when (row.r) { Lv.OK -> Col.BLANCO; Lv.WARN -> Col.AMARILLO_SUAVE; Lv.BAD -> Col.ROJO_SUAVE },
                when (row.r) { Lv.OK -> Col.LINEA; Lv.WARN -> Col.AMARILLO; Lv.BAD -> Col.ROJO }
            )
            val mark = when (row.r) { Lv.OK -> "✅"; Lv.WARN -> "⚠️"; Lv.BAD -> "❌" }
            rowBox.addView(text("$mark ${row.name}", 19f, true))
            rowBox.addView(text(row.detail, 16f, false, Col.GRIS))
            if (row.r != Lv.OK && row.fix != null) {
                rowBox.addView(bigButton("Arreglarlo en la configuración", Col.BLANCO, Col.TINTA) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                }, full())
            }
            c.addView(rowBox, full())
        }

        if (prefs.role == "padres") {
            c.addView(text("Pruebas que hay que hacer a mano", 22f, true), full())
            c.addView(bigButton("⏰ Probar la alarma de medicina (en 1 minuto)", Col.NARANJA) {
                Alarms.snoozeMed(this, 999, "Prueba de alarma", 1)
                Speaker.say(this, "En un minuto suena una alarma de prueba. Puedes bloquear el celular para ver si aparece.")
                android.widget.Toast.makeText(this, "En 1 minuto suena la alarma de prueba. Bloquea el celular para probarla.", android.widget.Toast.LENGTH_LONG).show()
            }, full())
            c.addView(bigButton("🔔 Probar una notificación", Col.BLANCO, Col.TINTA) {
                Notif.show(this, 4242, Notif.AVISOS, "Prueba de aviso", "Si ves esto, las notificaciones funcionan.")
            }, full())
        } else {
            c.addView(bigButton("🔔 Buscar avisos ahora", Col.AZUL) {
                Thread { ReportJobService.work(applicationContext) }.start()
                android.widget.Toast.makeText(this, "Buscando avisos nuevos…", android.widget.Toast.LENGTH_SHORT).show()
            }, full())
        }
        c.addView(bigButton("Probar otra vez", Col.BLANCO, Col.TINTA) { start() }, full())
    }
}
