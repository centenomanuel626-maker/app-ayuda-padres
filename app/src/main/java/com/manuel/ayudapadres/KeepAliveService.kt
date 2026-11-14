package com.manuel.ayudapadres

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.time.LocalTime

/**
 * Aviso fijo "Ayuda está activa". Mientras se ve, Android (y sobre todo Xiaomi/HyperOS)
 * no duerme la app: así los reportes al panel, las medicinas y la batería siguen funcionando.
 * También manda el reporte cada 15 minutos si el sistema atrasó el trabajo programado.
 */
class KeepAliveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var battery: BroadcastReceiver? = null

    private val tick = object : Runnable {
        override fun run() {
            val ctx = applicationContext
            if (System.currentTimeMillis() - Prefs(ctx).lastReport > 14 * 60_000L) {
                Thread {
                    try {
                        ReportJobService.work(ctx)
                    } catch (e: Exception) {
                        CrashLog.save(ctx, e, "reporte desde el aviso fijo")
                    }
                }.start()
            }
            handler.postDelayed(this, 15 * 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!goForeground()) {
            stopSelf()
            return
        }
        running = true
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (level < 0 || scale <= 0) return
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                BatteryCoach.check(c.applicationContext, level * 100 / scale, charging)
            }
        }
        try {
            registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            battery = r
        } catch (e: Exception) { }
        handler.postDelayed(tick, 60_000L)
    }

    private fun goForeground(): Boolean = try {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ayuda está activa", NotificationManager.IMPORTANCE_MIN).also {
                it.setShowBadge(false)
            }
        )
        val p = Prefs(this)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Ayuda está activa")
            .setContentText("${p.helperName} puede ayudarte. Toca para abrir.")
            .setOngoing(true)
            .setContentIntent(Notif.openApp(this, 90))
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, n)
        true
    } catch (e: Exception) {
        CrashLog.save(applicationContext, e, "aviso fijo")
        false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        battery?.let { try { unregisterReceiver(it) } catch (e: Exception) { } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "activa"
        const val ID = 4343

        /** true mientras el aviso fijo está activo (se manda al panel). */
        @Volatile
        var running = false

        /** Solo en el celular de los padres. Android no deja iniciarlo desde segundo plano: se ignora el error. */
        fun start(ctx: Context) {
            if (Prefs(ctx).role != "padres") return
            try {
                ctx.startForegroundService(Intent(ctx, KeepAliveService::class.java))
            } catch (e: Exception) { }
        }
    }
}

/** Recordatorios de batería en voz alta: al 30 %, al 15 % y antes de dormir. */
object BatteryCoach {

    private fun canTalk(ctx: Context): Boolean {
        val h = LocalTime.now().hour
        if (h < 8 || h >= 22) return false
        return try {
            ctx.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL
        } catch (e: Exception) {
            false
        }
    }

    private fun remind(ctx: Context, msg: String) {
        Notif.show(ctx, Ids.BATERIA, Notif.AVISOS, "🔋 Pon a cargar el celular", msg)
        if (canTalk(ctx)) Speaker.say(ctx, msg)
    }

    fun check(ctx: Context, pct: Int, charging: Boolean) {
        val p = Prefs(ctx)
        if (p.role != "padres") return
        if (charging) {
            if (p.batteryStage != 0) {
                p.batteryStage = 0
                Notif.cancel(ctx, Ids.BATERIA)
            }
            return
        }
        when {
            pct <= 15 && p.batteryStage < 2 -> {
                p.batteryStage = 2
                remind(ctx, "Te queda $pct por ciento de batería. Ponlo a cargar ahora, o se va a apagar.")
            }
            pct <= 30 && p.batteryStage < 1 -> {
                p.batteryStage = 1
                remind(ctx, "Te queda $pct por ciento de batería. Ponlo a cargar cuando puedas.")
            }
        }
    }

    /** A las 9 de la noche: si no está cargando y le queda poco, recordar cargarlo antes de dormir. */
    fun night(ctx: Context) {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (bm.isCharging || pct >= 70) return
        val msg = "Antes de dormir, pon el celular a cargar. Le queda $pct por ciento."
        Notif.show(ctx, Ids.BATERIA, Notif.AVISOS, "🔋 Antes de dormir", msg)
        if (canTalk(ctx)) Speaker.say(ctx, msg)
    }
}
