package org.mksys.morse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mksys.morse.core.model.ContactRecord
import org.mksys.morse.core.model.ConnectionRequestRecord
import org.mksys.morse.core.model.IdentityStatus
import org.mksys.morse.core.model.MessageRecord
import org.mksys.morse.core.service.ServiceBridge
import org.mksys.morse.core.service.TorStatus
import org.mksys.morse.ui.screens.AddContactScreen
import org.mksys.morse.ui.screens.ChatScreen
import org.mksys.morse.ui.screens.ConnectionRequestsScreen
import org.mksys.morse.ui.screens.ErrorDialog
import org.mksys.morse.ui.screens.IntegrityOverlay
import org.mksys.morse.ui.screens.MainScreen
import org.mksys.morse.ui.screens.PINScreen
import org.mksys.morse.ui.screens.ProcessingOverlay
import org.mksys.morse.ui.screens.WelcomeScreen
import org.mksys.morse.ui.theme.MorseTheme
import org.mksys.morse.ui.theme.OnBackground
import org.mksys.morse.ui.theme.Primary

sealed class Screen {
    data object Loading : Screen()
    data object Welcome : Screen()
    data object PINCreate : Screen()
    data class PINConfirm(val pin: String) : Screen()
    data object Processing : Screen()
    data object Main : Screen()
    data object AddContact : Screen()
    data class Chat(val contactPublicKey: String, val contactDisplayName: String) : Screen()
}

