package com.manuel.ayudapadres

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Busca la ubicación actual; si tarda, usa la última conocida. Llama a cb en el hilo principal. */
object Locate {
    fun get(ctx: Context, cb: (Location?) -> Unit) {
        val granted = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            cb(null)
            return
        }
        val lm = ctx.getSystemService(LocationManager::class.java)
        var best: Location? = null
        try {
            for (prov in lm.getProviders(true)) {
                val l = lm.getLastKnownLocation(prov) ?: continue
                val b = best
                if (b == null || l.time > b.time) best = l
            }
        } catch (e: SecurityException) { }

        val last = best
        val fresh = last != null && System.currentTimeMillis() - last.time < 5 * 60 * 1000
        if (fresh || Build.VERSION.SDK_INT < 30) {
            cb(last)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var done = false
        val finish: (Location?) -> Unit = { l ->
            if (!done) {
                done = true
                cb(l ?: last)
            }
        }
        handler.postDelayed({ finish(null) }, 8000)
        try {
            val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER
                           else LocationManager.NETWORK_PROVIDER
            lm.getCurrentLocation(provider, null, ctx.mainExecutor) { l -> handler.post { finish(l) } }
        } catch (e: Exception) {
            finish(null)
        }
    }
}
