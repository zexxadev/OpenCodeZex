package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MessagePart
import com.example.data.model.PermissionRequestItem
import com.example.data.model.SessionRunStatus
import com.example.ui.theme.LocalOpenCodeColors
import com.example.ui.theme.MonoCodeStyle
import kotlinx.coroutines.delay

@Composable
fun PulsingDot(
    color: androidx.compose.ui.graphics.Color,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha = if (active) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val anim by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        anim
    } else {
        1.0f
    }

    Box(
        modifier = modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun SessionStatusChip(
    status: SessionRunStatus,
    modifier: Modifier = Modifier
) {
    val semantic = LocalOpenCodeColors.current
    val (bg, fg, label, pulsing) = when (status) {
        SessionRunStatus.BUSY -> Quadruple(
            semantic.warningContainer,
            semantic.warning,
            "Agen Bekerja",
            true
        )
        SessionRunStatus.RETRY -> Quadruple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            "Mencoba Ulang",
            true
        )
        SessionRunStatus.IDLE -> Quadruple(
            semantic.successContainer,
            semantic.success,
            "Siap",
            false
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PulsingDot(color = fg, active = pulsing)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
fun ToolCallCard(
    part: MessagePart.ToolCall,
    modifier: Modifier = Modifier
) {
    val semantic = LocalOpenCodeColors.current
    var expanded by remember(part.id) { mutableStateOf(part.status == "error") }
    val hasExpandableDetail = !part.output.isNullOrBlank() || !part.error.isNullOrBlank()

    val icon = when (part.toolName.lowercase()) {
        "bash", "shell" -> Icons.Rounded.Terminal
        "read" -> Icons.Rounded.Description
        "edit", "write", "patch" -> Icons.Rounded.Edit
        "grep", "glob", "find" -> Icons.Rounded.Search
        else -> Icons.Rounded.Build
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (hasExpandableDetail) {
                    Modifier.clickable { expanded = !expanded }
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(
            1.dp,
            if (part.status == "error") MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = part.toolName,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = part.toolName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        if (!part.title.isNullOrBlank()) {
                            Text(
                                text = part.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (part.inputSummary.isNotBlank()) {
                        Text(
                            text = part.inputSummary,
                            style = MonoCodeStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                when (part.status.lowercase()) {
                    "running", "pending" -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = semantic.warning
                    )
                    "completed" -> Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Selesai",
                        tint = semantic.success,
                        modifier = Modifier.size(18.dp)
                    )
                    "error" -> Icon(
                        imageVector = Icons.Rounded.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (hasExpandableDetail) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Tutup output" else "Lihat output",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded && hasExpandableDetail) {
                val body = part.error ?: part.output.orEmpty()
                val clipboard = LocalClipboardManager.current
                var copiedOutput by remember(body) { mutableStateOf(false) }

                LaunchedEffect(copiedOutput) {
                    if (copiedOutput) {
                        delay(1800)
                        copiedOutput = false
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = semantic.codeBackground,
                    border = BorderStroke(1.dp, semantic.codeBorder)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (part.error != null) "error" else "output",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (part.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CopyCodeChipButton(
                                copied = copiedOutput,
                                enabled = body.isNotEmpty(),
                                onClick = {
                                    clipboard.setText(AnnotatedString(body))
                                    copiedOutput = true
                                },
                                modifier = Modifier.testTag("copy_tool_output_button")
                            )
                        }
                        HorizontalDivider(color = semantic.codeBorder.copy(alpha = 0.65f))
                        Text(
                            text = body.take(2500),
                            style = MonoCodeStyle,
                            color = if (part.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReasoningCard(
    part: MessagePart.Reasoning,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    var userToggled by remember(part.id) { mutableStateOf<Boolean?>(null) }
    val expanded = userToggled ?: isStreaming

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { userToggled = !expanded },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (isStreaming) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = "Reasoning",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isStreaming) "Agen Sedang Berpikir…" else "Proses Berpikir Agen",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isStreaming) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (isStreaming) {
                    PulsingDot(color = MaterialTheme.colorScheme.tertiary, active = true)
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle reasoning",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = if (isStreaming) "${part.text} ▍" else part.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
fun SseStreamStatusBadge(
    sseConnected: Boolean,
    isStreaming: Boolean,
    isLocalRuntime: Boolean,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalOpenCodeColors.current
    val (bg, fg, label) = when {
        isStreaming -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            "SSE Streaming"
        )
        sseConnected -> Triple(
            semantic.successContainer.copy(alpha = 0.7f),
            semantic.success,
            if (isLocalRuntime) "SSE Lokal Live" else "SSE Live"
        )
        else -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            "SSE Terputus"
        )
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = !sseConnected && !isLocalRuntime, onClick = onReconnect)
            .testTag("sse_status_badge"),
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            PulsingDot(color = fg, active = isStreaming || sseConnected)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PatchSummaryCard(
    part: MessagePart.Patch,
    onOpenDiffs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalOpenCodeColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDiffs),
        shape = RoundedCornerShape(10.dp),
        color = semantic.successContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, semantic.success.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Code,
                contentDescription = "Patch file",
                tint = semantic.success,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${part.files.size} file dimodifikasi",
                    style = MaterialTheme.typography.labelLarge,
                    color = semantic.onSuccessContainer
                )
                if (part.files.isNotEmpty()) {
                    Text(
                        text = part.files.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = semantic.onSuccessContainer.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "Lihat Diff",
                style = MaterialTheme.typography.labelMedium,
                color = semantic.success,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PermissionBannerCard(
    permission: PermissionRequestItem,
    onAllowOnce: () -> Unit,
    onAllowAlways: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalOpenCodeColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = semantic.warningContainer,
        border = BorderStroke(1.dp, semantic.warning.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = "Permintaan izin",
                    tint = semantic.warning,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Izin Diperlukan · ${permission.type}",
                        style = MaterialTheme.typography.labelLarge,
                        color = semantic.onWarningContainer
                    )
                    Text(
                        text = permission.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.onWarningContainer
                    )
                }
            }
            if (!permission.patternText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = permission.patternText,
                        style = MonoCodeStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAllowOnce,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("permission_allow_once"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Sekali", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onAllowAlways,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("permission_allow_always")
                ) {
                    Text("Selalu", maxLines = 1)
                }
                TextButton(
                    onClick = onReject,
                    modifier = Modifier.testTag("permission_reject"),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Tolak")
                }
            }
        }
    }
}
