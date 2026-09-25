package ge.cgc.streamextractor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object Notifications {
    const val CHANNEL = "cgc_mirror"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL,
            "CGCStreamExtractor",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Mirror status and HLS links"
        manager.createNotificationChannel(channel)
    }

    fun foreground(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("CGCStreamExtractor")
            .setContentText("Starting self-hosted HLS mirror…")
            .setOngoing(true)
            .build()
    }

    fun ready(context: Context, localUrl: String, publicUrl: String?) {
        val text = buildString {
            append("Local: ")
            append(localUrl)
            if (!publicUrl.isNullOrBlank()) {
                append("\nPublic: ")
                append(publicUrl)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("Mirror ready")
            .setContentText(publicUrl ?: localUrl)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(100, notification)
    }

    fun warning(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("CGCStreamExtractor")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(101, notification)
    }
}
