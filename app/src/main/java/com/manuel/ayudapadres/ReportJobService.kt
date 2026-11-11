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
            } catch (e: Exception) { }
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

        fun work(ctx: Context) {
            val p = Prefs(ctx)
            when (p.role) {
                "padres" -> parentWork(ctx, p)
                "manuel" -> helperWork(ctx, p)
            }
        }

        private fun parentWork(ctx: Context, p: Prefs) {
            InstallWatch.check(ctx)
            if (!Cloud.configured(ctx)) return

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
            if (p.lastSecurity.isNotEmpty()) status.put("security", p.lastSecurity)
            Cloud.reportStatus(ctx, status)

            // Batería baja: un aviso cada 3 horas como máximo.
            if (pct <= 20 && !charging && System.currentTimeMillis() - p.lastBatteryAlert > 3 * 60 * 60 * 1000L) {
                if (Cloud.alert(ctx, "warn", "Batería baja: $pct %. Recuérdale cargar el celular.")) {
                    p.lastBatteryAlert = System.currentTimeMillis()
                }
            }

            Cloud.fetchHiddenCategories(ctx)?.let { p.hiddenCats = it }
        }

        private fun helperWork(ctx: Context, p: Prefs) {
            if (!Cloud.configured(ctx)) return
            val alerts = Cloud.alerts(ctx, p.lastAlertId, 20) ?: return
            var maxId = p.lastAlertId
            // Vienen del más nuevo al más viejo.
            for (i in alerts.length() - 1 downTo 0) {
                val a = alerts.getJSONObject(i)
                val id = a.getLong("id")
                if (id > maxId) maxId = id
                if (p.lastAlertId == 0L) continue // primera vez: no inundar con avisos viejos
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
