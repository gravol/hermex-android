package com.hermes.chat.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * - Bullet lists (`- `, `* `) and numbered lists (`1. `)
 * - Headers (`# ## ###`)
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
            if (index > 0) Spacer(Modifier.height(4.dp))
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
                is TelegramTextBlock.Header -> {
                    val annotated = formatInline(block.text, linkColor = TelegramChatColors.Blue)
                    val fontSize = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    ClickableText(
                        text = annotated,
                        style = fontSize.copy(
                            color = color,
                            fontWeight = FontWeight.Bold,
                        ),
                        onClick = { offset ->
                            annotated.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { uriHandler.openUri(it.item) }
                        },
                    )
                }
                is TelegramTextBlock.BulletList -> {
                    Column {
                        block.items.forEachIndexed { itemIndex, item ->
                            Row(
                                modifier = Modifier.padding(start = 4.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = "\u2022",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = color.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    modifier = Modifier
                                        .width(18.dp)
                                        .padding(top = 1.dp),
                                )
                                val annotated = formatInline(item, linkColor = TelegramChatColors.Blue)
                                ClickableText(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = color),
                                    onClick = { offset ->
                                        annotated.getStringAnnotations("URL", offset, offset)
                                            .firstOrNull()?.let { uriHandler.openUri(it.item) }
                                    },
                                )
                            }
                            if (itemIndex < block.items.lastIndex) {
                                Spacer(Modifier.height(3.dp))
                            }
                        }
                    }
                }
                is TelegramTextBlock.NumberedList -> {
                    Column {
                        block.items.forEachIndexed { itemIndex, item ->
                            Row(
                                modifier = Modifier.padding(start = 4.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = "${block.start + itemIndex}.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = color.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    modifier = Modifier
                                        .width(22.dp)
                                        .padding(top = 1.dp),
                                )
                                val annotated = formatInline(item, linkColor = TelegramChatColors.Blue)
                                ClickableText(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = color),
                                    onClick = { offset ->
                                        annotated.getStringAnnotations("URL", offset, offset)
                                            .firstOrNull()?.let { uriHandler.openUri(it.item) }
                                    },
                                )
                            }
                            if (itemIndex < block.items.lastIndex) {
                                Spacer(Modifier.height(3.dp))
                            }
                        }
                    }
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
        ?: ""
    Surface(
        color = TelegramChatColors.DarkIncomingBubble.copy(alpha = 0.96f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(10.dp))
        ) {
            if (label.isNotBlank()) {
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
    data class Header(val text: String, val level: Int) : TelegramTextBlock
    data class BulletList(val items: List<String>) : TelegramTextBlock
    data class NumberedList(val items: List<String>, val start: Int = 1) : TelegramTextBlock
    data class Code(val text: String, val language: String? = null) : TelegramTextBlock
    data class Quote(val title: String, val subtitle: String = "") : TelegramTextBlock
}

/**
 * Regex that matches a numbered list item like "1. " or "12. " at line start.
 */
private val NUMBERED_ITEM = Regex("""^(\d+)[.)]\s""")

internal fun parseTelegramTextBlocks(text: String): List<TelegramTextBlock> {
    if (text.isBlank()) return emptyList()

    val blocks = mutableListOf<TelegramTextBlock>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var inCode = false
    var codeLanguage: String? = null

    // Temp accumulators for list detection
    var bulletItems = mutableListOf<String>()
    var numberedItems = mutableListOf<String>()
    var numberedStart = 1

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

    fun flushBulletList() {
        if (bulletItems.isNotEmpty()) {
            blocks.add(TelegramTextBlock.BulletList(bulletItems.toList()))
            bulletItems = mutableListOf()
        }
    }

    fun flushNumberedList() {
        if (numberedItems.isNotEmpty()) {
            blocks.add(TelegramTextBlock.NumberedList(numberedItems.toList(), numberedStart))
            numberedItems = mutableListOf()
            numberedStart = 1
        }
    }

    fun flushCode() {
        blocks.add(TelegramTextBlock.Code(code.toString().trim('\n'), codeLanguage))
        code.clear()
        codeLanguage = null
    }

    text.lines().forEach { line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                flushBulletList()
                flushNumberedList()
                if (inCode) {
                    flushCode()
                    inCode = false
                } else {
                    codeLanguage = trimmed.removePrefix("```").trim().ifBlank { null }
                    inCode = true
                }
            }
            inCode -> {
                code.appendLine(line)
            }
            line.isBlank() -> {
                flushParagraph()
                flushBulletList()
                flushNumberedList()
            }
            // Header: # ## ### #### ##### ######
            trimmed.startsWith("# ") || trimmed.startsWith("## ") ||
                trimmed.startsWith("### ") || trimmed.startsWith("#### ") ||
                trimmed.startsWith("##### ") || trimmed.startsWith("###### ") -> {
                flushParagraph()
                flushBulletList()
                flushNumberedList()
                val level = trimmed.takeWhile { it == '#' }.length
                val content = trimmed.dropWhile { it == '#' || it == ' ' }.trim()
                blocks.add(TelegramTextBlock.Header(content, level))
            }
            // Unordered list: - or *
            (trimmed.startsWith("- ") || trimmed.startsWith("* ")) && !trimmed.startsWith("**") -> {
                flushParagraph()
                flushNumberedList()
                val content = trimmed.removePrefix("-").removePrefix("*").trim()
                bulletItems.add(content)
            }
            // Ordered list: 1. or 1)
            NUMBERED_ITEM.find(trimmed) != null -> {
                flushParagraph()
                flushBulletList()
                val match = NUMBERED_ITEM.find(trimmed)!!
                val num = match.groupValues[1].toIntOrNull() ?: 1
                val content = trimmed.substring(match.value.length)
                if (numberedItems.isEmpty()) {
                    numberedStart = num
                }
                numberedItems.add(content)
            }
            else -> {
                paragraph.appendLine(line)
            }
        }
    }

    if (inCode) flushCode() else flushParagraph()
    flushBulletList()
    flushNumberedList()
    return blocks.ifEmpty { listOf(TelegramTextBlock.Paragraph(text)) }
}

private val URL_PATTERN = Regex(
    """(https?://[\w\-._~:/?#\[\]@!$&'()*+,;=%]+)""",
    RegexOption.IGNORE_CASE,
)

/**
 * Format inline markdown markers: **bold**, *italic*, `code`, and URLs.
 * Also handles the edge case where `*` at line start is a list marker, not italic.
 */
internal fun formatInline(text: String, linkColor: Color = Color.Blue): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // URL — detect before bold/italic so https:** isn't treated as bold
                text[i] == 'h' || text[i] == 'H' -> {
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
                // **bold** — must be checked before single * for italic
                text.startsWith("**", i) && text.length > i + 4 -> {
                    val end = text.indexOf("**", startIndex = i + 2)
                    if (end > i + 2 && end + 2 <= text.length) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // `inline code`
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
                // *italic* — but only when it looks like a pair
                text[i] == '*' && text.length > i + 2 -> {
                    val end = text.indexOf('*', startIndex = i + 1)
                    if (end > i + 1 && end + 1 <= text.length) {
                        // Make sure the closing * isn't followed by another *
                        // (that would be ** which is handled above)
                        if (end + 1 < text.length && text[end + 1] == '*') {
                            append(text[i])
                            i++
                        } else {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(text.substring(i + 1, end))
                            }
                            i = end + 1
                        }
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
