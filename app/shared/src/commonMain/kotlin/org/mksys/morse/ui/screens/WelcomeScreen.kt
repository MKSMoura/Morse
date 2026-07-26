package org.mksys.morse.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mksys.morse.ui.components.ServiceStatusContainer
import org.mksys.morse.ui.theme.OnBackground
import org.mksys.morse.ui.theme.OnPrimary
import org.mksys.morse.ui.theme.OnSurfaceVariant
import androidx.compose.ui.tooling.preview.Preview
import org.mksys.morse.ui.theme.DarkBackground
import org.mksys.morse.ui.theme.MorseTheme
import org.mksys.morse.ui.theme.Primary

@Composable
fun WelcomeScreen(
    serviceStatus: String,
    isServiceConnected: Boolean,
    onNameSubmit: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

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
            text = "Como deseja ser chamado?",
            style = MaterialTheme.typography.headlineMedium,
            color = OnBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { newName ->
                name = newName.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
            },
            label = { Text("Seu nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = OnSurfaceVariant,
                cursorColor = Primary
            )
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onNameSubmit(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text("Continuar", color = OnPrimary)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewWelcomeScreen() {
    MorseTheme {
        WelcomeScreen(
            serviceStatus = "Conectando...",
            isServiceConnected = false,
            onNameSubmit = {}
        )
    }
}
