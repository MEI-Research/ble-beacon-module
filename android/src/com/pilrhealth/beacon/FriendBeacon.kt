package com.pilrhealth.beacon

data class FriendBeacon(val name: String, val majorid: String, val minorid: String) {
    companion object {
        var beaconList = mutableListOf<FriendBeacon>()

        fun clearList() {
            beaconList.clear()
        }

        fun with(majorid: String, minorid: String) =
            beaconList.firstOrNull { it.majorid == majorid && it.minorid == minorid }

    }

    init {
        beaconList.add(this)
    }

    override fun equals(other: Any?): Boolean {
        return other is FriendBeacon && other.majorid == majorid && other.minorid == minorid
    }
}