package org.mksys.morse.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mksys.morse.ui.theme.DarkBackground
import org.mksys.morse.ui.theme.DarkSurfaceVariant
import org.mksys.morse.ui.theme.MorseTheme
import org.mksys.morse.ui.theme.OnSurfaceVariant
import org.mksys.morse.ui.theme.Primary
import org.mksys.morse.ui.theme.PrimaryDim
import org.mksys.morse.ui.theme.Success

@Composable
fun ServiceStatusContainer(
    status: String,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isConnected) Success else OnSurfaceVariant.copy(alpha = 0.3f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isConnected) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Success)
            )
        } else {
            RotatingCircles()
        }

        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isConnected) Success else OnSurfaceVariant
        )
    }
}

@Composable
fun RotatingCircles(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition()

    val colors = listOf(Primary, PrimaryDim, OnSurfaceVariant)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        colors.forEachIndexed { index, color ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewServiceStatusConnected() {
    MorseTheme {
        ServiceStatusContainer(
            status = "Conectado",
            isConnected = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewServiceStatusConnecting() {
    MorseTheme {
        ServiceStatusContainer(
            status = "Conectando ao Tor...",
            isConnected = false
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewRotatingCircles() {
    MorseTheme {
        RotatingCircles()
    }
}
