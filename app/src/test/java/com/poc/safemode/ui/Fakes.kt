package com.poc.safemode.ui

import com.poc.safemode.data.LocationProvider
import com.poc.safemode.data.TrustedAreaRepository
import com.poc.safemode.data.WifiInfoProvider
import com.poc.safemode.domain.ConnectedWifi
import com.poc.safemode.domain.LocationReading
import com.poc.safemode.domain.NearbyWifi
import com.poc.safemode.domain.TrustedArea
import com.poc.safemode.domain.WifiProtocol
import com.poc.safemode.domain.WifiSecurity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException

class FakeRepository(initial: List<TrustedArea> = emptyList()) : TrustedAreaRepository {
    val stored = MutableStateFlow(initial)
    var failOnWrite = false
    override val areas: Flow<List<TrustedArea>> = stored

    override suspend fun upsert(area: TrustedArea) {
        if (failOnWrite) throw IOException("disk full")
        stored.value = if (stored.value.any { it.id == area.id }) {
            stored.value.map { if (it.id == area.id) area else it }
        } else {
            stored.value + area
        }
    }

    override suspend fun delete(id: String) {
        if (failOnWrite) throw IOException("disk full")
        stored.value = stored.value.filterNot { it.id == id }
    }
}

class FakeWifi(
    var ssid: String? = null,
    var bssid: String? = "aa:aa:aa:aa:aa:01",
    var security: WifiSecurity = WifiSecurity.Unknown,
    var protocols: Set<WifiProtocol> = emptySet()
) : WifiInfoProvider {
    var calls = 0
    var wifiEnabled = true
    var nearby: List<NearbyWifi> = emptyList()
    var cached: Set<String> = emptySet()
    var scans = 0

    override suspend fun connected(): ConnectedWifi? {
        calls++
        return ssid?.let { ConnectedWifi(it, bssid, security, protocols) }
    }

    override fun isWifiEnabled() = wifiEnabled

    override suspend fun scanNearby(): List<NearbyWifi> {
        scans++
        return nearby
    }

    override fun cachedAccessPoints(ssid: String, security: WifiSecurity): Set<String> = cached
}

class FakeLocation(var reading: LocationReading? = null) : LocationProvider {
    var enabled = true
    var calls = 0
    override fun isLocationEnabled() = enabled
    override suspend fun current(): LocationReading? {
        calls++
        return reading
    }
}
