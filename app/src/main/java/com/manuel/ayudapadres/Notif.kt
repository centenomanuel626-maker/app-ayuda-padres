package com.manuel.ayudapadres

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Notif {
    const val AVISOS = "avisos"
    const val URGENTE = "urgente"
    const val MEDICINAS = "medicinas"
    const val ESTAFAS = "estafas"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(AVISOS, "Avisos", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(URGENTE, "Emergencias y alertas", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(MEDICINAS, "Medicinas", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(ESTAFAS, "Llamadas sospechosas", NotificationManager.IMPORTANCE_HIGH))
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun pendingActivity(ctx: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun pendingBroadcast(ctx: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun openApp(ctx: Context, requestCode: Int = 0): PendingIntent =
        pendingActivity(ctx, requestCode, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun show(
        ctx: Context, id: Int, channel: String, title: String, body: String,
        tap: PendingIntent? = null,
        actions: List<Pair<String, PendingIntent>> = emptyList(),
        fullScreen: PendingIntent? = null
    ) {
        if (!canPost(ctx)) return
        channels(ctx)
        val b = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tap ?: openApp(ctx, id))
        if (channel == URGENTE || channel == ESTAFAS) b.setCategory(Notification.CATEGORY_ALARM)
        if (fullScreen != null) {
            b.setFullScreenIntent(fullScreen, true)
            b.setCategory(Notification.CATEGORY_ALARM)
        }
        for ((label, pi) in actions) {
            b.addAction(Notification.Action.Builder(null, label, pi).build())
        }
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, b.build())
        } catch (e: SecurityException) { }
    }

    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
