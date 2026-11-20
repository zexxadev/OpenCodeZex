package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FileNode
import com.example.data.model.GrepMatch
import com.example.ui.theme.LocalOpenCodeColors
import com.example.ui.theme.MonoCodeStyle
import com.example.ui.viewmodel.ConnectionState
import com.example.ui.viewmodel.FileSearchMode
import com.example.ui.viewmodel.OpenCodeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    state: OpenCodeUiState,
    onLoadDirectory: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onSearchModeChange: (FileSearchMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: () -> Unit,
    onOpenFile: (String) -> Unit,
    onCloseFilePreview: () -> Unit,
    onInsertMentionToPrompt: (String) -> Unit,
    onOpenServers: () -> Unit,
    onCreateLocalFile: (String, String) -> Unit = { _, _ -> },
    onImportLocalFile: (Uri) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var showCreateFileDialog by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLocalFile(uri)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Workspace File",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = state.currentDirPath.ifBlank { "/ (akar workspace)" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    if (state.activeProfile.isLocalAndroid) {
                        IconButton(
                            onClick = { showCreateFileDialog = true },
                            modifier = Modifier.testTag("create_local_file_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Buat file baru")
                        }
                        IconButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("import_local_file_button")
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = "Impor file ke workspace")
                        }
                    }
                    IconButton(
                        onClick = { onLoadDirectory(state.currentDirPath) },
                        modifier = Modifier.testTag("refresh_files_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Muat ulang direktori")
                    }
                }
            }

            if (state.connectionState !is ConnectionState.Connected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Text("Server Belum Terhubung", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Hubungkan ke server OpenCode untuk menjelajah file, membaca kode, dan melakukan grep di workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onOpenServers) {
                            Text("Atur Server")
                        }
                    }
                }
                return@Column
            }

            // Mode Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.fileSearchMode == FileSearchMode.BROWSE,
                    onClick = { onSearchModeChange(FileSearchMode.BROWSE) },
                    label = { Text("Jelajah") },
                    leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("mode_browse")
                )
                FilterChip(
                    selected = state.fileSearchMode == FileSearchMode.FIND_FILE,
                    onClick = { onSearchModeChange(FileSearchMode.FIND_FILE) },
                    label = { Text("Cari File") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("mode_find_file")
                )
                FilterChip(
                    selected = state.fileSearchMode == FileSearchMode.GREP,
                    onClick = { onSearchModeChange(FileSearchMode.GREP) },
                    label = { Text("Grep Kode") },
                    leadingIcon = { Icon(Icons.Rounded.ManageSearch, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("mode_grep")
                )
            }

            if (state.fileSearchMode != FileSearchMode.BROWSE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.fileSearchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                if (state.fileSearchMode == FileSearchMode.FIND_FILE) {
                                    "Cari nama file, misal: MainActivity"
                                } else {
                                    "Cari teks/regex di kode…"
                                }
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onExecuteSearch() }),
                        trailingIcon = {
                            if (state.fileSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Bersihkan")
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("file_search_input")
                    )
                    Button(
                        onClick = onExecuteSearch,
                        modifier = Modifier.testTag("execute_file_search_button")
                    ) {
                        Text("Cari")
                    }
                }
            }

            if (state.isLoadingFiles) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (state.fileSearchMode) {
                    FileSearchMode.BROWSE -> {
                        if (state.currentDirPath.isNotEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onNavigateUp)
                                        .testTag("navigate_up_dir"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Folder induk")
                                        Text(".. (Kembali ke folder induk)", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }

                        if (state.fileNodes.isEmpty() && !state.isLoadingFiles) {
                            item {
                                Text(
                                    text = "Direktori kosong atau tidak dapat dibaca.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 20.dp)
                                )
                            }
                        } else {
                            items(state.fileNodes, key = { it.path }) { node ->
                                FileNodeRow(
                                    node = node,
                                    onClick = {
                                        if (node.isDirectory) onLoadDirectory(node.path)
                                        else onOpenFile(node.path)
                                    }
                                )
                            }
                        }
                    }

                    FileSearchMode.FIND_FILE -> {
                        items(state.foundFilePaths, key = { it }) { path ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFile(path) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = path,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    FileSearchMode.GREP -> {
                        items(state.grepMatches) { match ->
                            GrepMatchCard(
                                match = match,
                                onClick = { onOpenFile(match.path) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.previewFilePath != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onCloseFilePreview,
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = state.previewFilePath,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCloseFilePreview) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup preview")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onInsertMentionToPrompt(state.previewFilePath)
                            onCloseFilePreview()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("insert_file_mention_button")
                    ) {
                        Icon(Icons.Rounded.AlternateEmail, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sisipkan ke Prompt", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            val text = state.previewFileContent?.content ?: ""
                            if (text.isNotEmpty()) {
                                clipboard.setText(AnnotatedString(text))
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Salin")
                    }
                }

                if (state.isLoadingFilePreview) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val content = state.previewFileContent
                    val semantic = LocalOpenCodeColors.current
                    if (content == null || content.type == "binary") {
                        Text(
                            text = "File biner atau konten tidak tersedia.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 460.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = semantic.codeBackground,
                            border = BorderStroke(1.dp, semantic.codeBorder)
                        ) {
                            val lines = content.content.lines()
                            SelectionContainer {
                                Column(
                                    modifier = Modifier
                                        .verticalScroll(rememberScrollState())
                                        .horizontalScroll(rememberScrollState())
                                        .padding(12.dp)
                                ) {
                                    lines.take(800).forEachIndexed { idx, line ->
                                        Row {
                                            Text(
                                                text = "${idx + 1}".padStart(4) + "  ",
                                                style = MonoCodeStyle,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                            )
                                            Text(
                                                text = line,
                                                style = MonoCodeStyle,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    if (lines.size > 800) {
                                        Text(
                                            text = "… (${lines.size - 800} baris lainnya)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateFileDialog) {
        CreateLocalFileDialog(
            currentDir = state.currentDirPath,
            onDismiss = { showCreateFileDialog = false },
            onCreate = { path, content ->
                showCreateFileDialog = false
                onCreateLocalFile(path, content)
            }
        )
    }
}

@Composable
private fun CreateLocalFileDialog(
    currentDir: String,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var fileName by remember {
        mutableStateOf(if (currentDir.isBlank()) "main.kt" else "$currentDir/main.kt")
    }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat File di Workspace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Path File (misal: src/app.py)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Isi Awal (opsional)") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = fileName.isNotBlank(),
                onClick = { onCreate(fileName.trim(), content) }
            ) {
                Text("Buat File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
private fun FileNodeRow(
    node: FileNode,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (node.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                contentDescription = if (node.isDirectory) "Folder" else "File",
                tint = if (node.isDirectory) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (node.ignored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GrepMatchCard(
    match: GrepMatch,
    onClick: () -> Unit
) {
    val semantic = LocalOpenCodeColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = match.path,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = ":${match.lineNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = match.lineText,
                style = MonoCodeStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(semantic.codeBackground, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
