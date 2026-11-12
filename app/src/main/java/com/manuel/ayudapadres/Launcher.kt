package com.manuel.ayudapadres

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast

/** Prende o apaga el modo pantalla de inicio. */
object Launcher {
    fun set(a: Activity, on: Boolean) {
        Prefs(a).launcherMode = on
        a.packageManager.setComponentEnabledSetting(
            ComponentName(a, a.packageName + ".HomeAlias"),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        Toast.makeText(
            a,
            if (on) "Elige “Ayuda” como app de inicio predeterminada" else "Elige la pantalla de inicio normal del celular",
            Toast.LENGTH_LONG
        ).show()
        try {
            a.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                a.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) { }
        }
    }
}
