package com.manuel.ayudapadres

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/** Permisos que Manuel da una sola vez al configurar el celular. */
object Perms {
    const val REQ_RUNTIME = 20
    const val REQ_ROLE = 21

    /** granted: true / false, o null si no se puede comprobar (hay que revisarlo a mano). */
    data class Item(val name: String, val why: String, val granted: (Activity) -> Boolean?, val request: (Activity) -> Unit)

    private fun runtime(perm: String, name: String, why: String) = Item(name, why,
        { a -> a.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED },
        { a -> a.requestPermissions(arrayOf(perm), REQ_RUNTIME) })

    private fun settings(a: Activity, vararg intents: Intent) {
        for (i in intents) {
            try {
                a.startActivity(i)
                return
            } catch (e: Exception) { }
        }
    }

    private fun pkgUri(a: Activity) = Uri.parse("package:" + a.packageName)

    fun usageGranted(a: Activity): Boolean {
        val ops = a.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), a.packageName)
        else
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), a.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Permisos sin los que la emergencia o los avisos fallan. Se reportan al panel de Manuel. */
    fun missingCritical(ctx: android.content.Context): List<String> {
        val out = ArrayList<String>()
        fun has(perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION) && !has(Manifest.permission.ACCESS_COARSE_LOCATION)) out += "Ubicación"
        else if (Build.VERSION.SDK_INT >= 28 &&
            !ctx.getSystemService(android.location.LocationManager::class.java).isLocationEnabled) out += "GPS apagado"
        if (!has(Manifest.permission.CALL_PHONE)) out += "Llamadas"
        if (!has(Manifest.permission.READ_CONTACTS)) out += "Contactos (llamadas por WhatsApp)"
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) out += "Notificaciones"
        if (!ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)) out += "Segundo plano"
        return out
    }

    fun list(a: Activity, role: String): List<Item> {
        val out = ArrayList<Item>()
        if (Build.VERSION.SDK_INT >= 33) {
            out += runtime(Manifest.permission.POST_NOTIFICATIONS, "Notificaciones", "Avisos de medicinas, apps nuevas y emergencias")
        }
        out += Item("Funcionar en segundo plano", "Para que los avisos lleguen aunque no abran la app",
            { x -> x.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(x.packageName) },
            { x -> settings(x, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(x)),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
        if (Build.MANUFACTURER.lowercase().contains("xiaomi") || Build.MANUFACTURER.lowercase().contains("redmi")) {
            out += Item("Inicio automático (Xiaomi)", "Xiaomi cierra las apps en segundo plano; actívalo para esta app",
                { _ -> null },
                { x -> settings(x,
                    Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(x))) })
        }
        if (role != "padres") return out

        out += runtime(Manifest.permission.CALL_PHONE, "Hacer llamadas", "Para llamar con un solo toque")
        out += runtime(Manifest.permission.ACCESS_FINE_LOCATION, "Ubicación", "Para el botón de Emergencia")
        out += runtime(Manifest.permission.READ_CONTACTS, "Contactos", "Para llamar gratis por WhatsApp, la alerta de estafas y “Mi familia”")
        out += runtime(Manifest.permission.CAMERA, "Cámara", "Para la lupa")
        out += Item("Modificar ajustes del sistema", "Brillo, letra, girar pantalla y tiempo de pantalla",
            { x -> Settings.System.canWrite(x) },
            { x -> settings(x, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri(x))) })
        out += Item("Acceso a “No molestar”", "Para poner el celular en silencio",
            { x -> x.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted },
            { x -> settings(x, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) })
        out += Item("Acceso a datos de uso", "Para saber qué apps no usan",
            { x -> usageGranted(x) },
            { x -> settings(x, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        if (Build.VERSION.SDK_INT >= 31) {
            out += Item("Alarmas exactas", "Para que las medicinas suenen a la hora justa",
                { x -> x.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() },
                { x -> settings(x, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(x))) })
        }
        if (Build.VERSION.SDK_INT >= 34) {
            out += Item("Pantalla completa", "Para que la alarma de medicinas se vea aunque esté bloqueado",
                { x -> x.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() },
                { x -> settings(x, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri(x))) })
        }
        if (Build.VERSION.SDK_INT >= 29) {
            out += Item("Alerta contra estafas", "Elegir esta app como “Identificador de llamadas y spam”",
                { x -> x.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING) },
                { x ->
                    val rm = x.getSystemService(RoleManager::class.java)
                    try {
                        x.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE)
                    } catch (e: Exception) { }
                })
        }
        out += Item("Actualizar la app a distancia", "Permite que esta app (y solo ella) instale sus versiones nuevas",
            { x -> x.packageManager.canRequestPackageInstalls() },
            { x -> settings(x, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri(x))) })
        out += Item("TeamViewer QuickSupport instalado", "Para la ayuda a distancia",
            { x -> x.packageManager.getLaunchIntentForPackage("com.teamviewer.quicksupport.market") != null },
            { x -> settings(x,
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.teamviewer.quicksupport.market")),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.teamviewer.quicksupport.market"))) })
        return out
    }
}
