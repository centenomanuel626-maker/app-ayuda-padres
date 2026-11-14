package com.manuel.ayudapadres

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualización a distancia, sin mandar enlaces por mensaje:
 * 1. Busca la última versión publicada en el GitHub de Manuel.
 * 2. La descarga y comprueba que sea esta misma app, más nueva y firmada con la misma llave.
 * 3. La instala. En Android 12+ puede hacerlo sin preguntar, cuando la app ya se actualizó
 *    a sí misma alguna vez; si no, el celular muestra "¿Actualizar esta app?" y basta tocar "Actualizar".
 */
object Updater {
    private const val REPO = "centenomanuel626-maker/app-ayuda-padres"
    const val ACTION_STATUS = "com.manuel.ayudapadres.UPDATE_STATUS"
    private const val NOTIF_ID = 6000

    val current: Int get() = BuildConfig.VERSION_CODE

    private fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val code = c.responseCode
        val text = if (code in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
        c.disconnect()
        text
    } catch (e: Exception) {
        null
    }

    /** Número de la última versión publicada (la etiqueta v1.0.N), o null si no hay conexión. */
    fun latest(): Int? {
        val txt = get("https://api.github.com/repos/$REPO/releases/latest") ?: return null
        return try {
            JSONObject(txt).getString("tag_name").substringAfterLast('.').toInt()
        } catch (e: Exception) {
            null
        }
    }

    /** Descarga la versión indicada. Llamar fuera del hilo principal. */
    fun download(ctx: Context, code: Int): File? {
        val dir = File(ctx.cacheDir, "updates")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "app-$code.apk")
        return try {
            var url = URL("https://github.com/$REPO/releases/download/v1.0.$code/app-ayuda-padres.apk")
            // GitHub redirige al servidor de archivos: se sigue a mano para no depender del sistema.
            repeat(5) {
                val c = url.openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 15000
                c.readTimeout = 60000
                val status = c.responseCode
                if (status in 300..399) {
                    val next = c.getHeaderField("Location") ?: return null
                    c.disconnect()
                    if (!next.startsWith("https://")) return null
                    url = URL(next)
                } else if (status in 200..299) {
                    c.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                    c.disconnect()
                    return if (out.length() > 100_000) out else null
                } else {
                    c.disconnect()
                    return null
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Comprueba que el archivo sea esta app, más nueva y firmada con la misma llave. */
    @Suppress("DEPRECATION")
    fun verify(ctx: Context, apk: File): Boolean {
        val pm = ctx.packageManager
        return try {
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val info: PackageInfo = pm.getPackageArchiveInfo(apk.path, flags) ?: return false
            if (info.packageName != ctx.packageName) return false
            val newCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            if (newCode <= current) return false
            val mine = pm.getPackageInfo(ctx.packageName, flags)
            if (Build.VERSION.SDK_INT >= 28) {
                val a = info.signingInfo?.apkContentsSigners ?: return false
                val b = mine.signingInfo?.apkContentsSigners ?: return false
                a.isNotEmpty() && a.map { it.toCharsString() }.toSet() == b.map { it.toCharsString() }.toSet()
            } else {
                val a = info.signatures ?: return false
                val b = mine.signatures ?: return false
                a.map { it.toCharsString() }.toSet() == b.map { it.toCharsString() }.toSet()
            }
        } catch (e: Exception) {
            false
        }
    }

    /** ¿Puede instalarse sin que nadie toque nada? (Android 12+ y la app ya se instaló a sí misma). */
    @Suppress("DEPRECATION")
    fun canSilent(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        return try {
            ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName == ctx.packageName
        } catch (e: Exception) {
            false
        }
    }

    /** ¿Tiene permiso para instalar? ("Instalar apps desconocidas" activado para esta app) */
    fun allowedToInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Instala el archivo ya verificado. El resultado llega a UpdateReceiver. */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("app.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        else PendingIntent.FLAG_UPDATE_CURRENT
            val pending = PendingIntent.getBroadcast(
                ctx, 7700, Intent(ctx, UpdateReceiver::class.java).setAction(ACTION_STATUS), flags
            )
            session.commit(pending.intentSender)
        }
    }

    /**
     * Actualizar ahora, con la persona mirando. say() muestra y lee cada mensaje.
     * Se usa desde el celular de los padres y desde el panel de Manuel.
     */
    fun interactive(a: android.app.Activity, say: (String, String) -> Unit) {
        val ctx = a.applicationContext
        say("Buscando la versión nueva…", "info")
        Thread {
            val ui = { msg: String, kind: String -> a.runOnUiThread { say(msg, kind) } }
            val latest = latest()
            if (latest == null) {
                ui("No pude revisar: no hay internet.", "warn")
                return@Thread
            }
            if (latest <= current) {
                ui("Ya tienes la versión más nueva (1.0.$current).", "ok")
                return@Thread
            }
            if (!allowedToInstall(ctx)) {
                a.runOnUiThread {
                    say("Falta un permiso: prende “Permitir de esta fuente”, vuelve y toca Actualizar otra vez.", "warn")
                    try {
                        a.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            android.net.Uri.parse("package:" + ctx.packageName)))
                    } catch (e: Exception) { }
                }
                return@Thread
            }
            ui("Descargando la versión 1.0.$latest…", "info")
            val apk = download(ctx, latest)
            if (apk == null || !verify(ctx, apk)) {
                ui("No pude descargar la versión nueva. Intenta más tarde.", "warn")
                return@Thread
            }
            ui("Si aparece “¿Actualizar esta app?”, toca “Actualizar”.", "info")
            try {
                install(ctx, apk)
            } catch (e: Exception) {
                CrashLog.save(ctx, e, "actualizar ahora")
                ui("No se pudo instalar. Intenta más tarde.", "warn")
            }
        }.start()
    }

