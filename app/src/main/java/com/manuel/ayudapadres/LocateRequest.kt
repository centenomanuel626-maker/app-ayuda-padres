package com.manuel.ayudapadres

import android.content.Context
import org.json.JSONObject
import java.time.Instant

/**
 * "¿Dónde está?" desde el panel de Manuel: el celular de los padres manda su ubicación
 * y les muestra un aviso para que sepan que alguien la vio.
 */
object LocateRequest {

    /** Hace red: fuera del hilo principal. */
    fun check(ctx: Context) {
        val p = Prefs(ctx)
        if (p.role != "padres" || !Cloud.configured(ctx)) return
        val req = Cloud.fetchLocateRequest(ctx) ?: return
        if (req <= p.lastLocateReq) return
        p.lastLocateReq = req
        Locate.get(ctx) { l ->
            Thread {
                try {
                    if (l == null) {
                        Cloud.alert(ctx, "warn", "No pude ver la ubicación: el GPS está apagado o falta el permiso de ubicación.")
                    } else {
                        Cloud.reportStatus(
                            ctx, JSONObject()
                                .put("lat", l.latitude).put("lng", l.longitude)
                                .put("loc_acc", l.accuracy.toDouble())
                                .put("loc_at", Instant.ofEpochMilli(l.time).toString())
                        )
                        Notif.show(
                            ctx, Ids.UBICACION, Notif.AVISOS,
                            "📍 ${p.helperName} vio dónde estás",
                            "Lo pidió desde su panel para saber que estás bien."
                        )
                    }
                } catch (e: Exception) {
                    CrashLog.save(ctx, e, "ubicación a pedido")
                }
            }.start()
        }
    }
}
