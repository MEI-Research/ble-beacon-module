package com.pilrhealth.beacon

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionsHelper(val context: Context) {
    // Manifest.permission.ACCESS_BACKGROUND_LOCATION
    // Manifest.permission.ACCESS_FINE_LOCATION
    // Manifest.permission.BLUETOOTH_CONNECT
    // Manifest.permission.BLUETOOTH_SCAN

    fun permissionsNotGranted(backgroundAccessRequested: Boolean = true): List<String> {
        val missingPermissions = mutableListOf<String>();
        val permissionsGroups = beaconScanPermissionGroupsNeeded(backgroundAccessRequested)
        for (permissionsGroup in permissionsGroups) {
            for (permission in permissionsGroup) {
                if (!isPermissionGranted(permission)) {
                    missingPermissions.add(permission)
                }
            }
        }
        return missingPermissions
    }

    fun isPermissionGranted(permissionString: String): Boolean {
        return (ContextCompat.checkSelfPermission(context, permissionString) == PackageManager.PERMISSION_GRANTED)
    }
    fun setFirstTimeAskingPermission(permissionString: String, isFirstTime: Boolean) {
        val sharedPreference = context.getSharedPreferences("org.altbeacon.permisisons",
            AppCompatActivity.MODE_PRIVATE
        )
        sharedPreference.edit().putBoolean(permissionString,
            isFirstTime).apply()
    }

    fun isFirstTimeAskingPermission(permissionString: String): Boolean {
        val sharedPreference = context.getSharedPreferences(
            "org.altbeacon.permisisons",
            AppCompatActivity.MODE_PRIVATE
        )
        return sharedPreference.getBoolean(
            permissionString,
            true
        )
    }
    fun beaconScanPermissionGroupsNeeded(backgroundAccessRequested: Boolean = false): List<Array<String>> {
        val permissions = ArrayList<Array<String>>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // As of version M (6) we need FINE_LOCATION (or COARSE_LOCATION, but we ask for FINE)
            permissions.add(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // As of version Q (10) we need FINE_LOCATION and BACKGROUND_LOCATION
            if (backgroundAccessRequested) {
                permissions.add(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // As of version S (12) we need FINE_LOCATION, BLUETOOTH_SCAN and BACKGROUND_LOCATION
            // Manifest.permission.BLUETOOTH_CONNECT is not absolutely required to do just scanning,
            // but it is required if you want to access some info from the scans like the device name
            // and the aditional cost of requsting this access is minimal, so we just request it
            permissions.add(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        }
        return permissions
    }

}
