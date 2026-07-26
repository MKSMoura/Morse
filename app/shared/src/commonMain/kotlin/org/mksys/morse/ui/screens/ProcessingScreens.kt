package org.mksys.morse.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.mksys.morse.core.model.ConnectionRequestRecord
import org.mksys.morse.core.model.ContactRecord
import org.mksys.morse.core.model.MessageRecord
import org.mksys.morse.ui.theme.DarkBackground
import org.mksys.morse.ui.theme.DarkSurface
import org.mksys.morse.ui.theme.DarkSurfaceVariant
import org.mksys.morse.ui.theme.Error
import org.mksys.morse.ui.theme.OnBackground
import org.mksys.morse.ui.theme.OnPrimary
import org.mksys.morse.ui.theme.OnSurface
import org.mksys.morse.ui.theme.OnSurfaceVariant
import org.mksys.morse.ui.theme.Primary
import org.mksys.morse.ui.theme.Success
import org.mksys.morse.ui.theme.MorseTheme

private val AVATAR_COLORS = listOf(
    Primary, Success, Color(0xFF6B8AE8), Color(0xFFE86B8A),
    Color(0xFF8AE86B), Color(0xFFE8C56B), Color(0xFF6BE8D5)
)

private fun getAvatarColor(index: Int): Color {
    return AVATAR_COLORS[((index % AVATAR_COLORS.size) + AVATAR_COLORS.size) % AVATAR_COLORS.size]
}

private fun getInitials(name: String): String {
    val parts = name.trim().split("\\s+".toRegex())
    return if (parts.size >= 2) {
        "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
    } else if (name.isNotBlank()) {
        name.take(2).uppercase()
    } else {
        "?"
    }
}

private fun formatTimestamp(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> "${days}d"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "agora"
    }
}

data class BottomTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorseTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = OnBackground
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = OnBackground
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DarkSurface
        )
    )
}

@Composable
fun ProcessingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground.copy(alpha = 0.85f))
            .blur(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                color = Primary,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = OnBackground
            )
        }
    }
}

@Composable
fun ErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onLeaveForLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLeaveForLater,
        containerColor = DarkSurface,
        titleContentColor = OnBackground,
        textContentColor = OnSurfaceVariant,
        title = { Text("Erro de conexão") },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Tentar novamente", color = OnPrimary)
            }
        },
        dismissButton = {
            Button(
                onClick = onLeaveForLater,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
            ) {
                Text("Deixar pra depois", color = OnSurfaceVariant)
            }
        }
    )
}

@Composable
fun IntegrityOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = Primary,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = OnBackground
            )
        }
    }
}

