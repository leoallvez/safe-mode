package com.poc.safemode.domain

import kotlin.math.roundToInt

sealed interface SafeStatus {
    data class Inside(val area: TrustedArea) : SafeStatus

    data class WrongNetwork(val currentSsid: String?) : SafeStatus

    data class UnknownAccessPoint(val area: TrustedArea) : SafeStatus

    data class SecurityMismatch(val area: TrustedArea, val wifi: ConnectedWifi) : SafeStatus

    data class OutOfRange(val nearestArea: TrustedArea, val distanceMeters: Int) : SafeStatus

    data object NoSavedArea : SafeStatus

    data object NoLocationFix : SafeStatus

    data class LowAccuracy(val accuracyMeters: Int, val radiusMeters: Int) : SafeStatus
}

object SafeStatusEvaluator {

    fun anyAreaRecognizes(areas: List<TrustedArea>, wifi: ConnectedWifi?): Boolean =
        areas.any { it.recognizes(wifi) }

    private fun TrustedArea.hasAccessPointOf(wifi: ConnectedWifi?): Boolean =
        wifi != null && wifi.ssid == ssid && wifi.bssid != null && wifi.bssid in bssids

    private fun TrustedArea.recognizes(wifi: ConnectedWifi?): Boolean =
        wifi != null && hasAccessPointOf(wifi) && security.isCompatibleWith(wifi.security)

    fun evaluate(
        areas: List<TrustedArea>,
        wifi: ConnectedWifi?,
        location: LocationReading?
    ): SafeStatus {
        if (areas.isEmpty()) return SafeStatus.NoSavedArea

        val candidates = areas.filter { it.recognizes(wifi) }
        if (candidates.isEmpty()) {
            val sameAccessPoint = areas.firstOrNull { it.hasAccessPointOf(wifi) }
            val sameName = areas.firstOrNull { it.ssid == wifi?.ssid }
            return when {
                wifi != null && sameAccessPoint != null ->
                    SafeStatus.SecurityMismatch(sameAccessPoint, wifi)
                sameName != null -> SafeStatus.UnknownAccessPoint(sameName)
                else -> SafeStatus.WrongNetwork(wifi?.ssid)
            }
        }

        if (location == null) return SafeStatus.NoLocationFix

        val byDistance = candidates
            .map { area ->
                area to GeoDistance.metersBetween(
                    area.latitude, area.longitude, location.latitude, location.longitude
                )
            }
            .sortedBy { (_, distance) -> distance }
        val accuracy = location.accuracyMeters

        byDistance.firstOrNull { (area, distance) ->
            (accuracy == null || accuracy <= area.radiusMeters) && distance <= area.radiusMeters
        }?.let { (area, _) -> return SafeStatus.Inside(area) }

        if (accuracy != null) {
            byDistance.firstOrNull { (area, distance) ->
                accuracy > area.radiusMeters && distance - accuracy <= area.radiusMeters
            }?.let { (area, _) ->
                return SafeStatus.LowAccuracy(accuracy.roundToInt(), area.radiusMeters)
            }
        }

        val (nearest, distance) = byDistance.first()
        return SafeStatus.OutOfRange(nearest, distance.roundToInt())
    }
}
