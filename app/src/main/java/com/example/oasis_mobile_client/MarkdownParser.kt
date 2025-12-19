package com.example.oasis_mobile_client

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

object MarkdownParser {
    fun parseMarkdown(markdown: String, colors: ColorScheme): AnnotatedString {
        val builder = AnnotatedString.Builder()
        val codeBlockRegex = Regex("```([\\s\\S]*?)```")
        val linkRegex = Regex("\\[([^\\]]+)\\]\\(([^\\)]+)\\)")
        var cursor = 0
        val codeBlocks = codeBlockRegex.findAll(markdown).toList()

        fun appendWithInlineFormatting(text: String) {
            var localCursor = 0
            val combinedRegex = Regex("(`[^`]+`)|(\\*\\*[^*]+\\*\\*)|(\\*([^*]+)\\*)|(\\[[^\\]]+\\]\\([^\\)]+\\))")
            val matches = combinedRegex.findAll(text)
            for (match in matches) {
                if (match.range.first < localCursor) continue
                builder.append(text.substring(localCursor, match.range.first))
                val value = match.value
                when {
                    value.startsWith("`") -> {
                        val content = value.trim('`')
                        builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant)) {
                            append(content)
                        }
                    }
                    value.startsWith("**") -> {
                        val content = value.removeSurrounding("**")
                        builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(content)
                        }
                    }
                    value.startsWith("*") -> {
                        val content = value.removeSurrounding("*")
                        builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(content)
                        }
                    }
                    value.startsWith("[") -> {
                        val linkMatch = linkRegex.find(value)
                        if (linkMatch != null) {
                            val linkText = linkMatch.groupValues[1]
                            builder.withStyle(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)) {
                                append(linkText)
                            }
                        } else {
                            builder.append(value)
                        }
                    }
                    else -> builder.append(value)
                }
                localCursor = match.range.last + 1
            }
            if (localCursor < text.length) builder.append(text.substring(localCursor))
        }

        for (match in codeBlocks) {
            val preText = markdown.substring(cursor, match.range.first)
            appendWithInlineFormatting(preText)
            val codeContent = match.groupValues[1].trim()
            builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.secondaryContainer, color = colors.onSecondaryContainer)) {
                append("\n$codeContent\n")
            }
            cursor = match.range.last + 1
        }
        if (cursor < markdown.length) appendWithInlineFormatting(markdown.substring(cursor))
        return builder.toAnnotatedString()
    }

    fun markdownToPlain(text: String): String {
        // Fenced code blocks ``` ... ```
        var s = text.replace(Regex("```[\\s\\S]*?```", RegexOption.MULTILINE), " ")
        // Inline code `code`
        s = s.replace(Regex("`[^`]*`"), " ")
        // Remove images ![alt](url)
        s = s.replace(Regex("!\\[[^\\]]*]\\([^\\)]*\\)"), " ")
        // Convert links [text](url) to plain text
        s = s.replace(Regex("\\[([^\\]]+)]\\([^\\)]*\\)"), "$1")
        // Remove per-line blockquotes and list markers; replace table separators with spaces
        s = s.lines().joinToString(" ") { line ->
            var l = line
            l = l.replace(Regex("^\\s{0,3}>\\s?"), "")
            l = l.replace(Regex("^\\s*([-*+]|\\d+\\.)\\s+"), "")
            l = l.replace("|", " ")
            l.trim()
        }
        // Remove emphasis markers * _ ** __
        s = s.replace(Regex("([*_]{1,3}|__)"), "")
        // Remove HTML tags
        s = s.replace(Regex("<[^>]+>"), " ")
        // Normalize extra spaces
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }
}
