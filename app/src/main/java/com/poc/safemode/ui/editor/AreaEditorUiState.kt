package com.poc.safemode.ui.editor

import com.poc.safemode.domain.CoordinateParser
import com.poc.safemode.domain.NearbyWifi
import com.poc.safemode.domain.TrustedArea
import com.poc.safemode.domain.WifiProtocol
import com.poc.safemode.domain.WifiSecurity

data class AreaEditorUiState(
    val isEditing: Boolean = false,
    val name: String = "",
    val ssid: String = "",
    val bssids: Set<String> = emptySet(),
    val security: WifiSecurity = WifiSecurity.Unknown,
    val protocols: Set<WifiProtocol> = emptySet(),
    val nearbyNetworks: List<NearbyWifi>? = null,
    val latitude: String = "",
    val longitude: String = "",
    val radiusMeters: Int = TrustedArea.DEFAULT_RADIUS_METERS,
    val isLoadingWifi: Boolean = false,
    val isScanningWifi: Boolean = false,
    val isLoadingLocation: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false
) {
    val isLatitudeInvalid: Boolean
        get() = latitude.isNotBlank() && CoordinateParser.parseLatitude(latitude) == null

    val isLongitudeInvalid: Boolean
        get() = longitude.isNotBlank() && CoordinateParser.parseLongitude(longitude) == null

    val canSave: Boolean
        get() = !isSaving && toArea(id = "") != null

    fun toArea(id: String): TrustedArea? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || ssid.isEmpty() || bssids.isEmpty()) return null
        val parsedLatitude = CoordinateParser.parseLatitude(latitude) ?: return null
        val parsedLongitude = CoordinateParser.parseLongitude(longitude) ?: return null
        return TrustedArea(
            id, trimmedName, ssid, parsedLatitude, parsedLongitude, radiusMeters, bssids, security,
            protocols
        )
    }
}
