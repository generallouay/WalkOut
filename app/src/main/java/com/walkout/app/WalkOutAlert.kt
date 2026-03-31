package com.walkout.app

import android.app.NotificationManager
import android.content.Context
import android.os.*
import androidx.core.app.NotificationCompat

object WalkOutAlert {

    fun trigger(context: Context, text: String) {
        vibrate(context)
        showNotification(context, text)
    }

    private fun vibrate(context: Context) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(2500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(2500)
        }
    }

    private fun showNotification(context: Context, text: String) {
        val notification = NotificationCompat.Builder(context, MainActivity.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("WalkOut")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1001, notification)
    }
}
