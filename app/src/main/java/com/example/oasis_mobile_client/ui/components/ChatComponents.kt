package com.example.oasis_mobile_client.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.oasis_mobile_client.ChatViewModel
import com.example.oasis_mobile_client.MarkdownParser
import com.example.oasis_mobile_client.Message
import com.example.oasis_mobile_client.R

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Suppress("DEPRECATION")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(message: Message, onQuoteRequested: (String) -> Unit) {
    val bubbleColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val shape = if (message.isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 12.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!message.isUser) {
            Image(
                painter = painterResource(id = R.drawable.openwrt),
                contentDescription = stringResource(R.string.assistant),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 8.dp)
                    .clip(CircleShape)
            )
        }

        var showMenu by remember { mutableStateOf(false) }
        val clipboard = LocalClipboardManager.current

        Surface(
            color = bubbleColor,
            contentColor = textColor,
            shape = shape,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.8f)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showMenu = true }
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!message.isUser && message.toolUsed) {
                    ToolChip(label = message.toolLabel)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                androidx.compose.foundation.text.selection.SelectionContainer {
                    if (message.text.startsWith("UCI提案")) {
                        UciBlock(text = message.text)
                    } else {
                        if (message.isUser) {
                            Text(text = message.text)
                        } else {
                            Text(text = MarkdownParser.parseMarkdown(message.text, MaterialTheme.colorScheme))
                        }
                    }
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, onClick = {
                        clipboard.setText(AnnotatedString(message.text))
                        showMenu = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.quote)) }, onClick = {
                        onQuoteRequested(message.text)
                        showMenu = false
                    })
                }
            }
        }

        if (message.isUser) {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = stringResource(R.string.user),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(32.dp)
                    .padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.enter_message)) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                trailingIcon = {
                    IconButton(onClick = onSendClick, enabled = enabled) {
                        if (!enabled && value.isNotBlank()) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun UciBlock(text: String) {
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Surface(tonalElevation = 1.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(text, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(text))
                copied = true
            }) { Text(if (copied) "コピー済" else "コピー") }
        }
    }
}

@Composable
fun ToolChip(label: String?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(50),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = stringResource(R.string.tool_run),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = (label?.takeIf { it.isNotBlank() } ?: stringResource(R.string.tool_run)), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun AiServiceSelector(
    items: List<ChatViewModel.AiServiceItem>,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // If nothing selected, just show first item label or "Select"
    val selectedItem = items.firstOrNull { it.id == selectedId }
    val label = selectedItem?.label ?: "Select AI Model"

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        expanded = false
                        onSelect(item.id)
                    }
                )
            }
        }
    }
}
