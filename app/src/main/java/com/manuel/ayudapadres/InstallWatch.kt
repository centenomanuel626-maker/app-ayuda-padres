package com.manuel.ayudapadres

import android.content.Context
import android.content.Intent

/**
 * Vigila las apps nuevas: si aparece una que no estaba, pregunta "¿Tú instalaste esto?"
 * y le avisa a Manuel.
 */
object InstallWatch {

    /** Paquetes de las apps que aparecen en el menú del celular (las que tienen ícono). */
    fun launcherApps(ctx: Context): Map<String, String> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val out = HashMap<String, String>()
        for (ri in pm.queryIntentActivities(i, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName) continue
            out[pkg] = ri.loadLabel(pm).toString()
        }
        return out
    }

    @Synchronized
    fun check(ctx: Context) {
        val p = Prefs(ctx)
        if (p.role != "padres") return
        val current = launcherApps(ctx)
        val known = p.knownApps
        if (known.isEmpty()) {
            // Primera vez: todo lo que ya está instalado se considera conocido.
            p.knownApps = current.keys
            return
        }
        // Herramientas de la familia que se instalan durante la configuración: no son sospechosas.
        val familyTools = setOf("com.teamviewer.quicksupport.market", "com.whatsapp", "com.whatsapp.w4b")
        val nuevas = current.keys - known - p.approvedApps - familyTools
        p.knownApps = current.keys
        for (pkg in nuevas) {
            val label = current[pkg] ?: pkg
            val keep = Notif.pendingBroadcast(
                ctx, Ids.newApp(pkg),
                Intent(ctx, AlarmReceiver::class.java).setAction(Alarms.ACTION_APP_OK)
                    .putExtra("pkg", pkg).putExtra("label", label)
            )
            val remove = Notif.pendingActivity(
                ctx, Ids.newApp(pkg) + 1,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("uninstall", pkg).putExtra("token", p.intentToken)
            )
            Notif.show(
                ctx, Ids.newApp(pkg), Notif.URGENTE, "¿Tú instalaste “$label”?",
                "Apareció una app nueva en tu celular. Si no la instalaste tú, o te la mandaron por mensaje, quítala y avísale a ${p.helperName}.",
                tap = remove,
                actions = listOf("Sí, fui yo" to keep, "No, quitarla" to remove)
            )
            Cloud.alert(ctx, "warn", "Se instaló “$label”. Le preguntamos si la instaló.")
        }
    }
}
