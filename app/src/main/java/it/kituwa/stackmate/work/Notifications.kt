package it.kituwa.stackmate.work

import android.Manifest
import android.annotation.SuppressLint
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
import it.kituwa.stackmate.R
import it.kituwa.stackmate.data.Severity
import it.kituwa.stackmate.ui.MainActivity

object Notifications {

    const val CHANNEL_ID = "stackmate.status"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Server status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Alerts when a monitored server reports a problem"
                setShowBadge(false)
            },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun showSummary(context: Context, critical: Int, warning: Int, unreachable: Int) {
        if (!canPost(context)) return
        if (critical == 0 && warning == 0 && unreachable == 0) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }

        val text = buildList {
            if (critical > 0) add("$critical critical")
            if (warning > 0) add("$warning warning")
            if (unreachable > 0) add("$unreachable unreachable")
        }.joinToString(" · ")

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("StackMate needs attention")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    @SuppressLint("MissingPermission")
    fun clear(context: Context) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun severityLabel(severity: Severity) = severity.name.lowercase().replaceFirstChar { it.uppercase() }
}
