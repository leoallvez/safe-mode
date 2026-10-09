package com.poc.safemode.ui.areas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
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
import com.poc.safemode.domain.SafeStatus
import com.poc.safemode.domain.SafeStatusEvaluator
import com.poc.safemode.domain.TrustedArea
import com.poc.safemode.ui.MessageAction
import com.poc.safemode.ui.UiMessage
import com.poc.safemode.ui.securityArg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AreasUiState(
    val areas: List<TrustedArea> = emptyList(),
    val isLoading: Boolean = true,
    val isCheckingStatus: Boolean = false
)

class AreasViewModel(
    private val repository: TrustedAreaRepository,
    private val wifiInfoProvider: WifiInfoProvider,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(AreasUiState())
    val uiState: StateFlow<AreasUiState> = _uiState.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.areas.collect { areas ->
                _uiState.update { it.copy(areas = areas, isLoading = false) }
            }
        }
    }

    fun delete(areaId: String) {
        viewModelScope.launch {
            try {
                repository.delete(areaId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                send(R.string.msg_delete_failed)
            }
        }
    }

    fun checkStatus() {
        val state = _uiState.value
        if (state.isLoading || state.isCheckingStatus) return
        val areas = state.areas
        if (areas.isEmpty()) {
            send(R.string.msg_status_no_area)
            return
        }
        if (!locationProvider.isLocationEnabled()) {
            send(R.string.msg_location_disabled, action = MessageAction.OpenLocationSettings)
            return
        }
        _uiState.update { it.copy(isCheckingStatus = true) }
        viewModelScope.launch {
            try {
                val wifi = wifiInfoProvider.connected()
                val location = if (SafeStatusEvaluator.anyAreaRecognizes(areas, wifi)) {
                    locationProvider.current()
                } else {
                    null
                }
                _messages.trySend(SafeStatusEvaluator.evaluate(areas, wifi, location).toMessage())
            } finally {
                _uiState.update { it.copy(isCheckingStatus = false) }
            }
        }
    }

    private fun send(textRes: Int, action: MessageAction? = null) {
        _messages.trySend(UiMessage(textRes, action = action))
    }

    private fun SafeStatus.toMessage(): UiMessage = when (this) {
        is SafeStatus.Inside -> UiMessage(R.string.msg_status_inside, listOf(area.name))
        is SafeStatus.WrongNetwork -> if (currentSsid == null) {
            UiMessage(R.string.msg_status_no_wifi)
        } else {
            UiMessage(R.string.msg_status_wrong_network, listOf(currentSsid))
        }
        is SafeStatus.UnknownAccessPoint ->
            UiMessage(R.string.msg_status_unknown_access_point, listOf(area.ssid, area.name))
        is SafeStatus.SecurityMismatch -> UiMessage(
            R.string.msg_status_security_mismatch,
            listOf(
                area.ssid,
                securityArg(wifi.security, wifi.protocols),
                area.name,
                securityArg(area.security, area.protocols)
            )
        )
        is SafeStatus.OutOfRange -> UiMessage(
            R.string.msg_status_out_of_range,
            listOf(distanceMeters, nearestArea.name, nearestArea.radiusMeters)
        )
        SafeStatus.NoSavedArea -> UiMessage(R.string.msg_status_no_area)
        SafeStatus.NoLocationFix -> UiMessage(R.string.msg_location_unavailable)
        is SafeStatus.LowAccuracy ->
            UiMessage(R.string.msg_status_low_accuracy, listOf(accuracyMeters, radiusMeters))
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val context = checkNotNull(this[APPLICATION_KEY]).applicationContext
                AreasViewModel(
                    repository = DataStoreTrustedAreaRepository(context),
                    wifiInfoProvider = AndroidWifiInfoProvider(context),
                    locationProvider = FusedLocationProvider(context)
                )
            }
        }
    }
}
