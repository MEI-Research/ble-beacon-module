package com.pilrhealth.beacon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import org.altbeacon.beacon.Beacon
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.R
import org.altbeacon.beacon.Region
import org.appcelerator.titanium.util.TiRHelper
import java.security.Permission

const val KONTAKT_BEACON_ID = "F7826DA6-4FA2-4E98-8024-BC5B71E0893E"

private const val TAG = "BeaconDetector"

@RequiresApi(Build.VERSION_CODES.O)
class BeaconDetector(private val context: Context) {
    lateinit var region: Region

    var started = false;
    fun start() {
        if (started) {
            return
        }
        started = true
        Log.e(TAG, "Starting EMA beacon detection")

        val missingPermissions = PermissionsHelper(context).permissionsNotGranted()
        if (!missingPermissions.isEmpty()) {
            Log.e(TAG, "TODO: missing permissions: ${missingPermissions.joinToString(",")}.")
        }

        val beaconManager = BeaconManager.getInstanceForApplication(context)
        //BeaconManager.setDebug(true)

        beaconManager.beaconParsers.clear()

        // Uncomment if you want to block the library from updating its distance model database
        //BeaconManager.setDistanceModelUpdateUrl("")

        // The example shows how to find iBeacon.
        val parser = BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
        parser.setHardwareAssistManufacturerCodes(arrayOf(0x004c).toIntArray())
        beaconManager.beaconParsers.add(parser)


        setupForegroundService()
        beaconManager.setEnableScheduledScanJobs(false);
        beaconManager.setBackgroundBetweenScanPeriod(30000);
        beaconManager.setBackgroundScanPeriod(10000);

        region = Region("kontakt", Identifier.parse(KONTAKT_BEACON_ID), null, null)
        beaconManager.startMonitoring(region)
        beaconManager.startRangingBeacons(region)

        // Ranging callbacks will drop out if no beacons are detected
        // Monitoring callbacks will be delayed by up to 25 minutes on region exit
        // beaconManager.setIntentScanningStrategyEnabled(true)

        val regionViewModel = BeaconManager.getInstanceForApplication(context).getRegionViewModel(region)
        regionViewModel.rangedBeacons.observeForever( centralRangingObserver)

        // The code below will start "monitoring" for beacons matching the region definition below
        // the region definition is a wildcard that matches all beacons regardless of identifiers.
        // if you only want to detect beacons with a specific UUID, change the id1 paremeter to
        // a UUID like Identifier.parse("2F234454-CF6D-4A0F-ADF2-F4911BA9FFA6")
    }

    val centralRangingObserver = Observer<Collection<Beacon>> { beacons ->
        Log.d(TAG, "Ranged: ${beacons.count()} beacons")
        for (beacon: Beacon in beacons) {
            Log.d(TAG, "$beacon about ${beacon.distance} meters away")
        }
    }

    private fun setupForegroundService() {
        val builder = Notification.Builder(context, "BeaconReferenceApp")
        val notifIcon = try {
            TiRHelper.getApplicationResource("drawable.ic_launcher")
        } catch(_: TiRHelper.ResourceNotFoundException) {
            android.R.drawable.alert_light_frame
        }
        builder.setSmallIcon(notifIcon)
        builder.setContentTitle("Scanning for Beacons")
        val intent = Intent(context, BeaconReceiver::class.java)
        val pendingIntent =
            PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        builder.setContentIntent(pendingIntent);
        val channel =  NotificationChannel("beacon-ref-notification-id",
            "My Notification Name", NotificationManager.IMPORTANCE_DEFAULT)
        channel.setDescription("My Notification Channel Description")
        val notificationManager =  context.getSystemService(
            Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel);
        builder.setChannelId(channel.getId());
        BeaconManager.getInstanceForApplication(context)
            .enableForegroundServiceScanning(builder.build(), 456);
    }

    private fun allPermissionsGranted(context: Context, backgroundAccessRequested: Boolean): Boolean {
        val permissionsHelper = PermissionsHelper(context)
        val permissionsGroups = permissionsHelper.beaconScanPermissionGroupsNeeded(backgroundAccessRequested)
        for (permissionsGroup in permissionsGroups) {
            for (permission in permissionsGroup) {
                if (!permissionsHelper.isPermissionGranted(permission)) {
                    return false
                }
            }
        }
        return true
    }
}