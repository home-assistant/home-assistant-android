package io.homeassistant.companion.android.bluetooth.ble

data class IBeaconTransmitter(
    var uuid: String,
    var major: String,
    var minor: String,
    var transmitting: Boolean = false,
    var state: String,
    val manufacturer: Int = 0x004c,
    val beaconLayout: String = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
)
