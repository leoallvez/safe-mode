package com.poc.safemode.domain

data class TrustedArea(
    val id: String,
    val name: String,
    val ssid: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val bssids: Set<String>,
    val security: WifiSecurity = WifiSecurity.Unknown,
    val protocols: Set<WifiProtocol> = emptySet()
) {
    companion object {
        const val MIN_RADIUS_METERS = 50
        const val MAX_RADIUS_METERS = 500
        const val RADIUS_STEP_METERS = 10
        const val DEFAULT_RADIUS_METERS = 100
    }
}

data class LocationReading(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?
)
