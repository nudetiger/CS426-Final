package com.cs426.learningmocha.backup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cs426.learningmocha.MainActivity
import com.cs426.learningmocha.R
import com.cs426.learningmocha.data.prefs.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * Weekly "your library isn't backed up" nudge, checked on cold start.
 *
 * Deliberately not WorkManager or AlarmManager: the reminder only has to be seen
 * the next time the app is opened, and a scheduled wake-up would be a new
 * dependency plus a background-execution surface for no extra benefit.
 */
object BackupReminder {

    private const val CHANNEL_ID = "backup_reminder"
    private const val NOTIFICATION_ID = 1001
    private val INTERVAL_MS = TimeUnit.DAYS.toMillis(7)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.backup_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.backup_channel_description)
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun notifyIfOverdue(context: Context, settings: SettingsStore) {
        if (!settings.backupRemindersEnabled) return

        val since = settings.reminderClockAt
        if (since == 0L) {
            // First launch: start the countdown instead of nagging immediately.
            settings.reminderClockAt = System.currentTimeMillis()
            return
        }
        if (System.currentTimeMillis() - since < INTERVAL_MS) return
        if (!canPost(context)) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_post)
            .setContentTitle(context.getString(R.string.backup_reminder_title))
            .setContentText(context.getString(R.string.backup_reminder_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** POST_NOTIFICATIONS is a runtime permission from API 33; silently skip without it. */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
