package com.manuel.ayudapadres

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Trabajo que corre solo cada 15 minutos (lo mínimo que permite Android).
 * En el celular de los padres: manda batería, conexión y modo de sonido; revisa apps nuevas;
 * lee las categorías que Manuel escondió. En el celular de Manuel: trae avisos nuevos y los muestra.
 */
class ReportJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                work(applicationContext)
            } catch (e: Exception) {
                CrashLog.save(applicationContext, e, "reporte cada 15 minutos")
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 77

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, ReportJobService::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            try {
                js.schedule(job)
            } catch (e: Exception) { }
        }

        private val running = java.util.concurrent.atomic.AtomicBoolean(false)

        fun work(ctx: Context) {
            // El trabajo programado y el aviso fijo pueden coincidir: solo uno a la vez.
            if (!running.compareAndSet(false, true)) return
            try {
                doWork(ctx)
            } finally {
                running.set(false)
            }
        }

        private fun doWork(ctx: Context) {
            val p = Prefs(ctx)
            try { CrashLog.flush(ctx) } catch (e: Exception) { }
            when (p.role) {
                "padres" -> parentWork(ctx, p)
                "manuel" -> helperWork(ctx, p)
            }
        }

        private fun parentWork(ctx: Context, p: Prefs) {
            InstallWatch.check(ctx)
            KeepAliveService.start(ctx)
            val home = AppCleanup.impostorHome(ctx)
            if (home != null && home.first != p.lastHomeAlert) {
                p.lastHomeAlert = home.first
                Notif.show(ctx, Ids.PANTALLA_INICIO, Notif.URGENTE, "Otra app cambió tu pantalla de inicio",
                    "“${home.second}” se puso como pantalla de inicio. Suele llenar el celular de anuncios y gastar batería. Toca para arreglarlo.",
                    tap = Notif.pendingActivity(ctx, Ids.PANTALLA_INICIO,
                        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra("run", "tema").putExtra("token", p.intentToken)))
                Cloud.alert(ctx, "warn", "“${home.second}” se adueñó de la pantalla de inicio (por eso el celular se ve raro y gasta batería). Le pedimos que vuelva a la normal.")
            } else if (home == null && p.lastHomeAlert.isNotEmpty()) {
                p.lastHomeAlert = ""
                Notif.cancel(ctx, Ids.PANTALLA_INICIO)
            }
            if (!Cloud.configured(ctx)) {
                val bm = ctx.getSystemService(BatteryManager::class.java)
                BatteryCoach.check(ctx, bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY), bm.isCharging)
                return
            }

            val bm = ctx.getSystemService(BatteryManager::class.java)
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val net = when {
                caps == null -> "Sin conexión"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
                else -> "Conectado"
            }
            val ringer = when (ctx.getSystemService(AudioManager::class.java).ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> "Suena"
                AudioManager.RINGER_MODE_VIBRATE -> "Vibra"
                else -> "Silencio"
            }
            val status = JSONObject().put("battery", pct).put("charging", charging).put("network", net).put("ringer", ringer)
                .put("missing", Perms.missingCritical(ctx).joinToString(", "))
                .put("adblock", WebShield.adBlockActive(ctx))
            if (p.lastSecurity.isNotEmpty()) status.put("security", p.lastSecurity)
            status.put("home_app", home?.second ?: "")
            status.put("keep_alive", KeepAliveService.running)
            if (AppCleanup.isXiaomi()) status.put("phone_setup", XiaomiSetup.summary(ctx).take(300))
            // La lista de apps (para el panel) cada 6 horas: no cambia tan seguido.
            val sendApps = System.currentTimeMillis() - p.lastAppsReport > 6 * 60 * 60 * 1000L
            if (sendApps) status.put("apps", AppCleanup.snapshot(ctx))
            if (Cloud.reportStatus(ctx, status)) {
                p.lastReport = System.currentTimeMillis()
                if (sendApps) p.lastAppsReport = System.currentTimeMillis()
            }
            BatteryCoach.check(ctx, pct, charging)

            // Batería baja: un aviso cada 3 horas como máximo.
            if (pct <= 20 && !charging && System.currentTimeMillis() - p.lastBatteryAlert > 3 * 60 * 60 * 1000L) {
                if (Cloud.alert(ctx, "warn", "Batería baja: $pct %. Recuérdale cargar el celular.")) {
                    p.lastBatteryAlert = System.currentTimeMillis()
                }
            }

            Cloud.fetchHiddenCategories(ctx)?.let { p.hiddenCats = it }

            // Código de familia nuevo pedido desde el panel de Manuel.
            val pending = Cloud.fetchPendingCode(ctx)
            if (pending != null && FamilyCode.isStrong(pending) && !pending.equals(p.familyCode, ignoreCase = true)) {
                val adopted = Cloud.adoptCode(ctx)
                if (adopted != null && adopted.equals(pending, ignoreCase = true)) {
                    p.familyCode = adopted
                    Cloud.alert(ctx, "info", "Cambió al código de familia nuevo.")
                }
            }

            // Apps que Manuel pidió quitar.
            Cloud.fetchRemoveApps(ctx)?.let { RemoveRequest.sync(ctx, it) }

            // Actualización pedida desde el panel de Manuel.
            val wanted = Cloud.fetchUpdateRequest(ctx)
            if (wanted > Updater.current) Updater.runInBackground(ctx, wanted)
        }

        private fun helperWork(ctx: Context, p: Prefs) {
            if (!Cloud.configured(ctx)) return
            val alerts = Cloud.alerts(ctx, p.lastAlertId, 20) ?: return
            var maxId = p.lastAlertId
            // Solo la primera lectura se salta los avisos viejos; si la tabla estaba vacía, desde ya se avisa todo.
            val first = !p.alertsReady
            // Vienen del más nuevo al más viejo.
            for (i in alerts.length() - 1 downTo 0) {
                val a = alerts.getJSONObject(i)
                val id = a.getLong("id")
                if (id > maxId) maxId = id
                if (first) continue // primera vez: no inundar con avisos viejos
                val kind = a.optString("kind")
                if (kind == "ok" || kind == "info") continue
                val who = a.optString("device_label")
                val title = when (kind) {
                    "emergencia" -> "🆘 EMERGENCIA: $who"
                    "bad" -> "🔴 $who"
                    else -> "🟡 $who"
                }
                val open = Notif.pendingActivity(ctx, Ids.alert(id),
                    Intent(ctx, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Notif.show(ctx, Ids.alert(id), if (kind == "warn") Notif.AVISOS else Notif.URGENTE, title, a.optString("text"), tap = open)
            }
            p.lastAlertId = maxId
            p.alertsReady = true

            // Después de las 12:00, avisar una vez si alguien no tocó "Estoy bien" hoy.
            if (LocalTime.now().hour < 12) return
            val today = LocalDate.now().toString()
            val devices = Cloud.devices(ctx) ?: return
            val notified = p.estoyBienNotified.split(',').filter { it.startsWith(today) }.toMutableSet()
            for (i in 0 until devices.length()) {
                val d = devices.getJSONObject(i)
                val key = today + "|" + d.optString("id")
                if (key in notified) continue
                val lastOk = d.optString("last_ok", "")
                val okToday = try {
                    lastOk.isNotEmpty() && lastOk != "null" &&
                        OffsetDateTime.parse(lastOk).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
                } catch (e: Exception) {
                    false
                }
                if (!okToday) {
                    val who = d.optString("label")
                    Notif.show(ctx, 300000 + i, Notif.AVISOS, "$who no ha tocado “Estoy bien” hoy",
                        "Tal vez quieras llamarle.",
                        tap = Notif.pendingActivity(ctx, 300000 + i, Intent(ctx, PanelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
                    notified.add(key)
                }
            }
            p.estoyBienNotified = notified.joinToString(",")
        }
    }
}
