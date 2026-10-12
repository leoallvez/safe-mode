package com.poc.safemode

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class WifiDetails(
    val ssid: String?,
    val bssid: String?,
    val securityType: String?,
)

enum class LocationAuthorization {
    DENIED,
    SERVICES_DISABLED,
    APPROXIMATE,
    PRECISE,
}

/** Reads the current Wi-Fi connection and device location straight from the platform APIs. */
class DeviceInfoReader(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun locationAuthorization(): LocationAuthorization = when {
        !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAuthorization.DENIED
        !LocationManagerCompat.isLocationEnabled(locationManager) ->
            LocationAuthorization.SERVICES_DISABLED
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAuthorization.PRECISE
        else -> LocationAuthorization.APPROXIMATE
    }

    /**
     * Returns null when the device is not connected to Wi-Fi. SSID and BSSID are only
     * exposed by the system with precise location granted and location services on.
     */
    suspend fun readWifiDetails(): WifiDetails? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            readWifiInfo()?.let { info ->
                WifiDetails(
                    ssid = info.ssid.toSsidOrNull(),
                    bssid = info.bssid.toBssidOrNull(),
                    securityType = securityTypeLabel(info.currentSecurityType),
                )
            }
        } else {
            readWifiDetailsLegacy()
        }

    @SuppressLint("MissingPermission")
    suspend fun readLocation(): Location? {
        val authorization = locationAuthorization()
        if (authorization == LocationAuthorization.DENIED ||
            authorization == LocationAuthorization.SERVICES_DISABLED
        ) {
            return null
        }

        val enabledProviders = locationManager.getProviders(true)
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (authorization == LocationAuthorization.PRECISE) add(LocationManager.GPS_PROVIDER)
        }.filter { it in enabledProviders }

        val current = providers.firstOrNull()?.let { provider ->
            withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MS) { currentLocation(provider) }
        }
        return current ?: providers
            .mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                provider,
                cancellationSignal,
                ContextCompat.getMainExecutor(appContext),
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }

    /**
     * From Android 12 on, the SSID/BSSID are redacted unless the WifiInfo comes from a
     * NetworkCallback registered with FLAG_INCLUDE_LOCATION_INFO.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun readWifiInfo(): WifiInfo? {
        val result = CompletableDeferred<WifiInfo>()
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                (networkCapabilities.transportInfo as? WifiInfo)?.let(result::complete)
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        return try {
            // No callback is delivered when there is no Wi-Fi network, hence the timeout.
            withTimeoutOrNull(WIFI_INFO_TIMEOUT_MS) { result.await() }
        } finally {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun readWifiDetailsLegacy(): WifiDetails? {
        val activeCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (activeCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return null

        val info = wifiManager.connectionInfo ?: return null
        val bssid = info.bssid.toBssidOrNull()
        // Before Android 12 the security type is only available in the scan results.
        val capabilities = bssid?.let {
            runCatching { wifiManager.scanResults.firstOrNull { it.BSSID == bssid }?.capabilities }
                .getOrNull()
        }
        return WifiDetails(
            ssid = info.ssid.toSsidOrNull(),
            bssid = bssid,
            securityType = capabilities?.let(::securityTypeFromCapabilities),
        )
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val WIFI_INFO_TIMEOUT_MS = 2_000L
        const val CURRENT_LOCATION_TIMEOUT_MS = 10_000L
    }
}

private const val UNKNOWN_SSID = "<unknown ssid>"
private const val REDACTED_BSSID = "02:00:00:00:00:00"

private fun String?.toSsidOrNull(): String? =
    this?.takeUnless { it == UNKNOWN_SSID }?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() }

private fun String?.toBssidOrNull(): String? = this?.takeUnless { it == REDACTED_BSSID }

@SuppressLint("InlinedApi")
internal fun securityTypeLabel(securityType: Int): String? = when (securityType) {
    WifiInfo.SECURITY_TYPE_OPEN -> "Open"
    WifiInfo.SECURITY_TYPE_WEP -> "WEP"
    WifiInfo.SECURITY_TYPE_PSK -> "WPA/WPA2 Personal"
    WifiInfo.SECURITY_TYPE_EAP -> "WPA/WPA2 Enterprise"
    WifiInfo.SECURITY_TYPE_SAE -> "WPA3 Personal"
    WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3 Enterprise"
    WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "WPA3 Enterprise 192-bit"
    WifiInfo.SECURITY_TYPE_OWE -> "Enhanced Open (OWE)"
    WifiInfo.SECURITY_TYPE_WAPI_PSK -> "WAPI Personal"
    WifiInfo.SECURITY_TYPE_WAPI_CERT -> "WAPI Certificate"
    WifiInfo.SECURITY_TYPE_OSEN -> "OSEN"
    WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2, WifiInfo.SECURITY_TYPE_PASSPOINT_R3 -> "Passpoint"
    WifiInfo.SECURITY_TYPE_DPP -> "Easy Connect (DPP)"
    else -> null
}

/** Maps a ScanResult capabilities string such as "[WPA2-PSK-CCMP][ESS]". */
internal fun securityTypeFromCapabilities(capabilities: String): String = when {
    "SAE" in capabilities -> "WPA3 Personal"
    "EAP" in capabilities -> "WPA/WPA2 Enterprise"
    "PSK" in capabilities -> "WPA/WPA2 Personal"
    "WEP" in capabilities -> "WEP"
    "OWE" in capabilities -> "Enhanced Open (OWE)"
    else -> "Open"
}
