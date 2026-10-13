package com.poc.safemode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.poc.safemode.ui.theme.SafeModeTheme

private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
fun WifiInfoRoute(viewModel: WifiInfoViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    val refreshOrRequestPermission = {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFineLocation) viewModel.refresh() else permissionLauncher.launch(locationPermissions)
    }

    val onOpenSettings = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        context.startActivity(intent)
    }

    var initialLoadDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialLoadDone) {
            initialLoadDone = true
            refreshOrRequestPermission()
        }
    }

    WifiInfoScreen(
        uiState = uiState,
        onUpdateClick = refreshOrRequestPermission,
        onOpenSettingsClick = onOpenSettings,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiInfoScreen(
    uiState: WifiInfoUiState,
    onUpdateClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Wi-Fi & Location Info") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Wi-Fi Information Section
                Text(
                    text = "WI-FI INFORMATION",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )

                OutlinedTextField(
                    value = uiState.ssid,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("SSID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = uiState.bssid,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("BSSID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = uiState.securityType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Security Type") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Location Services Section
                Text(
                    text = "LOCATION SERVICES",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )

                OutlinedTextField(
                    value = uiState.authorizationStatus,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Authorization Status") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = uiState.accuracyAuthorization,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Accuracy Authorization") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                TextButton(
                    onClick = onOpenSettingsClick,
                ) {
                    Text("Open Settings")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.latitude,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = uiState.longitude,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom Action Button (Standard Material 3 Button)
            Button(
                onClick = onUpdateClick,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .height(50.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(text = "Update Wi-Fi Information")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WifiInfoScreenPreview() {
    SafeModeTheme {
        WifiInfoScreen(
            uiState = WifiInfoUiState(
                ssid = "Office-5G",
                bssid = "a4:91:b1:2c:7e:10",
                securityType = "WPA3 Personal",
                authorizationStatus = "Authorized When In Use",
                accuracyAuthorization = "Full Accuracy",
                latitude = "-23,625445",
                longitude = "-46,705629",
            ),
            onUpdateClick = {},
            onOpenSettingsClick = {},
        )
    }
}
