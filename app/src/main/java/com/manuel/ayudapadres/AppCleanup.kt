package com.manuel.ayudapadres

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject

/**
 * Orden del celular: qué apps hay, cuáles no se usan, cuáles se usan mucho
 * y si otra app se adueñó de la pantalla de inicio (los "temas" y "launchers" que llegan por anuncios).
 */
object AppCleanup {

    /** Apps que nunca se ofrecen para quitar. */
    val protectedApps = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "com.teamviewer.quicksupport.market", "com.teamviewer.host.market", "com.android.vending",
        "com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer", "com.android.contacts",
        "com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.mms",
        "com.android.settings", "com.sec.android.app.camera", "com.android.camera", "com.google.android.GoogleCamera",
        "com.sec.android.gallery3d", "com.miui.gallery", "com.google.android.apps.photos",
        "com.android.thememanager", "com.miui.securitycenter", "com.android.chrome", "com.google.android.gm",
        "com.google.android.youtube", "com.google.android.apps.maps", "com.android.deskclock", "com.google.android.deskclock",
        "com.sec.android.app.clockpackage", "com.android.camera2", "com.android.calendar", "com.samsung.android.calendar",
        "com.google.android.calendar", "com.google.android.contacts", "com.miui.home", "com.sec.android.app.launcher"
    )

    fun isXiaomi(): Boolean = Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }

    fun hasUsageAccess(ctx: Context): Boolean = try {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    fun isSystem(ctx: Context, pkg: String): Boolean = try {
        (ctx.packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: Exception) {
        false
    }

    fun installed(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun label(ctx: Context, pkg: String): String = try {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    /**
     * Si la pantalla de inicio la maneja una app instalada a mano (no la del celular ni esta app),
     * devuelve paquete y nombre. Esas apps suelen llenar el celular de anuncios y gastar batería.
     */
    fun impostorHome(ctx: Context): Pair<String, String>? = try {
        val pm = ctx.packageManager
        val ri = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = ri?.activityInfo?.packageName
        when {
            pkg == null || pkg == "android" -> null // sin elegir: sale el menú del sistema
            pkg == ctx.packageName -> null
            isSystem(ctx, pkg) -> null
            pkg in Prefs(ctx).keepApps -> null
            else -> pkg to ri.loadLabel(pm).toString()
        }
    } catch (e: Exception) {
        null
    }

    data class AppUse(
        val pkg: String, val label: String, val system: Boolean,
        /** Días sin abrirla (-1 si no se sabe). */
        val idleDays: Int,
        /** Minutos en pantalla los últimos 7 días (-1 si no se sabe). */
        val weekMinutes: Int,
        val installDays: Int,
        /** true si nunca se debe ofrecer quitarla. */
        val keep: Boolean
    )

    /** Todas las apps con ícono, con cuánto se usan. */
    fun apps(ctx: Context): List<AppUse> {
        val pm = ctx.packageManager
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val usage = hasUsageAccess(ctx)
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val long = if (usage) try { usm.queryAndAggregateUsageStats(now - 120 * day, now) } catch (e: Exception) { null } else null
        val week = if (usage) try { usm.queryAndAggregateUsageStats(now - 7 * day, now) } catch (e: Exception) { null } else null
        val p = Prefs(ctx)
        return InstallWatch.launcherApps(ctx).mapNotNull { (pkg, label) ->
            val info = try { pm.getPackageInfo(pkg, 0) } catch (e: Exception) { return@mapNotNull null }
            val last = long?.get(pkg)?.lastTimeUsed ?: 0L
            val idle = when {
                long == null -> -1
                last > 0 -> ((now - last) / day).toInt()
                else -> 999
            }
            val mins = if (week == null) -1 else ((week[pkg]?.totalTimeInForeground ?: 0L) / 60000L).toInt()
            AppUse(
                pkg, label, isSystem(ctx, pkg), idle, mins,
                ((now - info.firstInstallTime) / day).toInt(),
                pkg in protectedApps || pkg in p.approvedApps || pkg in p.keepApps
            )
        }
    }

    /** Lista compacta para el panel de Manuel (columna devices.apps). */
    fun snapshot(ctx: Context): JSONArray {
        val arr = JSONArray()
        apps(ctx).sortedWith(compareByDescending<AppUse> { it.weekMinutes }.thenByDescending { it.idleDays })
            .take(180)
            .forEach {
                arr.put(
                    JSONObject().put("p", it.pkg).put("n", it.label.take(40)).put("s", it.system)
                        .put("d", it.idleDays).put("m", it.weekMinutes).put("i", it.installDays).put("k", it.keep)
                )
            }
        // La base de datos acepta hasta 40 000 caracteres: si son demasiadas, se quitan las últimas.
        while (arr.length() > 0 && arr.toString().length > 35000) arr.remove(arr.length() - 1)
        return arr
    }

    /** Apps que no se abren hace más de 30 días y se pueden quitar. */
    fun unused(ctx: Context): List<AppUse> =
        apps(ctx).filter { !it.keep && it.installDays >= 7 && it.idleDays >= 30 }.sortedByDescending { it.idleDays }

    /** Las apps que más tiempo estuvieron en pantalla esta semana (sin contar las de la familia). */
    fun heavy(ctx: Context): List<AppUse> =
        apps(ctx).filter { it.weekMinutes >= 60 && it.pkg !in protectedApps }.sortedByDescending { it.weekMinutes }.take(3)
}

/** Apps que Manuel pidió quitar desde su panel. La persona confirma cada una (Android lo exige). */
object RemoveRequest {

    /** Las que siguen instaladas y la persona no pidió conservar. */
    fun pending(ctx: Context): List<String> {
        val p = Prefs(ctx)
        return p.removeRequest.filter { AppCleanup.installed(ctx, it) && it !in p.keepApps && it !in AppCleanup.protectedApps }
            .sortedBy { AppCleanup.label(ctx, it).lowercase() }
    }

    /** Se llama con la lista que hay en Supabase (cada 15 minutos). Hace red: fuera del hilo principal. */
    fun sync(ctx: Context, server: List<String>) {
        val p = Prefs(ctx)
        if (server.isEmpty()) {
            if (p.removeRequest.isNotEmpty()) {
                p.removeRequest = emptySet()
                Notif.cancel(ctx, Ids.QUITAR)
            }
            return
        }
        p.removeRequest = server.toSet()
        val left = pending(ctx)
        if (left.isEmpty()) {
            finish(ctx)
            return
        }
        val key = left.sorted().joinToString(",")
        if (key == p.removeNotified) return
        p.removeNotified = key
        val n = left.size
        Notif.show(
            ctx, Ids.QUITAR, Notif.AVISOS,
            "${p.helperName} te sugiere quitar $n app" + (if (n == 1) "" else "s"),
            "No las usas o gastan batería. Toca para verlas: tú decides cuáles quitar.",
            tap = Notif.pendingActivity(
                ctx, Ids.QUITAR,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("run", "pedidas").putExtra("token", p.intentToken)
            )
        )
    }

    /** Ya no queda nada pendiente: avisa a Manuel cómo terminó y borra el pedido. Hace red. */
    fun finish(ctx: Context) {
        val p = Prefs(ctx)
        val asked = p.removeRequest
        if (asked.isEmpty()) return
        val removed = asked.count { !AppCleanup.installed(ctx, it) }
        val kept = asked.filter { AppCleanup.installed(ctx, it) && it in p.keepApps }.map { AppCleanup.label(ctx, it) }
        if (!Cloud.clearRemoveApps(ctx)) return
        p.removeRequest = emptySet()
        p.removeNotified = ""
        Notif.cancel(ctx, Ids.QUITAR)
        Cloud.alert(
            ctx, "info",
            "Quitó $removed de ${asked.size} apps que le sugeriste." +
                if (kept.isNotEmpty()) " Quiso conservar: " + kept.joinToString(", ") + "." else ""
        )
        p.lastAppsReport = 0L // que la lista del panel se actualice en el próximo reporte
    }
}
