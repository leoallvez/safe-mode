package com.poc.safemode.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poc.safemode.R
import com.poc.safemode.domain.NearbyWifi
import com.poc.safemode.domain.WifiProtocol
import com.poc.safemode.domain.WifiSecurity
import com.poc.safemode.ui.MessagesEffect
import com.poc.safemode.ui.PermissionedAction
import com.poc.safemode.ui.components.LocationCard
import com.poc.safemode.ui.components.NearbyNetworksDialog
import com.poc.safemode.ui.components.SectionCard
import com.poc.safemode.ui.components.WifiCard
import com.poc.safemode.ui.rememberLocationPermissionState
import com.poc.safemode.ui.rememberMessagePresenter
import com.poc.safemode.ui.theme.SafeModeTheme

@Composable
fun AreaEditorScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AreaEditorViewModel = viewModel(factory = AreaEditorViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val presentMessage = rememberMessagePresenter(snackbarHostState)
    MessagesEffect(viewModel.messages, presentMessage)

    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) currentOnClose()
    }

    val permission = rememberLocationPermissionState(presentMessage, viewModel::run)

    LaunchedEffect(permission.isGranted) {
        if (permission.isGranted) viewModel.prefillConnectedWifi()
    }

    AreaEditorContent(
        uiState = uiState,
        hasLocationPermission = permission.isGranted,
        snackbarHostState = snackbarHostState,
        onBackClick = onClose,
        onGrantPermissionClick = permission.request,
        onNameChange = viewModel::onNameChange,
        onUseConnectedWifiClick = { permission.runOrRequest(PermissionedAction.UseConnectedWifi) },
        onSearchNetworksClick = { permission.runOrRequest(PermissionedAction.ScanNearbyWifi) },
        onNearbyNetworkSelected = viewModel::onNearbyNetworkSelected,
        onNearbySsidsDismissed = viewModel::onNearbySsidsDismissed,
        onLatitudeChange = viewModel::onLatitudeChange,
        onLongitudeChange = viewModel::onLongitudeChange,
        onRadiusChange = viewModel::onRadiusChange,
        onCaptureLocationClick = { permission.runOrRequest(PermissionedAction.CaptureLocation) },
        onSaveClick = viewModel::save,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AreaEditorContent(
    uiState: AreaEditorUiState,
    hasLocationPermission: Boolean,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onGrantPermissionClick: () -> Unit,
    onNameChange: (String) -> Unit,
    onUseConnectedWifiClick: () -> Unit,
    onSearchNetworksClick: () -> Unit,
    onNearbyNetworkSelected: (NearbyWifi) -> Unit,
    onNearbySsidsDismissed: () -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onRadiusChange: (Int) -> Unit,
    onCaptureLocationClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isEditing) {
                                R.string.editor_title_edit
                            } else {
                                R.string.editor_title_new
                            }
                        ),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(
                icon = Icons.Default.Edit,
                title = stringResource(R.string.name_card_title)
            ) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.name_label)) },
                    placeholder = { Text(stringResource(R.string.name_placeholder)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }

            WifiCard(
                ssid = uiState.ssid,
                accessPointCount = uiState.bssids.size,
                security = uiState.security,
                protocols = uiState.protocols,
                hasPermission = hasLocationPermission,
                isLoading = uiState.isLoadingWifi,
                isSearching = uiState.isScanningWifi,
                onUseConnectedClick = onUseConnectedWifiClick,
                onSearchClick = onSearchNetworksClick,
                onGrantPermissionClick = onGrantPermissionClick
            )

            LocationCard(
                latitude = uiState.latitude,
                longitude = uiState.longitude,
                isLatitudeInvalid = uiState.isLatitudeInvalid,
                isLongitudeInvalid = uiState.isLongitudeInvalid,
                radiusMeters = uiState.radiusMeters,
                hasPermission = hasLocationPermission,
                isLoading = uiState.isLoadingLocation,
                onLatitudeChange = onLatitudeChange,
                onLongitudeChange = onLongitudeChange,
                onRadiusChange = onRadiusChange,
                onCaptureClick = onCaptureLocationClick,
                onGrantPermissionClick = onGrantPermissionClick
            )

            Button(
                onClick = onSaveClick,
                enabled = uiState.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.action_save_area),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (uiState.nearbyNetworks != null) {
        NearbyNetworksDialog(
            networks = uiState.nearbyNetworks,
            selectedSsid = uiState.ssid,
            selectedSecurity = uiState.security,
            onSelected = onNearbyNetworkSelected,
            onDismiss = onNearbySsidsDismissed
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AreaEditorContentPreview() {
    SafeModeTheme {
        AreaEditorContent(
            uiState = AreaEditorUiState(
                isEditing = true,
                name = "Casa",
                ssid = "MinhaCasa_5G",
                bssids = setOf("a4:91:b1:3c:5e:f0", "a4:91:b1:3c:5e:f1"),
                security = WifiSecurity.Personal,
                protocols = setOf(WifiProtocol.Wpa2, WifiProtocol.Wpa3),
                latitude = "-23.550500",
                longitude = "-46.633300"
            ),
            hasLocationPermission = true,
            snackbarHostState = remember { SnackbarHostState() },
            onBackClick = {},
            onGrantPermissionClick = {},
            onNameChange = {},
            onUseConnectedWifiClick = {},
            onSearchNetworksClick = {},
            onNearbyNetworkSelected = {},
            onNearbySsidsDismissed = {},
            onLatitudeChange = {},
            onLongitudeChange = {},
            onRadiusChange = {},
            onCaptureLocationClick = {},
            onSaveClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AreaEditorContentEmptyPreview() {
    SafeModeTheme {
        AreaEditorContent(
            uiState = AreaEditorUiState(),
            hasLocationPermission = false,
            snackbarHostState = remember { SnackbarHostState() },
            onBackClick = {},
            onGrantPermissionClick = {},
            onNameChange = {},
            onUseConnectedWifiClick = {},
            onSearchNetworksClick = {},
            onNearbyNetworkSelected = {},
            onNearbySsidsDismissed = {},
            onLatitudeChange = {},
            onLongitudeChange = {},
            onRadiusChange = {},
            onCaptureLocationClick = {},
            onSaveClick = {}
        )
    }
}
