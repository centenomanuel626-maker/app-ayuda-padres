package com.manuel.ayudapadres

import android.content.Context
import android.net.ConnectivityManager

/** Protección contra anuncios, ventanas y avisos de páginas web. */
object WebShield {
    const val DNS = "dns.adguard-dns.com"

    val browsers = listOf(
        "com.android.chrome" to "Chrome",
        "com.sec.android.app.sbrowser" to "Samsung Internet",
        "com.mi.globalbrowser" to "Navegador Mi",
        "com.android.browser" to "Navegador",
        "org.mozilla.firefox" to "Firefox",
        "com.opera.browser" to "Opera",
        "com.opera.mini.native" to "Opera Mini",
        "com.microsoft.emmx" to "Edge",
        "com.brave.browser" to "Brave",
        "com.UCMobile.intl" to "UC Browser"
    )

    /** true si el DNS privado que bloquea anuncios está activo en la conexión actual. */
    fun adBlockActive(ctx: Context): Boolean = if (android.os.Build.VERSION.SDK_INT < 28) false else try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val lp = cm.getLinkProperties(cm.activeNetwork)
        lp != null && lp.isPrivateDnsActive && (lp.privateDnsServerName ?: "").contains("adguard")
    } catch (e: Exception) {
        false
    }

    fun installedBrowsers(ctx: Context): List<Pair<String, String>> = browsers.filter { (pkg, _) ->
        try {
            ctx.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
