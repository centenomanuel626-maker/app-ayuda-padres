package com.manuel.ayudapadres

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** Números fijos para notificaciones y alarmas. */
object Ids {
    const val ESTOY_BIEN = 1
    const val BATERIA = 2
    const val ESTAFA = 3
    const val REPORTE = 4
    const val NOCHE = 5
    const val QUITAR = 6
    const val PANTALLA_INICIO = 7
    fun med(id: Int) = 1000 + id
    fun newApp(pkg: String) = 5000 + (pkg.hashCode() and 0xFFFF)
    fun alert(id: Long) = 200000 + (id % 100000).toInt()
}

/** Recordatorios de medicinas y del "Estoy bien" diario. */
object Alarms {
    const val ACTION_MED = "com.manuel.ayudapadres.MED"
    const val ACTION_ESTOY_BIEN_RECORDAR = "com.manuel.ayudapadres.ESTOY_BIEN_RECORDAR"
    const val ACTION_ESTOY_BIEN_ENVIAR = "com.manuel.ayudapadres.ESTOY_BIEN_ENVIAR"
    const val ACTION_APP_OK = "com.manuel.ayudapadres.APP_OK"
    const val ACTION_NOCHE = "com.manuel.ayudapadres.NOCHE"
    const val NOCHE_HOUR = 21
    const val ESTOY_BIEN_HOUR = 10

    fun nextMed(p: Prefs): Prefs.Med? {
        val meds = p.meds
        if (meds.isEmpty()) return null
        val now = Calendar.getInstance()
        val mins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return meds.firstOrNull { it.hour * 60 + it.minute >= mins } ?: meds.first()
    }

    private fun nextTime(hour: Int, minute: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    /**
     * clock = true para las medicinas: se programa como alarma de reloj (el ícono del despertador),
     * que ni el ahorro de batería ni Xiaomi pueden atrasar.
     */
    private fun arm(ctx: Context, code: Int, at: Long, intent: Intent, clock: Boolean = false) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = Notif.pendingBroadcast(ctx, code, intent)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (clock && exact) {
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, Notif.openApp(ctx, code)), pi)
                return
            } catch (e: Exception) { }
        }
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun medIntent(ctx: Context, m: Prefs.Med): Intent =
        Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_MED)
            .putExtra("id", m.id).putExtra("name", m.name)

    fun scheduleMed(ctx: Context, m: Prefs.Med) = arm(ctx, Ids.med(m.id), nextTime(m.hour, m.minute), medIntent(ctx, m), clock = true)

    fun snoozeMed(ctx: Context, id: Int, name: String, minutes: Int = 10) {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_MED).putExtra("id", id).putExtra("name", name)
            .putExtra("snooze", true)
        arm(ctx, Ids.med(id) + 500, System.currentTimeMillis() + minutes * 60_000L, i, clock = true)
    }

    fun cancelMed(ctx: Context, id: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(Notif.pendingBroadcast(ctx, Ids.med(id), Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_MED)))
    }

    fun scheduleEstoyBien(ctx: Context) =
        arm(ctx, Ids.ESTOY_BIEN, nextTime(ESTOY_BIEN_HOUR, 0),
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_ESTOY_BIEN_RECORDAR))

    fun scheduleNight(ctx: Context) =
        arm(ctx, Ids.NOCHE, nextTime(NOCHE_HOUR, 0), Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_NOCHE))

    fun scheduleAll(ctx: Context) {
        val p = Prefs(ctx)
        if (p.role != "padres") return
        p.meds.forEach { scheduleMed(ctx, it) }
        scheduleEstoyBien(ctx)
        scheduleNight(ctx)
    }
}