@Composable
fun App(serviceBridge: ServiceBridge) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
    var userName by remember { mutableStateOf("") }
    var userPin by remember { mutableStateOf("") }
    var isServiceConnected by remember { mutableStateOf(false) }
    var torStatusMessage by remember { mutableStateOf("Iniciando serviços...") }

    var processingPhase by remember { mutableStateOf("connecting") }
    var needsActionMessage by remember { mutableStateOf<String?>(null) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf("") }

    var contacts by remember { mutableStateOf<List<ContactRecord>>(emptyList()) }
    var lastMessages by remember { mutableStateOf<Map<String, MessageRecord>>(emptyMap()) }
    var chatMessages by remember { mutableStateOf<List<MessageRecord>>(emptyList()) }
    var connectionRequests by remember { mutableStateOf<List<ConnectionRequestRecord>>(emptyList()) }
    var unreadRequestCount by remember { mutableStateOf(0L) }

    var isSendingRequest by remember { mutableStateOf(false) }
    var sendRequestResult by remember { mutableStateOf<String?>(null) }

    var selectedTab by remember { mutableIntStateOf(0) }

    val isSubScreen = screen is Screen.Chat || screen is Screen.AddContact

    if (isSubScreen) {
        BackHandler {
            when (screen) {
                is Screen.Chat -> {
                    val s = screen as Screen.Chat
                    serviceBridge.setConversationActive(s.contactPublicKey, false)
                    chatMessages = emptyList()
                    selectedTab = 0
                    screen = Screen.Main
                }
                is Screen.AddContact -> {
                    screen = Screen.Main
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(Unit) {
        serviceBridge.observeStatus().collect { status ->
            when (status) {
                is TorStatus.Starting -> {
                    torStatusMessage = "Iniciando..."
                    isServiceConnected = false
                }
                is TorStatus.Connecting -> {
                    torStatusMessage = status.message
                    isServiceConnected = false
                }
                is TorStatus.Connected -> {
                    torStatusMessage = "Conectado via Tor"
                    isServiceConnected = true
                }
                is TorStatus.NeedsAction -> {
                    torStatusMessage = status.message
                    needsActionMessage = status.message
                    isServiceConnected = false
                }
                is TorStatus.StoppedTrying -> {
                    torStatusMessage = "Falha na conexão"
                    isServiceConnected = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        serviceBridge.observeIdentity().collect { status ->
            if (status is IdentityStatus.Confirmed) {
                displayName = status.displayName
            }
            if (screen is Screen.Loading) {
                when (status) {
                    is IdentityStatus.Confirmed -> screen = Screen.Main
                    is IdentityStatus.Absent -> screen = Screen.Welcome
                    is IdentityStatus.Unknown -> { }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        serviceBridge.observeP2PEvents()?.collect {
            if (screen is Screen.Main) {
                scope.launch {
                    contacts = serviceBridge.getAllContacts()
                    lastMessages = serviceBridge.getLastMessagePerContact().associateBy { it.senderPublicKey }
                    unreadRequestCount = serviceBridge.getUnreadEventCount()
                    connectionRequests = serviceBridge.getConnectionRequests()
                }
            }
            if (screen is Screen.Chat) {
                val s = screen as Screen.Chat
                chatMessages = serviceBridge.getMessagesBySender(s.contactPublicKey)
            }
        }
    }

    LaunchedEffect(screen) {
        when (screen) {
            is Screen.Loading -> {
                val running = serviceBridge.isServiceRunning()
                if (!running) {
                    serviceBridge.startService()
                }
            }
            is Screen.Processing -> {
                processingPhase = "connecting"
                needsActionMessage = null
                saveErrorMessage = null
                scope.launch { serviceBridge.confirmRetry() }
            }
            is Screen.Main -> {
                scope.launch {
                    contacts = serviceBridge.getAllContacts()
                    lastMessages = serviceBridge.getLastMessagePerContact().associateBy { it.senderPublicKey }
                    unreadRequestCount = serviceBridge.getUnreadEventCount()
                    connectionRequests = serviceBridge.getConnectionRequests()
                }
            }
            is Screen.Chat -> {
                val s = screen as Screen.Chat
                serviceBridge.setConversationActive(s.contactPublicKey, true)
                chatMessages = serviceBridge.getMessagesBySender(s.contactPublicKey)
            }
            else -> {
                needsActionMessage = null
                saveErrorMessage = null
            }
        }
    }

    MorseTheme {
        when (val currentScreen = screen) {
            is Screen.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(torStatusMessage, color = OnBackground)
                    }
                }
            }

            is Screen.Welcome -> {
                WelcomeScreen(
                    serviceStatus = torStatusMessage,
                    isServiceConnected = isServiceConnected,
                    onNameSubmit = { name ->
                        userName = name
                        screen = Screen.PINCreate
                    }
                )
            }

            is Screen.PINCreate -> {
                PINScreen(
                    isConfirmation = false,
                    serviceStatus = torStatusMessage,
                    isServiceConnected = isServiceConnected,
                    firstPIN = null,
                    onPINSubmit = { pin ->
                        screen = Screen.PINConfirm(pin)
                    },
                    onBack = { screen = Screen.Welcome }
                )
            }

            is Screen.PINConfirm -> {
                PINScreen(
                    isConfirmation = true,
                    serviceStatus = torStatusMessage,
                    isServiceConnected = isServiceConnected,
                    firstPIN = currentScreen.pin,
                    onPINSubmit = {
                        userPin = currentScreen.pin
                        screen = Screen.Processing
                    },
                    onBack = { screen = Screen.PINCreate }
                )
            }

            is Screen.Processing -> {
                LaunchedEffect(isServiceConnected, processingPhase) {
                    if (isServiceConnected && processingPhase == "connecting") {
                        processingPhase = "save"
                    }
                }

                LaunchedEffect(processingPhase) {
                    when (processingPhase) {
                        "save" -> {
                            val result = serviceBridge.saveIdentity(userName, userPin)
                            when (result) {
                                is org.mksys.morse.core.service.SaveResult.Success -> {
                                    saveErrorMessage = null
                                    processingPhase = "integrity"
                                }
                                is org.mksys.morse.core.service.SaveResult.Error -> {
                                    saveErrorMessage = result.message
                                }
                            }
                        }
                        "integrity" -> {
                            val ok = serviceBridge.verifyIntegrity()
                            if (ok) {
                                userName = ""
                                userPin = ""
                                screen = Screen.Main
                            } else {
                                saveErrorMessage = "Não foi possível salvar os dados no dispositivo"
                            }
                        }
                    }
                }

                when (processingPhase) {
                    "save" -> {
                        val msg = saveErrorMessage ?: "Salvando identidade..."
                        IntegrityOverlay(message = msg)
                    }
                    "integrity" -> {
                        val msg = saveErrorMessage ?: "Verificando integridade..."
                        IntegrityOverlay(message = msg)
                    }
                    else -> ProcessingOverlay(message = torStatusMessage)
                }

                if (needsActionMessage != null) {
                    ErrorDialog(
                        message = needsActionMessage!!,
                        onRetry = {
                            needsActionMessage = null
                            scope.launch { serviceBridge.confirmRetry() }
                        },
                        onLeaveForLater = {
                            needsActionMessage = null
                            userName = ""
                            userPin = ""
                            screen = Screen.Loading
                        }
                    )
                }
            }

            is Screen.Main -> {
                MainScreen(
                    displayName = displayName,
                    contacts = contacts,
                    lastMessages = lastMessages,
                    connectionRequests = connectionRequests,
                    unreadRequestCount = unreadRequestCount,
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        selectedTab = tab
                    },
                    onAddContact = { screen = Screen.AddContact },
                    onOpenChat = { pubKey, name -> screen = Screen.Chat(pubKey, name) },
                    onAcceptRequest = { request ->
                        scope.launch {
                            serviceBridge.acceptConnectionRequest(
                                theirPublicKeyB64 = request.publicKey,
                                theirOnionAddress = request.onionAddress,
                                theirX25519PubB64 = request.x25519PublicKey,
                                displayName = request.displayName
                            )
                            connectionRequests = serviceBridge.getConnectionRequests()
                            unreadRequestCount = serviceBridge.getUnreadEventCount()
                            contacts = serviceBridge.getAllContacts()
                            lastMessages = serviceBridge.getLastMessagePerContact().associateBy { it.senderPublicKey }
                        }
                    },
                    onRejectRequest = { request ->
                        scope.launch {
                            serviceBridge.deleteContact(request.publicKey)
                            connectionRequests = serviceBridge.getConnectionRequests()
                        }
                    }
                )
            }

            is Screen.AddContact -> {
                AddContactScreen(
                    onBack = { screen = Screen.Main },
                    onSendRequest = { onion, x25519Key ->
                        isSendingRequest = true
                        sendRequestResult = null
                        scope.launch {
                            val ok = serviceBridge.sendConnectionRequest(onion, x25519Key)
                            isSendingRequest = false
                            sendRequestResult = if (ok) "Pedido enviado com sucesso" else "Falha ao enviar pedido"
                        }
                    },
                    isSending = isSendingRequest,
                    sendResult = sendRequestResult
                )
            }

            is Screen.Chat -> {
                ChatScreen(
                    contactPublicKey = currentScreen.contactPublicKey,
                    contactDisplayName = currentScreen.contactDisplayName,
                    messages = chatMessages,
                    onBack = {
                        serviceBridge.setConversationActive(currentScreen.contactPublicKey, false)
                        chatMessages = emptyList()
                        selectedTab = 0
                        screen = Screen.Main
                    },
                    onSendMessage = { content ->
                        scope.launch {
                            val contact = contacts.find { it.publicKeyB64 == currentScreen.contactPublicKey }
                            if (contact != null) {
                                serviceBridge.sendMessage(
                                    recipientPublicKeyB64 = contact.publicKeyB64,
                                    recipientX25519PublicKeyB64 = contact.x25519PublicKeyB64,
                                    recipientOnionAddress = contact.onionAddress,
                                    content = content
                                )
                                chatMessages = serviceBridge.getMessagesBySender(currentScreen.contactPublicKey)
                            }
                        }
                    }
                )
            }
        }
    }
}
