package com.manuel.ayudapadres

import android.Manifest
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import java.util.Locale

/** Lo que hace cada botón. */
class Acciones(private val a: MainActivity) {
    private val p get() = a.prefs
    private val helper get() = p.helperName
    private val am: AudioManager = a.getSystemService(AudioManager::class.java)
    private val cm: CameraManager = a.getSystemService(CameraManager::class.java)
    private var torchOn = false

    /** Botones que solo muestran algo dentro de la app y funcionan igual en modo práctica. */
    private val harmless = setOf(
        "update", "nointernet", "web", "lento", "quitar", "tema", "pedidas", "xiaomi", "seguridad", "lupa", "medicinas", "familia", "aprender", "bateria", "espacio"
    )

    init {
        try {
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    torchOn = enabled
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) { }
    }

    /** Estado que se muestra a la derecha de cada botón. */
    fun state(id: String): Pair<String, Boolean>? = try {
        when (id) {
            "ringer" -> when (am.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> "Suena" to true
                AudioManager.RINGER_MODE_VIBRATE -> "Vibra" to true
                else -> "Silencio" to false
            }
            "volumen" -> "${am.getStreamVolume(AudioManager.STREAM_RING)} de ${am.getStreamMaxVolume(AudioManager.STREAM_RING)}" to false
            "linterna" -> if (torchOn) "Prendida" to true else "Apagada" to false
            "wifi" -> {
                val wm = a.applicationContext.getSystemService(WifiManager::class.java)
                if (wm.isWifiEnabled) "Prendido" to true else "Apagado" to false
            }
            "girar" -> if (Settings.System.getInt(a.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1)
                "Gira" to false else "Fija" to true
            "apagado" -> {
                val ms = Settings.System.getInt(a.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30000)
                (if (ms >= 60000) "${ms / 60000} min" else "${ms / 1000} seg") to (ms >= 120000)
            }
            "bateria" -> "${batteryPct()} %" to false
            "estoybien" -> if (DateUtils.isToday(p.lastEstoyBien)) "Enviado" to true else null
            "medicinas" -> {
                val n = Alarms.nextMed(p)
                if (n == null) null else String.format(Locale.US, "%d:%02d", n.hour, n.minute) to false
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    fun run(id: String) {
        if (p.practice && id !in harmless) {
            val b = Catalog.btn(id, helper)
            a.say("Modo práctica: “${b.title}” sirve para: ${b.sub.lowercase()}. No cambié nada.", "info")
            return
        }
        when (id) {
            // ---- Internet ----
            "wifi" -> {
                a.say("Toca el nombre del wifi de tu casa.", "info")
                open(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS))
            }
            "nointernet" -> a.showDiag()
            "web" -> a.showWebProtection()
            "update" -> Updater.interactive(a) { msg, kind -> a.say(msg, kind) }
            "bluetooth" -> {
                guide("Prende el interruptor y toca el nombre de tus audífonos o del carro.")
                open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            "datos" -> {
                a.say("Prende el interruptor de “Datos móviles”.", "info")
                open(
                    Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS),
                    Intent(Settings.ACTION_WIRELESS_SETTINGS)
                )
            }

            // ---- Sonido y pantalla ----
            "ringer" -> cycleRinger()
            "volumen" -> volumeUp()
            "letra" -> fontSize()
            "notif" -> {
                guide("Busca la app que te molesta y apaga sus notificaciones.")
                open(
                    Intent("android.settings.ALL_APPS_NOTIFICATION_SETTINGS"),
                    Intent("android.settings.NOTIFICATION_SETTINGS"),
                    Intent(Settings.ACTION_SETTINGS)
                )
            }
            "brillo" -> brightness()
            "apagado" -> {
                if (needWrite()) return
                val ms = Settings.System.getInt(a.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30000)
                val next = if (ms >= 120000) 30000 else 300000
                if (!writeSys(Settings.System.SCREEN_OFF_TIMEOUT, next)) {
                    guide("Busca “Tiempo de espera de la pantalla” y elige 5 minutos.")
                    open(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                    return
                }
                a.say(if (next == 300000) "Listo, ahora la pantalla espera 5 minutos antes de apagarse."
                      else "Listo, la pantalla vuelve a apagarse a los 30 segundos.")
                a.refresh()
            }
            "girar" -> {
                if (needWrite()) return
                val on = Settings.System.getInt(a.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
                if (!writeSys(Settings.System.ACCELEROMETER_ROTATION, if (on) 0 else 1)) {
                    a.say("Este celular no me deja cambiarlo. Usa el botón “Girar” de los ajustes rápidos, arriba.", "warn")
                    return
                }
                a.say(if (on) "Listo, la pantalla ya no se voltea." else "Listo, la pantalla vuelve a girar.")
                a.refresh()
            }

            // ---- Ayuda ----
            "llamar" -> call(p.helperPhone, helper)
            "olvide" -> {
                Speaker.say(a, "Llamando a $helper para que te ayude con tu contraseña.")
                call(p.helperPhone, helper)
            }
            "familia" -> a.showFamily()
            "video" -> {
                guide("Arriba a la derecha, toca el dibujo de la cámara.")
                if (!whatsapp(p.helperPhone, null)) a.say("No encontré WhatsApp en este celular.", "warn")
            }
            "remoto" -> remote()
            "pantalla" -> shareScreen()
            "estoybien" -> estoyBien()
            "emergencia" -> confirmEmergency()
            "aprender" -> a.showTutorial(0)

            // ---- Mi celular ----
            "linterna" -> torch()
            "lupa" -> a.startActivity(Intent(a, MagnifierActivity::class.java))
            "medicinas" -> a.showMeds()
            "bateria" -> battery()
            "clave" -> {
                guide("Elige “PIN” y escribe 4 números fáciles de recordar para ti.")
                open(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD), Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
            "espacio" -> storage()
            "updates" -> updates()
            "lento" -> a.showSlow()
            "tema" -> a.showTheme()
            "pedidas" -> a.showRemoveRequested()
            "xiaomi" -> a.showXiaomiSetup()
            "quitar" -> a.showUnused()
            "seguridad" -> a.showSecurity()
        }
    }

    // ---------------- Ayudantes ----------------

    /** Abre la primera pantalla que funcione de la lista. */
    fun open(vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                a.startActivity(i)
                return true
            } catch (e: Exception) { }
        }
        a.say("No pude abrir esa pantalla. Llama a $helper.", "warn")
        return false
    }

    /** Instrucción que sigue visible y se escucha aunque se abra otra pantalla del celular. */
    fun guide(msg: String) {
        Toast.makeText(a, msg, Toast.LENGTH_LONG).show()
        Speaker.say(a, msg)
    }

    /** Cambia un ajuste del sistema; algunos celulares lo bloquean aunque haya permiso. */
    private fun writeSys(name: String, value: Int): Boolean = try {
        Settings.System.putInt(a.contentResolver, name, value)
    } catch (e: Exception) {
        false
    }

    private fun needWrite(): Boolean {
        if (Settings.System.canWrite(a)) return false
        a.say("Falta un permiso para cambiar ajustes. $helper lo tiene que dar una vez.", "warn")
        return true
    }

    private fun installed(pkg: String): Boolean = try {
        a.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun call(number: String, name: String) {
        if (number.isBlank()) {
            a.say("Falta el número de $name. Pídele a $helper que lo configure.", "warn")
            return
        }
        Speaker.say(a, "Llamando a $name")
        // Primero por WhatsApp: es gratis aunque estén en países distintos.
        if (whatsappCall(number, video = false)) return
        val granted = a.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        open(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + number.trim())))
    }

    /** Abre WhatsApp con ese número (con código de país) y, si se da, un mensaje ya escrito. */
    fun whatsapp(number: String, message: String?): Boolean {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return false
        val url = "https://wa.me/$digits" + if (message != null) "?text=" + Uri.encode(message) else ""
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            if (!installed(pkg)) continue
            try {
                a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))
                return true
            } catch (e: Exception) { }
        }
        return false
    }

