package com.manuel.ayudapadres

import android.app.Activity
import android.os.Bundle
import android.view.Gravity

/** Pantalla grande que aparece a la hora de cada medicina, aunque el celular esté bloqueado. */
class MedAlarmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra("id", 0)
        val name = intent.getStringExtra("name") ?: "tu medicina"
        Speaker.init(this)
        val c = scrollPage()
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.addView(text("💊", 90f))
        c.addView(text("Hora de tu medicina", 26f, false, Col.GRIS))
        c.addView(text(name, 40f, true))
        c.addView(bigButton("Ya me la tomé", Col.VERDE) {
            Notif.cancel(this, Ids.med(id))
            Thread { Cloud.alert(applicationContext, "info", "Tomó su medicina: $name.") }.start()
            finish()
        }, full())
        c.addView(bigButton("Recuérdame en 10 minutos", Col.BLANCO, Col.TINTA) {
            Notif.cancel(this, Ids.med(id))
            Alarms.snoozeMed(this, id, name)
            finish()
        }, full())
        Speaker.say(this, "Hora de tu medicina: $name")
    }
}
