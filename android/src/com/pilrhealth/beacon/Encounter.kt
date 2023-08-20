package com.pilrhealth.beacon

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.pilrhealth.EmaMessageQueue
import com.pilrhealth.PersistedProperty
import com.pilrhealth.persistedLong
import org.appcelerator.titanium.TiApplication
import java.util.Date
import kotlin.math.sqrt

private const val TAG = "Encounter"
private const val MAJOR_ID = "MAJOR_ID"
private const val MINOR_ID = "MINOR_ID"

enum class EncounterState { INACTIVE, TRANSIENT, ACTUAL }

@RequiresApi(api = Build.VERSION_CODES.O)
data class Encounter private constructor(
    val majorId: String,
    val minorId: String,
    var name: String,
    var tag: String,
) {
    var state: EncounterState = EncounterState.INACTIVE
    var startedAt: Long = -1
    var lastDetectedAt: Long = startedAt
    var updateScheduledAt: Long = Long.MAX_VALUE

    // Stats
    var numDeltaT = -1
    var maxDeltaT = -1L
    var sumDeltaT = 0.0
    var sumDeltaT2 = 0.0

    val expiresAt get() =
        lastDetectedAt + if (state == EncounterState.ACTUAL) actualEncounterTimeout else transientEncounterTimeout

    val actualAt get() = startedAt + minimumEncounterDuration

    companion object {
        private val encounterMap = mutableMapOf<Pair<String,String>, Encounter>()

        var messageQueue : EmaMessageQueue? = null

        var transientEncounterTimeout: Long by persistedLong(2 * 60 * 1000)
        var actualEncounterTimeout:    Long by persistedLong(3 * 60 * 1000)
        var minimumEncounterDuration:  Long by persistedLong(3 * 60 * 1000)
        var friendList: String by PersistedProperty<String>( "", fromString={it},
            // update encounterMap whenever friendList is updated
            willUpdate={ _, str ->
                synchronized(encounterMap) {
                    Log.d(TAG, "setting friend list: '$str'")
                    for (friend in str.split(Regex(""",\s*"""))) {
                        val (name, major, minor_optTag) =
                            friend.split(Regex("-"), 3)
                        val (minor, tag) =
                            if (minor_optTag.contains("-"))
                                minor_optTag.split(Regex("-"), 2)
                            else
                                listOf(minor_optTag, "$major-$minor_optTag")
                        val encounter = encounterMap.get(Pair(major,minor))
                        if (encounter == null) {
                            encounterMap[Pair(major,minor)] = Encounter(major, minor, name, tag)
                        }
                        else {
                            encounter.name = name
                            encounter.tag = tag
                        }
                    }
                    Log.d(TAG, "beaconMap=$encounterMap")
                    str
                }
            })


        fun beaconDetected(majorid: String, minorid: String) = synchronized(encounterMap) {
            val now = System.currentTimeMillis()
            val encounter = encounterMap.get(Pair(majorid, minorid))
            if (encounter == null) {
                Log.i(TAG, "Ignoring unknown beacon $majorid-$minorid")
                return
            }
            if (encounter.state == EncounterState.INACTIVE) {
                encounter.becomeTransient(now)
                // DEBUG - no commit
                //EncounterNotifier.sendNotification(encounter)
            }
            encounter.onDetected(now)
        }

        fun updateEncounterForBeacon(majorId: String, minorId: String) = synchronized(encounterMap) {
            val encounter = encounterMap[majorId to minorId]
            Log.d(TAG, "updateEncounterForBeacon for $majorId-$minorId $encounter")
            encounter?.onScheduledUpdate(System.currentTimeMillis())
        }
    }

    fun onDetected(now: Long) {
        numDeltaT += 1
        if (numDeltaT > 0) {
            val deltaT = now - lastDetectedAt
            sumDeltaT += deltaT
            sumDeltaT2 += deltaT * deltaT
            maxDeltaT = maxDeltaT.coerceAtLeast(deltaT)
        }
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
        if (state == EncounterState.TRANSIENT && now >= actualAt) {
            becomeActual(now)
        }
        scheduleNextUpdate()
    }

    fun becomeTransient(now: Long) {
        Log.i(TAG, "become transient: $this")
        startedAt = now
        lastDetectedAt = now
        state = EncounterState.TRANSIENT
        numDeltaT = -1
        maxDeltaT = -1
        sumDeltaT = 0.0
        sumDeltaT2 = 0.0
    }

    fun expire(now: Long) {
        Log.i(TAG, "Expire: $this")
        val isActual = state == EncounterState.ACTUAL
        state = EncounterState.INACTIVE
        if (!isActual) {
            messageQueue?.sendMessage(toMap(false) + mapOf(
                "event_type" to "start_transient_encounter",
                "timestamp" to EmaMessageQueue.encodeTimestamp(startedAt),
            ))
        }
        messageQueue?.sendMessage(toMap() + mapOf(
            "event_type" to (
                    if (isActual)
                        "end_actual_encounter"
                    else
                        "end_transient_encounter"),
            "timestamp" to EmaMessageQueue.encodeTimestamp(now),
            "last_detected" to EmaMessageQueue.encodeTimestamp(lastDetectedAt),
        ))
    }

    fun becomeActual(_now: Long) {
        Log.i(TAG, "Become actual: $this")
        state = EncounterState.ACTUAL
        EncounterNotifier.sendNotification(this)
        messageQueue?.sendMessage(toMap() + mapOf(
            "event_type" to  "start_actual_encounter",
            "timestamp" to EmaMessageQueue.encodeTimestamp(startedAt),
        ))
    }

    fun scheduleNextUpdate() {
        val updateAt = if (state == EncounterState.ACTUAL) expiresAt else Math.min(expiresAt, actualAt)
        if (updateAt > updateScheduledAt && updateScheduledAt > System.currentTimeMillis()) {
            // Log.d(TAG, "Earlier update already scheduled for $this")
            return
        }
        Log.i(TAG, "Schedule updateAt=${Date(updateAt)} for $this")
        val context = TiApplication.getInstance().applicationContext
        val updateIntent =
            Intent(context, EncounterUpdateReceiver::class.java)
                .putExtra(MAJOR_ID, majorId)
                .putExtra(MINOR_ID, minorId)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, updateIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(TiApplication.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, updateAt, pendingIntent)
        updateScheduledAt = updateAt
    }


    fun toMap(includeStats: Boolean = true): Map<String, Any> {
        val result = mutableMapOf(
            "friend_name" to name,
            "kontakt_beacon_id" to  tag,
            "min_duration_secs" to  minimumEncounterDuration / 1e3,
            "actual_enc_timeout_secs" to  actualEncounterTimeout / 1e3,
            "transient_enc_timeout_secs" to  transientEncounterTimeout / 1e3,
        )
        if (includeStats) {
            result.put("max_detect_event_delta_t", maxDeltaT)
            result.put("num_events", numDeltaT)
            if (numDeltaT > 0) {
                val mean = sumDeltaT / 1e3 / numDeltaT
                val std = sqrt(sumDeltaT2 / 1e6 / numDeltaT - mean * mean)
                result.put("avg_detect_event_delta_t", mean);
                result.put("sd_detect_event_delta_t", std);
            }
        }
        return result
    }

    override fun toString(): String {
        return "Encounter(tag=$tag, state=$state, expy=${expiresAt/60000.0} actual=${actualAt/60000.0}"
    }
}

@RequiresApi(api = Build.VERSION_CODES.O)
class EncounterUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.e("EncounterUpdateReceiver", "onReceive at ${intent.extras}")
        val majorId = intent.extras?.getString(MAJOR_ID) ?: return
        val minorId = intent.extras?.getString(MINOR_ID) ?: return
        Encounter.updateEncounterForBeacon(majorId, minorId)
    }
}
