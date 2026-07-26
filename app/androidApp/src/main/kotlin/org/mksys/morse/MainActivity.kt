package org.mksys.morse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import org.mksys.morse.service.AndroidServiceBridge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val serviceBridge = remember {
                AndroidServiceBridge(applicationContext).also { it.bind() }
            }

            DisposableEffect(Unit) {
                onDispose {
                    serviceBridge.unbind()
                }
            }

            App(serviceBridge = serviceBridge)
        }
    }
}
