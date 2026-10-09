package com.poc.safemode.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poc.safemode.R
import com.poc.safemode.domain.NearbyWifi
import com.poc.safemode.domain.WifiProtocol
import com.poc.safemode.domain.WifiSecurity
import com.poc.safemode.ui.securityText
import com.poc.safemode.ui.theme.SafeModeTheme

@Composable
internal fun WifiCard(
    ssid: String,
    accessPointCount: Int,
    security: WifiSecurity,
    protocols: Set<WifiProtocol>,
    hasPermission: Boolean,
    isLoading: Boolean,
    isSearching: Boolean,
    onUseConnectedClick: () -> Unit,
    onSearchClick: () -> Unit,
    onGrantPermissionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val missingAccessPoint = ssid.isNotEmpty() && accessPointCount == 0

    SectionCard(
        icon = Icons.Default.Wifi,
        title = stringResource(R.string.wifi_card_title),
        modifier = modifier
    ) {
        OutlinedTextField(
            value = ssid,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.wifi_ssid_label)) },
            isError = missingAccessPoint,
            supportingText = when {
                missingAccessPoint -> {
                    { Text(stringResource(R.string.wifi_missing_access_point)) }
                }
                ssid.isNotEmpty() -> {
                    {
                        Text(
                            stringResource(
                                R.string.wifi_network_summary,
                                securityText(security, protocols),
                                pluralStringResource(
                                    R.plurals.wifi_access_points,
                                    accessPointCount,
                                    accessPointCount
                                )
                            )
                        )
                    }
                }
                hasPermission -> {
                    { Text(stringResource(R.string.wifi_ssid_empty_hint)) }
                }
                else -> null
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )

        if (ssid.isNotEmpty() && security.isWeak) {
            Text(
                text = stringResource(R.string.wifi_weak_security_warning),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (!hasPermission) {
            PermissionRequest(
                rationale = stringResource(R.string.permission_wifi_rationale),
                onGrantClick = onGrantPermissionClick
            )
            return@SectionCard
        }

        CardActionButton(
            text = stringResource(R.string.wifi_use_connected),
            isLoading = isLoading,
            onClick = onUseConnectedClick
        )

        CardActionButton(
            text = stringResource(R.string.wifi_search),
            isLoading = isSearching,
            onClick = onSearchClick,
            secondary = true
        )
    }
}

@Composable
internal fun NearbyNetworksDialog(
    networks: List<NearbyWifi>,
    selectedSsid: String,
    selectedSecurity: WifiSecurity,
    onSelected: (NearbyWifi) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.wifi_nearby_title)) },
        text = {
            LazyColumn {
                items(networks, key = { "${it.security.name}:${it.ssid}" }) { network ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(network) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = network.ssid,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = securityText(network.security, network.protocols),
                                fontSize = 12.sp,
                                color = if (network.security.isWeak) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (network.ssid == selectedSsid && network.security == selectedSecurity) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun NearbyNetworksDialogPreview() {
    SafeModeTheme {
        NearbyNetworksDialog(
            networks = listOf(
                NearbyWifi(
                    "MinhaCasa_5G", setOf("a4:91:b1:3c:5e:f0"), WifiSecurity.Personal,
                    setOf(WifiProtocol.Wpa2, WifiProtocol.Wpa3)
                ),
                NearbyWifi("Cafe_Livre", setOf("c0:00:00:00:00:01"), WifiSecurity.Open),
                NearbyWifi("Empresa", setOf("d0:00:00:00:00:01"), WifiSecurity.Enterprise)
            ),
            selectedSsid = "MinhaCasa_5G",
            selectedSecurity = WifiSecurity.Personal,
            onSelected = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WifiCardPreview() {
    SafeModeTheme {
        WifiCard(
            ssid = "MinhaCasa_5G",
            accessPointCount = 2,
            security = WifiSecurity.Personal,
            protocols = setOf(WifiProtocol.Wpa2, WifiProtocol.Wpa3),
            hasPermission = true,
            isLoading = false,
            isSearching = false,
            onUseConnectedClick = {},
            onSearchClick = {},
            onGrantPermissionClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WifiCardNoPermissionPreview() {
    SafeModeTheme {
        WifiCard(
            ssid = "",
            accessPointCount = 0,
            security = WifiSecurity.Unknown,
            protocols = emptySet(),
            hasPermission = false,
            isLoading = false,
            isSearching = false,
            onUseConnectedClick = {},
            onSearchClick = {},
            onGrantPermissionClick = {}
        )
    }
}
