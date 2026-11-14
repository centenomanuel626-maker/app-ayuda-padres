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
    }
}
