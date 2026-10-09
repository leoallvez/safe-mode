package com.poc.safemode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeStatusEvaluatorTest {

    private val homeAp = "aa:00:00:00:00:01"
    private val homeAp5g = "aa:00:00:00:00:02"
    private val officeAp = "bb:00:00:00:00:01"
    private val branchAp = "bb:00:00:00:00:02"
    private val strangerAp = "ee:00:00:00:00:99"

    private val home = TrustedArea("1", "Casa", "CasaWifi", 0.0, 0.0, 100, setOf(homeAp, homeAp5g))

    private val office = TrustedArea("2", "Trabalho", "Corp", 1.0, 0.0, 200, setOf(officeAp))
    private val branch = TrustedArea("3", "Filial", "Corp", 2.0, 0.0, 200, setOf(branchAp))

    private val atHome = ConnectedWifi("CasaWifi", homeAp)

    private fun readingAt(latitude: Double, accuracy: Float? = 10f) =
        LocationReading(latitude, 0.0, accuracy)

    private fun evaluate(
        areas: List<TrustedArea>,
        wifi: ConnectedWifi?,
        location: LocationReading?
    ) = SafeStatusEvaluator.evaluate(areas, wifi, location)

    @Test
    fun noSavedArea() {
        assertEquals(SafeStatus.NoSavedArea, evaluate(emptyList(), atHome, readingAt(0.0)))
    }

    @Test
    fun insideWhenAccessPointMatchesAndWithinRadius() {
        assertEquals(
            SafeStatus.Inside(home),
            evaluate(listOf(home, office), atHome, readingAt(0.0005))
        )
    }

    @Test
    fun anyRegisteredAccessPointOfTheAreaIsAccepted() {
        assertEquals(
            SafeStatus.Inside(home),
            evaluate(listOf(home), ConnectedWifi("CasaWifi", homeAp5g), readingAt(0.0))
        )
    }

    @Test
    fun sameNameOnAnUnregisteredAccessPointIsRejectedEvenAtTheExactPoint() {
        assertEquals(
            SafeStatus.UnknownAccessPoint(home),
            evaluate(listOf(home), ConnectedWifi("CasaWifi", strangerAp), readingAt(0.0))
        )
    }

    @Test
    fun registeredAccessPointUnderAnotherNameIsRejected() {
        assertEquals(
            SafeStatus.WrongNetwork("Renomeada"),
            evaluate(listOf(home), ConnectedWifi("Renomeada", homeAp), readingAt(0.0))
        )
    }

    @Test
    fun hiddenAccessPointAddressIsNeverAccepted() {
        assertEquals(
            SafeStatus.UnknownAccessPoint(home),
            evaluate(listOf(home), ConnectedWifi("CasaWifi", null), readingAt(0.0))
        )
    }

    @Test
    fun areaWithoutAccessPointsIsNeverRecognized() {
        val legacy = home.copy(bssids = emptySet())
        assertEquals(
            SafeStatus.UnknownAccessPoint(legacy),
            evaluate(listOf(legacy), atHome, readingAt(0.0))
        )
    }

    @Test
    fun wrongNetworkEvenAtTheExactPoint() {
        assertEquals(
            SafeStatus.WrongNetwork("Outra"),
            evaluate(listOf(home, office), ConnectedWifi("Outra", strangerAp), readingAt(0.0))
        )
    }

    @Test
    fun noWifiIsWrongNetworkWithNullSsid() {
        assertEquals(SafeStatus.WrongNetwork(null), evaluate(listOf(home), null, null))
    }

    @Test
    fun ssidComparisonIsCaseSensitive() {
        val status = evaluate(listOf(home), ConnectedWifi("casawifi", homeAp), readingAt(0.0))
        assertTrue(status is SafeStatus.WrongNetwork)
    }

    @Test
    fun insideRequiresTheNetworkOfThatSameArea() {
        val status = evaluate(listOf(home, office), ConnectedWifi("Corp", officeAp), readingAt(0.0))
        assertTrue(status is SafeStatus.OutOfRange)
        assertEquals(office, (status as SafeStatus.OutOfRange).nearestArea)
    }

    @Test
    fun sameNetworkNameInTwoPlacesIsToldApartByAccessPoint() {
        val status = evaluate(
            listOf(office, branch), ConnectedWifi("Corp", officeAp), readingAt(2.0)
        )
        assertTrue(status is SafeStatus.OutOfRange)
        assertEquals(office, (status as SafeStatus.OutOfRange).nearestArea)

        assertEquals(
            SafeStatus.Inside(branch),
            evaluate(listOf(office, branch), ConnectedWifi("Corp", branchAp), readingAt(2.0005))
        )
    }

    @Test
    fun outOfRangeReportsTheNearestRecognizedAreaAndDistance() {
        val shared = setOf(officeAp)
        val areas = listOf(office.copy(bssids = shared), branch.copy(bssids = shared))
        val status = evaluate(areas, ConnectedWifi("Corp", officeAp), readingAt(1.9))
        assertEquals(
            SafeStatus.OutOfRange(nearestArea = areas[1], distanceMeters = 11_119),
            status
        )
    }

    @Test
    fun noLocationFixWhenNetworkMatchesButThereIsNoReading() {
        assertEquals(SafeStatus.NoLocationFix, evaluate(listOf(home), atHome, null))
    }

    @Test
    fun lowAccuracyWhenWorseThanRadiusAndAreaIsWithinReach() {
        assertEquals(
            SafeStatus.LowAccuracy(accuracyMeters = 150, radiusMeters = 100),
            evaluate(listOf(home), atHome, readingAt(0.0, accuracy = 150f))
        )
        assertEquals(
            SafeStatus.LowAccuracy(accuracyMeters = 150, radiusMeters = 100),
            evaluate(listOf(home), atHome, readingAt(0.002, accuracy = 150f))
        )
    }

    @Test
    fun imprecisionDoesNotMatterWhenTheAreaIsFarBeyondIt() {
        val status = evaluate(listOf(home), atHome, readingAt(1.0, accuracy = 150f))
        assertTrue(status is SafeStatus.OutOfRange)
    }

    @Test
    fun accuracyEqualToRadiusIsAccepted() {
        assertEquals(
            SafeStatus.Inside(home),
            evaluate(listOf(home), atHome, readingAt(0.0, accuracy = 100f))
        )
    }

    @Test
    fun missingAccuracyIsAccepted() {
        assertEquals(
            SafeStatus.Inside(home),
            evaluate(listOf(home), atHome, readingAt(0.0, accuracy = null))
        )
    }

    @Test
    fun anyAreaRecognizes() {
        val areas = listOf(home, office)
        assertTrue(SafeStatusEvaluator.anyAreaRecognizes(areas, ConnectedWifi("Corp", officeAp)))
        assertFalse(SafeStatusEvaluator.anyAreaRecognizes(areas, ConnectedWifi("Corp", homeAp)))
        assertFalse(
            SafeStatusEvaluator.anyAreaRecognizes(areas, ConnectedWifi("CasaWifi", strangerAp))
        )
        assertFalse(SafeStatusEvaluator.anyAreaRecognizes(areas, null))
    }

    @Test
    fun sameAccessPointWithAnotherSecurityTypeIsRejected() {
        val protectedHome = home.copy(security = WifiSecurity.Personal)
        val openTwin = ConnectedWifi("CasaWifi", homeAp, WifiSecurity.Open)

        assertEquals(
            SafeStatus.SecurityMismatch(protectedHome, openTwin),
            evaluate(listOf(protectedHome), openTwin, readingAt(0.0))
        )
        assertFalse(SafeStatusEvaluator.anyAreaRecognizes(listOf(protectedHome), openTwin))
    }

    @Test
    fun sameSecurityTypeIsAccepted() {
        val protectedHome = home.copy(security = WifiSecurity.Personal)
        assertEquals(
            SafeStatus.Inside(protectedHome),
            evaluate(
                listOf(protectedHome),
                ConnectedWifi("CasaWifi", homeAp, WifiSecurity.Personal),
                readingAt(0.0)
            )
        )
    }

    @Test
    fun unknownSecurityOnEitherSideIsNotCompared() {
        val protectedHome = home.copy(security = WifiSecurity.Personal)
        assertEquals(
            SafeStatus.Inside(protectedHome),
            evaluate(listOf(protectedHome), atHome, readingAt(0.0))
        )
        assertEquals(
            SafeStatus.Inside(home),
            evaluate(
                listOf(home), ConnectedWifi("CasaWifi", homeAp, WifiSecurity.Open), readingAt(0.0)
            )
        )
    }

    @Test
    fun unknownAccessPointIsReportedBeforeSecurity() {
        val protectedHome = home.copy(security = WifiSecurity.Personal)
        assertEquals(
            SafeStatus.UnknownAccessPoint(protectedHome),
            evaluate(
                listOf(protectedHome),
                ConnectedWifi("CasaWifi", strangerAp, WifiSecurity.Open),
                readingAt(0.0)
            )
        )
    }
}
