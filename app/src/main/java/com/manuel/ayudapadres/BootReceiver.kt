package com.manuel.ayudapadres

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Al prender el celular o actualizar la app, vuelve a programar alarmas y reportes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Alarms.scheduleAll(ctx)
        ReportJobService.schedule(ctx)
    }
}
