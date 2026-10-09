package com.poc.safemode.data

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.poc.safemode.domain.AccessPointReading
import com.poc.safemode.domain.ConnectedWifi
import com.poc.safemode.domain.NearbyWifi
import com.poc.safemode.domain.WifiSecurity
import com.poc.safemode.domain.groupByNetwork
import com.poc.safemode.domain.normalizeBssid
import com.poc.safemode.domain.normalizeSsid
import com.poc.safemode.domain.protocolsFromAndroidType
import com.poc.safemode.domain.protocolsFromCapabilities
import com.poc.safemode.domain.securityFromAndroidType
import com.poc.safemode.domain.securityFromCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

interface WifiInfoProvider {
    suspend fun connected(): ConnectedWifi?

    fun isWifiEnabled(): Boolean

    suspend fun scanNearby(): List<NearbyWifi>

    fun cachedAccessPoints(ssid: String, security: WifiSecurity): Set<String>
}

class AndroidWifiInfoProvider(context: Context) : WifiInfoProvider {

    private val appContext = context.applicationContext

    override suspend fun connected(): ConnectedWifi? {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wifiInfoFromNetworkCallback()
        } else {
            wifiInfoFromWifiManager()
        }
        val ssid = normalizeSsid(info?.ssid) ?: return null
        val bssid = normalizeBssid(info?.bssid)
        val advertised = bssid?.let { connectedBssid ->
            appContext.getSystemService(WifiManager::class.java)
                ?.let(::readAccessPoints)
                ?.firstOrNull { it.bssid == connectedBssid }
        }
        val reportedType = if (info != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.currentSecurityType
        } else {
            null
        }
        val reported = reportedType?.let(::securityFromAndroidType) ?: WifiSecurity.Unknown
        val security = if (reported != WifiSecurity.Unknown) {
            reported
        } else {
            advertised?.security ?: WifiSecurity.Unknown
        }
        val advertisedProtocols = advertised
            ?.takeIf { it.security == security }
            ?.protocols
            .orEmpty()
        val protocols = advertisedProtocols.ifEmpty {
            reportedType?.let(::protocolsFromAndroidType).orEmpty()
        }
        return ConnectedWifi(ssid, bssid, security, protocols)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun wifiInfoFromNetworkCallback(): WifiInfo? {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val infos = callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback(
                FLAG_INCLUDE_LOCATION_INFO
            ) {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    trySend(networkCapabilities.transportInfo as? WifiInfo)
                }
            }
            connectivityManager.registerNetworkCallback(request, callback)
            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        }
        return withTimeoutOrNull(CALLBACK_TIMEOUT_MILLIS) { infos.first() }
    }

    @Suppress("DEPRECATION")
    private fun wifiInfoFromWifiManager(): WifiInfo? =
        appContext.getSystemService(WifiManager::class.java)?.connectionInfo

    override fun isWifiEnabled(): Boolean =
        appContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true

    override suspend fun scanNearby(): List<NearbyWifi> {
        val wifiManager = appContext.getSystemService(WifiManager::class.java)
            ?: return emptyList()
        val scanFinished = callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    trySend(Unit)
                }
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            val started = try {
                @Suppress("DEPRECATION")
                wifiManager.startScan()
            } catch (_: SecurityException) {
                false
            }
            if (!started) trySend(Unit)
            awaitClose { appContext.unregisterReceiver(receiver) }
        }
        withTimeoutOrNull(SCAN_TIMEOUT_MILLIS) { scanFinished.first() }
        return groupByNetwork(readAccessPoints(wifiManager))
    }

    override fun cachedAccessPoints(ssid: String, security: WifiSecurity): Set<String> {
        if (security == WifiSecurity.Unknown) return emptySet()
        val wifiManager = appContext.getSystemService(WifiManager::class.java)
            ?: return emptySet()
        return readAccessPoints(wifiManager)
            .filter { it.ssid == ssid && it.security == security }
            .mapNotNull { it.bssid }
            .toSet()
    }

    @SuppressLint("MissingPermission")
    private fun readAccessPoints(wifiManager: WifiManager): List<AccessPointReading> {
        val results = try {
            wifiManager.scanResults.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        return results.mapNotNull { result ->
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.wifiSsid?.toString()
            } else {
                @Suppress("DEPRECATION")
                result.SSID
            }
            normalizeSsid(raw)?.let { ssid ->
                AccessPointReading(
                    ssid = ssid,
                    bssid = normalizeBssid(result.BSSID),
                    level = result.level,
                    security = securityFromCapabilities(result.capabilities),
                    protocols = protocolsFromCapabilities(result.capabilities)
                )
            }
        }
    }

    private companion object {
        const val CALLBACK_TIMEOUT_MILLIS = 2_000L
        const val SCAN_TIMEOUT_MILLIS = 10_000L
    }
}