    /**
     * Todo el proceso en segundo plano (pedido desde el panel de Manuel).
     * Si no puede instalar sin preguntar, deja un aviso para que tocando se termine.
     */
    fun runInBackground(ctx: Context, target: Int) {
        val p = Prefs(ctx)
        if (target <= current) return
        // Un intento por hora como máximo, para no gastar datos si algo falla.
        if (System.currentTimeMillis() - p.lastUpdateTry < 60 * 60 * 1000L) return
        p.lastUpdateTry = System.currentTimeMillis()
        val apk = download(ctx, target)
        if (apk == null || !verify(ctx, apk)) {
            Cloud.alert(ctx, "warn", "No se pudo descargar o verificar la versión 1.0.$target.")
            return
        }
        if (canSilent(ctx) && allowedToInstall(ctx)) {
            try {
                install(ctx, apk)
                return
            } catch (e: Exception) {
                CrashLog.save(ctx, e, "actualización en segundo plano")
            }
        }
        // Hace falta un toque: aviso que abre la app y termina la actualización.
        val open = Notif.pendingActivity(
            ctx, NOTIF_ID,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("run", "update").putExtra("token", p.intentToken)
        )
        Notif.show(ctx, NOTIF_ID, Notif.AVISOS, "Hay una versión nueva de la app",
            "${p.helperName} la mandó. Toca aquí y después toca “Actualizar”.", tap = open,
            actions = listOf("Actualizar" to open))
    }
}

/** Recibe el resultado de la instalación. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Updater.ACTION_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // El sistema pide confirmar: se muestra la pantalla "¿Actualizar esta app?".
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(confirm)
                } catch (e: Exception) {
                    Notif.show(ctx, 6001, Notif.AVISOS, "Falta confirmar la actualización",
                        "Toca aquí y después toca “Actualizar”.",
                        tap = PendingIntent.getActivity(ctx, 6001, confirm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Notif.cancel(ctx, 6000)
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "código $status"
                val pending = goAsync()
                Thread {
                    try {
                        Cloud.alert(ctx.applicationContext, "warn", "La actualización no se instaló: $msg")
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }
}
