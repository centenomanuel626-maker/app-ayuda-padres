package com.manuel.ayudapadres

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import java.security.SecureRandom

/** Primera vez que se abre la app: ¿es el celular de los padres o el de Manuel? */
class SetupActivity : Activity() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        chooseRole()
    }

    private fun chooseRole() {
        val c = scrollPage()
        c.addView(heading("Bienvenido"))
        c.addView(text("¿En qué celular estás instalando la app?", 22f), full())
        c.addView(rowButton("👵", "En el celular de mis padres", "Botones grandes para que se ayuden solos", Col.VERDE_AZUL) {
            parentSetup()
        }, full())
        c.addView(rowButton("🧑‍💻", "En mi celular", "Para ver el panel y recibir los avisos", Col.AZUL) {
            helperSetup()
        }, full())
    }

    private fun parentSetup() {
        val c = scrollPage()
        c.addView(heading("Celular de mis padres"))
        c.addView(text("¿Cómo los saluda la app?", 19f, true))
        val name = input("mamá, papá...", prefs.parentName)
        c.addView(name, full())
        c.addView(text("¿Quién los ayuda?", 19f, true))
        val helper = input("Tu nombre", prefs.helperName)
        c.addView(helper, full())
        c.addView(text("Tu teléfono, con código de país", 19f, true))
        c.addView(text("Ejemplo: +57 300 123 4567. Se usa para llamarte y para WhatsApp.", 16f, false, Col.GRIS))
        val phone = input("+57 300 123 4567", prefs.helperPhone, numeric = true)
        c.addView(phone, full())
        c.addView(text("Código de familia (opcional)", 19f, true))
        c.addView(text("Lo ves en la app de tu celular, en “Mi panel”. Sin código, la app funciona igual pero sin panel ni avisos.", 16f, false, Col.GRIS))
        val code = input("XXXX-XXXX-XXXX", prefs.familyCode)
        c.addView(code, full())
        c.addView(text("PIN para la configuración (obligatorio)", 19f, true))
        c.addView(text("4 números. Así tus padres no cambian la configuración sin querer.", 16f, false, Col.GRIS))
        val pin = input("1234", prefs.pin, numeric = true)
        c.addView(pin, full())

        c.addView(bigButton("Guardar y dar permisos", Col.VERDE) {
            if (phone.text.toString().filter { it.isDigit() }.length < 8) {
                Toast.makeText(this, "Escribe tu teléfono con código de país", Toast.LENGTH_LONG).show()
                return@bigButton
            }
            val pinText = pin.text.toString().trim()
            if (pinText.length < 4 || !pinText.all { it.isDigit() }) {
                Toast.makeText(this, "Escribe un PIN de al menos 4 números", Toast.LENGTH_LONG).show()
                return@bigButton
            }
            prefs.parentName = name.text.toString().trim().ifEmpty { "mamá" }
            prefs.helperName = helper.text.toString().trim().ifEmpty { "Manuel" }
            prefs.helperPhone = phone.text.toString().trim()
            prefs.familyCode = code.text.toString()
            prefs.pin = pin.text.toString().trim()
            prefs.role = "padres"
            prefs.knownApps = InstallWatch.launcherApps(this).keys
            Alarms.scheduleAll(this)
            ReportJobService.schedule(this)
            startActivities(arrayOf(
                Intent(this, MainActivity::class.java),
                Intent(this, SettingsActivity::class.java)
            ))
            finish()
        }, full())
        c.addView(bigButton("Atrás", Col.BLANCO, Col.TINTA) { chooseRole() }, full())
    }

    private fun helperSetup() {
        if (prefs.familyCode.isEmpty()) prefs.familyCode = newCode()
        val c = scrollPage()
        c.addView(heading("Mi celular"))
        c.addView(text("Tu nombre", 19f, true))
        val helper = input("Tu nombre", prefs.helperName)
        c.addView(helper, full())
        c.addView(text("Código de familia", 19f, true))
        c.addView(text("Escríbelo en la app de cada celular de tus padres. Guárdalo: es la llave de tu panel.", 16f, false, Col.GRIS))
        val code = input("Código", prefs.familyCode)
        code.textSize = 28f
        code.gravity = Gravity.CENTER
        c.addView(code, full())
        c.addView(bigButton("Copiar el código", Col.BLANCO, Col.TINTA) {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("Código de familia", code.text.toString()))
            Toast.makeText(this, "Código copiado", Toast.LENGTH_SHORT).show()
        }, full())
        if (BuildConfig.SUPABASE_URL.isBlank()) {
            val warn = card(Col.AMARILLO_SUAVE, Col.AMARILLO)
            warn.addView(text("Esta versión de la app no tiene Supabase configurado, así que el panel no recibirá datos. Revisa la guía de instalación.", 17f))
            c.addView(warn, full())
        }
        c.addView(bigButton("Listo", Col.VERDE) {
            prefs.helperName = helper.text.toString().trim().ifEmpty { "Manuel" }
            prefs.familyCode = code.text.toString()
            prefs.role = "manuel"
            ReportJobService.schedule(this)
            startActivity(Intent(this, PanelActivity::class.java))
            finish()
        }, full())
        c.addView(bigButton("Atrás", Col.BLANCO, Col.TINTA) { chooseRole() }, full())
    }

    companion object {
        fun newCode(): String {
            val abc = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
            val r = SecureRandom()
            val raw = (1..12).map { abc[r.nextInt(abc.length)] }.joinToString("")
            return raw.chunked(4).joinToString("-")
        }
    }
}
