package com.example.oasis_mobile_client.ui.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.oasis_mobile_client.Message
import com.example.oasis_mobile_client.ui.components.MessageItem

@Composable
fun MessageList(
    messages: List<Message>,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    sending: Boolean,
    onQuoteRequested: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        reverseLayout = true, // Display latest messages at the bottom
        state = listState
    ) {
        if (sending) {
            item(key = "typing_indicator") {
                MessageItem(message = Message(text = "…", isUser = false), onQuoteRequested = onQuoteRequested)
            }
        }
        items(messages.reversed(), key = { message -> message.id }) { message ->
            MessageItem(message = message, onQuoteRequested = onQuoteRequested)
        }
    }
}
