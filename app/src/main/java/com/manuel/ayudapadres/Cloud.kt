package com.manuel.ayudapadres

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Conexión con Supabase por su API REST. Cada llamada lleva el código de familia
 * en la cabecera x-family-code; las reglas de seguridad de la base de datos solo
 * dejan ver y escribir las filas de esa familia.
 * Todas las funciones hacen red: llamarlas siempre fuera del hilo principal.
 */
object Cloud {

    fun configured(ctx: Context): Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank() &&
            Prefs(ctx).familyCode.length >= 8

    private fun request(ctx: Context, method: String, path: String, body: String? = null, prefer: String? = null): String? {
        if (!configured(ctx)) return null
        return try {
            val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/rest/v1/" + path)
            val c = url.openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            c.setRequestProperty("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
            c.setRequestProperty("x-family-code", Prefs(ctx).familyCode)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            if (prefer != null) c.setRequestProperty("Prefer", prefer)
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            if (code in 200..299) text else null
        } catch (e: Exception) {
            null
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // ---------- Celular de los padres ----------

    fun reportStatus(ctx: Context, status: JSONObject): Boolean {
        val p = Prefs(ctx)
        status.put("id", p.deviceId)
        status.put("family_code", p.familyCode)
        status.put("label", p.parentName.replaceFirstChar { it.uppercase() })
        status.put("model", Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL)
        status.put("last_seen", java.time.Instant.now().toString())
        status.put("version", BuildConfig.VERSION_NAME)
        return request(ctx, "POST", "devices?on_conflict=id", status.toString(), "resolution=merge-duplicates,return=minimal") != null
    }

    fun alert(ctx: Context, kind: String, text: String, lat: Double? = null, lng: Double? = null): Boolean {
        val p = Prefs(ctx)
        val o = JSONObject()
            .put("family_code", p.familyCode)
            .put("device_id", p.deviceId)
            .put("device_label", if (p.role == "manuel") p.helperName else p.parentName.replaceFirstChar { it.uppercase() })
            .put("kind", kind)
            .put("text", text.take(1900))
        if (lat != null && lng != null) o.put("lat", lat).put("lng", lng)
        return request(ctx, "POST", "alerts", o.toString(), "return=minimal") != null
    }

    /** Lee las categorías que Manuel escondió para este celular. Devuelve null si no hay conexión. */
    fun fetchHiddenCategories(ctx: Context): Set<String>? {
        val txt = request(ctx, "GET", "devices?select=hidden_categories&id=eq." + enc(Prefs(ctx).deviceId)) ?: return null
        return try {
            val arr = JSONArray(txt)
            if (arr.length() == 0) return null
            val cats = arr.getJSONObject(0).optJSONArray("hidden_categories") ?: return emptySet()
            (0 until cats.length()).map { cats.getString(it) }.toSet()
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Celular de Manuel ----------

    fun devices(ctx: Context): JSONArray? {
        val txt = request(ctx, "GET", "devices?select=*&order=label.asc") ?: return null
        return try { JSONArray(txt) } catch (e: Exception) { null }
    }

    fun alerts(ctx: Context, afterId: Long = 0, limit: Int = 50): JSONArray? {
        val txt = request(ctx, "GET", "alerts?select=*&id=gt.$afterId&order=id.desc&limit=$limit") ?: return null
        return try { JSONArray(txt) } catch (e: Exception) { null }
    }

    // HttpURLConnection no permite PATCH, así que los cambios van por funciones RPC (ver supabase/schema.sql).
    fun resolveAlert(ctx: Context, id: Long): Boolean =
        request(ctx, "POST", "rpc/resolve_alert", JSONObject().put("alert_id", id).toString()) != null

    fun sendErrors(ctx: Context, rows: JSONArray): Boolean =
        request(ctx, "POST", "app_errors", rows.toString(), "return=minimal") != null

    /** Guarda el resultado del diagnóstico completo en la tarjeta de este celular. */
    fun reportDiagnostic(ctx: Context, summary: String): Boolean =
        reportStatus(ctx, JSONObject().put("diagnostic", summary))

    fun removeDevice(ctx: Context, deviceId: String): Boolean =
        request(ctx, "POST", "rpc/remove_device", JSONObject().put("target_device", deviceId).toString()) != null

    fun setHiddenCategories(ctx: Context, deviceId: String, cats: Set<String>): Boolean {
        val arr = JSONArray()
        cats.forEach { arr.put(it) }
        return request(
            ctx, "POST", "rpc/set_hidden_categories",
            JSONObject().put("target_device", deviceId).put("cats", arr).toString()
        ) != null
    }
}
