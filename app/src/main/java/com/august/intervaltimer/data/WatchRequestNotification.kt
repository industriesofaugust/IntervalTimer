package com.august.intervaltimer.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.august.intervaltimer.MainActivity
import com.august.intervaltimer.R
import com.august.intervaltimer.sync.LiveLinkCodec

/**
 * "Workout on your watch, tap to follow". The tap opens MainActivity with the
 * request attached, so it still works if the app was closed in between.
 */
object WatchRequestNotification {

    private const val CHANNEL_ID = "watch_workout"
    private const val NOTIFICATION_ID = 1
    private const val EXTRA_REQUEST = "watch_request"
    private const val EXTRA_NODE = "watch_node"

    @SuppressLint("MissingPermission") // Checked by areNotificationsEnabled().
    fun show(context: Context, pending: WatchRequest) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout on watch",
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)

        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_REQUEST, LiveLinkCodec.encode(pending.request))
            .putExtra(EXTRA_NODE, pending.nodeId)
        val tap = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Workout on your watch")
            .setContentText("Tap to follow along on your phone")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** The request a notification tap carried, or null for any other launch. */
    fun requestFrom(intent: Intent?): WatchRequest? {
        val bytes = intent?.getByteArrayExtra(EXTRA_REQUEST) ?: return null
        val node = intent.getStringExtra(EXTRA_NODE) ?: return null
        // Handled once: opening the app again from Recents must not follow again.
        intent.removeExtra(EXTRA_REQUEST)
        val request = LiveLinkCodec.decodeRequest(bytes) ?: return null
        return WatchRequest(request, node)
    }
}
