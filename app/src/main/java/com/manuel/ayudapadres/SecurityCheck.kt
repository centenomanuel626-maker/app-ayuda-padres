package com.manuel.ayudapadres

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Revisión de seguridad: busca señales de que el sistema fue alterado
 * (antes de la venta o después) o de que alguien espía el celular.
 * No cambia nada; solo mira.
 */
object SecurityCheck {
    enum class Level { OK, WARN, BAD }
    data class Item(val name: String, val level: Level, val detail: String)

    private val knownAccessibility = listOf(
        "com.google.android.marvin.talkback", "com.samsung.accessibility", "com.samsung.android.accessibility",
        "com.google.android.accessibility", "com.miui.accessibility", "com.xiaomi", "com.miui",
        "com.samsung.android.app.talkback", "com.samsung.android.bixby", "com.google.android.apps.accessibility"
    )
    private val knownAdmins = listOf(
        "com.google.android.gms", "com.samsung.android.fmm", "com.samsung.klmsagent", "com.miui", "com.xiaomi",
        "com.samsung.android.knox", "com.sec.enterprise"
    )
    private val remoteApps = mapOf(
        "com.anydesk.anydeskandroid" to "AnyDesk",
        "com.teamviewer.host.market" to "TeamViewer Host",
        "com.rsupport.mvagent" to "Mobizen",
        "com.sand.airdroid" to "AirDroid",
        "com.sand.airmirror" to "AirMirror",
        "com.splashtop.streamer.addon.knox" to "Splashtop",
        "com.realvnc.viewer.android" to "VNC",
        "com.rustdesk.rustdesk" to "RustDesk"
    )
    private val spyWords = listOf("spy", "stalk", "mspy", "flexispy", "hoverwatch", "cocospy", "spyzie", "eyezy", "xnspy", "monitor.agent")

    private fun prop(key: String): String = try {
        val proc = Runtime.getRuntime().exec(arrayOf("getprop", key))
        proc.inputStream.bufferedReader().use { it.readText() }.trim()
    } catch (e: Exception) {
        ""
    }

