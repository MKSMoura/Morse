package org.mksys.morse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mksys.morse.ui.components.ServiceStatusContainer
import org.mksys.morse.ui.theme.DarkBackground
import org.mksys.morse.ui.theme.DarkSurfaceVariant
import org.mksys.morse.ui.theme.MorseTheme
import org.mksys.morse.ui.theme.Error
import org.mksys.morse.ui.theme.OnBackground
import org.mksys.morse.ui.theme.OnSurfaceVariant
import org.mksys.morse.ui.theme.Primary

@Composable
fun PINScreen(
    isConfirmation: Boolean,
    serviceStatus: String,
    isServiceConnected: Boolean,
    firstPIN: String?,
    onPINSubmit: (String) -> Unit,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val title = if (isConfirmation) "Confirme seu PIN" else "Crie seu PIN"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ServiceStatusContainer(
            status = serviceStatus,
            isConnected = isServiceConnected
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = OnBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(8) { index ->
                val filled = index < pin.length
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (filled) Primary else androidx.compose.ui.graphics.Color.Transparent)
                        .border(2.dp, if (filled) Primary else OnSurfaceVariant, CircleShape)
                )
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error!!, color = Error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(32.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("←", "0", "✓")
        )

        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                row.forEach { key ->
                    val boxModifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .clickable {
                            when (key) {
                                "←" -> {
                                    if (pin.isNotEmpty()) {
                                        pin = pin.dropLast(1)
                                        error = null
                                    }
                                }
                                "✓" -> {
                                    if (pin.length == 8) {
                                        if (isConfirmation && firstPIN != null) {
                                            if (pin == firstPIN) {
                                                onPINSubmit(pin)
                                            } else {
                                                error = "PINs não conferem"
                                                pin = ""
                                            }
                                        } else {
                                            onPINSubmit(pin)
                                        }
                                    }
                                }
                                else -> {
                                    if (pin.length < 8) {
                                        pin += key
                                        error = null
                                    }
                                }
                            }
                        }

                    Box(
                        modifier = boxModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.headlineSmall,
                            color = OnBackground
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        if (isConfirmation) {
            TextButton(onClick = onBack) {
                Text("Voltar", color = OnSurfaceVariant)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewPINScreenCreate() {
    MorseTheme {
        PINScreen(
            isConfirmation = false,
            serviceStatus = "Conectado",
            isServiceConnected = true,
            firstPIN = null,
            onPINSubmit = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewPINScreenConfirm() {
    MorseTheme {
        PINScreen(
            isConfirmation = true,
            serviceStatus = "Conectado",
            isServiceConnected = true,
            firstPIN = "12345678",
            onPINSubmit = {},
            onBack = {}
        )
    }
}
