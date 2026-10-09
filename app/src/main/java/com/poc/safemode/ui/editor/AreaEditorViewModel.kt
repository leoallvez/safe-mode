package com.poc.safemode.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.poc.safemode.R
import com.poc.safemode.data.AndroidWifiInfoProvider
import com.poc.safemode.data.DataStoreTrustedAreaRepository
import com.poc.safemode.data.FusedLocationProvider
import com.poc.safemode.data.LocationProvider
import com.poc.safemode.data.TrustedAreaRepository
import com.poc.safemode.data.WifiInfoProvider
import com.poc.safemode.domain.CoordinateParser
import com.poc.safemode.domain.NearbyWifi
import com.poc.safemode.domain.TrustedArea
import com.poc.safemode.domain.WifiProtocol
import com.poc.safemode.domain.WifiSecurity
import com.poc.safemode.ui.MessageAction
import com.poc.safemode.ui.PermissionedAction
import com.poc.safemode.ui.Routes
import com.poc.safemode.ui.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AreaEditorViewModel(
    private val areaId: String?,
    private val repository: TrustedAreaRepository,
    private val wifiInfoProvider: WifiInfoProvider,
    private val locationProvider: LocationProvider,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(AreaEditorUiState(isEditing = areaId != null))
    val uiState: StateFlow<AreaEditorUiState> = _uiState.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            val editing = repository.areas.first().firstOrNull { it.id == areaId }
                ?: return@launch
            _uiState.update { current ->
                if (current.isPristine()) {
                    current.copy(
                        name = editing.name,
                        ssid = editing.ssid,
                        bssids = editing.bssids,
                        security = editing.security,
                        protocols = editing.protocols,
                        latitude = CoordinateParser.format(editing.latitude),
                        longitude = CoordinateParser.format(editing.longitude),
                        radiusMeters = editing.radiusMeters
                    )
                } else {
                    current
                }
            }
        }
    }

    private var prefillAttempted = false

    fun prefillConnectedWifi() {
        if (areaId != null || prefillAttempted) return
        prefillAttempted = true
        if (!locationProvider.isLocationEnabled()) return
        viewModelScope.launch {
            val wifi = wifiInfoProvider.connected() ?: return@launch
            val bssid = wifi.bssid ?: return@launch
            val accessPoints =
                setOf(bssid) + wifiInfoProvider.cachedAccessPoints(wifi.ssid, wifi.security)
            _uiState.update {
                if (it.ssid.isEmpty()) {
                    it.copy(
                        ssid = wifi.ssid,
                        bssids = accessPoints,
                        security = wifi.security,
                        protocols = wifi.protocols
                    )
                } else {
                    it
                }
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }

    fun onNearbyNetworkSelected(network: NearbyWifi) =
        applyNetwork(network.ssid, network.security, network.protocols, network.bssids)

    fun onNearbySsidsDismissed() = _uiState.update { it.copy(nearbyNetworks = null) }

    private fun applyNetwork(
        ssid: String,
        security: WifiSecurity,
        protocols: Set<WifiProtocol>,
        accessPoints: Set<String>
    ) = _uiState.update {
        val sameNetwork = it.ssid == ssid && it.security.isCompatibleWith(security)
        it.copy(
            ssid = ssid,
            bssids = if (sameNetwork) it.bssids + accessPoints else accessPoints,
            security = if (sameNetwork && security == WifiSecurity.Unknown) it.security else security,
            protocols = if (sameNetwork && protocols.isEmpty()) it.protocols else protocols,
            nearbyNetworks = null
        )
    }

    fun onLatitudeChange(value: String) = _uiState.update { it.copy(latitude = value) }

    fun onLongitudeChange(value: String) = _uiState.update { it.copy(longitude = value) }

    fun onRadiusChange(meters: Int) = _uiState.update {
        it.copy(
            radiusMeters = meters.coerceIn(
                TrustedArea.MIN_RADIUS_METERS,
                TrustedArea.MAX_RADIUS_METERS
            )
        )
    }

    fun run(action: PermissionedAction) {
        when (action) {
            PermissionedAction.UseConnectedWifi -> useConnectedWifi()
            PermissionedAction.ScanNearbyWifi -> scanNearbyWifi()
            PermissionedAction.CaptureLocation -> captureLocation()
            PermissionedAction.CheckStatus -> Unit
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isSaved) return
        val area = state.toArea(id = areaId ?: newId()) ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                repository.upsert(area)
                _uiState.update { it.copy(isSaved = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                send(R.string.msg_save_failed)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun useConnectedWifi() {
        if (_uiState.value.isLoadingWifi || !ensureLocationEnabled()) return
        _uiState.update { it.copy(isLoadingWifi = true) }
        viewModelScope.launch {
            try {
                val wifi = wifiInfoProvider.connected()
                val bssid = wifi?.bssid
                when {
                    wifi == null -> send(R.string.msg_wifi_not_connected)
                    bssid == null -> send(R.string.msg_wifi_no_access_point)
                    else -> applyNetwork(
                        wifi.ssid,
                        wifi.security,
                        wifi.protocols,
                        setOf(bssid) +
                            wifiInfoProvider.cachedAccessPoints(wifi.ssid, wifi.security)
                    )
                }
            } finally {
                _uiState.update { it.copy(isLoadingWifi = false) }
            }
        }
    }

    private fun scanNearbyWifi() {
        if (_uiState.value.isScanningWifi) return
        if (!wifiInfoProvider.isWifiEnabled()) {
            send(R.string.msg_wifi_disabled)
            return
        }

        if (!ensureLocationEnabled()) return
        _uiState.update { it.copy(isScanningWifi = true) }
        viewModelScope.launch {
            try {
                val networks = wifiInfoProvider.scanNearby().filter { it.bssids.isNotEmpty() }
                if (networks.isEmpty()) {
                    send(R.string.msg_wifi_none_found)
                } else {
                    _uiState.update { it.copy(nearbyNetworks = networks) }
                }
            } finally {
                _uiState.update { it.copy(isScanningWifi = false) }
            }
        }
    }

    private fun captureLocation() {
        if (_uiState.value.isLoadingLocation || !ensureLocationEnabled()) return
        _uiState.update { it.copy(isLoadingLocation = true) }
        viewModelScope.launch {
            try {
                val reading = locationProvider.current()
                if (reading == null) {
                    send(R.string.msg_location_unavailable)
                } else {
                    _uiState.update {
                        it.copy(
                            latitude = CoordinateParser.format(reading.latitude),
                            longitude = CoordinateParser.format(reading.longitude)
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(isLoadingLocation = false) }
            }
        }
    }

    private fun ensureLocationEnabled(): Boolean {
        if (locationProvider.isLocationEnabled()) return true
        send(R.string.msg_location_disabled, action = MessageAction.OpenLocationSettings)
        return false
    }

    private fun send(textRes: Int, action: MessageAction? = null) {
        _messages.trySend(UiMessage(textRes, action = action))
    }

    private fun AreaEditorUiState.isPristine(): Boolean =
        name.isEmpty() && ssid.isEmpty() && bssids.isEmpty() &&
            security == WifiSecurity.Unknown && latitude.isEmpty() && longitude.isEmpty() &&
            radiusMeters == TrustedArea.DEFAULT_RADIUS_METERS

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val context = checkNotNull(this[APPLICATION_KEY]).applicationContext
                AreaEditorViewModel(
                    areaId = createSavedStateHandle()[Routes.ARG_AREA_ID],
                    repository = DataStoreTrustedAreaRepository(context),
                    wifiInfoProvider = AndroidWifiInfoProvider(context),
                    locationProvider = FusedLocationProvider(context)
                )
            }
        }
    }
}
