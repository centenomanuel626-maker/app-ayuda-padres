package com.manuel.ayudapadres

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Al prender el celular o actualizar la app, vuelve a programar alarmas y reportes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // El receptor es público (lo exige el sistema): solo se aceptan estos dos avisos.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Alarms.scheduleAll(ctx)
        ReportJobService.schedule(ctx)
        KeepAliveService.start(ctx)
        // Al prender: contarle a Manuel cuándo se apagó y con cuánta batería.
        val p = Prefs(ctx)
        val off = p.lastShutdown
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && off.isNotEmpty() && p.role == "padres") {
            p.lastShutdown = ""
            val whenMs = off.substringBefore('|').toLongOrNull() ?: return
            val bat = off.substringAfter('|').toIntOrNull() ?: -1
            val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            val zone = java.time.ZoneId.systemDefault()
            val offAt = java.time.Instant.ofEpochMilli(whenMs).atZone(zone).format(fmt)
            val onAt = java.time.LocalTime.now().format(fmt)
            val why = if (bat in 0..3) "se quedó sin batería" else "lo apagaron (tenía $bat %)"
            val text = "El celular se apagó a las $offAt ($why) y volvió a prender a las $onAt."
            val pending = goAsync()
            Thread {
                try {
                    Cloud.reportStatus(ctx, org.json.JSONObject().put("last_off", text))
                    Cloud.alert(ctx, "info", text)
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }
}
