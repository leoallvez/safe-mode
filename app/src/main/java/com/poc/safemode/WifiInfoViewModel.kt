package com.poc.safemode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val UNKNOWN = "Unknown"

data class WifiInfoUiState(
    val ssid: String = UNKNOWN,
    val bssid: String = UNKNOWN,
    val securityType: String = UNKNOWN,
    val authorizationStatus: String = UNKNOWN,
    val latitude: String = UNKNOWN,
    val longitude: String = UNKNOWN,
    val isLoading: Boolean = false,
)

class WifiInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val reader = DeviceInfoReader(application)

    private val _uiState = MutableStateFlow(WifiInfoUiState())
    val uiState: StateFlow<WifiInfoUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    authorizationStatus = reader.locationAuthorization().label(),
                    isLoading = true,
                )
            }
            // Wi-Fi answers almost instantly while a location fix can take seconds,
            // so each one updates the screen as soon as it is ready.
            coroutineScope {
                launch {
                    val wifi = orNullIfRevoked { reader.readWifiDetails() }
                    _uiState.update {
                        it.copy(
                            ssid = wifi?.ssid ?: UNKNOWN,
                            bssid = wifi?.bssid ?: UNKNOWN,
                            securityType = wifi?.securityType ?: UNKNOWN,
                        )
                    }
                }
                launch {
                    val location = orNullIfRevoked { reader.readLocation() }
                    _uiState.update {
                        it.copy(
                            latitude = location?.latitude?.formatCoordinate() ?: UNKNOWN,
                            longitude = location?.longitude?.formatCoordinate() ?: UNKNOWN,
                        )
                    }
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}

/** A permission can be revoked between the check and the read. */
private inline fun <T> orNullIfRevoked(block: () -> T?): T? =
    try {
        block()
    } catch (_: SecurityException) {
        null
    }

private fun LocationAuthorization.label(): String = when (this) {
    LocationAuthorization.DENIED -> "Denied"
    LocationAuthorization.SERVICES_DISABLED -> "Location Services Disabled"
    LocationAuthorization.APPROXIMATE -> "Authorized When In Use (Approximate)"
    LocationAuthorization.PRECISE -> "Authorized When In Use"
}

private fun Double.formatCoordinate(): String = String.format(Locale.getDefault(), "%.6f", this)
