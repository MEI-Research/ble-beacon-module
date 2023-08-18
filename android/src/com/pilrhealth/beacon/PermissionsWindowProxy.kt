package com.pilrhealth.beacon

import android.os.Build
import androidx.annotation.RequiresApi
import org.appcelerator.kroll.annotations.Kroll
import org.appcelerator.kroll.common.TiConfig
import org.appcelerator.titanium.proxy.TiActivityWindowProxy

@RequiresApi(Build.VERSION_CODES.O)
@Kroll.proxy(creatableInModule = BleBeaconModule::class)
class PermissionsWindowProxy: TiActivityWindowProxy() {
    companion object {
        // Standard Debugging variables
        private const val LCAT = "PermissionsWindowProxy"
        private val DBG = TiConfig.LOGD
    }

}