package com.manuel.ayudapadres

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = Prefs(ctx)
        when (intent.action) {
            Alarms.ACTION_MED -> {
                val id = intent.getIntExtra("id", 0)
                val name = intent.getStringExtra("name") ?: "tu medicina"
                // Programa el de mañana (si no es un "recuérdame en 10 minutos").
                if (!intent.getBooleanExtra("snooze", false)) {
                    p.meds.firstOrNull { it.id == id }?.let { Alarms.scheduleMed(ctx, it) }
                }
                val open = Intent(ctx, MedAlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("id", id).putExtra("name", name)
                val pi = Notif.pendingActivity(ctx, Ids.med(id), open)
                Notif.show(ctx, Ids.med(id), Notif.MEDICINAS, "💊 Hora de tu medicina", name, tap = pi, fullScreen = pi)
            }

            Alarms.ACTION_ESTOY_BIEN_RECORDAR -> {
                Alarms.scheduleEstoyBien(ctx)
                if (DateUtils.isToday(p.lastEstoyBien)) return
                // Abre la app y envía el aviso desde ahí: así funciona también sin Supabase (por WhatsApp)
                // y se ve la confirmación en pantalla grande.
                val send = Notif.pendingActivity(ctx, Ids.ESTOY_BIEN + 10,
                    Intent(ctx, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("run", "estoybien").putExtra("token", p.intentToken))
                Notif.show(ctx, Ids.ESTOY_BIEN, Notif.AVISOS, "¿Todo bien hoy?",
                    "Toca “Estoy bien” para avisarle a ${p.helperName}.",
                    actions = listOf("✅ Estoy bien" to send))
            }

            Alarms.ACTION_ESTOY_BIEN_ENVIAR -> {
                p.lastEstoyBien = System.currentTimeMillis()
                Notif.cancel(ctx, Ids.ESTOY_BIEN)
                val pending = goAsync()
                Thread {
                    try {
                        Cloud.alert(ctx.applicationContext, "ok", Catalog.ESTOY_BIEN_TEXT)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }

            Alarms.ACTION_NOCHE -> {
                Alarms.scheduleNight(ctx)
                BatteryCoach.night(ctx)
            }

            Alarms.ACTION_APP_OK -> {
                val pkg = intent.getStringExtra("pkg") ?: return
                val label = intent.getStringExtra("label") ?: pkg
                Notif.cancel(ctx, Ids.newApp(pkg))
                val pending = goAsync()
                Thread {
                    try {
                        Cloud.alert(ctx.applicationContext, "info", "Dice que sí instaló “$label”.")
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }
}
