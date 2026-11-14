package com.manuel.ayudapadres

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Todo lo que la app guarda en el celular. */
class Prefs(context: Context) {
    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("ayuda", Context.MODE_PRIVATE)

    private fun str(k: String, d: String = ""): String = p.getString(k, d) ?: d
    private fun putStr(k: String, v: String) = p.edit().putString(k, v).apply()
    private fun bool(k: String, d: Boolean = false): Boolean = p.getBoolean(k, d)
    private fun putBool(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()
    private fun strSet(k: String): Set<String> = p.getStringSet(k, emptySet())?.toSet() ?: emptySet()
    private fun putSet(k: String, v: Set<String>) = p.edit().putStringSet(k, HashSet(v)).apply()

    /** "padres" en el celular de mamá o papá, "manuel" en el celular de quien ayuda. */
    var role: String
        get() = str("role")
        set(v) = putStr("role", v)

    /** Cómo saluda la app: "mamá", "papá"... */
    var parentName: String
        get() = str("parentName", "mamá")
        set(v) = putStr("parentName", v)

    /** Nombre de quien ayuda (aparece en los botones). */
    var helperName: String
        get() = str("helperName", "Manuel")
        set(v) = putStr("helperName", v)

    var helperPhone: String
        get() = str("helperPhone").ifBlank { BuildConfig.HELPER_PHONE }
        set(v) = putStr("helperPhone", v)

    /** Código secreto que une los celulares de la familia en Supabase. */
    var familyCode: String
        get() = str("familyCode")
        set(v) = putStr("familyCode", v.trim().uppercase())

    /**
     * PIN de configuración. Se guarda solo su huella (SHA-256 con sal), nunca el número:
     * aunque alguien copie los datos del celular, no ve el PIN.
     * Leerlo devuelve la huella (o "" si no hay); para comprobarlo usar pinMatches().
     */
    var pin: String
        get() = str("pin")
        set(v) = putStr("pin", if (v.isEmpty()) "" else "h:" + hashPin(v))

    fun pinMatches(input: String): Boolean {
        val stored = str("pin")
        if (stored.isEmpty()) return false
        if (!stored.startsWith("h:")) {
            // PIN guardado por una versión anterior, sin huella: se compara y se convierte.
            val ok = stored == input
            if (ok) pin = input
            return ok
        }
        return stored == "h:" + hashPin(input)
    }

    private fun hashPin(v: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(("ayuda|" + deviceId + "|" + v).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    val deviceId: String
        get() {
            var id = p.getString("deviceId", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                putStr("deviceId", id)
            }
            return id
        }

    var hiddenCats: Set<String>
        get() = strSet("hiddenCats")
        set(v) = putSet("hiddenCats", v)

    var approvedApps: Set<String>
        get() = strSet("approvedApps")
        set(v) = putSet("approvedApps", v)

    var knownApps: Set<String>
        get() = strSet("knownApps")
        set(v) = putSet("knownApps", v)

    var launcherMode: Boolean
        get() = bool("launcherMode")
        set(v) = putBool("launcherMode", v)

    var practice: Boolean
        get() = bool("practice")
        set(v) = putBool("practice", v)

    var voice: Boolean
        get() = bool("voice", true)
        set(v) = putBool("voice", v)

    var tutorialDone: Boolean
        get() = bool("tutorialDone")
        set(v) = putBool("tutorialDone", v)

    var lastEstoyBien: Long
        get() = p.getLong("lastEstoyBien", 0L)
        set(v) = p.edit().putLong("lastEstoyBien", v).apply()

    var lastUpdateTry: Long
        get() = p.getLong("lastUpdateTry", 0L)
        set(v) = p.edit().putLong("lastUpdateTry", v).apply()

    var lastReport: Long
        get() = p.getLong("lastReport", 0L)
        set(v) = p.edit().putLong("lastReport", v).apply()

    var lastBatteryAlert: Long
        get() = p.getLong("lastBatteryAlert", 0L)
        set(v) = p.edit().putLong("lastBatteryAlert", v).apply()

    /** Para el celular de Manuel: último aviso ya notificado. */
    var lastAlertId: Long
        get() = p.getLong("lastAlertId", 0L)
        set(v) = p.edit().putLong("lastAlertId", v).apply()

    /** Para el celular de Manuel: dispositivos ya avisados hoy por no tocar "Estoy bien". */
    var estoyBienNotified: String
        get() = str("estoyBienNotified")
        set(v) = putStr("estoyBienNotified", v)

    /** Clave interna: solo los avisos de esta misma app pueden pedirle acciones a la pantalla principal. */
    val intentToken: String
        get() {
            var t = p.getString("intentToken", null)
            if (t == null) {
                t = UUID.randomUUID().toString()
                putStr("intentToken", t)
            }
            return t
        }

    var pinFails: Int
        get() = p.getInt("pinFails", 0)
        set(v) = p.edit().putInt("pinFails", v).apply()

    var pinLockUntil: Long
        get() = p.getLong("pinLockUntil", 0L)
        set(v) = p.edit().putLong("pinLockUntil", v).apply()

    /** Celular de Manuel: true cuando ya se leyó la lista de avisos al menos una vez. */
    var alertsReady: Boolean
        get() = bool("alertsReady")
        set(v) = putBool("alertsReady", v)

    var lastSecurity: String
        get() = str("lastSecurity")
        set(v) = putStr("lastSecurity", v)

    // ---------- Medicinas ----------
    data class Med(val id: Int, val name: String, val hour: Int, val minute: Int)

    var meds: List<Med>
        get() {
            val out = ArrayList<Med>()
            try {
                val arr = JSONArray(str("meds", "[]"))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(Med(o.getInt("id"), o.getString("name"), o.getInt("hour"), o.getInt("minute")))
                }
            } catch (e: Exception) { }
            return out.sortedWith(compareBy({ it.hour }, { it.minute }))
        }
        set(v) {
            val arr = JSONArray()
            v.forEach {
                arr.put(JSONObject().put("id", it.id).put("name", it.name).put("hour", it.hour).put("minute", it.minute))
            }
            putStr("meds", arr.toString())
        }

    fun nextMedId(): Int = (meds.maxOfOrNull { it.id } ?: 0) + 1

    // ---------- Contactos con foto ----------
    data class Person(val name: String, val phone: String, val photo: String)

    var people: List<Person>
        get() {
            val out = ArrayList<Person>()
            try {
                val arr = JSONArray(str("people", "[]"))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    out.add(Person(o.getString("name"), o.getString("phone"), o.optString("photo", "")))
                }
            } catch (e: Exception) { }
            return out
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(JSONObject().put("name", it.name).put("phone", it.phone).put("photo", it.photo)) }
            putStr("people", arr.toString())
        }
}