    private fun label(ctx: Context, pkg: String): String = try {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun installed(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun run(ctx: Context): List<Item> {
        val out = ArrayList<Item>()

        // 1. Firmware oficial
        out += if (Build.TAGS?.contains("test-keys") == true)
            Item("Sistema original", Level.BAD, "El sistema está firmado con claves de prueba: no es el oficial")
        else Item("Sistema original", Level.OK, "Firmado como sistema oficial")

        // 2. Bootloader y arranque verificado
        val boot = prop("ro.boot.verifiedbootstate")
        val flashLocked = prop("ro.boot.flash.locked")
        val warranty = prop("ro.boot.warranty_bit")
        out += when {
            warranty == "1" -> Item("Arranque protegido", Level.BAD, "Knox de Samsung marca que el sistema fue alterado")
            boot == "orange" || flashLocked == "0" -> Item("Arranque protegido", Level.BAD, "El bootloader está desbloqueado")
            boot == "yellow" -> Item("Arranque protegido", Level.WARN, "El arranque usa una clave que no es la de fábrica")
            boot == "green" -> Item("Arranque protegido", Level.OK, "El bootloader está bloqueado")
            else -> Item("Arranque protegido", Level.WARN, "No pude comprobarlo en este modelo")
        }

        // 3. Root
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su", "/vendor/bin/su", "/data/local/xbin/su", "/data/local/bin/su")
        val rootApps = listOf("com.topjohnwu.magisk", "eu.chainfire.supersu", "com.kingroot.kinguser", "io.github.huskydg.magisk")
        val rooted = suPaths.any { File(it).exists() } || rootApps.any { installed(ctx, it) }
        out += if (rooted) Item("Sistema sin modificar", Level.BAD, "Tiene root: alguien puede controlar todo el celular")
        else Item("Sistema sin modificar", Level.OK, "No hay root")

        // 4. Parche de seguridad
        out += try {
            val patch = LocalDate.parse(Build.VERSION.SECURITY_PATCH)
            val months = ChronoUnit.MONTHS.between(patch, LocalDate.now())
            when {
                months > 18 -> Item("Parches de seguridad", Level.BAD, "El último parche es de hace $months meses; ya no recibe actualizaciones")
                months > 6 -> Item("Parches de seguridad", Level.WARN, "El último parche es de hace $months meses")
                else -> Item("Parches de seguridad", Level.OK, "Al día (${Build.VERSION.SECURITY_PATCH})")
            }
        } catch (e: Exception) {
            Item("Parches de seguridad", Level.WARN, "No pude leer la fecha del parche")
        }

        // 5. Certificados instalados por usuario (permiten leer el tráfico de internet)
        out += try {
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null)
            val user = ks.aliases().toList().count { it.startsWith("user:") }
            if (user > 0) Item("Certificados", Level.BAD, "Hay $user certificado(s) instalado(s) a mano que pueden leer tu internet")
            else Item("Certificados", Level.OK, "Ninguno instalado a mano")
        } catch (e: Exception) {
            Item("Certificados", Level.WARN, "No pude revisarlos")
        }

        // 6. Administradores del dispositivo
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        val admins = dpm.activeAdmins?.map { it.packageName }?.distinct() ?: emptyList()
        val owner = admins.firstOrNull { dpm.isDeviceOwnerApp(it) }
        val unknownAdmins = admins.filter { a -> a != ctx.packageName && knownAdmins.none { a.startsWith(it) } }
        out += when {
            owner != null -> Item("Nadie administra el celular", Level.BAD, "Lo administra: ${label(ctx, owner)}")
            unknownAdmins.isNotEmpty() -> Item("Nadie administra el celular", Level.WARN,
                "Apps con permiso de administrador: " + unknownAdmins.joinToString { label(ctx, it) })
            else -> Item("Nadie administra el celular", Level.OK, "Sin perfiles de empresa ni administradores extraños")
        }

        // 7. VPN activa
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val vpn = cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        out += if (vpn) Item("VPN", Level.WARN, "Hay una VPN activa. Si no la pusiste tú, puede desviar tu internet")
        else Item("VPN", Level.OK, "Ninguna VPN activa")

        // 8. Apps que leen la pantalla o las notificaciones
        val acc = (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .split(':').filter { it.isNotBlank() }.map { it.substringBefore('/') }.distinct()
        val listeners = try {
            (Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "")
                .split(':').filter { it.isNotBlank() }.map { it.substringBefore('/') }.distinct()
        } catch (e: Exception) {
            emptyList()
        }
        val readers = (acc + listeners).distinct().filter { r ->
            r != ctx.packageName && knownAccessibility.none { r.startsWith(it) } &&
                !r.startsWith("com.teamviewer") && !r.startsWith("com.google.android") && !r.startsWith("com.samsung.android")
        }
        out += if (readers.isNotEmpty()) Item("Apps que leen la pantalla", Level.WARN,
            "Pueden ver lo que escribes o tus mensajes: " + readers.joinToString { label(ctx, it) })
        else Item("Apps que leen la pantalla", Level.OK, "Ninguna desconocida")

        // 9. Control remoto y apps espía
        val remotes = remoteApps.filterKeys { installed(ctx, it) }.values.toList()
        val spies = InstallWatch.launcherApps(ctx).keys.filter { pkg -> spyWords.any { pkg.lowercase().contains(it) } }
            .map { label(ctx, it) }
        val family = installed(ctx, "com.teamviewer.quicksupport.market")
        out += when {
            spies.isNotEmpty() -> Item("Control remoto", Level.BAD, "Parece una app espía: " + spies.joinToString())
            remotes.isNotEmpty() -> Item("Control remoto", Level.WARN, "Apps para controlar el celular a distancia: " + remotes.joinToString())
            family -> Item("Control remoto", Level.OK, "Solo TeamViewer QuickSupport, para la ayuda de la familia")
            else -> Item("Control remoto", Level.OK, "Ninguna app de control remoto")
        }

        // 10. Depuración USB
        val adb = Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        out += if (adb) Item("Depuración USB", Level.WARN, "Está prendida y no hace falta")
        else Item("Depuración USB", Level.OK, "Apagada")

        return out
    }

    /** "verde", "amarillo" o "rojo". */
    fun overall(items: List<Item>): String = when {
        items.any { it.level == Level.BAD } -> "rojo"
        items.any { it.level == Level.WARN } -> "amarillo"
        else -> "verde"
    }
}
