package com.bliss.blisschat.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.bliss.blisschat.MainActivity
import com.bliss.blisschat.R

/**
 * Incoming-call notifications.
 *  - phone locked / screen off: full-screen call screen (full-screen intent)
 *  - phone unlocked: heads-up notification on top with "Ответить" / "Отклонить"
 */
object Notifications {
    const val CH_CALLS = "calls_v2"
    const val CH_MESSAGES = "messages_v3"
    const val EXTRA_CALL_ID = "call_id"
    const val EXTRA_ACCEPT = "accept_call"
    const val EXTRA_CHAT_ID = "open_chat"
    private const val CALL_NOTIFICATION_ID = 7001

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        nm.createNotificationChannel(
            NotificationChannel(CH_CALLS, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Звонки в blisschat"
                setSound(
                    ring,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MESSAGES, "Сообщения", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Новые сообщения"
                enableVibration(true)
            }
        )
    }

    fun canPost(ctx: Context) =
        Build.VERSION.SDK_INT < 33 || ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun fullScreenAllowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun showIncomingCall(ctx: Context, callId: String, callerName: String, video: Boolean, fullScreen: Boolean) {
        if (!canPost(ctx)) return
        createChannels(ctx)
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_CALL_ID, callId)
        val answer = Intent(open).putExtra(EXTRA_ACCEPT, true)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openPi = PendingIntent.getActivity(ctx, 1, open, flags)
        val answerPi = PendingIntent.getActivity(ctx, 2, answer, flags)
        val declinePi = PendingIntent.getBroadcast(
            ctx, 3, Intent(ctx, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_DECLINE).putExtra(EXTRA_CALL_ID, callId), flags
        )
        val person = Person.Builder().setName(callerName).setImportant(true).build()
        val n = NotificationCompat.Builder(ctx, CH_CALLS)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(callerName)
            .setContentText(if (video) "Входящий видеозвонок" else "Входящий звонок")
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declinePi, answerPi))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPi)
            .setTimeoutAfter(45_000)
            .apply { if (fullScreen) setFullScreenIntent(openPi, true) }
            .build()
        NotificationManagerCompat.from(ctx).notify(CALL_NOTIFICATION_ID, n)
    }

    fun cancelCall(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(CALL_NOTIFICATION_ID)
    }

    fun showMessage(ctx: Context, chatId: String, senderName: String, body: String = "") {
        if (!canPost(ctx)) return
        createChannels(ctx)
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_CHAT_ID, chatId)
        val pi = PendingIntent.getActivity(ctx, chatId.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(senderName)
            .setContentText(body.ifBlank { "Новое сообщение" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifBlank { "Новое сообщение" }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        NotificationManagerCompat.from(ctx).notify(chatId.hashCode(), n)
    }
}
