package com.poc.safemode.data

import com.poc.safemode.domain.TrustedArea
import com.poc.safemode.domain.WifiProtocol
import com.poc.safemode.domain.WifiSecurity
import com.poc.safemode.domain.normalizeBssid
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object TrustedAreaJson {

    fun encode(areas: List<TrustedArea>): String {
        val array = JSONArray()
        areas.forEach { area ->
            array.put(
                JSONObject()
                    .put("id", area.id)
                    .put("name", area.name)
                    .put("ssid", area.ssid)
                    .put("bssids", JSONArray(area.bssids.sorted()))
                    .put("security", area.security.name)
                    .put("protocols", JSONArray(area.protocols.sorted().map { it.name }))
                    .put("latitude", area.latitude)
                    .put("longitude", area.longitude)
                    .put("radiusMeters", area.radiusMeters)
            )
        }
        return array.toString()
    }

    fun decode(json: String?): List<TrustedArea> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(::decodeArea)
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun decodeArea(item: JSONObject): TrustedArea? = try {
        TrustedArea(
            id = item.getString("id"),
            name = item.getString("name"),
            ssid = item.getString("ssid"),
            latitude = item.getDouble("latitude"),
            longitude = item.getDouble("longitude"),
            radiusMeters = item.getInt("radiusMeters"),
            bssids = decodeBssids(item.optJSONArray("bssids")),
            security = decodeSecurity(item.optString("security")),
            protocols = decodeProtocols(item.optJSONArray("protocols"))
        )
    } catch (_: JSONException) {
        null
    }

    private fun decodeSecurity(name: String): WifiSecurity =
        WifiSecurity.entries.firstOrNull { it.name == name } ?: WifiSecurity.Unknown

    private fun decodeProtocols(array: JSONArray?): Set<WifiProtocol> {
        if (array == null) return emptySet()
        return (0 until array.length())
            .mapNotNull { index ->
                val name = array.optString(index)
                WifiProtocol.entries.firstOrNull { it.name == name }
            }
            .toSet()
    }

    private fun decodeBssids(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        return (0 until array.length())
            .mapNotNull { index -> normalizeBssid(array.optString(index)) }
            .toSet()
    }
}
