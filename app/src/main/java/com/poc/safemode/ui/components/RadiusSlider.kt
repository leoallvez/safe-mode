package com.poc.safemode.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poc.safemode.R
import com.poc.safemode.domain.TrustedArea
import com.poc.safemode.ui.theme.SafeModeTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RadiusSlider(
    radiusMeters: Int,
    onRadiusChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val min = TrustedArea.MIN_RADIUS_METERS
    val max = TrustedArea.MAX_RADIUS_METERS
    val step = TrustedArea.RADIUS_STEP_METERS

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.radius_title),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = stringResource(R.string.radius_value, radiusMeters),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        val sliderInteraction = remember { MutableInteractionSource() }
        val isDragged by sliderInteraction.collectIsDraggedAsState()
        val isPressed by sliderInteraction.collectIsPressedAsState()
        val thumbScale by animateFloatAsState(
            targetValue = if (isDragged || isPressed) 1.2f else 1f,
            label = "thumbScale"
        )
        val primary = MaterialTheme.colorScheme.primary
        val trackBrush = Brush.horizontalGradient(
            listOf(primary.copy(alpha = 0.45f), primary)
        )
        val inactiveTrack = MaterialTheme.colorScheme.surfaceVariant
        val radiusDescription = stringResource(R.string.radius_state_description, radiusMeters)
        Slider(
            value = radiusMeters.toFloat(),
            onValueChange = { onRadiusChange((it / step).roundToInt() * step) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min) / step - 1,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = radiusDescription },
            interactionSource = sliderInteraction,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .scale(thumbScale)
                        .shadow(6.dp, CircleShape)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(primary, CircleShape)
                    )
                }
            },
            track = { sliderState ->
                val range = sliderState.valueRange
                val fraction = (sliderState.value - range.start) /
                    (range.endInclusive - range.start)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                ) {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(color = inactiveTrack, cornerRadius = radius)
                    drawRoundRect(
                        brush = trackBrush,
                        size = Size(size.width * fraction, size.height),
                        cornerRadius = radius
                    )
                }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.radius_value, min),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.radius_value, max),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RadiusSliderPreview() {
    SafeModeTheme {
        RadiusSlider(radiusMeters = 100, onRadiusChange = {})
    }
}
