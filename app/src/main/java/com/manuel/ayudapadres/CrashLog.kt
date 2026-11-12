package com.manuel.ayudapadres

import android.app.Application
import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Arranca antes que todo: deja instalado el registro de errores. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

/**
 * Registro de errores: si la app se cierra por un fallo, guarda el detalle en el celular
 * y lo manda a Supabase (tabla app_errors) la próxima vez que haya internet.
 * Así los fallos se pueden corregir aunque nadie los vea en pantalla.
 */
object CrashLog {
    private const val PREFS = "errores"
    private const val KEY = "pendientes"

    fun install(app: Context) {
        val ctx = app.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                save(ctx, e, "cierre inesperado (" + thread.name + ")", sync = true)
            } catch (x: Throwable) { }
            previous?.uncaughtException(thread, e)
        }
        // Lo que quedó guardado de la vez anterior se manda ahora, en segundo plano.
        Thread { flush(ctx) }.start()
    }

    /** Guarda un error. sync=true escribe en el acto (para cuando la app se está cerrando). */
    @Synchronized
    fun save(ctx: Context, e: Throwable, place: String, sync: Boolean = false) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = try { JSONArray(sp.getString(KEY, "[]")) } catch (x: Exception) { JSONArray() }
        val stack = e.stackTraceToString().take(4000)
        arr.put(
            JSONObject()
                .put("place", place)
                .put("message", (e.javaClass.simpleName + ": " + (e.message ?: "")).take(500))
                .put("stack", stack)
                .put("happened_at", java.time.Instant.now().toString())
        )
        // Solo se guardan los 20 más recientes.
        val trimmed = JSONArray()
        val start = maxOf(0, arr.length() - 20)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        val editor = sp.edit().putString(KEY, trimmed.toString())
        if (sync) editor.commit() else editor.apply()
    }

    fun pendingCount(ctx: Context): Int = try {
        JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")).length()
    } catch (e: Exception) {
        0
    }

    /** Manda los errores guardados. Llamar fuera del hilo principal. */
    @Synchronized
    fun flush(ctx: Context) {
        if (!Cloud.configured(ctx)) return
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = try { JSONArray(sp.getString(KEY, "[]")) } catch (x: Exception) { return }
        if (arr.length() == 0) return
        val p = Prefs(ctx)
        val rows = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            rows.put(
                JSONObject()
                    .put("family_code", p.familyCode)
                    .put("device_id", p.deviceId)
                    .put("role", p.role)
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("model", Build.MANUFACTURER + " " + Build.MODEL)
                    .put("android", Build.VERSION.RELEASE)
                    .put("place", o.optString("place"))
                    .put("message", o.optString("message"))
                    .put("stack", o.optString("stack"))
                    .put("happened_at", o.optString("happened_at"))
            )
        }
        if (Cloud.sendErrors(ctx, rows)) sp.edit().putString(KEY, "[]").apply()
    }
}
