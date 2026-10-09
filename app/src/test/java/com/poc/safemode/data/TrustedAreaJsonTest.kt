package com.poc.safemode.data

import com.poc.safemode.domain.TrustedArea
import com.poc.safemode.domain.WifiProtocol
import com.poc.safemode.domain.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedAreaJsonTest {

    private val areas = listOf(
        TrustedArea(
            "1", "Casa", "MinhaCasa_5G", -23.5505, -46.6333, 100,
            setOf("a4:91:b1:3c:5e:f0", "a4:91:b1:3c:5e:f1"), WifiSecurity.Personal,
            setOf(WifiProtocol.Wpa2, WifiProtocol.Wpa3)
        ),
        TrustedArea(
            "2", "Trabalho \"sede\"", "Rede com espaço, vírgula e ç", 40.0, 179.999999, 500,
            setOf("00:1a:2b:3c:4d:5e"), WifiSecurity.Open
        )
    )

    @Test
    fun roundTripsTheList() {
        assertEquals(areas, TrustedAreaJson.decode(TrustedAreaJson.encode(areas)))
    }

    @Test
    fun emptyListRoundTrips() {
        assertTrue(TrustedAreaJson.decode(TrustedAreaJson.encode(emptyList())).isEmpty())
    }

    @Test
    fun missingOrUnreadableContentIsAnEmptyList() {
        assertTrue(TrustedAreaJson.decode(null).isEmpty())
        assertTrue(TrustedAreaJson.decode("").isEmpty())
        assertTrue(TrustedAreaJson.decode("not json").isEmpty())
        assertTrue(TrustedAreaJson.decode("{\"id\":\"1\"}").isEmpty())
    }

    @Test
    fun incompleteItemsAreSkippedAndTheRestIsKept() {
        val json = """
            [
              {"id":"1","name":"Casa","ssid":"MinhaCasa_5G","bssids":["a4:91:b1:3c:5e:f0","a4:91:b1:3c:5e:f1"],"security":"Personal","protocols":["Wpa2","Wpa3","Wpa9"],"latitude":-23.5505,"longitude":-46.6333,"radiusMeters":100},
              {"id":"x","name":"Sem coordenadas","ssid":"Rede"},
              42
            ]
        """.trimIndent()
        assertEquals(listOf(areas[0]), TrustedAreaJson.decode(json))
    }

    @Test
    fun areaSavedBeforeBssidExistedIsReadWithNoAccessPoints() {
        val json = """
            [{"id":"1","name":"Casa","ssid":"MinhaCasa_5G","latitude":-23.5505,"longitude":-46.6333,"radiusMeters":100}]
        """.trimIndent()
        assertEquals(
            listOf(
                areas[0].copy(
                    bssids = emptySet(),
                    security = WifiSecurity.Unknown,
                    protocols = emptySet()
                )
            ),
            TrustedAreaJson.decode(json)
        )
    }

    @Test
    fun storedAccessPointsAreNormalizedAndPlaceholdersDropped() {
        val json = """
            [{"id":"1","name":"Casa","ssid":"MinhaCasa_5G","bssids":["A4:91:B1:3C:5E:F0","02:00:00:00:00:00",""],"latitude":-23.5505,"longitude":-46.6333,"radiusMeters":100}]
        """.trimIndent()
        assertEquals(setOf("a4:91:b1:3c:5e:f0"), TrustedAreaJson.decode(json).single().bssids)
    }

    @Test
    fun unrecognizedSecurityNameIsReadAsUnknown() {
        val json = """
            [{"id":"1","name":"Casa","ssid":"MinhaCasa_5G","bssids":["a4:91:b1:3c:5e:f0"],"security":"Quantum","latitude":-23.5505,"longitude":-46.6333,"radiusMeters":100}]
        """.trimIndent()
        assertEquals(WifiSecurity.Unknown, TrustedAreaJson.decode(json).single().security)
    }
}
