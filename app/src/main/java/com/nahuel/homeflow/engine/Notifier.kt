package com.nahuel.homeflow.engine

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nahuel.homeflow.HomeFlowApp
import com.nahuel.homeflow.MainActivity
import com.nahuel.homeflow.R

/**
 * Run feedback. While the app is visible: a toast. In the background toasts are unreliable
 * (Android 12+ limits them), so errors become a notification instead; background successes
 * stay silent - the run history has them.
 */
object Notifier {
    private const val CHANNEL_ID = "smartflow_results"
    private const val ERROR_ID = 2

    fun result(ctx: Context, msg: String, isError: Boolean) {
        val app = ctx.applicationContext
        if (HomeFlowApp.isForeground) {
            Handler(Looper.getMainLooper()).post { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
        } else if (isError) {
            notifyError(app, msg)
        }
    }

    private fun notifyError(ctx: Context, msg: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Automations-Fehler", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_smartflow)
            .setContentTitle("SmartFlow")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(ERROR_ID, n)
    }
}
