/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cat.itur.app.core.model.Broadcast
import cat.itur.app.feature.map.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val CHANNEL_ID = "activity_broadcasts"

/**
 * Posts a system notification for an operator broadcast received during an ongoing activity
 * (UC-ACTIVITY-007). Broadcasts are also shown in-app via [cat.itur.app.feature.map.ui.MapUiState.Ongoing];
 * this is best-effort background delivery on top of that, so it silently no-ops without
 * POST_NOTIFICATIONS (Android 13+) rather than blocking the feature on that permission.
 */
class BroadcastNotifier
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Activity alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Messages sent by the activity organiser or system operator."
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun notify(broadcast: Broadcast) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_broadcast)
            .setContentTitle("Activity alert")
            .setContentText(broadcast.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(broadcast.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(broadcast.id.hashCode(), notification)
    }
}
