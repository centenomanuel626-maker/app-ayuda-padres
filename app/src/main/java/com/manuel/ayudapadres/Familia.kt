package com.manuel.ayudapadres

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** ¿Se puede hablar en voz alta ahora? (de día y sin el celular en silencio) */
object Voz {
    fun canTalk(ctx: Context, fromHour: Int = 8, toHour: Int = 22): Boolean {
        val h = LocalTime.now().hour
        if (h < fromHour || h >= toHour) return false
        return try {
            ctx.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL
        } catch (e: Exception) {
            false
        }
    }
}

/** "💬 Mandarle un aviso": Manuel escribe y en el celular de los padres sale en pantalla y se lee en voz alta. */
object MessageCheck {

    /** Hace red: fuera del hilo principal. */
    fun check(ctx: Context) {
        val p = Prefs(ctx)
        if (p.role != "padres" || !Cloud.configured(ctx)) return
        val (text, at) = Cloud.fetchMessage(ctx) ?: return
        if (at <= p.lastMsgAt) return
        p.lastMsgAt = at
        p.lastMsg = text
        p.lastMsgDismissed = false
        val open = Notif.pendingActivity(
            ctx, Ids.AVISO_MANUEL,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        Notif.show(ctx, Ids.AVISO_MANUEL, Notif.URGENTE, "💬 ${p.helperName} te dice:", text, tap = open)
        if (Voz.canTalk(ctx, 7, 22)) Speaker.say(ctx, "${p.helperName} te dice: $text")
        Cloud.reportStatus(ctx, JSONObject().put("message_seen_at", Instant.now().toString()))
    }
}

/** Recordatorios que Manuel agenda desde su panel ("Doctor el jueves a las 10"): avisa el día antes y una hora antes. */
object ReminderSync {
    private val hora = DateTimeFormatter.ofPattern("EEEE d 'a las' h:mm a", Locale("es"))

    /**
     * Las horas se guardan como "hora de reloj" (la que Manuel eligió, sin zona): así "10:00" es 10:00
     * en el país de los padres aunque Manuel viva en otro huso horario.
     */
    fun wallMillisToLocal(wallMs: Long): Long =
        java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(wallMs), java.time.ZoneOffset.UTC)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun describe(atMs: Long): String =
        Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()).format(hora)

    /** Para el panel: muestra la hora de reloj guardada. */
    fun describeWall(wallMs: Long): String =
        Instant.ofEpochMilli(wallMs).atZone(java.time.ZoneOffset.UTC).format(hora)

    /** Hace red: fuera del hilo principal. */
    fun sync(ctx: Context) {
        val p = Prefs(ctx)
        if (p.role != "padres" || !Cloud.configured(ctx)) return
        val arr = Cloud.fetchReminders(ctx) ?: return
        val now = System.currentTimeMillis()
        val wanted = HashMap<String, Pair<String, Long>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optLong("id")
            val at = try {
                wallMillisToLocal(java.time.OffsetDateTime.parse(o.getString("at")).toInstant().toEpochMilli())
            } catch (e: Exception) {
                continue
            }
            if (at < now - 60 * 60 * 1000L) continue
            wanted["$id|$at"] = o.optString("text") to at
        }
        val before = p.remindersScheduled
        // Los que Manuel borró o cambió: se cancelan.
        for (key in before - wanted.keys) {
            val id = key.substringBefore('|').toLongOrNull() ?: continue
            for (n in 0..2) Alarms.cancelReminder(ctx, id, n)
        }
        for ((key, v) in wanted) {
            if (key in before) continue
            val id = key.substringBefore('|').toLong()
            val (text, at) = v
            val dayBefore = at - 24 * 60 * 60 * 1000L
            val hourBefore = at - 60 * 60 * 1000L
            if (dayBefore > now) Alarms.armReminder(ctx, id, 0, dayBefore, text, at, "Mañana")
            if (hourBefore > now) Alarms.armReminder(ctx, id, 1, hourBefore, text, at, "En una hora")
            if (at > now) Alarms.armReminder(ctx, id, 2, at, text, at, "Ahora")
        }
        p.remindersScheduled = wanted.keys
    }

    /** Cuando suena: aviso en pantalla y en voz alta. */
    fun fire(ctx: Context, id: Long, text: String, at: Long, cuando: String) {
        val msg = when (cuando) {
            "Mañana" -> "Recordatorio para mañana: $text, ${describe(at)}."
            "En una hora" -> "En una hora: $text."
            else -> "Es la hora: $text."
        }
        Notif.show(ctx, Ids.reminder(id), Notif.URGENTE, "📅 Recordatorio", msg, tap = Notif.openApp(ctx, Ids.reminder(id)))
        if (Voz.canTalk(ctx, 7, 22)) Speaker.say(ctx, msg)
    }
}
