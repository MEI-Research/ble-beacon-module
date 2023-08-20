package com.pilrhealth.beacon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.appcelerator.titanium.TiApplication
import org.appcelerator.titanium.util.TiRHelper

private const val TAG = "EncounterNotifier"
private const val CHANNEL_ID = "12"
private const val CHANNEL_NAME = "EMA Plot Location"
private const val GROUP_NAME = "ema_plot_loc"
private const val notificationId = 200001

@RequiresApi(api = Build.VERSION_CODES.O)
object EncounterNotifier {
    fun sendNotification(encounter: Encounter) {
        val context = TiApplication.getInstance()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        );

        val icon = //try {
        //    TiRHelper.getApplicationResource("drawable.ic_launcher");
        //} catch(_: TiRHelper.ResourceNotFoundException) {
            android.R.drawable.alert_light_frame
        // }

        Log.e(TAG, "DEBUG>>> icon=$icon")

        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                setPackage(null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK+Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        var builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("encounter title")
            .setContentText("Start ${encounter}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroupSummary(true)
            .setGroup(GROUP_NAME)
            .setStyle(NotificationCompat.InboxStyle())
            .setColor((Color.parseColor("#3F51B5")))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }

}