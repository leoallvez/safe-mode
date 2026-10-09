package com.poc.safemode.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.poc.safemode.R
import kotlinx.coroutines.launch

internal class LocationPermissionState(
    val isGranted: Boolean,
    val request: () -> Unit,
    val runOrRequest: (PermissionedAction) -> Unit
)

@Composable
internal fun rememberLocationPermissionState(
    presentMessage: suspend (UiMessage) -> Unit,
    onGranted: (PermissionedAction) -> Unit
): LocationPermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    var isGranted by remember { mutableStateOf(context.hasFineLocation()) }

    LifecycleResumeEffect(Unit) {
        isGranted = context.hasFineLocation()
        onPauseOrDispose { }
    }

    var pendingAction by rememberSaveable { mutableStateOf<PermissionedAction?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val action = pendingAction
        pendingAction = null
        isGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val message = when {
            isGranted -> {
                action?.let(onGranted)
                null
            }
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> UiMessage(
                R.string.msg_permission_approximate,
                action = MessageAction.OpenAppSettings
            )
            activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACCESS_FINE_LOCATION
            ) -> UiMessage(
                R.string.msg_permission_blocked,
                action = MessageAction.OpenAppSettings
            )
            else -> UiMessage(R.string.msg_permission_denied)
        }
        if (message != null) {
            scope.launch { presentMessage(message) }
        }
    }

    val launch: (PermissionedAction?) -> Unit = { action ->
        pendingAction = action
        launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    return LocationPermissionState(
        isGranted = isGranted,
        request = { launch(null) },
        runOrRequest = { action ->
            if (context.hasFineLocation()) onGranted(action) else launch(action)
        }
    )
}

private fun Context.hasFineLocation(): Boolean = ContextCompat.checkSelfPermission(
    this, Manifest.permission.ACCESS_FINE_LOCATION
) == PackageManager.PERMISSION_GRANTED
