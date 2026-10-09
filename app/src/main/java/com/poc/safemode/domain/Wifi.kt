package com.poc.safemode.domain

enum class WifiSecurity {
    Open,
    Wep,
    Personal,
    Enterprise,
    Unknown;

    val isWeak: Boolean
        get() = this == Open || this == Wep

    fun isCompatibleWith(other: WifiSecurity): Boolean =
        this == Unknown || other == Unknown || this == other
}

enum class WifiProtocol(val label: String) {
    Wep("WEP"),
    Wpa("WPA"),
    Wpa2("WPA2"),
    Wpa3("WPA3"),
    Owe("OWE"),
    Wapi("WAPI")
}

fun Set<WifiProtocol>.label(): String = sorted().joinToString("/") { it.label }

data class ConnectedWifi(
    val ssid: String,
    val bssid: String?,
    val security: WifiSecurity = WifiSecurity.Unknown,
    val protocols: Set<WifiProtocol> = emptySet()
)

data class NearbyWifi(
    val ssid: String,
    val bssids: Set<String>,
    val security: WifiSecurity = WifiSecurity.Unknown,
    val protocols: Set<WifiProtocol> = emptySet()
)

data class AccessPointReading(
    val ssid: String,
    val bssid: String?,
    val level: Int,
    val security: WifiSecurity = WifiSecurity.Unknown,
    val protocols: Set<WifiProtocol> = emptySet()
)

private const val UNKNOWN_SSID = "<unknown ssid>"

fun normalizeSsid(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == UNKNOWN_SSID) return null
    val unquoted = if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
        raw.substring(1, raw.length - 1)
    } else {
        raw
    }
    return unquoted.takeIf { it.isNotEmpty() }
}

private val PLACEHOLDER_BSSIDS = setOf("02:00:00:00:00:00", "00:00:00:00:00:00")

fun normalizeBssid(raw: String?): String? {
    val bssid = raw?.trim()?.lowercase()
    if (bssid.isNullOrEmpty() || bssid in PLACEHOLDER_BSSIDS) return null
    return bssid
}

fun securityFromCapabilities(capabilities: String?): WifiSecurity {
    if (capabilities == null) return WifiSecurity.Unknown
    val upper = capabilities.uppercase()
    return when {
        "EAP" in upper -> WifiSecurity.Enterprise
        "PSK" in upper || "SAE" in upper -> WifiSecurity.Personal
        "WEP" in upper -> WifiSecurity.Wep
        else -> WifiSecurity.Open
    }
}

private val CAPABILITY_TOKEN = Regex("""\[([^\]]+)]""")

fun protocolsFromCapabilities(capabilities: String?): Set<WifiProtocol> {
    if (capabilities == null) return emptySet()
    val protocols = mutableSetOf<WifiProtocol>()
    CAPABILITY_TOKEN.findAll(capabilities.uppercase()).forEach { match ->
        val token = match.groupValues[1]
        when {
            token.startsWith("WEP") -> protocols += WifiProtocol.Wep
            token.startsWith("WPA-") -> protocols += WifiProtocol.Wpa
            token.startsWith("WPA2-") || token.startsWith("RSN-") -> {
                if ("OWE" in token) protocols += WifiProtocol.Owe
                if ("SAE" in token || "EAP_SUITE_B" in token) protocols += WifiProtocol.Wpa3
                if ("PSK" in token) protocols += WifiProtocol.Wpa2
                if ("EAP" in token && "EAP_SUITE_B" !in token) protocols += WifiProtocol.Wpa2
            }
            token.startsWith("WAPI") -> protocols += WifiProtocol.Wapi
        }
    }
    return protocols
}

private const val TYPE_OPEN = 0
private const val TYPE_WEP = 1
private const val TYPE_PSK = 2
private const val TYPE_EAP = 3
private const val TYPE_SAE = 4
private const val TYPE_EAP_WPA3_ENTERPRISE_192_BIT = 5
private const val TYPE_OWE = 6
private const val TYPE_WAPI_PSK = 7
private const val TYPE_WAPI_CERT = 8
private const val TYPE_EAP_WPA3_ENTERPRISE = 9
private const val TYPE_PASSPOINT_R1_R2 = 11
private const val TYPE_PASSPOINT_R3 = 12
private const val TYPE_DPP = 13

fun securityFromAndroidType(type: Int): WifiSecurity = when (type) {
    TYPE_OPEN, TYPE_OWE -> WifiSecurity.Open
    TYPE_WEP -> WifiSecurity.Wep
    TYPE_PSK, TYPE_SAE, TYPE_WAPI_PSK, TYPE_DPP -> WifiSecurity.Personal
    TYPE_EAP,
    TYPE_EAP_WPA3_ENTERPRISE_192_BIT,
    TYPE_WAPI_CERT,
    TYPE_EAP_WPA3_ENTERPRISE,
    TYPE_PASSPOINT_R1_R2,
    TYPE_PASSPOINT_R3 -> WifiSecurity.Enterprise
    else -> WifiSecurity.Unknown
}

fun protocolsFromAndroidType(type: Int): Set<WifiProtocol> = when (type) {
    TYPE_WEP -> setOf(WifiProtocol.Wep)
    TYPE_PSK, TYPE_EAP -> setOf(WifiProtocol.Wpa, WifiProtocol.Wpa2)
    TYPE_SAE, TYPE_EAP_WPA3_ENTERPRISE_192_BIT, TYPE_EAP_WPA3_ENTERPRISE ->
        setOf(WifiProtocol.Wpa3)
    TYPE_OWE -> setOf(WifiProtocol.Owe)
    TYPE_WAPI_PSK, TYPE_WAPI_CERT -> setOf(WifiProtocol.Wapi)
    else -> emptySet()
}

fun groupByNetwork(readings: List<AccessPointReading>): List<NearbyWifi> =
    readings
        .groupBy { it.ssid to it.security }
        .map { (network, accessPoints) ->
            val (ssid, security) = network
            NearbyWifi(
                ssid = ssid,
                bssids = accessPoints.mapNotNull { it.bssid }.toSet(),
                security = security,
                protocols = accessPoints.flatMap { it.protocols }.toSet()
            ) to accessPoints.maxOf { it.level }
        }
        .sortedWith(
            compareByDescending<Pair<NearbyWifi, Int>> { it.second }
                .thenBy { it.first.ssid }
                .thenBy { it.first.security }
        )
        .map { it.first }
