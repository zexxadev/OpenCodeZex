package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DiffComputation
import com.example.data.model.DiffLineType
import com.example.data.model.FileDiff
import com.example.ui.theme.LocalOpenCodeColors
import com.example.ui.theme.MonoCodeStyle

@Composable
fun DiffViewerPane(
    diffs: List<FileDiff>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    if (isLoading && diffs.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (diffs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Difference,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = "Belum Ada Perubahan Kode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Perubahan file yang dibuat agen OpenCode pada sesi ini akan tampil lengkap dengan diff baris-demi-baris.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val totalAdd = diffs.sumOf { it.additions }
    val totalDel = diffs.sumOf { it.deletions }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${diffs.size} file berubah",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "+$totalAdd",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalOpenCodeColors.current.diffAddText,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "-$totalDel",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalOpenCodeColors.current.diffRemoveText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        items(diffs, key = { it.file }) { diff ->
            FileDiffCard(diff = diff)
        }
    }
}

@Composable
private fun FileDiffCard(diff: FileDiff) {
    val semantic = LocalOpenCodeColors.current
    var expanded by remember(diff.file) { mutableStateOf(true) }
    val diffLines = remember(diff.before, diff.after) {
        DiffComputation.computeDiffLines(diff.before, diff.after)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = diff.file,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "+${diff.additions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.diffAddText
                )
                Text(
                    text = "-${diff.deletions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.diffRemoveText
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle diff",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                if (diffLines.isEmpty()) {
                    Text(
                        text = "File biner atau tanpa perubahan baris teks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(semantic.codeBackground)
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    ) {
                        diffLines.forEach { line ->
                            val bg = when (line.type) {
                                DiffLineType.ADDED -> semantic.diffAddBackground
                                DiffLineType.REMOVED -> semantic.diffRemoveBackground
                                DiffLineType.HUNK -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                DiffLineType.CONTEXT -> Color.Transparent
                            }
                            val fg = when (line.type) {
                                DiffLineType.ADDED -> semantic.diffAddText
                                DiffLineType.REMOVED -> semantic.diffRemoveText
                                DiffLineType.HUNK -> MaterialTheme.colorScheme.onSurfaceVariant
                                DiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            }
                            val prefix = when (line.type) {
                                DiffLineType.ADDED -> "+"
                                DiffLineType.REMOVED -> "-"
                                DiffLineType.HUNK -> " "
                                DiffLineType.CONTEXT -> " "
                            }
                            val lineNumStr = (line.newLineNum ?: line.oldLineNum)?.toString() ?: ""

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bg)
                                    .padding(horizontal = 10.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lineNumStr.padStart(4),
                                    style = MonoCodeStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.width(38.dp)
                                )
                                Text(
                                    text = "$prefix ${line.content}",
                                    style = MonoCodeStyle,
                                    color = fg
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
