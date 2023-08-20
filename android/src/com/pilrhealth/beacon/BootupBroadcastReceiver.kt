package com.pilrhealth.beacon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi

private const val TAG = "BootupBroadcastReceiver"

@RequiresApi(Build.VERSION_CODES.O)
class BootupBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        BeaconDetector().start("unknown")
    }
}