package com.pilrhealth.beacon

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.pilrhealth.AppMessageQueue
import org.appcelerator.titanium.TiApplication
import java.lang.Math.abs
import java.util.Date

private const val WARN_THRESHOLD_MIN = 5.0

@RequiresApi(api = Build.VERSION_CODES.O)
class EncounterAlarms : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive at ${intent.extras}")
        val majorId = intent.extras?.getString(MAJOR_ID) ?: return
        val minorId = intent.extras?.getString(MINOR_ID) ?: return
        val scheduledFor = intent.extras?.getLong(SCHEDULE_FOR) ?: -1L
        val dt = (System.currentTimeMillis() - scheduledFor) / 1000.0 / 60.0
        if (abs(dt) > WARN_THRESHOLD_MIN) {
            AppMessageQueue.appLog("$TAG receive alarm for $majorId-$minorId was delayed by $dt min",
                "scheduled_for" to Date(scheduledFor).toString()
            )
        }
        Encounter.updateEncounterForBeacon(majorId, minorId)
    }

    companion object {
        private const val TAG = "Encounter"
        private const val MAJOR_ID = "MAJOR_ID"
        private const val MINOR_ID = "MINOR_ID"
        private const val SCHEDULE_FOR = "SCHEDULE_FOR"

        fun scheduleAlarm(updateAt: Long, majorId: String, minorId: String) {
            // Update immediately if time has passed
            if (updateAt <= System.currentTimeMillis()) {
                Encounter.updateEncounterForBeacon(majorId, minorId)
                return
            }
            val context = TiApplication.getInstance().applicationContext
            val updateIntent =
                Intent(context, EncounterAlarms::class.java)
                    .putExtra(MAJOR_ID, majorId)
                    .putExtra(MINOR_ID, minorId)
                    .putExtra(SCHEDULE_FOR, updateAt)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, updateIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = context.getSystemService(TiApplication.ALARM_SERVICE) as AlarmManager
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, updateAt, pendingIntent)
        }
    }
}