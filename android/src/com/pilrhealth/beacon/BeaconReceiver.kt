package com.pilrhealth.beacon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "BeaconReceiver"

class BeaconReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.e(TAG, "DEBUG>>> Got an intent")
        TODO("Not yet implemented")
    }
}