    private fun online(): Boolean = try {
        val cm = a.getSystemService(android.net.ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    } catch (e: Exception) {
        true
    }

    private fun batteryPct(): Int =
        a.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    // ---------------- Sonido ----------------

    private fun cycleRinger() {
        val nm = a.getSystemService(NotificationManager::class.java)
        val next = when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE ->
                if (nm.isNotificationPolicyAccessGranted) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        setRinger(next)
    }

    fun setRinger(mode: Int) {
        val nm = a.getSystemService(NotificationManager::class.java)
        if (mode == AudioManager.RINGER_MODE_SILENT && !nm.isNotificationPolicyAccessGranted) {
            a.say("Para poner silencio falta un permiso. $helper lo tiene que dar una vez.", "warn")
            return
        }
        try {
            am.ringerMode = mode
        } catch (e: SecurityException) {
            a.say("Falta un permiso. $helper lo tiene que dar una vez.", "warn")
            return
        }
        a.say(
            when (mode) {
                AudioManager.RINGER_MODE_NORMAL -> "Listo, el teléfono ya suena."
                AudioManager.RINGER_MODE_VIBRATE -> "Listo, el teléfono ya vibra."
                else -> "Listo, el teléfono está en silencio."
            }
        )
        a.refresh()
    }

    private fun volumeUp() {
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            try { am.ringerMode = AudioManager.RINGER_MODE_NORMAL } catch (e: SecurityException) { }
        }
        for (stream in intArrayOf(AudioManager.STREAM_RING, AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_NOTIFICATION)) {
            try {
                am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, if (stream == AudioManager.STREAM_RING) AudioManager.FLAG_SHOW_UI else 0)
            } catch (e: SecurityException) { }
        }
        val v = am.getStreamVolume(AudioManager.STREAM_RING)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_RING)
        a.say(if (v >= max) "El volumen ya está al máximo." else "Listo, subí el volumen. Ahora está en $v de $max.")
        a.refresh()
    }

    // ---------------- Pantalla ----------------

    private fun fontSize() {
        if (!Settings.System.canWrite(a)) {
            guide("Busca “Tamaño de fuente” y mueve la barrita hacia la derecha.")
            open(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            return
        }
        val steps = floatArrayOf(0.9f, 1.0f, 1.15f, 1.3f, 1.45f)
        a.sheet { c, d ->
            val cur = Settings.System.getFloat(a.contentResolver, Settings.System.FONT_SCALE, 1f)
            c.addView(a.heading("Tamaño de letra"))
            c.addView(a.text("Así se ve la letra ahora.", 20f))
            c.addView(a.bigButton("Letra más grande", Col.AZUL) {
                val next = steps.firstOrNull { it > cur + 0.01f } ?: steps.last()
                setFont(next)
                d.dismiss()
            }, a.full())
            c.addView(a.bigButton("Letra más pequeña", Col.BLANCO, Col.TINTA) {
                val next = steps.lastOrNull { it < cur - 0.01f } ?: steps.first()
                setFont(next)
                d.dismiss()
            }, a.full())
            c.addView(a.bigButton("Cerrar", Col.BLANCO, Col.TINTA) { d.dismiss() }, a.full())
        }
    }

    private fun setFont(scale: Float) {
        try {
            Settings.System.putFloat(a.contentResolver, Settings.System.FONT_SCALE, scale)
            a.say("Listo, cambié el tamaño de la letra.")
        } catch (e: Exception) {
            guide("Busca “Tamaño de fuente” y mueve la barrita hacia la derecha.")
            open(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
    }

    private fun brightness() {
        if (needWrite()) return
        a.sheet { c, d ->
            c.addView(a.heading("Brillo de pantalla"))
            c.addView(a.bigButton("☀️ Más claro", Col.AZUL) { changeBrightness(+50) }, a.full())
            c.addView(a.bigButton("🌙 Más oscuro", Col.BLANCO, Col.TINTA) { changeBrightness(-50) }, a.full())
            c.addView(a.bigButton("Listo", Col.VERDE) { d.dismiss() }, a.full())
        }
    }

    private fun changeBrightness(delta: Int) {
        val cr = a.contentResolver
        writeSys(Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        val next = (cur + delta).coerceIn(15, 255)
        if (writeSys(Settings.System.SCREEN_BRIGHTNESS, next)) Speaker.say(a, if (delta > 0) "Más claro" else "Más oscuro")
        else a.say("Este celular no me deja cambiar el brillo. Usa la barrita de los ajustes rápidos, arriba.", "warn")
    }

    // ---------------- Ayuda ----------------

    /** Antes de abrir TeamViewer pregunta con quién habla: es la herramienta favorita de los estafadores. */
    private fun remote() {
        Speaker.say(a, "¿Estás hablando con $helper ahora mismo?")
        a.sheet { c, d ->
            c.addView(a.heading("¿Estás hablando con $helper ahora mismo?"))
            c.addView(a.text("El ID de TeamViewer solo se le da a $helper. Los bancos, la compañía de teléfono o el técnico nunca te lo piden: si alguien te lo pide, es una estafa.", 20f))
            c.addView(a.bigButton("Sí, estoy hablando con $helper", Col.VERDE) {
                d.dismiss()
                if (Cloud.configured(a)) Thread { Cloud.alert(a.applicationContext, "info", "Abrió “Ayuda a distancia” (TeamViewer).") }.start()
                openRemoteHelp()
            }, a.full())
            c.addView(a.bigButton("No, me lo pidió otra persona", Col.ROJO) {
                d.dismiss()
                a.say("No le des ningún número. Cuelga y llama a $helper.", "warn")
                if (Cloud.configured(a)) Thread { Cloud.alert(a.applicationContext, "bad", "Alguien le pidió abrir TeamViewer y dijo que NO era $helper. Posible estafa: llámale.") }.start()
            }, a.full())
            c.addView(a.bigButton("Primero llamar a $helper", Col.BLANCO, Col.TINTA) {
                d.dismiss()
                call(p.helperPhone, helper)
            }, a.full())
        }
    }

    /** Videollamada de WhatsApp directa a Manuel y guía para compartir la pantalla completa. */
    private fun shareScreen() {
        val steps = "Cuando $helper conteste: toca la pantalla, toca el botón de compartir pantalla, el de la flecha, " +
            "elige “Pantalla completa” y toca “Iniciar”."
        a.sheet { c, d ->
            c.addView(a.heading("Mostrarle tu pantalla a $helper"))
            c.addView(a.text("1. Te llamo por videollamada de WhatsApp.\n2. Cuando conteste, toca la pantalla.\n3. Toca el botón de compartir pantalla (📲 la flecha).\n4. Elige “Pantalla completa” y toca “Iniciar”.", 21f))
            c.addView(a.bigButton("Llamar a $helper y compartir", Col.VERDE) {
                d.dismiss()
                Toast.makeText(a, steps, Toast.LENGTH_LONG).show()
                Speaker.say(a, steps)
                if (!whatsappCall(p.helperPhone, video = true)) {
                    guide("Toca el dibujo de la cámara arriba a la derecha para la videollamada.")
                    if (!whatsapp(p.helperPhone, null)) a.say("No encontré WhatsApp en este celular.", "warn")
                }
            }, a.full())
            c.addView(a.bigButton("Cancelar", Col.BLANCO, Col.TINTA) { d.dismiss() }, a.full())
        }
    }

    /**
     * Abre la videollamada de WhatsApp directamente. Busca el contacto de Manuel en los contactos
     * del celular (WhatsApp agrega una fila de "videollamada" por cada contacto que lo usa).
     */
    private fun whatsappCall(number: String, video: Boolean): Boolean {
        if (a.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
        val digits = number.filter { it.isDigit() }
        if (digits.length < 7) return false
        val tail = digits.takeLast(10)
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            if (!installed(pkg)) continue
            val kind = if (video) "video.call" else "voip.call"
            val mime = if (pkg == "com.whatsapp") "vnd.android.cursor.item/vnd.com.whatsapp.$kind"
                       else "vnd.android.cursor.item/vnd.com.whatsapp.w4b.$kind"
            try {
                a.contentResolver.query(
                    android.provider.ContactsContract.Data.CONTENT_URI,
                    arrayOf("_id", "data1"),
                    "mimetype = ?", arrayOf(mime), null
                )?.use { cur ->
                    while (cur.moveToNext()) {
                        val data1 = (cur.getString(1) ?: "").filter { it.isDigit() }
                        if (data1.endsWith(tail)) {
                            val uri = Uri.parse("content://com.android.contacts/data/" + cur.getLong(0))
                            a.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).setPackage(pkg))
                            return true
                        }
                    }
                }
            } catch (e: Exception) { }
        }
        return false
    }

    private fun openRemoteHelp() {
        val pkg = "com.teamviewer.quicksupport.market"
        val li = a.packageManager.getLaunchIntentForPackage(pkg)
        if (li != null) {
            guide("Dile a $helper el número de “Tu ID” que aparece en TeamViewer. Cuando se conecte, toca “Permitir”.")
            open(li)
        } else {
            a.say("Falta instalar TeamViewer QuickSupport. $helper te ayuda.", "warn")
            open(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
            )
        }
    }

    private fun estoyBien() {
        p.lastEstoyBien = System.currentTimeMillis()
        Notif.cancel(a, Ids.ESTOY_BIEN)
        if (Cloud.configured(a)) {
            a.say("Listo, $helper ya sabe que estás bien.")
            Thread {
                val ok = Cloud.alert(a.applicationContext, "ok", Catalog.ESTOY_BIEN_TEXT)
                if (!ok) a.runOnUiThread {
                    a.say("No hay internet. Te abro WhatsApp: toca enviar.", "warn")
                    whatsapp(p.helperPhone, "Estoy bien 👍")
                }
            }.start()
        } else {
            a.say("Toca el botón de enviar en WhatsApp.", "info")
            if (!whatsapp(p.helperPhone, "Estoy bien 👍")) a.say("No encontré WhatsApp. Llama a $helper.", "warn")
        }
        a.refresh()
    }

    private fun confirmEmergency() {
        Speaker.say(a, "¿Necesitas ayuda? Le envío a $helper dónde estás.")
        a.sheet { c, d ->
            c.addView(a.heading("¿Necesitas ayuda?"))
            c.addView(a.text("Le envío a $helper dónde estás, con un mapa.", 21f))
            c.addView(a.bigButton("Sí, enviar mi ubicación", Col.ROJO) {
                d.dismiss()
                sendEmergency()
            }, a.full())
            c.addView(a.bigButton("No, era un error", Col.BLANCO, Col.TINTA) { d.dismiss() }, a.full())
        }
    }

    private fun sendEmergency() {
        a.say("Buscando dónde estás…", "info")
        Locate.get(a) { loc ->
            val link = if (loc != null) "https://maps.google.com/?q=${loc.latitude},${loc.longitude}" else null
            val msg = "🆘 ${p.parentName.replaceFirstChar { it.uppercase() }} necesita ayuda. " +
                (if (link != null) "Está aquí: $link" else "No pude saber la ubicación.")
            if (Cloud.configured(a)) {
                Thread { Cloud.alert(a.applicationContext, "emergencia", msg, loc?.latitude, loc?.longitude) }.start()
            }
            if (!online()) {
                // Sin internet WhatsApp no llama: en una emergencia se usa la línea normal aunque cueste.
                a.say("No hay internet. Llamando a $helper por teléfono.", "warn")
                val granted = a.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                open(Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + p.helperPhone.trim())))
            } else if (whatsappCall(p.helperPhone, video = false)) {
                a.say("Llamando a $helper por WhatsApp. Tu ubicación va a su panel.")
            } else {
                a.say("Toca el botón de enviar para que $helper reciba tu ubicación. Después llámalo.", "warn")
                if (!whatsapp(p.helperPhone, msg)) call(p.helperPhone, helper)
            }
        }
    }

    // ---------------- Mi celular ----------------

    private fun torch() {
        try {
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) {
                a.say("Este celular no tiene linterna.", "warn")
                return
            }
            val on = !torchOn
            cm.setTorchMode(id, on)
            torchOn = on
            a.say(if (on) "Listo, la linterna está prendida." else "Listo, apagué la linterna.")
            a.refresh()
        } catch (e: Exception) {
            a.say("La cámara está ocupada. Cierra la otra app e intenta otra vez.", "warn")
        }
    }

    private fun battery() {
        val pct = batteryPct()
        val charging = a.getSystemService(BatteryManager::class.java).isCharging
        val advice = when {
            charging -> "Se está cargando."
            pct >= 50 -> "Tienes bastante batería."
            pct >= 20 -> "Cárgalo hoy."
            else -> "Ponlo a cargar ya."
        }
        Speaker.say(a, "Te queda $pct por ciento de batería. $advice")
        a.sheet { c, d ->
            c.addView(a.heading("Batería"))
            c.addView(a.text("$pct %", 56f, true, if (pct < 20) Col.ROJO else Col.TINTA))
            c.addView(a.text(advice, 22f))
            c.addView(a.bigButton("Prender ahorro de batería", Col.AZUL) {
                d.dismiss()
                guide("Prende el interruptor de “Ahorro de energía”.")
                open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            }, a.full())
            c.addView(a.bigButton("Cerrar", Col.BLANCO, Col.TINTA) { d.dismiss() }, a.full())
        }
    }

    private fun storage() {
        val st = StatFs(Environment.getDataDirectory().path)
        val free = st.availableBytes / 1_000_000_000.0
        val total = st.totalBytes / 1_000_000_000.0
        val txt = String.format(Locale("es"), "Tienes %.1f GB libres de %.0f GB.", free, total)
        Speaker.say(a, txt)
        a.sheet { c, d ->
            c.addView(a.heading("Liberar espacio"))
            c.addView(a.text(txt, 22f))
            c.addView(a.bigButton("Abrir el limpiador", Col.AZUL) {
                d.dismiss()
                val cleaner = listOf("com.samsung.android.lool", "com.miui.cleanmaster", "com.miui.securitycenter")
                    .mapNotNull { a.packageManager.getLaunchIntentForPackage(it) }
                    .firstOrNull()
                guide("Toca “Optimizar” o “Limpiar”.")
                if (cleaner != null) open(cleaner) else open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
            }, a.full())
            c.addView(a.bigButton("Ver apps que no uso", Col.BLANCO, Col.TINTA) {
                d.dismiss()
                a.showUnused()
            }, a.full())
            c.addView(a.bigButton("Cerrar", Col.BLANCO, Col.TINTA) { d.dismiss() }, a.full())
        }
    }

    private fun updates() {
        a.sheet { c, d ->
            c.addView(a.heading("Actualizaciones"))
            c.addView(a.text("Mejor con wifi y el celular conectado al cargador.", 20f))
            c.addView(a.bigButton("Revisar el celular", Col.AZUL) {
                d.dismiss()
                guide("Toca “Descargar e instalar”. No lo apagues mientras se actualiza.")
                open(Intent("android.settings.SYSTEM_UPDATE_SETTINGS"), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            }, a.full())
            c.addView(a.bigButton("Revisar las apps", Col.BLANCO, Col.TINTA) {
                d.dismiss()
                guide("Toca tu foto arriba a la derecha y luego “Administrar apps”. Después toca “Actualizar todo”.")
                val store = a.packageManager.getLaunchIntentForPackage("com.android.vending")
                if (store != null) open(store) else a.say("No encontré la Play Store.", "warn")
            }, a.full())
            c.addView(a.bigButton("Cerrar", Col.BLANCO, Col.TINTA) { d.dismiss() }, a.full())
        }
    }

    /** Quita una app: si vino con el celular solo se puede desactivar. */
    fun uninstall(pkg: String) {
        if (p.practice) {
            a.say("Modo práctica: no quité nada.", "info")
            return
        }
        val ai = try {
            a.packageManager.getApplicationInfo(pkg, 0)
        } catch (e: Exception) {
            a.say("Esa app ya no está en el celular.", "info")
            return
        }
        if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
            guide("Esta app vino con el celular. Toca “Desactivar”.")
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        } else {
            a.say("Toca “Aceptar” para quitarla.", "info")
            open(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        }
    }

    // ---------------- Configuración y voz ----------------

    /** Pide el PIN; si todavía no hay uno, obliga a crearlo. */
    fun askPin(titleText: String, onOk: () -> Unit) {
        val creating = p.pin.isEmpty()
        val wait = p.pinLockUntil - System.currentTimeMillis()
        if (!creating && wait > 0) {
            a.say("Demasiados intentos. Espera ${wait / 60000 + 1} minuto(s).", "warn")
            return
        }
        a.sheet { c, d ->
            c.addView(a.heading(titleText))
            c.addView(a.text(if (creating) "Crea un PIN de al menos 4 números. Solo $helper debe saberlo." else "Solo para $helper. Escribe el PIN.", 20f))
            val pin = a.input("PIN", numeric = true)
            c.addView(pin, a.full())
            c.addView(a.bigButton(if (creating) "Guardar PIN" else "Entrar", Col.AZUL) {
                val t = pin.text.toString().trim()
                when {
                    creating && (t.length < 4 || !t.all { it.isDigit() }) -> {
                        pin.setText("")
                        pin.hint = "Mínimo 4 números"
                    }
                    creating -> {
                        p.pin = t
                        d.dismiss()
                        onOk()
                    }
                    p.pinMatches(t) -> {
                        p.pinFails = 0
                        d.dismiss()
                        onOk()
                    }
                    else -> {
                        p.pinFails = p.pinFails + 1
                        pin.setText("")
                        if (p.pinFails >= 5) {
                            p.pinFails = 0
                            p.pinLockUntil = System.currentTimeMillis() + 10 * 60 * 1000L
                            d.dismiss()
                            a.say("Demasiados intentos. Espera 10 minutos.", "warn")
                            if (Cloud.configured(a)) Thread {
                                Cloud.alert(a.applicationContext, "warn", "Alguien se equivocó 5 veces con el PIN de la configuración.")
                            }.start()
                        } else {
                            pin.hint = "PIN equivocado (${5 - p.pinFails} intentos más)"
                        }
                    }
                }
            }, a.full())
            c.addView(a.bigButton("Cancelar", Col.BLANCO, Col.TINTA) { d.dismiss() }, a.full())
        }
    }

    fun openSettings() = askPin("Configuración") {
        a.startActivity(Intent(a, SettingsActivity::class.java))
    }

    /** Salida de emergencia del modo pantalla de inicio. */
    fun exitLauncher() = askPin("Salir de la pantalla de inicio") {
        Launcher.set(a, false)
    }

    fun voiceCommand(results: List<String>) {
        val t = results.joinToString(" ").lowercase()
        fun has(vararg w: String) = w.any { t.contains(it) }
        when {
            has("emergencia", "auxilio", "socorro") -> run("emergencia")
            has("estoy bien") -> run("estoybien")
            has("no tengo internet", "no hay internet", "internet") -> run("nointernet")
            has("wifi", "wi-fi", "wi fi") -> run("wifi")
            has("letra") -> run("letra")
            has("brillo", "oscur", "muy claro", "más claro") -> run("brillo")
            has("compartir", "mostrar mi pantalla", "mostrarle", "ver mi pantalla") -> run("pantalla")
            has("llama", "llamar") -> run("llamar")
            has("linterna", "luz") -> run("linterna")
            has("se descarga", "dura poco", "lento", "trabado") -> run("lento")
            has("tema", "se ve raro", "se ve feo", "pantalla rara", "íconos", "iconos") -> run("tema")
            has("actualiz", "descarg") -> run("updates")
            has("vibr") -> if (p.practice) run("ringer") else setRinger(AudioManager.RINGER_MODE_VIBRATE)
            has("silencio") -> if (p.practice) run("ringer") else setRinger(AudioManager.RINGER_MODE_SILENT)
            has("volumen", "no oigo", "no escucho", "más alto") -> run("volumen")
            has("que suene", "sonido", "timbre") -> if (p.practice) run("ringer") else setRinger(AudioManager.RINGER_MODE_NORMAL)
            has("batería", "bateria", "carga") -> run("bateria")
            has("medicina", "pastilla", "remedio") -> run("medicinas")
            has("lupa", "no veo", "leer") -> run("lupa")
            has("seguridad", "espía", "espia", "espiando", "hackea") -> run("seguridad")
            has("lento", "trabado", "pegado", "reiniciar") -> run("lento")
            has("bluetooth", "audífono", "audifono", "carro") -> run("bluetooth")
            has("contraseña", "clave") -> run("clave")
            has("espacio", "lleno", "memoria") -> run("espacio")
            has("familia", "foto") -> run("familia")
            else -> a.say("No entendí. Toca el micrófono e intenta otra vez.", "warn")
        }
    }
}
