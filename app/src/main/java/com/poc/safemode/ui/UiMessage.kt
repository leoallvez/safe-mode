package com.poc.safemode.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import com.poc.safemode.R

enum class MessageAction { OpenAppSettings, OpenLocationSettings }

class ResArg(@param:StringRes val res: Int, val args: List<Any> = emptyList())

internal fun Resources.resolve(@StringRes res: Int, args: List<Any>): String = getString(
    res,
    *args.map { if (it is ResArg) resolve(it.res, it.args) else it }.toTypedArray()
)

class UiMessage(
    @param:StringRes val textRes: Int,
    val args: List<Any> = emptyList(),
    val action: MessageAction? = null
)

enum class PermissionedAction { UseConnectedWifi, ScanNearbyWifi, CaptureLocation, CheckStatus }

@Composable
internal fun rememberMessagePresenter(
    snackbarHostState: SnackbarHostState
): suspend (UiMessage) -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    return { message ->
        val result = snackbarHostState.showSnackbar(
            message = resources.resolve(message.textRes, message.args),
            actionLabel = message.action?.let { resources.getString(R.string.action_open_settings) },
            duration = if (message.action != null || message.args.isNotEmpty()) {
                SnackbarDuration.Long
            } else {
                SnackbarDuration.Short
            }
        )
        if (result == SnackbarResult.ActionPerformed) {
            message.action?.let { context.openSettings(it) }
        }
    }
}

@Composable
internal fun MessagesEffect(
    messages: Flow<UiMessage>,
    present: suspend (UiMessage) -> Unit
) {
    LaunchedEffect(messages) {
        messages.collectLatest { present(it) }
    }
}

private fun Context.openSettings(action: MessageAction) {
    val intent = when (action) {
        MessageAction.OpenAppSettings -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        MessageAction.OpenLocationSettings -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}
