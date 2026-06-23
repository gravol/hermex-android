package com.hermes.chat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hermes.chat.ui.theme.TelegramChatColors

/**
 * Telegram-ish renderer for Hermes responses.
 *
 * Supports:
 * - **bold** / *italic* / `inline code`
 * - fenced code blocks ```shell ... ``` with Telegram-like header/body cards
 * - blockquote quote cards (`> Jeff`, `> • Voice message`)
 * - clickable URLs (tap to open in browser)
 */
@Composable
fun FormattedMessageText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val blocks = parseTelegramTextBlocks(text)
    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(6.dp))
            when (block) {
                is TelegramTextBlock.Paragraph -> {
                    val annotated = formatInline(block.text, linkColor = TelegramChatColors.Blue)
                    ClickableText(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium.copy(color = color),
                        onClick = { offset ->
                            annotated.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { uriHandler.openUri(it.item) }
                        },
                    )
                }
                is TelegramTextBlock.Code -> CodeBlock(text = block.text, language = block.language)
                is TelegramTextBlock.Quote -> QuoteBlock(block)
            }
        }
    }
}

@Composable
private fun CodeBlock(text: String, language: String?) {
    val label = language
        ?.takeIf { it.isNotBlank() }
        ?.lowercase()
        ?: "shell"
    Surface(
        color = TelegramChatColors.DarkIncomingBubble.copy(alpha = 0.96f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(10.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TelegramChatColors.Blue.copy(alpha = 0.32f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
            Text(
                text = text.trimEnd(),
                color = Color(0xFFE8F0F8),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF05070A))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun QuoteBlock(block: TelegramTextBlock.Quote) {
    Surface(
        color = Color(0xFF3A2534).copy(alpha = 0.88f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                modifier = Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFFF5F7E))
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = block.title,
                    color = Color(0xFFFF8AA1),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                )
                if (block.subtitle.isNotBlank()) {
                    Text(
                        text = block.subtitle,
                        color = Color(0xFFFF8AA1),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

internal sealed interface TelegramTextBlock {
    data class Paragraph(val text: String) : TelegramTextBlock
    data class Code(val text: String, val language: String? = null) : TelegramTextBlock
    data class Quote(val title: String, val subtitle: String = "") : TelegramTextBlock
}

internal fun parseTelegramTextBlocks(text: String): List<TelegramTextBlock> {
    if (text.isBlank()) return emptyList()

    val blocks = mutableListOf<TelegramTextBlock>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var inCode = false
    var codeLanguage: String? = null

    fun flushParagraph() {
        val value = paragraph.toString().trimEnd('\n')
        if (value.isBlank()) {
            paragraph.clear()
            return
        }

        val quoteLines = value.lines()
        if (quoteLines.isNotEmpty() && quoteLines.all { it.trimStart().startsWith(">") }) {
            val cleaned = quoteLines.map { it.trimStart().removePrefix(">").trim() }.filter { it.isNotBlank() }
            if (cleaned.isNotEmpty()) {
                blocks.add(TelegramTextBlock.Quote(cleaned.first(), cleaned.drop(1).joinToString("\n")))
            }
        } else {
            blocks.add(TelegramTextBlock.Paragraph(value))
        }
        paragraph.clear()
    }

    fun flushCode() {
        blocks.add(TelegramTextBlock.Code(code.toString().trim('\n'), codeLanguage))
        code.clear()
        codeLanguage = null
    }

    text.lines().forEach { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (inCode) {
                flushCode()
                inCode = false
            } else {
                flushParagraph()
                codeLanguage = trimmed.removePrefix("```").trim().ifBlank { null }
                inCode = true
            }
        } else if (inCode) {
            code.appendLine(line)
        } else if (line.isBlank()) {
            flushParagraph()
        } else {
            paragraph.appendLine(line)
        }
    }

    if (inCode) flushCode() else flushParagraph()
    return blocks.ifEmpty { listOf(TelegramTextBlock.Paragraph(text)) }
}

private val URL_PATTERN = Regex(
    """(https?://[\w\-._~:/?#\[\]@!$&'()*+,;=%]+)""",
    RegexOption.IGNORE_CASE,
)

internal fun formatInline(text: String, linkColor: Color = Color.Blue): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // URL — detect before bold/italic so https:** isn't treated as bold
                text[i] == 'h' -> {
                    val match = URL_PATTERN.find(text, i)
                    if (match != null && match.range.first == i) {
                        val url = match.value
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            )
                        ) {
                            append(url)
                        }
                        pop()
                        i = match.range.last + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", startIndex = i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', startIndex = i + 1)
                    if (end > i + 1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = TelegramChatColors.DarkCanvas.copy(alpha = 0.55f),
                            )
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                text[i] == '*' -> {
                    val end = text.indexOf('*', startIndex = i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
