package com.poc.safemode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiTest {

    @Test
    fun ssidStripsSurroundingQuotes() {
        assertEquals("MinhaCasa_5G", normalizeSsid("\"MinhaCasa_5G\""))
    }

    @Test
    fun ssidKeepsUnquotedHex() {
        assertEquals("4d696e6861", normalizeSsid("4d696e6861"))
    }

    @Test
    fun ssidKeepsQuotesThatArePartOfTheName() {
        assertEquals("a\"b", normalizeSsid("\"a\"b\""))
    }

    @Test
    fun unknownOrEmptySsidBecomesNull() {
        assertNull(normalizeSsid(null))
        assertNull(normalizeSsid(""))
        assertNull(normalizeSsid("<unknown ssid>"))
        assertNull(normalizeSsid("\"\""))
    }

    @Test
    fun bssidIsLowercasedAndTrimmed() {
        assertEquals("a4:91:b1:3c:5e:f0", normalizeBssid(" A4:91:B1:3C:5E:F0 "))
    }

    @Test
    fun placeholderOrEmptyBssidBecomesNull() {
        assertNull(normalizeBssid(null))
        assertNull(normalizeBssid(""))
        assertNull(normalizeBssid("02:00:00:00:00:00"))
        assertNull(normalizeBssid("00:00:00:00:00:00"))
    }

    @Test
    fun groupByNetworkCollectsAllAccessPointsOfTheSameName() {
        val readings = listOf(
            AccessPointReading("Fraca", "aa:00:00:00:00:01", -85),
            AccessPointReading("Casa", "aa:00:00:00:00:02", -70),
            AccessPointReading("Forte", "aa:00:00:00:00:03", -40),
            AccessPointReading("Casa", "aa:00:00:00:00:04", -50)
        )

        assertEquals(
            listOf(
                NearbyWifi("Forte", setOf("aa:00:00:00:00:03")),
                NearbyWifi("Casa", setOf("aa:00:00:00:00:02", "aa:00:00:00:00:04")),
                NearbyWifi("Fraca", setOf("aa:00:00:00:00:01"))
            ),
            groupByNetwork(readings)
        )
    }

    @Test
    fun groupByNetworkKeepsNetworksWithoutIdentifiedAccessPoint() {
        assertEquals(
            listOf(NearbyWifi("Casa", emptySet())),
            groupByNetwork(listOf(AccessPointReading("Casa", null, -60)))
        )
    }

    @Test
    fun groupByNetworkBreaksTiesByName() {
        val readings = listOf(
            AccessPointReading("B", "aa:00:00:00:00:01", -60),
            AccessPointReading("A", "aa:00:00:00:00:02", -60)
        )
        assertEquals(listOf("A", "B"), groupByNetwork(readings).map { it.ssid })
        assertEquals(emptyList<NearbyWifi>(), groupByNetwork(emptyList()))
    }

    @Test
    fun securityFromScanCapabilities() {
        assertEquals(WifiSecurity.Open, securityFromCapabilities("[ESS]"))
        assertEquals(WifiSecurity.Open, securityFromCapabilities(""))
        assertEquals(WifiSecurity.Open, securityFromCapabilities("[RSN-OWE-CCMP][ESS]"))
        assertEquals(WifiSecurity.Wep, securityFromCapabilities("[WEP][ESS]"))
        assertEquals(WifiSecurity.Personal, securityFromCapabilities("[WPA2-PSK-CCMP][ESS]"))
        assertEquals(WifiSecurity.Personal, securityFromCapabilities("[RSN-SAE-CCMP][ESS]"))
        assertEquals(
            WifiSecurity.Personal,
            securityFromCapabilities("[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS][WPS]")
        )
        assertEquals(WifiSecurity.Enterprise, securityFromCapabilities("[WPA2-EAP-CCMP][ESS]"))
        assertEquals(WifiSecurity.Unknown, securityFromCapabilities(null))
    }

    @Test
    fun securityFromAndroidSecurityType() {
        assertEquals(WifiSecurity.Open, securityFromAndroidType(0))
        assertEquals(WifiSecurity.Open, securityFromAndroidType(6))
        assertEquals(WifiSecurity.Wep, securityFromAndroidType(1))
        assertEquals(WifiSecurity.Personal, securityFromAndroidType(2))
        assertEquals(WifiSecurity.Personal, securityFromAndroidType(4))
        assertEquals(WifiSecurity.Enterprise, securityFromAndroidType(3))
        assertEquals(WifiSecurity.Enterprise, securityFromAndroidType(9))
        assertEquals(WifiSecurity.Unknown, securityFromAndroidType(-1))
        assertEquals(WifiSecurity.Unknown, securityFromAndroidType(99))
    }

    @Test
    fun onlyOpenAndWepAreWeak() {
        assertEquals(
            listOf(WifiSecurity.Open, WifiSecurity.Wep),
            WifiSecurity.entries.filter { it.isWeak }
        )
    }

    @Test
    fun unknownSecurityIsCompatibleWithAnything() {
        assertEquals(true, WifiSecurity.Unknown.isCompatibleWith(WifiSecurity.Open))
        assertEquals(true, WifiSecurity.Personal.isCompatibleWith(WifiSecurity.Unknown))
        assertEquals(true, WifiSecurity.Personal.isCompatibleWith(WifiSecurity.Personal))
        assertEquals(false, WifiSecurity.Personal.isCompatibleWith(WifiSecurity.Open))
    }

    @Test
    fun groupByNetworkKeepsSameNameWithDifferentSecurityApart() {
        val readings = listOf(
            AccessPointReading("Casa", "aa:00:00:00:00:01", -50, WifiSecurity.Personal),
            AccessPointReading("Casa", "aa:00:00:00:00:02", -55, WifiSecurity.Personal),
            AccessPointReading("Casa", "ee:00:00:00:00:99", -40, WifiSecurity.Open)
        )

        assertEquals(
            listOf(
                NearbyWifi("Casa", setOf("ee:00:00:00:00:99"), WifiSecurity.Open),
                NearbyWifi(
                    "Casa", setOf("aa:00:00:00:00:01", "aa:00:00:00:00:02"), WifiSecurity.Personal
                )
            ),
            groupByNetwork(readings)
        )
    }

    @Test
    fun protocolsFromScanCapabilities() {
        assertEquals(emptySet<WifiProtocol>(), protocolsFromCapabilities("[ESS]"))
        assertEquals(emptySet<WifiProtocol>(), protocolsFromCapabilities(null))
        assertEquals(setOf(WifiProtocol.Wep), protocolsFromCapabilities("[WEP][ESS]"))
        assertEquals(setOf(WifiProtocol.Wpa), protocolsFromCapabilities("[WPA-PSK-TKIP][ESS]"))
        assertEquals(setOf(WifiProtocol.Wpa2), protocolsFromCapabilities("[WPA2-PSK-CCMP][ESS]"))
        assertEquals(setOf(WifiProtocol.Wpa2), protocolsFromCapabilities("[RSN-PSK-CCMP][ESS]"))
        assertEquals(setOf(WifiProtocol.Wpa3), protocolsFromCapabilities("[RSN-SAE-CCMP][ESS]"))
        assertEquals(
            setOf(WifiProtocol.Wpa2, WifiProtocol.Wpa3),
            protocolsFromCapabilities("[RSN-PSK+SAE-CCMP][ESS][WPS]")
        )
        assertEquals(
            setOf(WifiProtocol.Wpa, WifiProtocol.Wpa2),
            protocolsFromCapabilities("[WPA-PSK-TKIP+CCMP][WPA2-PSK-CCMP][ESS]")
        )
        assertEquals(setOf(WifiProtocol.Wpa2), protocolsFromCapabilities("[WPA2-EAP-CCMP][ESS]"))
        assertEquals(
            setOf(WifiProtocol.Wpa3),
            protocolsFromCapabilities("[RSN-EAP_SUITE_B_192-GCMP-256][ESS]")
        )
        assertEquals(setOf(WifiProtocol.Owe), protocolsFromCapabilities("[RSN-OWE-CCMP][ESS]"))
    }

    @Test
    fun protocolsFromAndroidSecurityType() {
        assertEquals(emptySet<WifiProtocol>(), protocolsFromAndroidType(0))
        assertEquals(setOf(WifiProtocol.Wep), protocolsFromAndroidType(1))
        assertEquals(setOf(WifiProtocol.Wpa, WifiProtocol.Wpa2), protocolsFromAndroidType(2))
        assertEquals(setOf(WifiProtocol.Wpa3), protocolsFromAndroidType(4))
        assertEquals(setOf(WifiProtocol.Owe), protocolsFromAndroidType(6))
        assertEquals(emptySet<WifiProtocol>(), protocolsFromAndroidType(-1))
    }

    @Test
    fun protocolLabelIsOrderedAndJoinedWithSlash() {
        assertEquals("WPA2/WPA3", setOf(WifiProtocol.Wpa3, WifiProtocol.Wpa2).label())
        assertEquals("WPA/WPA2", setOf(WifiProtocol.Wpa2, WifiProtocol.Wpa).label())
        assertEquals("WEP", setOf(WifiProtocol.Wep).label())
        assertEquals("", emptySet<WifiProtocol>().label())
    }

    @Test
    fun groupByNetworkMergesTheProtocolsOfItsAccessPoints() {
        val readings = listOf(
            AccessPointReading(
                "Casa", "aa:00:00:00:00:01", -50, WifiSecurity.Personal, setOf(WifiProtocol.Wpa2)
            ),
            AccessPointReading(
                "Casa", "aa:00:00:00:00:02", -55, WifiSecurity.Personal, setOf(WifiProtocol.Wpa3)
            )
        )

        assertEquals(
            setOf(WifiProtocol.Wpa2, WifiProtocol.Wpa3),
            groupByNetwork(readings).single().protocols
        )
    }
}
