package com.poc.safemode.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.poc.safemode.R
import com.poc.safemode.domain.WifiProtocol
import com.poc.safemode.domain.WifiSecurity
import com.poc.safemode.domain.label

@StringRes
internal fun WifiSecurity.labelRes(): Int = when (this) {
    WifiSecurity.Open -> R.string.wifi_security_open
    WifiSecurity.Wep -> R.string.wifi_security_wep
    WifiSecurity.Personal -> R.string.wifi_security_personal
    WifiSecurity.Enterprise -> R.string.wifi_security_enterprise
    WifiSecurity.Unknown -> R.string.wifi_security_unknown
}

internal fun securityArg(security: WifiSecurity, protocols: Set<WifiProtocol>): ResArg =
    if (protocols.isEmpty()) {
        ResArg(security.labelRes())
    } else {
        ResArg(
            R.string.wifi_security_with_protocol,
            listOf(ResArg(security.labelRes()), protocols.label())
        )
    }

@Composable
internal fun securityText(security: WifiSecurity, protocols: Set<WifiProtocol>): String {
    val category = stringResource(security.labelRes())
    return if (protocols.isEmpty()) {
        category
    } else {
        stringResource(R.string.wifi_security_with_protocol, category, protocols.label())
    }
}
