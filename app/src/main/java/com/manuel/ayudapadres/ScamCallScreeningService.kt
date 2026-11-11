package com.manuel.ayudapadres

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Alerta contra estafas: cuando llama un número que no está en los contactos,
 * muestra un aviso grande con consejos. No bloquea ninguna llamada.
 * Para que funcione, la app tiene que ser la app de "Identificador de llamadas y spam"
 * (se elige en la configuración de Manuel).
 */
class ScamCallScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        respondToCall(details, CallScreeningService.CallResponse.Builder().build())

        if (Build.VERSION.SDK_INT >= 29 && details.callDirection != Call.Details.DIRECTION_INCOMING) return
        val number = details.handle?.schemeSpecificPart ?: ""
        if (number.isNotBlank() && isKnown(number)) return

        val shown = if (number.isBlank()) "un número oculto" else number
        Notif.show(
            this, Ids.ESTAFA, Notif.ESTAFAS,
            "⚠️ Te llama $shown",
            "No está en tus contactos. Si te piden dinero, datos del banco, claves o códigos que llegan por mensaje: cuelga y llama a ${Prefs(this).helperName}."
        )
    }

    private fun isKnown(number: String): Boolean {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return true
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(uri, arrayOf("_id"), null, null, null)?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            true
        }
    }
}
