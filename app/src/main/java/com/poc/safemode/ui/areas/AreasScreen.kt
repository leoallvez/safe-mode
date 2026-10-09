package com.poc.safemode.ui.areas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poc.safemode.R
import com.poc.safemode.domain.CoordinateParser
import com.poc.safemode.domain.TrustedArea
import com.poc.safemode.domain.WifiProtocol
import com.poc.safemode.domain.WifiSecurity
import com.poc.safemode.ui.MessagesEffect
import com.poc.safemode.ui.PermissionedAction
import com.poc.safemode.ui.components.ButtonProgressIndicator
import com.poc.safemode.ui.securityText
import com.poc.safemode.ui.rememberLocationPermissionState
import com.poc.safemode.ui.rememberMessagePresenter
import com.poc.safemode.ui.theme.SafeModeTheme

@Composable
fun AreasScreen(
    onNewAreaClick: () -> Unit,
    onAreaClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AreasViewModel = viewModel(factory = AreasViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val presentMessage = rememberMessagePresenter(snackbarHostState)
    MessagesEffect(viewModel.messages, presentMessage)

    val permission = rememberLocationPermissionState(presentMessage) {
        viewModel.checkStatus()
    }

    AreasContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNewAreaClick = onNewAreaClick,
        onAreaClick = onAreaClick,
        onDeleteConfirmed = viewModel::delete,
        onTestStatusClick = {
            if (uiState.areas.isEmpty()) {
                viewModel.checkStatus()
            } else {
                permission.runOrRequest(PermissionedAction.CheckStatus)
            }
        },
        modifier = modifier
    )
}

@Composable
internal fun AreasContent(
    uiState: AreasUiState,
    snackbarHostState: SnackbarHostState,
    onNewAreaClick: () -> Unit,
    onAreaClick: (String) -> Unit,
    onDeleteConfirmed: (String) -> Unit,
    onTestStatusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDelete = uiState.areas.firstOrNull { it.id == pendingDeleteId }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AreasActions(
                isCheckingStatus = uiState.isCheckingStatus,
                onNewAreaClick = onNewAreaClick,
                onTestStatusClick = onTestStatusClick
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.screen_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (uiState.areas.isEmpty() && !uiState.isLoading) {
                item {
                    Text(
                        text = stringResource(R.string.areas_empty),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(uiState.areas, key = { it.id }) { area ->
                AreaItem(
                    area = area,
                    onClick = { onAreaClick(area.id) },
                    onDeleteClick = { pendingDeleteId = area.id }
                )
            }
        }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.areas_delete_title)) },
            text = { Text(stringResource(R.string.areas_delete_message, pendingDelete.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteConfirmed(pendingDelete.id)
                        pendingDeleteId = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun AreaItem(
    area: TrustedArea,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = area.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.areas_item_wifi,
                        area.ssid,
                        securityText(area.security, area.protocols)
                    ),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.areas_item_details,
                        area.radiusMeters,
                        CoordinateParser.format(area.latitude),
                        CoordinateParser.format(area.longitude)
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(
                        R.string.areas_delete_description, area.name
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AreasActions(
    isCheckingStatus: Boolean,
    onNewAreaClick: () -> Unit,
    onTestStatusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onNewAreaClick,
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
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.action_new_area),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        OutlinedButton(
            onClick = onTestStatusClick,
            enabled = !isCheckingStatus,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(25.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCheckingStatus) {
                    ButtonProgressIndicator()
                }
                Text(
                    text = stringResource(R.string.action_test_status),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AreasContentPreview() {
    SafeModeTheme {
        AreasContent(
            uiState = AreasUiState(
                areas = listOf(
                    TrustedArea(
                        "1", "Casa", "MinhaCasa_5G", -23.5505, -46.6333, 100, emptySet(),
                        WifiSecurity.Personal, setOf(WifiProtocol.Wpa2, WifiProtocol.Wpa3)
                    ),
                    TrustedArea(
                        "2", "Trabalho", "Trabalho_Secure", -23.5614, -46.6559, 250, emptySet(),
                        WifiSecurity.Open
                    )
                ),
                isLoading = false
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNewAreaClick = {},
            onAreaClick = {},
            onDeleteConfirmed = {},
            onTestStatusClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AreasContentEmptyPreview() {
    SafeModeTheme {
        AreasContent(
            uiState = AreasUiState(isLoading = false),
            snackbarHostState = remember { SnackbarHostState() },
            onNewAreaClick = {},
            onAreaClick = {},
            onDeleteConfirmed = {},
            onTestStatusClick = {}
        )
    }
}
