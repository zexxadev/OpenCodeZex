package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.OpenCodeSession
import com.example.data.model.SessionRunStatus
import com.example.data.model.SessionStatusInfo
import com.example.ui.components.PulsingDot
import com.example.ui.components.SessionStatusChip
import com.example.ui.components.ThemeToggleButton
import com.example.ui.theme.LocalOpenCodeColors
import com.example.ui.theme.ThemePreference
import com.example.ui.viewmodel.ConnectionState
import com.example.ui.viewmodel.OpenCodeUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsScreen(
    state: OpenCodeUiState,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit,
    onOpenServers: () -> Unit,
    onThemeChange: (ThemePreference) -> Unit = {},
    onSearchChange: (String) -> Unit,
    onCreateSession: (String?) -> Unit,
    onOpenSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onForkSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingSession by remember { mutableStateOf<OpenCodeSession?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = if (LocalOpenCodeColors.current.isGlass) Color.Transparent else MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.connectionState is ConnectionState.Connected) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = "Sesi Baru") },
                    text = { Text("Sesi Baru") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("create_session_fab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                // Header
                SessionsHeaderBar(
                    state = state,
                    onRefresh = onRefresh,
                    onOpenServers = onOpenServers,
                    onThemeChange = onThemeChange
                )

                if (state.isLoadingSessions) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.connectionState !is ConnectionState.Connected) {
                        item {
                            ServerConnectionHeroCard(
                                state = state,
                                onReconnect = onReconnect,
                                onOpenServers = onOpenServers
                            )
                        }
                    } else {
                        item {
                            OutlinedTextField(
                                value = state.sessionSearchQuery,
                                onValueChange = onSearchChange,
                                placeholder = { Text("Cari sesi coding…") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (state.sessionSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchChange("") }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("session_search_input")
                            )
                        }

                        if (state.filteredSessions.isEmpty() && !state.isLoadingSessions) {
                            item {
                                EmptySessionsCard(
                                    hasQuery = state.sessionSearchQuery.isNotBlank(),
                                    onCreateSession = { showCreateDialog = true }
                                )
                            }
                        } else {
                            items(state.filteredSessions, key = { it.id }) { session ->
                                val statusInfo = state.sessionStatuses[session.id] ?: SessionStatusInfo()
                                SessionListCard(
                                    session = session,
                                    statusInfo = statusInfo,
                                    isActive = session.id == state.activeSessionId,
                                    onClick = { onOpenSession(session.id) },
                                    onRename = { renamingSession = session },
                                    onFork = { onForkSession(session.id) },
                                    onDelete = { onDeleteSession(session.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        SessionTitleDialog(
            dialogTitle = "Buat Sesi OpenCode Baru",
            initialTitle = "",
            confirmLabel = "Mulai Sesi",
            onDismiss = { showCreateDialog = false },
            onConfirm = { title ->
                showCreateDialog = false
                onCreateSession(title)
            }
        )
    }

    renamingSession?.let { target ->
        SessionTitleDialog(
            dialogTitle = "Ubah Judul Sesi",
            initialTitle = target.title,
            confirmLabel = "Simpan",
            onDismiss = { renamingSession = null },
            onConfirm = { newTitle ->
                renamingSession = null
                onRenameSession(target.id, newTitle)
            }
        )
    }
}

@Composable
private fun SessionsHeaderBar(
    state: OpenCodeUiState,
    onRefresh: () -> Unit,
    onOpenServers: () -> Unit,
    onThemeChange: (ThemePreference) -> Unit
) {
    val semantic = LocalOpenCodeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "OpenCodeZex",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (state.sseConnected) "LIVE SSE" else "CLIENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable(onClick = onOpenServers),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val (dotColor, labelText) = when (val conn = state.connectionState) {
                    is ConnectionState.Connected -> semantic.success to "${state.activeProfile.name} · ${conn.latencyMs}ms"
                    is ConnectionState.Connecting -> semantic.warning to "Menghubungkan ${state.activeProfile.name}…"
                    is ConnectionState.Error -> MaterialTheme.colorScheme.error to "${state.activeProfile.name} · Terputus"
                    is ConnectionState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant to state.activeProfile.name
                }
                PulsingDot(
                    color = dotColor,
                    active = state.connectionState is ConnectionState.Connecting
                )
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ThemeToggleButton(
                currentTheme = state.themePreference,
                onSelectTheme = onThemeChange
            )
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("refresh_sessions_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Muat ulang sesi"
                )
            }
            IconButton(
                onClick = onOpenServers,
                modifier = Modifier.testTag("open_servers_shortcut")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Pengaturan server"
                )
            }
        }
    }
}

@Composable
private fun ServerConnectionHeroCard(
    state: OpenCodeUiState,
    onReconnect: () -> Unit,
    onOpenServers: () -> Unit
) {
    val semantic = LocalOpenCodeColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_opencode_hero),
                    contentDescription = "Ilustrasi Workspace OpenCode",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                            )
                        )
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = state.activeProfile.normalizedUrl.ifBlank { "Server belum diatur" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (val conn = state.connectionState) {
                    is ConnectionState.Connecting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Menghubungkan ke server OpenCode…",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    is ConnectionState.Error -> {
                        Text(
                            text = conn.diagnostic.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = conn.diagnostic.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = semantic.codeBackground,
                            border = BorderStroke(1.dp, semantic.codeBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = conn.diagnostic.hint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "Hubungkan ke Server OpenCode",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Jalankan `opencode serve --hostname 0.0.0.0 --port 4096` di komputer Anda lalu uji koneksi untuk mulai mengelola sesi agen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onReconnect,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hero_retry_connect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Uji Koneksi")
                    }
                    OutlinedButton(
                        onClick = onOpenServers,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hero_configure_server_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Atur Server")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySessionsCard(
    hasQuery: Boolean,
    onCreateSession: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (hasQuery) "Sesi Tidak Ditemukan" else "Belum Ada Sesi Coding",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (hasQuery) "Coba kata kunci pencarian lain." else "Buat sesi baru untuk mulai mengirim prompt ke agen OpenCode di workspace Anda.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasQuery) {
                Button(
                    onClick = onCreateSession,
                    modifier = Modifier.testTag("empty_create_session_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Buat Sesi Pertama")
                }
            }
        }
    }
}

@Composable
private fun SessionListCard(
    session: OpenCodeSession,
    statusInfo: SessionStatusInfo,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onFork: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val semantic = LocalOpenCodeColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("session_card_${session.id}"),
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = session.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (statusInfo.status != SessionRunStatus.IDLE) {
                        SessionStatusChip(status = statusInfo.status)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = formatTimestamp(session.time?.updated ?: session.time?.created ?: 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    session.summary?.takeIf { it.files > 0 }?.let { summary ->
                        Text(
                            text = "${summary.files} file",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "+${summary.additions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.diffAddText
                        )
                        Text(
                            text = "-${summary.deletions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.diffRemoveText
                        )
                    }
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("session_menu_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Opsi sesi",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Ubah Judul") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Fork Sesi") },
                        leadingIcon = { Icon(Icons.Rounded.CallSplit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onFork()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionTitleDialog(
    dialogTitle: String,
    initialTitle: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Judul Sesi (opsional)") },
                placeholder = { Text("Misal: Refactor Auth & Tests") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_title_dialog_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title) },
                modifier = Modifier.testTag("session_title_dialog_confirm")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

private fun formatTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return "Baru saja"
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale("id", "ID"))
    return formatter.format(Date(epochMs))
}
