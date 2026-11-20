package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalOpenCodeColors
import com.example.ui.theme.MonoCodeStyle
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.delay
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode

@Composable
fun MarkdownCodeText(
    markdown: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val semantic = LocalOpenCodeColors.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val displayMarkdown = remember(markdown, isStreaming) {
        if (isStreaming && !hasUnclosedFence(markdown) && markdown.isNotBlank()) {
            "$markdown ▍"
        } else {
            markdown
        }
    }

    val colors = markdownColor(
        text = colorScheme.onSurface,
        codeText = colorScheme.onSurface,
        inlineCodeText = colorScheme.primary,
        linkText = colorScheme.primary,
        codeBackground = semantic.codeBackground,
        inlineCodeBackground = semantic.codeBackground,
        dividerColor = semantic.subtleBorder
    )

    val mdTypography = markdownTypography(
        h1 = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        h2 = typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        h3 = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        h4 = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        h5 = typography.titleSmall,
        h6 = typography.labelLarge,
        text = typography.bodyMedium,
        paragraph = typography.bodyMedium,
        code = MonoCodeStyle,
        inlineCode = MonoCodeStyle.copy(fontSize = 13.sp),
        quote = typography.bodyMedium.copy(color = colorScheme.onSurfaceVariant),
        ordered = typography.bodyMedium,
        bullet = typography.bodyMedium,
        list = typography.bodyMedium
    )

    val components = markdownComponents(
        codeFence = { model ->
            val extracted = remember(model.content, model.node.startOffset, model.node.endOffset) {
                extractCodeFence(model.content, model.node)
            }
            CodeBlockCard(
                language = extracted.language,
                code = extracted.code,
                isStreaming = isStreaming && extracted.isUnclosed
            )
        },
        codeBlock = { model ->
            val code = remember(model.content, model.node.startOffset, model.node.endOffset) {
                extractIndentedCodeBlock(model.content, model.node)
            }
            CodeBlockCard(
                language = "",
                code = code,
                isStreaming = false
            )
        }
    )

    SelectionContainer(modifier = modifier.testTag("markdown_rendered_content")) {
        Markdown(
            content = displayMarkdown,
            colors = colors,
            typography = mdTypography,
            components = components,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val semantic = LocalOpenCodeColors.current
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val displayCode = remember(code, isStreaming) {
        if (isStreaming) "$code▍" else code
    }
    val highlightedText = remember(displayCode, language, semantic.isDark) {
        highlightCodeToAnnotatedString(
            code = displayCode,
            language = language,
            isDark = semantic.isDark
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("code_block_card"),
        shape = RoundedCornerShape(10.dp),
        color = semantic.codeBackground,
        border = BorderStroke(
            width = 1.dp,
            color = if (isStreaming) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else semantic.codeBorder
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = language.ifBlank { "code" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isStreaming) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PulsingDot(
                                color = MaterialTheme.colorScheme.primary,
                                active = true
                            )
                            Text(
                                text = "Streaming",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                CopyCodeChipButton(
                    copied = copied,
                    enabled = code.isNotEmpty(),
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                    modifier = Modifier.testTag("copy_code_block_button")
                )
            }

            HorizontalDivider(color = semantic.codeBorder.copy(alpha = 0.65f))

            SelectionContainer {
                Text(
                    text = highlightedText,
                    style = MonoCodeStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun CopyCodeChipButton(
    copied: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalOpenCodeColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = if (copied) {
            semantic.successContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (copied) {
                semantic.success.copy(alpha = 0.5f)
            } else {
                semantic.codeBorder
            }
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics(mergeDescendants = true) {
                contentDescription = if (copied) "Copied to Clipboard" else "Copy to Clipboard"
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                contentDescription = null,
                tint = if (copied) semantic.success else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (copied) "Copied" else "Copy",
                style = MaterialTheme.typography.labelSmall,
                color = if (copied) semantic.success else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private data class ExtractedCodeFence(
    val language: String,
    val code: String,
    val isUnclosed: Boolean
)

private fun extractCodeFence(content: String, node: ASTNode): ExtractedCodeFence {
    val children = node.children
    val langNode = children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
    val language = if (langNode != null) {
        content.substring(langNode.startOffset, langNode.endOffset).trim()
    } else {
        ""
    }

    val firstEol = children.firstOrNull { it.type == MarkdownTokenTypes.EOL }
    val endNode = children.lastOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_END }
    val isUnclosed = endNode == null

    val startOffset = firstEol?.endOffset ?: node.startOffset
    val endOffset = (endNode?.startOffset ?: node.endOffset).coerceAtLeast(startOffset)
    val rawBody = if (startOffset <= content.length && endOffset <= content.length) {
        content.substring(startOffset, endOffset)
    } else {
        ""
    }
    return ExtractedCodeFence(
        language = language,
        code = rawBody.trimEnd('\n', '\r'),
        isUnclosed = isUnclosed
    )
}

private fun extractIndentedCodeBlock(content: String, node: ASTNode): String {
    val start = node.startOffset.coerceIn(0, content.length)
    val end = node.endOffset.coerceIn(start, content.length)
    return content.substring(start, end)
        .lines()
        .joinToString("\n") { line ->
            when {
                line.startsWith("    ") -> line.drop(4)
                line.startsWith("\t") -> line.drop(1)
                else -> line
            }
        }
        .trimEnd('\n', '\r')
}

private fun hasUnclosedFence(markdown: String): Boolean {
    var activeFence: String? = null
    for (line in markdown.lines()) {
        val trimmed = line.trimStart()
        val fence = when {
            trimmed.startsWith("```") -> "```"
            trimmed.startsWith("~~~") -> "~~~"
            else -> null
        }
        if (fence != null) {
            if (activeFence == null) {
                activeFence = fence
            } else if (activeFence == fence && trimmed.removePrefix(fence).isBlank()) {
                activeFence = null
            }
        }
    }
    return activeFence != null
}

private fun highlightCodeToAnnotatedString(
    code: String,
    language: String,
    isDark: Boolean
): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")
    return runCatching {
        val highlights = Highlights.Builder()
            .code(code)
            .theme(SyntaxThemes.atom(darkMode = isDark))
            .language(mapSyntaxLanguage(language))
            .build()
            .getHighlights()

        buildAnnotatedString {
            append(code)
            for (highlight in highlights) {
                val start = highlight.location.start.coerceIn(0, code.length)
                val end = highlight.location.end.coerceIn(start, code.length)
                if (start < end) {
                    when (highlight) {
                        is ColorHighlight -> addStyle(
                            SpanStyle(color = Color(highlight.rgb or 0xFF000000.toInt())),
                            start = start,
                            end = end
                        )
                        is BoldHighlight -> addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            start = start,
                            end = end
                        )
                    }
                }
            }
        }
    }.getOrElse { AnnotatedString(code) }
}

private fun mapSyntaxLanguage(language: String): SyntaxLanguage {
    return when (language.trim().lowercase()) {
        "kt", "kotlin" -> SyntaxLanguage.KOTLIN
        "java" -> SyntaxLanguage.JAVA
        "py", "python" -> SyntaxLanguage.PYTHON
        "js", "javascript", "jsx" -> SyntaxLanguage.JAVASCRIPT
        "ts", "typescript", "tsx" -> SyntaxLanguage.TYPESCRIPT
        "sh", "bash", "zsh", "shell" -> SyntaxLanguage.SHELL
        "rs", "rust" -> SyntaxLanguage.RUST
        "go", "golang" -> SyntaxLanguage.GO
        "c" -> SyntaxLanguage.C
        "cpp", "c++", "cc", "hpp" -> SyntaxLanguage.CPP
        "cs", "csharp", "c#" -> SyntaxLanguage.CSHARP
        "swift" -> SyntaxLanguage.SWIFT
        "rb", "ruby" -> SyntaxLanguage.RUBY
        "php" -> SyntaxLanguage.PHP
        "dart" -> SyntaxLanguage.DART
        "pl", "perl" -> SyntaxLanguage.PERL
        "coffee", "coffeescript" -> SyntaxLanguage.COFFEESCRIPT
        else -> SyntaxLanguage.DEFAULT
    }
}
