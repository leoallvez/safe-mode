package com.poc.safemode.ui.areas

import com.poc.safemode.R
import com.poc.safemode.domain.LocationReading
import com.poc.safemode.domain.TrustedArea
import com.poc.safemode.domain.WifiProtocol
import com.poc.safemode.domain.WifiSecurity
import com.poc.safemode.ui.FakeLocation
import com.poc.safemode.ui.FakeRepository
import com.poc.safemode.ui.FakeWifi
import com.poc.safemode.ui.MessageAction
import com.poc.safemode.ui.ResArg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AreasViewModelTest {

    private val home = TrustedArea("1", "Casa", "CasaWifi", -23.5505, -46.6333, 150, setOf(HOME_AP))
    private val office = TrustedArea("2", "Trabalho", "Corp", -23.5614, -46.6559, 250, setOf(OFFICE_AP))

    private val repository = FakeRepository()

    private fun connectTo(ssid: String?, bssid: String? = null) {
        wifi.ssid = ssid
        wifi.bssid = bssid
    }

    private val wifi = FakeWifi()
    private val location = FakeLocation()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AreasViewModel(repository, wifi, location)

    @Test
    fun listsSavedAreasAndFollowsChanges() {
        repository.stored.value = listOf(home)
        val viewModel = viewModel()
        assertEquals(listOf(home), viewModel.uiState.value.areas)
        assertFalse(viewModel.uiState.value.isLoading)

        repository.stored.value = listOf(home, office)
        assertEquals(listOf(home, office), viewModel.uiState.value.areas)
    }

    @Test
    fun deleteRemovesOnlyThatArea() {
        repository.stored.value = listOf(home, office)
        val viewModel = viewModel()

        viewModel.delete(home.id)

        assertEquals(listOf(office), repository.stored.value)
        assertEquals(listOf(office), viewModel.uiState.value.areas)
    }

    @Test
    fun deleteFailureIsReportedAndKeepsTheArea() = runTest {
        repository.stored.value = listOf(home)
        repository.failOnWrite = true
        val viewModel = viewModel()

        viewModel.delete(home.id)

        assertEquals(listOf(home), viewModel.uiState.value.areas)
        assertEquals(R.string.msg_delete_failed, viewModel.messages.first().textRes)
    }

    @Test
    fun checkStatusWithoutAreasAsksToCreateOne() = runTest {
        val viewModel = viewModel()

        viewModel.checkStatus()

        assertEquals(R.string.msg_status_no_area, viewModel.messages.first().textRes)
        assertEquals(0, wifi.calls)
    }

    @Test
    fun checkStatusInsideNamesTheArea() = runTest {
        repository.stored.value = listOf(home, office)
        connectTo("Corp", OFFICE_AP)
        location.reading = LocationReading(office.latitude, office.longitude, 10f)
        val viewModel = viewModel()

        viewModel.checkStatus()

        val message = viewModel.messages.first()
        assertEquals(R.string.msg_status_inside, message.textRes)
        assertEquals(listOf<Any>("Trabalho"), message.args)
        assertFalse(viewModel.uiState.value.isCheckingStatus)
    }

    @Test
    fun checkStatusOnUnknownNetworkSkipsTheLocationRead() = runTest {
        repository.stored.value = listOf(home, office)
        connectTo("Vizinho", "ee:00:00:00:00:99")
        val viewModel = viewModel()

        viewModel.checkStatus()

        val message = viewModel.messages.first()
        assertEquals(R.string.msg_status_wrong_network, message.textRes)
        assertEquals(listOf<Any>("Vizinho"), message.args)
        assertEquals(0, location.calls)
    }

    @Test
    fun checkStatusWithoutWifi() = runTest {
        repository.stored.value = listOf(home)
        connectTo(null)
        val viewModel = viewModel()

        viewModel.checkStatus()

        assertEquals(R.string.msg_status_no_wifi, viewModel.messages.first().textRes)
    }

    @Test
    fun checkStatusOutOfRangeReportsDistanceAreaAndRadius() = runTest {
        repository.stored.value = listOf(home)
        connectTo("CasaWifi", HOME_AP)
        location.reading = LocationReading(home.latitude + 0.01, home.longitude, 10f)
        val viewModel = viewModel()

        viewModel.checkStatus()

        val message = viewModel.messages.first()
        assertEquals(R.string.msg_status_out_of_range, message.textRes)
        assertEquals(listOf<Any>(1112, "Casa", 150), message.args)
    }

    @Test
    fun checkStatusWithoutLocationFix() = runTest {
        repository.stored.value = listOf(home)
        connectTo("CasaWifi", HOME_AP)
        location.reading = null
        val viewModel = viewModel()

        viewModel.checkStatus()

        assertEquals(R.string.msg_location_unavailable, viewModel.messages.first().textRes)
    }

    @Test
    fun locationDisabledOffersSettingsAndSkipsTheProviders() = runTest {
        repository.stored.value = listOf(home)
        location.enabled = false
        val viewModel = viewModel()

        viewModel.checkStatus()

        val message = viewModel.messages.first()
        assertEquals(R.string.msg_location_disabled, message.textRes)
        assertEquals(MessageAction.OpenLocationSettings, message.action)
        assertEquals(0, wifi.calls)
        assertTrue(viewModel.uiState.value.areas.isNotEmpty())
    }

    @Test
    fun checkStatusOnAnUnregisteredAccessPointSkipsTheLocationRead() = runTest {
        repository.stored.value = listOf(home)
        connectTo("CasaWifi", "ee:00:00:00:00:99")
        location.reading = LocationReading(home.latitude, home.longitude, 10f)
        val viewModel = viewModel()

        viewModel.checkStatus()

        val message = viewModel.messages.first()
        assertEquals(R.string.msg_status_unknown_access_point, message.textRes)
        assertEquals(listOf<Any>("CasaWifi", "Casa"), message.args)
        assertEquals(0, location.calls)
    }

    @Test
    fun checkStatusWithAnotherSecurityTypeNamesBothAndSkipsTheLocationRead() = runTest {
        repository.stored.value = listOf(
            home.copy(
                security = WifiSecurity.Personal,
                protocols = setOf(WifiProtocol.Wpa2, WifiProtocol.Wpa3)
            )
        )
        connectTo("CasaWifi", HOME_AP)
        wifi.security = WifiSecurity.Open
        location.reading = LocationReading(home.latitude, home.longitude, 10f)
        val viewModel = viewModel()

        viewModel.checkStatus()

        val message = viewModel.messages.first()
        assertEquals(R.string.msg_status_security_mismatch, message.textRes)
        assertEquals("CasaWifi", message.args[0])
        val current = message.args[1] as ResArg
        assertEquals(R.string.wifi_security_open, current.res)
        assertEquals("Casa", message.args[2])
        val registered = message.args[3] as ResArg
        assertEquals(R.string.wifi_security_with_protocol, registered.res)
        assertEquals(R.string.wifi_security_personal, (registered.args[0] as ResArg).res)
        assertEquals("WPA2/WPA3", registered.args[1])
        assertEquals(0, location.calls)
    }

    private companion object {
        const val HOME_AP = "aa:00:00:00:00:01"
        const val OFFICE_AP = "bb:00:00:00:00:01"
    }
}
