package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatMessage
import com.example.data.model.MessagePart
import com.example.ui.components.MarkdownCodeText
import com.example.ui.components.PatchSummaryCard
import com.example.ui.components.PulsingDot
import com.example.ui.components.ReasoningCard
import com.example.ui.components.ToolCallCard
import com.example.ui.theme.LocalOpenCodeColors
import com.example.ui.viewmodel.ComposerMode
import com.example.ui.viewmodel.OpenCodeUiState
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    state: OpenCodeUiState,
    onTextChange: (String) -> Unit,
    onSelectFileMention: (String) -> Unit,
    onSelectMode: (ComposerMode) -> Unit,
    onOpenAgentMenu: () -> Unit,
    onOpenModelSheet: () -> Unit,
    onOpenCommandMenu: () -> Unit,
    onOpenDiffs: () -> Unit,
    onReconnectSse: () -> Unit,
    onSend: () -> Unit,
    onAbort: () -> Unit,
    onRetryResponse: (failedMessageId: String?) -> Unit = {},
    onDismissFailedPrompt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (!state.sseConnected && !state.activeProfile.isLocalAndroid) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Stream SSE server sedang terputus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onReconnectSse,
                        modifier = Modifier.testTag("chat_reconnect_sse_button")
                    ) {
                        Text("Hubungkan")
                    }
                }
            }
        }

        ChatTimelinePane(
            messages = state.messages,
            isLoading = state.isLoadingMessages,
            isSessionBusy = state.isActiveSessionBusy,
            isSendingPrompt = state.isSendingPrompt,
            activeModelLabel = state.selectedModel?.modelID ?: state.localProviderConfig.modelId,
            onQuickPrompt = { prompt ->
                onSelectMode(ComposerMode.PROMPT)
                onTextChange(prompt)
            },
            onOpenDiffs = onOpenDiffs,
            onRetryMessage = { msgId -> onRetryResponse(msgId) },
            modifier = Modifier.weight(1f)
        )

        val retryBanner = state.retryBannerMessage
        if (!retryBanner.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("retry_status_banner"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = retryBanner,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        val failedPrompt = state.failedPromptRequest?.takeIf {
            it.sessionId == state.activeSessionId || state.activeSessionId == null
        }
        if (failedPrompt != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("failed_prompt_banner"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Error respon AI",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = failedPrompt.errorTitle.ifBlank { "Gagal Mengirim ke Endpoint AI" },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = failedPrompt.errorDetail.ifBlank {
                                    "Endpoint tidak merespons dalam 30 detik setelah 3x percobaan ulang."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onDismissFailedPrompt,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dismiss_failed_prompt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Tutup pesan error",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = { onRetryResponse(null) },
                            enabled = !state.isSendingPrompt,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("retry_failed_prompt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(18.dp)
                            )
                            Text("Ulangi Respon")
                        }
                    }
                }
            }
        }

        PromptComposerBar(
            state = state,
            onTextChange = onTextChange,
            onSelectFileMention = onSelectFileMention,
            onSelectMode = onSelectMode,
            onOpenAgentMenu = onOpenAgentMenu,
            onOpenModelSheet = onOpenModelSheet,
            onOpenCommandMenu = onOpenCommandMenu,
            onSend = onSend,
            onAbort = onAbort
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatTimelinePane(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    isSessionBusy: Boolean,
    isSendingPrompt: Boolean,
    activeModelLabel: String,
    onQuickPrompt: (String) -> Unit,
    onOpenDiffs: () -> Unit,
    onRetryMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) true
            else {
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= totalItems - 2
            }
        }
    }

    val liveStreamSignature = remember(messages) {
        val last = messages.lastOrNull() ?: return@remember "empty"
        val charCount = last.parts.sumOf { part ->
            when (part) {
                is MessagePart.Text -> part.text.length
                is MessagePart.Reasoning -> part.text.length
                is MessagePart.ToolCall -> (part.output?.length ?: 0) + (part.error?.length ?: 0) + part.status.length
                is MessagePart.Patch -> part.files.size
                else -> 1
            }
        }
        "${messages.size}:${last.id}:${last.parts.size}:$charCount:${last.completedAt ?: -1L}"
    }

    LaunchedEffect(liveStreamSignature) {
        if (messages.isNotEmpty() && isNearBottom) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    if (isLoading && messages.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (messages.isEmpty()) {
        val suggestions = remember {
            listOf(
                "Pindai file workspace & jelaskan arsitekturnya",
                "Buat fungsi utilitas Kotlin lengkap dengan unit test",
                "Cari potensi bug atau TODO di kode proyek",
                "Tulis endpoint REST JSON dengan Retrofit"
            )
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(14.dp)
                            .size(32.dp)
                    )
                }
                Text(
                    text = "AI Coding Chat Siap (SSE Real-Time)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Model aktif: $activeModelLabel. Respons kode, proses berpikir agen, dan output tool dialirkan langsung token-demi-token.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEachIndexed { index, suggestion ->
                        AssistChip(
                            onClick = { onQuickPrompt(suggestion) },
                            label = {
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("chat_quick_prompt_$index")
                        )
                    }
                }
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("chat_timeline_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                if (!msg.isAssistant) {
                    UserMessageBubble(message = msg)
                } else {
                    val isTailAssistant = index == messages.lastIndex
                    val isLiveStreaming = msg.isStreaming || (isTailAssistant && isSessionBusy)
                    AssistantMessageItem(
                        message = msg,
                        isLiveStreaming = isLiveStreaming,
                        isRetryingBlocked = isSendingPrompt || isSessionBusy,
                        onOpenDiffs = onOpenDiffs,
                        onRetryResponse = { onRetryMessage(msg.id) }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isNearBottom,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(messages.lastIndex)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("chat_scroll_to_bottom")
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Lompat ke respons terbaru"
                )
            }
        }
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessage) {
    val textContent = message.parts
        .filterIsInstance<MessagePart.Text>()
        .joinToString("\n") { it.text }
        .ifBlank { "(Lampiran / Perintah)" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Text(
                text = textContent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessageItem(
    message: ChatMessage,
    isLiveStreaming: Boolean,
    isRetryingBlocked: Boolean,
    onOpenDiffs: () -> Unit,
    onRetryResponse: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = "Agen OpenCode",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(5.dp)
                        .size(16.dp)
                )
            }
            Text(
                text = listOfNotNull(
                    message.agent?.replaceFirstChar { it.uppercase() },
                    message.modelId
                ).joinToString(" · ").ifBlank { "OpenCode Agent" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isLiveStreaming) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        PulsingDot(color = MaterialTheme.colorScheme.primary, active = true)
                        Text(
                            text = "Streaming",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (!message.errorText.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("assistant_error_card"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Respon AI error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Respon AI Gagal",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = message.errorText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = onRetryResponse,
                            enabled = !isRetryingBlocked,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("retry_ai_response_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(18.dp)
                            )
                            Text("Ulangi Respon")
                        }
                    }
                }
            }
        }

        if (isLiveStreaming && message.parts.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    text = "Menunggu token SSE pertama…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        message.parts.forEachIndexed { partIdx, part ->
            val isLastPart = partIdx == message.parts.lastIndex
            when (part) {
                is MessagePart.Reasoning -> {
                    if (part.text.isNotBlank()) {
                        ReasoningCard(
                            part = part,
                            isStreaming = isLiveStreaming && isLastPart
                        )
                    }
                }
                is MessagePart.Text -> {
                    if (part.text.isNotBlank()) {
                        MarkdownCodeText(
                            markdown = part.text,
                            isStreaming = isLiveStreaming && isLastPart
                        )
                    }
                }
                is MessagePart.ToolCall -> {
                    ToolCallCard(part = part)
                }
                is MessagePart.Patch -> {
                    PatchSummaryCard(part = part, onOpenDiffs = onOpenDiffs)
                }
                is MessagePart.FileAttachment -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text(part.filename, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                is MessagePart.StepSummary -> {
                    if (part.tokensTotal != null || part.cost != null) {
                        val tokensTxt = part.tokensTotal?.let { "$it tok" } ?: ""
                        val costTxt = part.cost?.takeIf { it > 0.0 }?.let { "$${"%.4f".format(it)}" } ?: ""
                        val combined = listOf(tokensTxt, costTxt).filter { it.isNotBlank() }.joinToString(" · ")
                        if (combined.isNotBlank()) {
                            Text(
                                text = combined,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = LocalOpenCodeColors.current.subtleBorder)
    }
}

@Composable
private fun PromptComposerBar(
    state: OpenCodeUiState,
    onTextChange: (String) -> Unit,
    onSelectFileMention: (String) -> Unit,
    onSelectMode: (ComposerMode) -> Unit,
    onOpenAgentMenu: () -> Unit,
    onOpenModelSheet: () -> Unit,
    onOpenCommandMenu: () -> Unit,
    onSend: () -> Unit,
    onAbort: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (state.fileMentions.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.heightIn(max = 180.dp)) {
                        state.fileMentions.take(5).forEach { path ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clickable { onSelectFileMention(path) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = false,
                    onClick = onOpenAgentMenu,
                    label = { Text("Agen: ${state.selectedAgent}") },
                    leadingIcon = { Icon(Icons.Rounded.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("select_agent_chip")
                )

                val modelLabel = state.selectedModel?.modelID?.substringAfterLast('/') ?: "Model Default"
                FilterChip(
                    selected = false,
                    onClick = onOpenModelSheet,
                    label = { Text(modelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("select_model_chip")
                )

                ComposerMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.composerMode == mode,
                        onClick = {
                            if (mode == ComposerMode.COMMAND && state.composerText.isBlank()) {
                                onOpenCommandMenu()
                            } else {
                                onSelectMode(mode)
                            }
                        },
                        label = { Text(mode.label) },
                        leadingIcon = if (mode == ComposerMode.SHELL) {
                            { Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        } else null,
                        modifier = Modifier.testTag("composer_mode_${mode.name.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val placeholder = when (state.composerMode) {
                    ComposerMode.PROMPT -> "Tulis instruksi coding… (ketik @ untuk file)"
                    ComposerMode.COMMAND -> "Perintah slash, misal: review"
                    ComposerMode.SHELL -> "Perintah bash di workspace…"
                }
                OutlinedTextField(
                    value = state.composerText,
                    onValueChange = onTextChange,
                    placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("prompt_input")
                )

                if (state.isActiveSessionBusy && state.composerText.isBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .size(52.dp)
                            .clickable(onClick = onAbort)
                            .testTag("stop_session_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = "Hentikan",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else {
                    val canSend = state.composerText.isNotBlank() && !state.isSendingPrompt
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(52.dp)
                            .clickable(enabled = canSend, onClick = onSend)
                            .testTag("send_prompt_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (state.isSendingPrompt) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Kirim prompt",
                                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
