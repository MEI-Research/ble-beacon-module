package com.pilrhealth.beacon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import com.pilrhealth.AppMessageQueue
import com.pilrhealth.EventTimes
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.Region
import org.appcelerator.titanium.TiApplication
import org.appcelerator.titanium.util.TiRHelper
import java.lang.Exception

const val KONTAKT_BEACON_ID = "F7826DA6-4FA2-4E98-8024-BC5B71E0893E"

private const val TAG = "BeaconDetector"


/**
 * Provide a facade to the Android Beacon Library to start the detection service.
 * # IMPORTANT NOTE
 *
 * This module is a slightly modfied version of the Android Beacon Library Reference app.
 * https://github.com/davidgyoung/android-beacon-library-reference-kotlin/blob/master/app/src/main/java/org/altbeacon/beaconreference/BeaconReferenceApplication.kt0
 * Please maintain that. Updates to processing beacon detctions should be in Encounter.
 */
@RequiresApi(Build.VERSION_CODES.O)
object BeaconDetector {
    var betweenScanPeriod: Long = 30 * 1000
    var scanPeriod: Long = 10 * 1000
    var scanIntervalWarningThreshold = 5 * 60 * 1000L

    val scanTimes = EventTimes()

    //lateinit var region: Region

    private val debug = false

    private var started = false
    // private var started by persistedBoolean(false)

    init {
        Log.e(TAG,"created, betweenScanPeriod=$betweenScanPeriod")
        //AppMessageQueue.appLog("$TAG created, betweenScanPeriod=$betweenScanPeriod")
    }

    fun start(whence: String) {
        val context: Context = TiApplication.getInstance()

        // This line is needed so that the friends list is loaded (by side-effect) after the
        // application has been killed and the service restarts.
        val friends = Encounter.friendList
        AppMessageQueue.appLog("$TAG start",
           "whence" to whence,
            "alreadyStarted" to started,
            "betweenScanPeriod" to betweenScanPeriod,
            "scanPeriod" to scanPeriod,
            "scanIntervalWarningThreshold" to scanIntervalWarningThreshold,
        )

        if (started) {
            return
        }
        // if (!BeaconScanPermissionsActivity.allPermissionsGranted(context, true)) {
        //     val intent = Intent(context, BeaconScanPermissionsActivity::class.java)
        //     intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //     intent.putExtra("backgroundAccessRequested", true)
        //     context.startActivity(intent)
        // }

        val missingPermissions = PermissionsHelper(context).permissionsNotGranted()
        if (!missingPermissions.isEmpty()) {
            Log.e(TAG, "TODO: missing permissions: ${missingPermissions.joinToString(",")}.")
            return

        }

        started = true

        val beaconManager = BeaconManager.getInstanceForApplication(context)
        BeaconManager.setDebug(debug)

        beaconManager.beaconParsers.clear()

        // Uncomment if you want to block the library from updating its distance model database
        //BeaconManager.setDistanceModelUpdateUrl("")

        // The example shows how to find iBeacon.
        val parser = BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
        parser.setHardwareAssistManufacturerCodes(arrayOf(0x004c).toIntArray())
        beaconManager.beaconParsers.add(parser)


        try {
            setupForegroundService(context)
            beaconManager.setEnableScheduledScanJobs(false);
            beaconManager.setBackgroundBetweenScanPeriod(betweenScanPeriod);
            beaconManager.setBackgroundScanPeriod(scanPeriod);

            val region = Region("kontakt", Identifier.parse(KONTAKT_BEACON_ID), null, null)
            beaconManager.startMonitoring(region)
            beaconManager.startRangingBeacons(region)

            // Ranging callbacks will drop out if no beacons are detected
            // Monitoring callbacks will be delayed by up to 25 minutes on region exit

            // See https://altbeacon.github.io/android-beacon-library/detection_times.html
            // If enabled I get kotlin.UninitializedPropertyAccessException:
            // lateinit property scanState has not been initialized
            //beaconManager.setIntentScanningStrategyEnabled(true)

            val regionViewModel =
                BeaconManager.getInstanceForApplication(context).getRegionViewModel(region)
            regionViewModel.rangedBeacons.observeForever(centralRangingObserver)
        }
        catch (e: Exception) {
            Log.e(TAG, "Service already started--hopefully", e)
        }

        // The code below will start "monitoring" for beacons matching the region definition below
        // the region definition is a wildcard that matches all beacons regardless of identifiers.
        // if you only want to detect beacons with a specific UUID, change the id1 paremeter to
        // a UUID like Identifier.parse("2F234454-CF6D-4A0F-ADF2-F4911BA9FFA6")
    }

    val centralRangingObserver = Observer<Collection<Beacon>> { beacons ->
        Log.d(TAG, "Detected ${beacons.count()} beacons, ${scanTimes.stats()}")
        scanTimes.previousMillis = System.currentTimeMillis()
        if(scanTimes.previousDTMillis > scanIntervalWarningThreshold) {
            AppMessageQueue.appLog("Long time since last scan ${scanTimes.stats()}")
        }
        for (beacon: Beacon in beacons) {
            // Log.d(TAG, "$beacon about ${beacon.distance} meters away")
            Encounter.beaconDetected(beacon.id2.toString(), beacon.id3.toString())
        }
    }

    private fun setupForegroundService(context: Context) {
        val builder = Notification.Builder(context, "BeaconReferenceApp")
        val notifIcon = try {
            TiRHelper.getApplicationResource("drawable.ic_launcher")
        } catch(_: TiRHelper.ResourceNotFoundException) {
            android.R.drawable.alert_light_frame
        }
        builder.setSmallIcon(notifIcon)
        builder.setContentTitle("Scanning for Beacons")
        //val intent = Intent(context, BeaconReceiver::class.java)
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent == null) {
            throw IllegalStateException("Can't create launch intent for ${context.packageName}")
        }
        val pendingIntent =
            PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        builder.setContentIntent(pendingIntent);
        val channel =  NotificationChannel("beacon-ref-notification-id",
            "My Notification Name", NotificationManager.IMPORTANCE_DEFAULT)
        channel.setDescription("Beacon Detection")
        val notificationManager =  context.getSystemService(
            Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel);
        builder.setChannelId(channel.getId());
        BeaconManager.getInstanceForApplication(context)
            .enableForegroundServiceScanning(builder.build(), 456);
    }
}