@Composable
fun MainScreen(
    displayName: String,
    contacts: List<ContactRecord>,
    lastMessages: Map<String, MessageRecord>,
    connectionRequests: List<ConnectionRequestRecord>,
    unreadRequestCount: Long,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onAddContact: () -> Unit,
    onOpenChat: (publicKey: String, displayName: String) -> Unit,
    onAcceptRequest: (ConnectionRequestRecord) -> Unit,
    onRejectRequest: (ConnectionRequestRecord) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.publicKeyB64.contains(searchQuery, ignoreCase = true)
        }
    }

    val initials = remember(displayName) { getInitials(displayName) }

    val tabs = remember {
        listOf(
            BottomTab("Conversas", Icons.Filled.Forum, Icons.Outlined.Forum),
            BottomTab("Contatos", Icons.Filled.People, Icons.Outlined.People),
            BottomTab("Feed", Icons.Filled.Star, Icons.Outlined.Star),
            BottomTab("Config.", Icons.Filled.Settings, Icons.Outlined.Settings),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Primary, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleMedium,
                                color = OnPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "Morse",
                            style = MaterialTheme.typography.titleLarge,
                            color = OnBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = OnSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(onClick = {
                            if (unreadRequestCount > 0L) onTabSelected(2)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notificações",
                                tint = if (unreadRequestCount > 0L) Primary else OnSurfaceVariant
                            )
                        }
                        if (unreadRequestCount > 0L) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .background(Error, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (unreadRequestCount > 9) "9+" else "${unreadRequestCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                modifier = Modifier.height(64.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = selectedTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onTabSelected(index) },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Primary,
                            selectedTextColor = Primary,
                            unselectedIconColor = OnSurfaceVariant,
                            unselectedTextColor = OnSurfaceVariant,
                            indicatorColor = Primary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        when (selectedTab) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (searchQuery.isBlank() && contacts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Nenhuma conversa ainda",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OnBackground
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Comece adicionando um contato",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = onAddContact,
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = OnPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Adicionar contato", color = OnPrimary)
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar conversas...", color = OnSurfaceVariant) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                cursorColor = Primary,
                                focusedTextColor = OnBackground,
                                unfocusedTextColor = OnBackground
                            )
                        )

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredContacts) { contact ->
                                val lastMsg = lastMessages[contact.publicKeyB64]
                                ContactItem(
                                    contact = contact,
                                    lastMessage = lastMsg,
                                    onClick = { onOpenChat(contact.publicKeyB64, contact.displayName) }
                                )
                            }

                            if (searchQuery.isBlank() && contacts.isNotEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(
                                            onClick = onAddContact,
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = OnSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Adicionar contato", color = OnSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Contatos",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }
            2 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (connectionRequests.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum pedido pendente",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(connectionRequests) { request ->
                                val initials = remember(request.displayName) { getInitials(request.displayName) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Primary, shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initials,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = OnPrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = request.displayName.ifBlank { request.publicKey.take(16) },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = OnBackground
                                        )
                                        Text(
                                            text = formatTimestamp(request.timestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OnSurfaceVariant
                                        )
                                    }

                                    IconButton(onClick = { onRejectRequest(request) }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Rejeitar",
                                            tint = Error
                                        )
                                    }

                                    IconButton(onClick = { onAcceptRequest(request) }) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Aceitar",
                                            tint = Success
                                        )
                                    }
                                }

                                HorizontalDivider(color = DarkSurfaceVariant, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
            3 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Em construção",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactItem(
    contact: ContactRecord,
    lastMessage: MessageRecord?,
    onClick: () -> Unit
) {
    val initials = remember(contact.displayName) { getInitials(contact.displayName) }
    val colorIndex = remember(contact.publicKeyB64) {
        contact.publicKeyB64.hashCode()
    }
    val avatarColor = remember(colorIndex) { getAvatarColor(colorIndex) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(avatarColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = OnPrimary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = contact.displayName.ifBlank { contact.publicKeyB64.take(16) },
                    style = MaterialTheme.typography.titleSmall,
                    color = OnBackground,
                    modifier = Modifier.weight(1f)
                )

                if (lastMessage != null) {
                    Text(
                        text = formatTimestamp(lastMessage.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
            }

            if (lastMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (lastMessage.isOutgoing) "Você: ${lastMessage.content}" else lastMessage.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Toque para iniciar conversa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }

    HorizontalDivider(color = DarkSurfaceVariant, thickness = 0.5.dp)
}

@Composable
fun ChatScreen(
    contactPublicKey: String,
    contactDisplayName: String,
    messages: List<MessageRecord>,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    BackHandler { onBack() }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            MorseTopBar(
                title = contactDisplayName.ifBlank { contactPublicKey.take(16) },
                onBack = onBack
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                items(messages) { msg ->
                    MessageBubble(msg)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(8.dp)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Mensagem...", color = OnSurfaceVariant) },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        cursorColor = Primary,
                        focusedTextColor = OnBackground,
                        unfocusedTextColor = OnBackground
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = if (inputText.isNotBlank()) Primary else OnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageRecord) {
    val bubbleColor = if (message.isOutgoing) Primary.copy(alpha = 0.15f) else DarkSurfaceVariant
    val shape = if (message.isOutgoing) {
        RoundedCornerShape(12.dp, 12.dp, 4.dp, 12.dp)
    } else {
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 4.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 4.dp),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bubbleColor, shape)
                .padding(10.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = OnBackground
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
                if (message.isOutgoing) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = message.status,
                        tint = if (message.status == "delivered") Success else OnSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionRequestsScreen(
    requests: List<ConnectionRequestRecord>,
    onBack: () -> Unit,
    onAccept: (ConnectionRequestRecord) -> Unit,
    onReject: (ConnectionRequestRecord) -> Unit
) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            MorseTopBar(
                title = "Pedidos de conexão",
                onBack = onBack
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (requests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhum pedido pendente",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(requests) { request ->
                    val initials = remember(request.displayName) { getInitials(request.displayName) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Primary, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleSmall,
                                color = OnPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = request.displayName.ifBlank { request.publicKey.take(16) },
                                style = MaterialTheme.typography.titleSmall,
                                color = OnBackground
                            )
                            Text(
                                text = formatTimestamp(request.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant
                            )
                        }

                        IconButton(onClick = { onReject(request) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Rejeitar",
                                tint = Error
                            )
                        }

                        IconButton(onClick = { onAccept(request) }) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Aceitar",
                                tint = Success
                            )
                        }
                    }

                    HorizontalDivider(color = DarkSurfaceVariant, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun AddContactScreen(
    onBack: () -> Unit,
    onSendRequest: (onion: String, x25519PubKey: String) -> Unit,
    isSending: Boolean = false,
    sendResult: String? = null
) {
    BackHandler { onBack() }

    var onionAddress by remember { mutableStateOf("") }
    var x25519PubKey by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            MorseTopBar(
                title = "Adicionar contato",
                onBack = onBack
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Endereço onion do contato",
                style = MaterialTheme.typography.labelLarge,
                color = OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = onionAddress,
                onValueChange = { onionAddress = it },
                placeholder = { Text("ex: abc123...xyz.onion", color = OnSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = DarkSurfaceVariant,
                    cursorColor = Primary,
                    focusedTextColor = OnBackground,
                    unfocusedTextColor = OnBackground
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Chave pública X25519 (Base64)",
                style = MaterialTheme.typography.labelLarge,
                color = OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = x25519PubKey,
                onValueChange = { x25519PubKey = it },
                placeholder = { Text("Cole a chave pública X25519", color = OnSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = DarkSurfaceVariant,
                    cursorColor = Primary,
                    focusedTextColor = OnBackground,
                    unfocusedTextColor = OnBackground
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onSendRequest(onionAddress.trim(), x25519PubKey.trim()) },
                enabled = onionAddress.isNotBlank() && x25519PubKey.isNotBlank() && !isSending,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    disabledContainerColor = DarkSurfaceVariant
                )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        color = OnPrimary,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (isSending) "Enviando..." else "Enviar pedido de conexão",
                    color = if (isSending || onionAddress.isBlank() || x25519PubKey.isBlank()) OnSurfaceVariant else OnPrimary
                )
            }

            if (sendResult != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = sendResult,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sendResult.contains("sucesso") || sendResult.contains("enviado")) Success else Error
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewProcessingOverlay() {
    MorseTheme {
        ProcessingOverlay(message = "Criando sua identidade...")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewErrorDialog() {
    MorseTheme {
        ErrorDialog(
            message = "Falha ao conectar ao Tor. Verifique sua conexão e tente novamente.",
            onRetry = {},
            onLeaveForLater = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewIntegrityOverlay() {
    MorseTheme {
        IntegrityOverlay(message = "Verificando integridade do banco de dados...")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewMainScreen() {
    MorseTheme {
        MainScreen(
            displayName = "João Silva",
            contacts = emptyList(),
            lastMessages = emptyMap(),
            connectionRequests = emptyList(),
            unreadRequestCount = 0L,
            selectedTab = 0,
            onTabSelected = {},
            onAddContact = {},
            onOpenChat = { _, _ -> },
            onAcceptRequest = {},
            onRejectRequest = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewAddContactScreen() {
    MorseTheme {
        AddContactScreen(
            onBack = {},
            onSendRequest = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewChatScreen() {
    MorseTheme {
        ChatScreen(
            contactPublicKey = "abc123",
            contactDisplayName = "Maria",
            messages = emptyList(),
            onBack = {},
            onSendMessage = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1012)
@Composable
private fun PreviewConnectionRequestsScreen() {
    MorseTheme {
        ConnectionRequestsScreen(
            requests = emptyList(),
            onBack = {},
            onAccept = {},
            onReject = {}
        )
    }
}
