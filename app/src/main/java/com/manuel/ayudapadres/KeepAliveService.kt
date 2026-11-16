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
 * Aviso fijo que mantiene viva la app.
 * - En el celular de los padres ("Ayuda está activa"): Xiaomi/HyperOS no la duerme; reportes, medicinas,
 *   batería, avisos de Manuel, ubicación y apps basura recién instaladas funcionan al momento.
 * - En el celular de Manuel ("Panel activo"): revisa los avisos cada minuto, así una emergencia
 *   le llega casi al instante, y le manda el resumen de la mañana.
 */
class KeepAliveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val receivers = ArrayList<BroadcastReceiver>()
    private var role = "padres"

    private fun bg(what: String, block: (Context) -> Unit) {
        val ctx = applicationContext
        Thread {
            try {
                block(ctx)
            } catch (e: Exception) {
                CrashLog.save(ctx, e, what)
            }
        }.start()
    }

    /** Cada 15 minutos (padres): el reporte, si el sistema atrasó el trabajo programado. */
    private val tick = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() - Prefs(applicationContext).lastReport > 14 * 60_000L) {
                bg("reporte desde el aviso fijo") { ReportJobService.work(it) }
            }
            handler.postDelayed(this, 15 * 60_000L)
        }
    }

    /** Cada 2 minutos (padres): ¿Manuel pidió la ubicación o mandó un aviso? */
    private val quick = object : Runnable {
        override fun run() {
            bg("consulta rápida") {
                LocateRequest.check(it)
                MessageCheck.check(it)
            }
            handler.postDelayed(this, 2 * 60_000L)
        }
    }

    /** Cada minuto (Manuel): avisos nuevos y resumen de la mañana. */
    private val panel = object : Runnable {
        override fun run() {
            bg("panel activo") { ReportJobService.helperWork(it, Prefs(it)) }
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        role = Prefs(this).role
        if (!goForeground()) {
            stopSelf()
            return
        }
        running = true
        if (role == "manuel") {
            handler.postDelayed(panel, 5_000L)
            return
        }
        listen(IntentFilter(Intent.ACTION_BATTERY_CHANGED)) { c, i ->
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level >= 0 && scale > 0) {
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                BatteryCoach.check(c.applicationContext, level * 100 / scale, charging)
            }
        }
        // App recién instalada: se revisa al momento (escudo contra apps basura).
        listen(IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }) { c, i ->
            if (!i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) bg("app nueva") { InstallWatch.check(it) }
        }
        // Se apaga: se anota la hora y la batería para contárselo a Manuel al prender.
        listen(IntentFilter(Intent.ACTION_SHUTDOWN)) { c, _ ->
            val bm = c.getSystemService(BatteryManager::class.java)
            Prefs(c).lastShutdown = "${System.currentTimeMillis()}|${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}"
        }
        handler.postDelayed(tick, 60_000L)
        handler.postDelayed(quick, 30_000L)
    }

    private fun listen(filter: IntentFilter, cb: (Context, Intent) -> Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = cb(c, i)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, filter, Context.RECEIVER_EXPORTED) else registerReceiver(r, filter)
            receivers.add(r)
        } catch (e: Exception) { }
    }

    private fun goForeground(): Boolean = try {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ayuda está activa", NotificationManager.IMPORTANCE_MIN).also {
                it.setShowBadge(false)
            }
        )
        val p = Prefs(this)
        val (title, body) = if (role == "manuel")
            "Panel activo" to "Te aviso al momento si tus padres necesitan algo."
        else
            "Ayuda está activa" to "${p.helperName} puede ayudarte. Toca para abrir."
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setContentIntent(
                if (role == "manuel") Notif.pendingActivity(this, 91, Intent(this, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else Notif.openApp(this, 90)
            )
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
        for (r in receivers) try { unregisterReceiver(r) } catch (e: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "activa"
        const val ID = 4343

        /** true mientras el aviso fijo está activo (se manda al panel). */
        @Volatile
        var running = false

        /** Padres y Manuel. Android no deja iniciarlo desde segundo plano: se ignora el error. */
        fun start(ctx: Context) {
            val r = Prefs(ctx).role
            if (r != "padres" && r != "manuel") return
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
