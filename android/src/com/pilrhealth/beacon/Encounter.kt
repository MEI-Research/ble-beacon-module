package com.pilrhealth.beacon

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.appcelerator.titanium.TiApplication
import java.util.Date

private const val TAG = "Encounter"
private const val MAJOR_ID = "MAJOR_ID"
private const val MINOR_ID = "MINOR_ID"

@RequiresApi(api = Build.VERSION_CODES.O)
data class Encounter private constructor(
    val beacon: NamedBeacon,
    val startedAt: Long,
    var lastDetectedAt: Long = startedAt,
    var isActual: Boolean = false,
    var updateScheduledAt: Long = Long.MAX_VALUE
) {
    val expiresAt get() =
        lastDetectedAt + if (isActual) actualEncounterTimeout else transientEncounterTimeout

    val actualAt get() = startedAt + minimumEncounterDuration

    companion object {
        var transientEncounterTimeout: Long = 2 * 60 * 1000
        var actualEncounterTimeout: Long = 3 * 60 * 1000
        var minimumEncounterDuration: Long = 3 * 60 * 1000

        private val beaconMap = mutableMapOf<Pair<String, String>, NamedBeacon>()
        private val encounterMap = mutableMapOf<NamedBeacon, Encounter>()

        fun addNamedBeacon(name: String, majorId: String, minorId: String) = synchronized(beaconMap) {
            beaconMap[Pair(majorId, minorId)] = NamedBeacon(name, majorId, minorId)
        }

        fun beaconDetected(majorid: String, minorid: String) = synchronized(beaconMap) {
            val now = System.currentTimeMillis()
            val beacon = beaconMap[Pair(majorid, minorid)]
            if (beacon == null) {
                Log.i(TAG, "Ignoring unknown beacon $majorid-$minorid")
                return
            }
            var encounter = encounterMap[beacon]
            if (encounter == null) {
                encounter = Encounter(beacon, now)
                encounterMap[beacon] = encounter
                Log.i(TAG, "start transient encounter $encounter for $beacon")
            }
            encounter.onDetected(now)
        }

        fun updateEncounterForBeacon(majorId: String, minorId: String) = synchronized(beaconMap) {
            val beacon = beaconMap[Pair(majorId, minorId)]
            val encounter = encounterMap[beacon]
            Log.d(TAG, "updateEncounterForBeacon for $majorId-$minorId $encounter")
            encounter?.onScheduledUpdate(System.currentTimeMillis())
        }
    }

    fun onDetected(now: Long) {
        lastDetectedAt = now
        scheduleNextUpdate()
    }

    fun onScheduledUpdate(now: Long) {
        Log.d(TAG, "onScheduledUpate: expired=${now >= expiresAt}, actual=${now >= actualAt}, now=$now, $this")
        updateScheduledAt = Long.MAX_VALUE

        if (now >= expiresAt) {
            expire(now)
            return
        }
        if (!isActual && now >= actualAt) {
            becomeActual(now)
        }
        scheduleNextUpdate()
    }

    fun expire(now: Long) {
        Log.i(TAG, "Expire $this")
        encounterMap.remove(beacon)
    }

    fun becomeActual(now: Long) {
        Log.i(TAG, "Become actual $this")
        isActual = true
    }

    fun scheduleNextUpdate() {
        val updateAt = if (isActual ) expiresAt else Math.min(expiresAt, actualAt)
        if (updateAt > updateScheduledAt && updateScheduledAt > System.currentTimeMillis()) {
            // Log.d(TAG, "Earlier update already scheduled for $this")
            return
        }
        Log.i(TAG, "Schedule updateAt=${Date(updateAt)} for $this")
        val context = TiApplication.getInstance().applicationContext
        val updateIntent =
            Intent(context, EncounterUpdateReceiver::class.java)
                .putExtra(MAJOR_ID, beacon.majorId)
                .putExtra(MINOR_ID, beacon.minorId)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, updateIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(TiApplication.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, updateAt, pendingIntent)
        updateScheduledAt = updateAt
    }
}

data class NamedBeacon(
    val name: String,
    val majorId: String,
    val minorId: String,
)

@RequiresApi(api = Build.VERSION_CODES.O)
class EncounterUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.e("EncounterUpdateReceiver", "onReceive at ${intent.extras}")
        val majorId = intent.extras?.getString(MAJOR_ID) ?: return
        val minorId = intent.extras?.getString(MINOR_ID) ?: return
        Encounter.updateEncounterForBeacon(majorId, minorId)
    }
}
