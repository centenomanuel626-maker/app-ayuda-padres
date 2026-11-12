package com.manuel.ayudapadres

import android.app.AlertDialog
import android.app.Activity
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale

/** Configuración (solo para Manuel; en el celular de los padres está protegida con PIN). */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun section(c: LinearLayout, title: String) {
        val t = text(title, 24f, true)
        t.setPadding(0, dp(18), 0, 0)
        c.addView(t, full())
    }

    private fun toggleRow(c: LinearLayout, emoji: String, title: String, sub: String, on: Boolean, change: (Boolean) -> Unit) {
        c.addView(rowButton(emoji, title, sub, if (on) Col.VERDE else Col.GRIS, if (on) "Sí" else "No", on) {
            change(!on)
            render()
        }, full())
    }

    private fun render() {
        val c = scrollPage()
        val padres = prefs.role == "padres"
        val head = hbox(12)
        head.gravity = Gravity.CENTER_VERTICAL
        val back = text("‹ Volver", 20f, true)
        back.setPadding(dp(14), dp(10), dp(16), dp(10))
        back.background = rounded(Col.BLANCO, 16, Col.LINEA)
        back.setOnClickListener { finish() }
        head.addView(back)
        head.addView(text("Configuración", 26f, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(head, full())

        // ---------- Modo de este celular ----------
        val mode = card(Col.FONDO, Col.AZUL)
        mode.addView(text("Este celular está en modo:", 17f, false, Col.GRIS))
        mode.addView(text(if (padres) "👵 Celular de mis padres" else "🧑‍💻 Mi celular (panel de ${prefs.helperName})", 22f, true))
        mode.addView(bigButton(if (padres) "Cambiar a “Mi celular” (ver mi panel)" else "Cambiar a “Celular de mis padres”", Col.AZUL) {
            confirm(if (padres) "¿Cambiar este celular a tu panel? Se conserva el código de familia." else "¿Cambiar este celular al modo de tus padres?") {
                switchRole(if (padres) "manuel" else "padres")
            }
        }, full())
        c.addView(mode, full())

        c.addView(bigButton("🧪 Probar todo en este celular", Col.VERDE) {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }, full())
        c.addView(text("Revisa una por una las funciones (llamadas, ubicación, alarmas, avisos…) y deja el resultado en el panel. Hazlo en cada celular antes de dárselo a tus padres.", 16f, false, Col.GRIS), full())

        // ---------- Datos ----------
        section(c, "Datos")
        if (padres) {
            c.addView(editRow("Cómo saluda la app", prefs.parentName) { prefs.parentName = it }, full())
            c.addView(editRow("Quién ayuda", prefs.helperName) { prefs.helperName = it }, full())
            c.addView(editRow("Teléfono de quien ayuda", prefs.helperPhone, numeric = true) { prefs.helperPhone = it }, full())
            c.addView(editRow("PIN de configuración", if (prefs.pin.isEmpty()) "Sin PIN" else "••••", numeric = true, raw = "") {
                if (it.length >= 4 && it.all { ch -> ch.isDigit() }) prefs.pin = it
                else Toast.makeText(this, "El PIN debe tener al menos 4 números", Toast.LENGTH_LONG).show()
            }, full())
        } else {
            c.addView(editRow("Tu nombre", prefs.helperName) { prefs.helperName = it }, full())
        }
        c.addView(editRow("Código de familia", prefs.familyCode.ifEmpty { "Sin código" }, raw = prefs.familyCode) { prefs.familyCode = it }, full())
        val cloud = card(if (Cloud.configured(this)) Col.VERDE_SUAVE else Col.AMARILLO_SUAVE, null)
        cloud.addView(text(
            when {
                BuildConfig.SUPABASE_URL.isBlank() -> "Supabase no está configurado en esta versión de la app."
                prefs.familyCode.length < 8 -> "Falta el código de familia para conectarse al panel."
                else -> "Conectado a Supabase con el código de familia."
            }, 17f))
        cloud.addView(bigButton("Probar la conexión", Col.BLANCO, Col.TINTA) { testCloud() }, full())
        c.addView(cloud, full())

        // ---------- Permisos ----------
        if (Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }) {
            val xi = card(Col.AMARILLO_SUAVE, Col.AMARILLO)
            xi.addView(text("Importante en Xiaomi", 20f, true))
            xi.addView(text("Xiaomi cierra las apps en segundo plano. Si no activas estas dos cosas, no sonarán las medicinas ni llegarán los avisos a tu panel:\n1. Inicio automático: activado.\n2. Ahorro de batería: “Sin restricciones”.", 17f))
            xi.addView(bigButton("Abrir Inicio automático", Col.AMARILLO) {
                try {
                    startActivity(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
                }
            }, full())
            xi.addView(bigButton("Abrir ajustes de batería de la app", Col.BLANCO, Col.TINTA) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
            }, full())
            c.addView(xi, full())
        }

        if (padres) {
            val missing = Perms.missingCritical(this)
            if (missing.isNotEmpty()) {
                val m = card(Col.ROJO_SUAVE, Col.ROJO)
                m.addView(text("Falta para que la emergencia funcione: " + missing.joinToString(", "), 19f, true, Col.ROJO))
                m.addView(text("Actívalo abajo, en Permisos.", 16f))
                c.addView(m, full())
            }
        }

        section(c, "Permisos")
        c.addView(text("Toca los que tengan ❌ y acepta. Se hace una sola vez.", 17f, false, Col.GRIS), full())
        for (item in Perms.list(this, prefs.role)) {
            val g = item.granted(this)
            val state = when (g) {
                true -> "✅"
                false -> "❌"
                null -> "Revisar"
            }
            c.addView(rowButton("🔐", item.name, item.why, Col.AZUL, state, g == true) { item.request(this) }, full())
        }

        if (!padres) {
            c.addView(bigButton("Ir a mi panel", Col.AZUL) {
                startActivity(Intent(this, PanelActivity::class.java))
                finish()
            }, full())
            resetButton(c)
            return
        }

        // ---------- Medicinas ----------
        section(c, "Medicinas")
        for (m in prefs.meds) {
            c.addView(rowButton("💊", m.name, String.format(Locale.US, "%d:%02d · tocar para borrar", m.hour, m.minute), Col.NARANJA) {
                confirm("¿Borrar ${m.name}?") {
                    prefs.meds = prefs.meds.filter { it.id != m.id }
                    Alarms.cancelMed(this, m.id)
                    render()
                }
            }, full())
        }
        c.addView(bigButton("Agregar medicina", Col.NARANJA) { addMed() }, full())

        // ---------- Mi familia ----------
        section(c, "Mi familia (contactos con foto)")
        for (person in prefs.people) {
            c.addView(rowButton("👤", person.name, person.phone + " · tocar para borrar", Col.VERDE_AZUL) {
                confirm("¿Quitar a ${person.name}?") {
                    prefs.people = prefs.people.filter { it != person }
                    render()
                }
            }, full())
        }
        c.addView(bigButton("Agregar contacto", Col.VERDE_AZUL) {
            try {
                startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_CONTACT)
            } catch (e: Exception) {
                Toast.makeText(this, "No pude abrir los contactos", Toast.LENGTH_LONG).show()
            }
        }, full())

        // ---------- Categorías ----------
        section(c, "Categorías en la pantalla principal")
        for (cat in Catalog.cats) {
            val on = cat.id !in prefs.hiddenCats
            toggleRow(c, cat.emoji, cat.name, "Mostrar en la pantalla principal", on) { show ->
                prefs.hiddenCats = if (show) prefs.hiddenCats - cat.id else prefs.hiddenCats + cat.id
                // También en Supabase: si no, el reporte de 15 minutos lo devolvería a como estaba.
                val cats = prefs.hiddenCats
                val ctx = applicationContext
                Thread { Cloud.setHiddenCategories(ctx, prefs.deviceId, cats) }.start()
            }
        }

        // ---------- Protección ----------
        section(c, "Protección contra instalaciones")
        c.addView(rowButton("✅", "Apps aprobadas", "${prefs.approvedApps.size} apps · se muestran en modo pantalla de inicio y no se sugiere quitarlas", Col.AZUL) {
            chooseApps()
        }, full())
        toggleRow(c, "🏠", "Modo pantalla de inicio", "La app reemplaza la pantalla de inicio y solo muestra las apps aprobadas", prefs.launcherMode) { on ->
            setLauncher(on)
        }
        c.addView(text("Además deja apagado “Instalar apps desconocidas” para el navegador y las demás apps. La app avisa cada vez que aparece una app nueva.", 16f, false, Col.GRIS), full())

        // ---------- Otros ----------
        section(c, "Otros")
        toggleRow(c, "🔈", "Leer en voz alta", "La app lee los mensajes y las instrucciones", prefs.voice) { prefs.voice = it }
        toggleRow(c, "🎓", "Modo práctica", "Los botones solo explican para qué sirven", prefs.practice) { prefs.practice = it }
        c.addView(bigButton("Ver el tutorial otra vez", Col.BLANCO, Col.TINTA) {
            prefs.tutorialDone = false
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }, full())
        c.addView(bigButton("Volver a la app", Col.VERDE) { finish() }, full())
        resetButton(c)
    }

    private fun editRow(label: String, shown: String, numeric: Boolean = false, raw: String? = null, save: (String) -> Unit): LinearLayout =
        rowButton("✏️", label, shown, Col.GRIS) {
            sheet { s, d ->
                s.addView(heading(label))
                val e = input(label, raw ?: shown, numeric)
                s.addView(e, full())
                s.addView(bigButton("Guardar", Col.VERDE) {
                    save(e.text.toString().trim())
                    d.dismiss()
                    render()
                }, full())
                s.addView(bigButton("Cancelar", Col.BLANCO, Col.TINTA) { d.dismiss() }, full())
            }
        }

    private fun confirm(question: String, yes: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(question)
            .setPositiveButton("Sí") { _, _ -> yes() }
            .setNegativeButton("No", null)
            .show()
    }

    /** Cambia el modo sin borrar nada: se conserva el código de familia, los nombres y el teléfono. */
    private fun switchRole(role: String) {
        if (prefs.launcherMode) setLauncher(false)
        prefs.role = role
        if (role == "manuel") {
            prefs.alertsReady = false
            // Este celular deja de reportar como "padres": se quita del panel.
            val ctx = applicationContext
            val id = prefs.deviceId
            Thread { Cloud.removeDevice(ctx, id) }.start()
            startActivity(Intent(this, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } else {
            if (prefs.pin.isEmpty()) Toast.makeText(this, "Pon un PIN en esta configuración", Toast.LENGTH_LONG).show()
            prefs.knownApps = InstallWatch.launcherApps(this).keys
            Alarms.scheduleAll(this)
            startActivities(arrayOf(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                Intent(this, SettingsActivity::class.java)
            ))
        }
        finish()
    }

    private fun resetButton(c: LinearLayout) {
        c.addView(bigButton("Configurar desde cero", Col.BLANCO, Col.ROJO) {
            confirm("¿Borrar la configuración de este celular?") {
                prefs.role = ""
                if (prefs.launcherMode) setLauncher(false)
                startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        }, full())
    }

    private fun testCloud() {
        Toast.makeText(this, "Probando…", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = if (prefs.role == "padres") Cloud.reportStatus(applicationContext, JSONObject())
                     else Cloud.devices(applicationContext) != null
            runOnUiThread {
                Toast.makeText(this, if (ok) "Conexión correcta" else "No se pudo conectar. Revisa el código y el internet.", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun addMed() {
        sheet { s, d ->
            s.addView(heading("Agregar medicina"))
            val name = input("Nombre y dosis (ej. Losartán 50 mg)")
            s.addView(name, full())
            s.addView(bigButton("Elegir la hora", Col.NARANJA) {
                val n = name.text.toString().trim()
                if (n.isEmpty()) {
                    name.hint = "Escribe el nombre primero"
                    return@bigButton
                }
                d.dismiss()
                TimePickerDialog(this, { _, h, m ->
                    val med = Prefs.Med(prefs.nextMedId(), n, h, m)
                    prefs.meds = prefs.meds + med
                    Alarms.scheduleMed(this, med)
                    render()
                }, 8, 0, true).show()
            }, full())
            s.addView(bigButton("Cancelar", Col.BLANCO, Col.TINTA) { d.dismiss() }, full())
        }
    }

    private fun chooseApps() {
        val apps = InstallWatch.launcherApps(this).entries.sortedBy { it.value.lowercase() }
        val labels = apps.map { it.value }.toTypedArray()
        val checked = apps.map { it.key in prefs.approvedApps }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Apps aprobadas")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Guardar") { _, _ ->
                prefs.approvedApps = apps.filterIndexed { i, _ -> checked[i] }.map { it.key }.toSet()
                render()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setLauncher(on: Boolean) = Launcher.set(this, on)

    @Deprecated("API clásica de resultados")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CONTACT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.query(
                uri,
                arrayOf("display_name", "data1", "photo_uri"), null, null, null
            )?.use { cur ->
                if (cur.moveToFirst()) {
                    val person = Prefs.Person(cur.getString(0) ?: "", cur.getString(1) ?: "", cur.getString(2) ?: "")
                    if (person.phone.isNotBlank()) prefs.people = prefs.people + person
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "No pude leer ese contacto", Toast.LENGTH_LONG).show()
        }
        render()
    }

    companion object {
        const val REQ_CONTACT = 31
    }
}
