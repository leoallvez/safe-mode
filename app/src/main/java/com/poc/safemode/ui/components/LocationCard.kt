package com.poc.safemode.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.poc.safemode.R
import com.poc.safemode.ui.theme.SafeModeTheme

@Composable
internal fun LocationCard(
    latitude: String,
    longitude: String,
    isLatitudeInvalid: Boolean,
    isLongitudeInvalid: Boolean,
    radiusMeters: Int,
    hasPermission: Boolean,
    isLoading: Boolean,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onRadiusChange: (Int) -> Unit,
    onCaptureClick: () -> Unit,
    onGrantPermissionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        icon = Icons.Default.LocationOn,
        title = stringResource(R.string.location_card_title),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CoordinateField(
                value = latitude,
                label = stringResource(R.string.location_latitude_label),
                errorText = stringResource(R.string.location_latitude_error),
                isInvalid = isLatitudeInvalid,
                onValueChange = onLatitudeChange,
                modifier = Modifier.weight(1f)
            )
            CoordinateField(
                value = longitude,
                label = stringResource(R.string.location_longitude_label),
                errorText = stringResource(R.string.location_longitude_error),
                isInvalid = isLongitudeInvalid,
                onValueChange = onLongitudeChange,
                modifier = Modifier.weight(1f)
            )
        }

        if (hasPermission) {
            CardActionButton(
                text = stringResource(R.string.location_capture),
                isLoading = isLoading,
                onClick = onCaptureClick
            )
        } else {
            PermissionRequest(
                rationale = stringResource(R.string.permission_location_rationale),
                onGrantClick = onGrantPermissionClick
            )
        }

        RadiusSlider(
            radiusMeters = radiusMeters,
            onRadiusChange = onRadiusChange
        )
    }
}

@Composable
private fun CoordinateField(
    value: String,
    label: String,
    errorText: String,
    isInvalid: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        isError = isInvalid,
        supportingText = if (isInvalid) {
            { Text(errorText) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(8.dp),
        singleLine = true
    )
}

@Preview(showBackground = true)
@Composable
private fun LocationCardPreview() {
    SafeModeTheme {
        LocationCard(
            latitude = "-23.550500",
            longitude = "200",
            isLatitudeInvalid = false,
            isLongitudeInvalid = true,
            radiusMeters = 100,
            hasPermission = true,
            isLoading = false,
            onLatitudeChange = {},
            onLongitudeChange = {},
            onRadiusChange = {},
            onCaptureClick = {},
            onGrantPermissionClick = {}
        )
    }
}
