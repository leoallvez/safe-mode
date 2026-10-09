package com.poc.safemode.ui.editor

import com.poc.safemode.R
import com.poc.safemode.domain.LocationReading
import com.poc.safemode.domain.NearbyWifi
import com.poc.safemode.domain.TrustedArea
import com.poc.safemode.domain.WifiProtocol
import com.poc.safemode.domain.WifiSecurity
import com.poc.safemode.ui.FakeLocation
import com.poc.safemode.ui.FakeRepository
import com.poc.safemode.ui.FakeWifi
import com.poc.safemode.ui.MessageAction
import com.poc.safemode.ui.PermissionedAction
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AreaEditorViewModelTest {

    private val home =
        TrustedArea("1", "Casa", "CasaWifi", -23.5505, -46.6333, 150, setOf(HOME_AP))
    private val office =
        TrustedArea("2", "Trabalho", "Corp", -23.5614, -46.6559, 250, setOf(OFFICE_AP))

    private val repository = FakeRepository()
    private val wifi = FakeWifi(bssid = HOME_AP)
    private val location = FakeLocation()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(areaId: String? = null) =
        AreaEditorViewModel(areaId, repository, wifi, location, newId = { "new-id" })

    private fun AreaEditorViewModel.fillValidForm() {
        onNameChange("  Casa ")
        wifi.ssid = "CasaWifi"
        run(PermissionedAction.UseConnectedWifi)
        onLatitudeChange("-23,5505")
        onLongitudeChange("-46.6333")
        onRadiusChange(150)
    }

    @Test
    fun newAreaStartsEmptyEvenWithOtherAreasSaved() {
        repository.stored.value = listOf(home, office)
        val state = viewModel().uiState.value
        assertFalse(state.isEditing)
        assertEquals("", state.name)
        assertEquals("", state.ssid)
        assertTrue(state.bssids.isEmpty())
        assertFalse(state.canSave)
    }

    @Test
    fun editingLoadsTheAreaIntoTheForm() {
        repository.stored.value = listOf(home, office)
        val state = viewModel(office.id).uiState.value
        assertTrue(state.isEditing)
        assertEquals("Trabalho", state.name)
        assertEquals("Corp", state.ssid)
        assertEquals(setOf(OFFICE_AP), state.bssids)
        assertEquals("-23.561400", state.latitude)
        assertEquals("-46.655900", state.longitude)
        assertEquals(250, state.radiusMeters)
        assertTrue(state.canSave)
    }

    @Test
    fun saveRequiresNameNetworkAndValidCoordinates() {
        val viewModel = viewModel()
        viewModel.fillValidForm()
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.onNameChange("   ")
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.onNameChange("Casa")
        viewModel.onLatitudeChange("91")
        assertTrue(viewModel.uiState.value.isLatitudeInvalid)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.save()
        assertTrue(repository.stored.value.isEmpty())
        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun networkIsRequiredToSave() {
        val viewModel = viewModel()
        viewModel.onNameChange("Casa")
        viewModel.onLatitudeChange("-23.5505")
        viewModel.onLongitudeChange("-46.6333")
        assertFalse(viewModel.uiState.value.canSave)

        wifi.ssid = "CasaWifi"
        viewModel.run(PermissionedAction.UseConnectedWifi)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun areaWithoutAccessPointsCannotBeSavedUntilTheNetworkIsRegisteredAgain() {
        repository.stored.value = listOf(home.copy(bssids = emptySet()))
        val viewModel = viewModel(home.id)
        assertEquals("CasaWifi", viewModel.uiState.value.ssid)
        assertFalse(viewModel.uiState.value.canSave)

        wifi.ssid = "CasaWifi"
        viewModel.run(PermissionedAction.UseConnectedWifi)

        assertEquals(setOf(HOME_AP), viewModel.uiState.value.bssids)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun saveCreatesANewAreaWithTrimmedNameAndTheAccessPoint() {
        repository.stored.value = listOf(office)
        val viewModel = viewModel()
        viewModel.fillValidForm()

        viewModel.save()

        assertEquals(listOf(office, home.copy(id = "new-id")), repository.stored.value)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun saveWhileEditingReplacesTheAreaKeepingItsIdAndAccessPoints() {
        repository.stored.value = listOf(home, office)
        val viewModel = viewModel(home.id)
        viewModel.onNameChange("Casa nova")
        viewModel.onRadiusChange(300)

        viewModel.save()

        assertEquals(
            listOf(home.copy(name = "Casa nova", radiusMeters = 300), office),
            repository.stored.value
        )
    }

    @Test
    fun savingTwiceDoesNotDuplicate() {
        val viewModel = viewModel()
        viewModel.fillValidForm()

        viewModel.save()
        viewModel.save()

        assertEquals(1, repository.stored.value.size)
    }

    @Test
    fun saveFailureIsReportedAndKeepsTheEditorOpen() = runTest {
        repository.failOnWrite = true
        val viewModel = viewModel()
        viewModel.fillValidForm()

        viewModel.save()

        assertFalse(viewModel.uiState.value.isSaved)
        assertTrue(viewModel.uiState.value.canSave)
        assertEquals(R.string.msg_save_failed, viewModel.messages.first().textRes)
    }

    @Test
    fun radiusIsClampedToTheAllowedRange() {
        val viewModel = viewModel()
        viewModel.onRadiusChange(10_000)
        assertEquals(TrustedArea.MAX_RADIUS_METERS, viewModel.uiState.value.radiusMeters)
        viewModel.onRadiusChange(0)
        assertEquals(TrustedArea.MIN_RADIUS_METERS, viewModel.uiState.value.radiusMeters)
    }

    @Test
    fun useConnectedWifiAlsoRegistersOtherVisibleAccessPointsOfThatNetwork() {
        wifi.ssid = "CasaWifi"
        wifi.cached = setOf(HOME_AP, HOME_AP_5G)
        val viewModel = viewModel()

        viewModel.run(PermissionedAction.UseConnectedWifi)

        assertEquals("CasaWifi", viewModel.uiState.value.ssid)
        assertEquals(setOf(HOME_AP, HOME_AP_5G), viewModel.uiState.value.bssids)
        assertFalse(viewModel.uiState.value.isLoadingWifi)
    }

    @Test
    fun useConnectedWifiOnTheSameNetworkAddsTheAccessPoint() {
        repository.stored.value = listOf(home)
        wifi.ssid = "CasaWifi"
        wifi.bssid = HOME_AP_5G
        val viewModel = viewModel(home.id)

        viewModel.run(PermissionedAction.UseConnectedWifi)

        assertEquals(setOf(HOME_AP, HOME_AP_5G), viewModel.uiState.value.bssids)
    }

    @Test
    fun useConnectedWifiOnAnotherNetworkReplacesNameAndAccessPoints() {
        repository.stored.value = listOf(home)
        wifi.ssid = "Nova"
        wifi.bssid = OFFICE_AP
        val viewModel = viewModel(home.id)

        viewModel.run(PermissionedAction.UseConnectedWifi)

        assertEquals("Nova", viewModel.uiState.value.ssid)
        assertEquals(setOf(OFFICE_AP), viewModel.uiState.value.bssids)
    }

    @Test
    fun useConnectedWifiWithoutWifiKeepsTheCurrentNetwork() = runTest {
        repository.stored.value = listOf(home)
        wifi.ssid = null
        val viewModel = viewModel(home.id)

        viewModel.run(PermissionedAction.UseConnectedWifi)

        assertEquals("CasaWifi", viewModel.uiState.value.ssid)
        assertEquals(setOf(HOME_AP), viewModel.uiState.value.bssids)
        assertEquals(R.string.msg_wifi_not_connected, viewModel.messages.first().textRes)
    }

    @Test
    fun useConnectedWifiWithoutAccessPointAddressIsRefused() = runTest {
        wifi.ssid = "CasaWifi"
        wifi.bssid = null
        val viewModel = viewModel()

        viewModel.run(PermissionedAction.UseConnectedWifi)

        assertEquals("", viewModel.uiState.value.ssid)
        assertEquals(R.string.msg_wifi_no_access_point, viewModel.messages.first().textRes)
    }

    @Test
    fun captureLocationFillsBothFields() {
        location.reading = LocationReading(-23.5505, -46.6333, 12f)
        val viewModel = viewModel()

        viewModel.run(PermissionedAction.CaptureLocation)

        assertEquals("-23.550500", viewModel.uiState.value.latitude)
        assertEquals("-46.633300", viewModel.uiState.value.longitude)
        assertFalse(viewModel.uiState.value.isLoadingLocation)
    }

    @Test
    fun captureLocationWithoutFixReportsIt() = runTest {
        val viewModel = viewModel()

        viewModel.run(PermissionedAction.CaptureLocation)

        assertEquals("", viewModel.uiState.value.latitude)
        assertEquals(R.string.msg_location_unavailable, viewModel.messages.first().textRes)
    }

    @Test
    fun locationDisabledOffersSettingsAndSkipsTheProviders() = runTest {
        location.enabled = false
        wifi.ssid = "CasaWifi"
        val viewModel = viewModel()

        viewModel.run(PermissionedAction.UseConnectedWifi)

        val message = viewModel.messages.first()
        assertEquals(R.string.msg_location_disabled, message.textRes)
        assertEquals(MessageAction.OpenLocationSettings, message.action)
        assertEquals(0, wifi.calls)
    }

    @Test
    fun scanOffersNearbyNetworksAndPickingOneRegistersItsAccessPoints() {
        wifi.nearby = listOf(
            NearbyWifi("CasaWifi", setOf(HOME_AP, HOME_AP_5G)),
            NearbyWifi("Vizinho", setOf(NEIGHBOR_AP))
        )
        val viewModel = viewModel()

        viewModel.run(PermissionedAction.ScanNearbyWifi)
        assertEquals(wifi.nearby, viewModel.uiState.value.nearbyNetworks)
        assertEquals("", viewModel.uiState.value.ssid)
        assertFalse(viewModel.uiState.value.isScanningWifi)

        viewModel.onNearbyNetworkSelected(wifi.nearby.first { it.ssid == "CasaWifi" })
        assertEquals("CasaWifi", viewModel.uiState.value.ssid)
        assertEquals(setOf(HOME_AP, HOME_AP_5G), viewModel.uiState.value.bssids)
        assertNull(viewModel.uiState.value.nearbyNetworks)
    }

    @Test
    fun scanLeavesOutNetworksWithoutAnIdentifiedAccessPoint() {
        wifi.nearby = listOf(
            NearbyWifi("SemEndereco", emptySet()),
            NearbyWifi("Vizinho", setOf(NEIGHBOR_AP))
        )
        val viewModel = viewModel()

        viewModel.run(PermissionedAction.ScanNearbyWifi)

        assertEquals(listOf("Vizinho"), viewModel.uiState.value.nearbyNetworks?.map { it.ssid })
    }

    @Test
    fun dismissingTheNearbyListKeepsTheCurrentNetwork() {
        repository.stored.value = listOf(home)
        wifi.nearby = listOf(NearbyWifi("Vizinho", setOf(NEIGHBOR_AP)))
        val viewModel = viewModel(home.id)

        viewModel.run(PermissionedAction.ScanNearbyWifi)
        viewModel.onNearbySsidsDismissed()

        assertEquals("CasaWifi", viewModel.uiState.value.ssid)
        assertEquals(setOf(HOME_AP), viewModel.uiState.value.bssids)
        assertNull(viewModel.uiState.value.nearbyNetworks)
    }

    @Test
    fun scanWithNoResultsReportsItInsteadOfOpeningAnEmptyList() = runTest {
        val viewModel = viewModel()

        viewModel.run(PermissionedAction.ScanNearbyWifi)

        assertNull(viewModel.uiState.value.nearbyNetworks)
        assertEquals(R.string.msg_wifi_none_found, viewModel.messages.first().textRes)
    }

    @Test
    fun scanWithWifiOffAsksToTurnItOn() = runTest {
        wifi.wifiEnabled = false
        wifi.nearby = listOf(NearbyWifi("CasaWifi", setOf(HOME_AP)))
        val viewModel = viewModel()

        viewModel.run(PermissionedAction.ScanNearbyWifi)

        assertEquals(R.string.msg_wifi_disabled, viewModel.messages.first().textRes)
        assertEquals(0, wifi.scans)
    }

    @Test
    fun scanWithLocationOffOffersSettings() = runTest {
        location.enabled = false
        wifi.nearby = listOf(NearbyWifi("CasaWifi", setOf(HOME_AP)))
        val viewModel = viewModel()

        viewModel.run(PermissionedAction.ScanNearbyWifi)

        assertEquals(MessageAction.OpenLocationSettings, viewModel.messages.first().action)
        assertEquals(0, wifi.scans)
    }

    @Test
    fun prefillUsesTheConnectedNetworkAndItsAccessPointsForANewArea() {
        wifi.ssid = "CasaWifi"
        wifi.cached = setOf(HOME_AP_5G)
        val viewModel = viewModel()

        viewModel.prefillConnectedWifi()

        assertEquals("CasaWifi", viewModel.uiState.value.ssid)
        assertEquals(setOf(HOME_AP, HOME_AP_5G), viewModel.uiState.value.bssids)
    }

    @Test
    fun prefillNeverTouchesAnAreaBeingEdited() {
        repository.stored.value = listOf(home)
        wifi.ssid = "Outra"
        wifi.bssid = OFFICE_AP
        val viewModel = viewModel(home.id)

        viewModel.prefillConnectedWifi()

        assertEquals("CasaWifi", viewModel.uiState.value.ssid)
        assertEquals(setOf(HOME_AP), viewModel.uiState.value.bssids)
        assertEquals(0, wifi.calls)
    }

    @Test
    fun prefillIsSilentWhenThereIsNothingToRead() = runTest {
        wifi.ssid = null
        val withoutWifi = viewModel()
        withoutWifi.prefillConnectedWifi()
        assertEquals("", withoutWifi.uiState.value.ssid)

        wifi.ssid = "CasaWifi"
        wifi.bssid = null
        val withoutAccessPoint = viewModel()
        withoutAccessPoint.prefillConnectedWifi()
        assertEquals("", withoutAccessPoint.uiState.value.ssid)

        location.enabled = false
        wifi.bssid = HOME_AP
        val locationOff = viewModel()
        locationOff.prefillConnectedWifi()
        assertEquals("", locationOff.uiState.value.ssid)

        locationOff.run(PermissionedAction.UseConnectedWifi)
        assertEquals(R.string.msg_location_disabled, locationOff.messages.first().textRes)
    }

    @Test
    fun prefillRunsOnlyOnceAndDoesNotOverrideAChoice() {
        wifi.ssid = "CasaWifi"
        wifi.nearby = listOf(NearbyWifi("Vizinho", setOf(NEIGHBOR_AP)))
        val viewModel = viewModel()
        viewModel.prefillConnectedWifi()
        viewModel.run(PermissionedAction.ScanNearbyWifi)
        viewModel.onNearbyNetworkSelected(wifi.nearby.first { it.ssid == "Vizinho" })
        wifi.ssid = "Outra"

        viewModel.prefillConnectedWifi()

        assertEquals("Vizinho", viewModel.uiState.value.ssid)
        assertEquals(setOf(NEIGHBOR_AP), viewModel.uiState.value.bssids)
        assertEquals(1, wifi.calls)
    }

    @Test
    fun securityTypeOfTheConnectedNetworkIsSavedWithTheArea() {
        wifi.security = WifiSecurity.Personal
        val viewModel = viewModel()
        viewModel.fillValidForm()
        assertEquals(WifiSecurity.Personal, viewModel.uiState.value.security)

        viewModel.save()

        assertEquals(WifiSecurity.Personal, repository.stored.value.single().security)
    }

    @Test
    fun editingLoadsTheSecurityType() {
        repository.stored.value = listOf(home.copy(security = WifiSecurity.Enterprise))
        assertEquals(WifiSecurity.Enterprise, viewModel(home.id).uiState.value.security)
    }

    @Test
    fun pickingANearbyNetworkTakesItsSecurityType() {
        wifi.nearby = listOf(NearbyWifi("Cafe", setOf(NEIGHBOR_AP), WifiSecurity.Open))
        val viewModel = viewModel()

        viewModel.run(PermissionedAction.ScanNearbyWifi)
        viewModel.onNearbyNetworkSelected(wifi.nearby.single())

        assertEquals(WifiSecurity.Open, viewModel.uiState.value.security)
        assertEquals(setOf(NEIGHBOR_AP), viewModel.uiState.value.bssids)
    }

    @Test
    fun sameNameWithAnotherSecurityTypeReplacesInsteadOfAddingAccessPoints() {
        repository.stored.value = listOf(home.copy(security = WifiSecurity.Personal))
        wifi.ssid = "CasaWifi"
        wifi.bssid = NEIGHBOR_AP
        wifi.security = WifiSecurity.Open
        val viewModel = viewModel(home.id)

        viewModel.run(PermissionedAction.UseConnectedWifi)

        assertEquals(setOf(NEIGHBOR_AP), viewModel.uiState.value.bssids)
        assertEquals(WifiSecurity.Open, viewModel.uiState.value.security)
    }

    @Test
    fun unknownSecurityOnANewReadingKeepsTheKnownOneAndAddsTheAccessPoint() {
        repository.stored.value = listOf(home.copy(security = WifiSecurity.Personal))
        wifi.ssid = "CasaWifi"
        wifi.bssid = HOME_AP_5G
        wifi.security = WifiSecurity.Unknown
        val viewModel = viewModel(home.id)

        viewModel.run(PermissionedAction.UseConnectedWifi)

        assertEquals(setOf(HOME_AP, HOME_AP_5G), viewModel.uiState.value.bssids)
        assertEquals(WifiSecurity.Personal, viewModel.uiState.value.security)
    }

    @Test
    fun areaWithoutSecurityTypeLearnsItWhenTheNetworkIsRegisteredAgain() {
        repository.stored.value = listOf(home)
        wifi.ssid = "CasaWifi"
        wifi.security = WifiSecurity.Personal
        val viewModel = viewModel(home.id)

        viewModel.run(PermissionedAction.UseConnectedWifi)

        assertEquals(setOf(HOME_AP), viewModel.uiState.value.bssids)
        assertEquals(WifiSecurity.Personal, viewModel.uiState.value.security)
    }

    @Test
    fun protocolsOfTheConnectedNetworkAreSavedWithTheArea() {
        wifi.security = WifiSecurity.Personal
        wifi.protocols = setOf(WifiProtocol.Wpa2, WifiProtocol.Wpa3)
        val viewModel = viewModel()
        viewModel.fillValidForm()

        viewModel.save()

        assertEquals(
            setOf(WifiProtocol.Wpa2, WifiProtocol.Wpa3),
            repository.stored.value.single().protocols
        )
    }

    @Test
    fun pickingANearbyNetworkTakesItsProtocols() {
        wifi.nearby = listOf(
            NearbyWifi("Antiga", setOf(NEIGHBOR_AP), WifiSecurity.Wep, setOf(WifiProtocol.Wep))
        )
        val viewModel = viewModel()

        viewModel.run(PermissionedAction.ScanNearbyWifi)
        viewModel.onNearbyNetworkSelected(wifi.nearby.single())

        assertEquals(setOf(WifiProtocol.Wep), viewModel.uiState.value.protocols)
    }

    @Test
    fun readingWithoutProtocolsKeepsTheOnesAlreadyKnownForTheSameNetwork() {
        repository.stored.value = listOf(
            home.copy(security = WifiSecurity.Personal, protocols = setOf(WifiProtocol.Wpa2))
        )
        wifi.ssid = "CasaWifi"
        wifi.security = WifiSecurity.Personal
        val viewModel = viewModel(home.id)

        viewModel.run(PermissionedAction.UseConnectedWifi)
        assertEquals(setOf(WifiProtocol.Wpa2), viewModel.uiState.value.protocols)

        wifi.protocols = setOf(WifiProtocol.Wpa3)
        viewModel.run(PermissionedAction.UseConnectedWifi)
        assertEquals(setOf(WifiProtocol.Wpa3), viewModel.uiState.value.protocols)
    }

    private companion object {
        const val HOME_AP = "aa:00:00:00:00:01"
        const val HOME_AP_5G = "aa:00:00:00:00:02"
        const val OFFICE_AP = "bb:00:00:00:00:01"
        const val NEIGHBOR_AP = "ee:00:00:00:00:99"
    }
}
