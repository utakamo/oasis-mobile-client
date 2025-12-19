package com.example.oasis_mobile_client

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.oasis_mobile_client.ui.theme.OasismobileclientTheme
import com.example.oasis_mobile_client.MarkdownParser
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material3.ColorScheme

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OasismobileclientTheme(dynamicColor = true) {
                val loginState by chatViewModel.loginState.collectAsStateWithLifecycle()
                val discoveryState by chatViewModel.discoveryState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    chatViewModel.tryAutoLoginIfNeeded()
                }

                if (loginState is LoginState.Success) {
                    ChatScreen(chatViewModel)
                } else {
                    LoginScreen(
                        onLoginClick = { ip, userId, password ->
                            chatViewModel.login(ip, userId, password)
                        },
                        onDiscoverClick = { chatViewModel.discoverOasisDevices() },
                        onRetryLogin = { chatViewModel.retryLogin() },
                        loginState = loginState,
                        discoveryState = discoveryState,
                        onDismissDialog = { chatViewModel.clearDiscoveryState() },
                        onDismissLoginError = { chatViewModel.clearLoginState() }
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    var inputText by remember { mutableStateOf("") }
    val sysmsgs by viewModel.sysmsgList.collectAsStateWithLifecycle()
    val selectedKey by viewModel.selectedSysmsgKey.collectAsStateWithLifecycle()
    val reboot by viewModel.rebootBanner.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val title by viewModel.chatTitle.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val voiceEnabled by viewModel.voiceEnabled.collectAsStateWithLifecycle()
    val speechRate by viewModel.speechRate.collectAsStateWithLifecycle()
    val speechPitch by viewModel.speechPitch.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val atBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    LaunchedEffect(viewModel.messages.size) {
        if (atBottom && viewModel.messages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Initialize/release TextToSpeech and speak messages
    val context = LocalContext.current
    val ttsManager = remember { TextToSpeechManager(context) }

    LaunchedEffect(speechRate) {
        ttsManager.setSpeechRate(speechRate)
    }

    LaunchedEffect(speechPitch) {
        ttsManager.setPitch(speechPitch)
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    var lastSpokenIndex by remember { mutableStateOf(0) }

    LaunchedEffect(voiceEnabled) {
        // Avoid bulk reading of recent history when toggling voice mode
        lastSpokenIndex = viewModel.messages.size
    }

    LaunchedEffect(viewModel.messages.size) {
        if (!voiceEnabled) return@LaunchedEffect
        val msgs = viewModel.messages
        for (i in lastSpokenIndex until msgs.size) {
            val m = msgs[i]
            if (!m.isUser && !m.text.startsWith("UCI提案")) {
                val spoken = MarkdownParser.markdownToPlain(m.text)
                if (spoken.isNotBlank()) {
                    ttsManager.speak(spoken, "msg_$i")
                }
            }
        }
        lastSpokenIndex = msgs.size
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val retryLabel = stringResource(id = R.string.retry)
    LaunchedEffect(lastError) {
        lastError?.let {
            val result = snackbarHostState.showSnackbar(it, actionLabel = retryLabel)
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.retryLastFailed()
            }
            viewModel.consumeError()
        }
    }

    var openMenu by remember { mutableStateOf(false) }
    var openSysmsgDialog by remember { mutableStateOf(false) }
    var openHistoryDialog by remember { mutableStateOf(false) }
    var openSettingsDialog by remember { mutableStateOf(false) }
    var openToolsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(openHistoryDialog) {
        if (openHistoryDialog) {
            viewModel.refreshHistory()
        }
    }

    LaunchedEffect(openToolsDialog) {
        if (openToolsDialog) {
            viewModel.refreshTools()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(text = title ?: stringResource(R.string.oasis_title)) },
                    navigationIcon = {
                        IconButton(onClick = { openMenu = true }) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.system_messages))
                        }
                    },
                    actions = {
                        // Voice mode toggle (speaker)
                        IconButton(onClick = { viewModel.toggleVoiceEnabled() }) {
                            if (voiceEnabled) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.voice_on))
                            } else {
                                Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = stringResource(R.string.voice_off))
                            }
                        }
                        // New chat
                        IconButton(onClick = { viewModel.startNewChat() }) {
                            Icon(Icons.Filled.AddCircle, contentDescription = stringResource(R.string.new_chat))
                        }
                        // Settings (gear)
                        IconButton(onClick = { openSettingsDialog = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                )
                DropdownMenu(expanded = openMenu, onDismissRequest = { openMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.system_messages)) },
                        onClick = {
                            openMenu = false
                            openSysmsgDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tools)) },
                        onClick = {
                            openMenu = false
                            openToolsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history)) },
                        onClick = {
                            openMenu = false
                            openHistoryDialog = true
                        }
                    )
                }
                
                if (reboot) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = stringResource(R.string.reboot_required))
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.dismissRebootBanner() }) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            MessageInput(
                value = inputText,
                onValueChange = {
                    inputText = it
                    viewModel.onInputTextChanged(it)
                },
                onSendClick = {
                    if (!sending && inputText.isNotBlank()) {
                        viewModel.sendMessage()
                        inputText = "" // Clear the UI input field
                    }
                },
                enabled = (!sending && inputText.isNotBlank())
            )
        }
    ) { paddingValues ->
        val bgBrush = Brush.verticalGradient(colors = listOf(Color(0xFFF7F7F7), Color(0xFFEDEBFF)))
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(bgBrush)
        ) {
            // Show AI service selector at the top of the chat content
            val services by viewModel.aiServices.collectAsStateWithLifecycle()
            val selectedServiceId by viewModel.selectedServiceId.collectAsStateWithLifecycle()
            if (services.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AiServiceSelector(
                        items = services,
                        selectedId = selectedServiceId,
                        onSelect = { viewModel.selectAiService(it) }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Message list area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                MessageList(
                    messages = viewModel.messages,
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    sending = sending,
                    onQuoteRequested = { quote ->
                        val quoted = quote.lines().joinToString("\n") { "> " + it } + "\n\n"
                        inputText = quoted
                        viewModel.onInputTextChanged(quoted)
                    }
                )
                if (!atBottom && viewModel.messages.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    ) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.latest))
                    }
                }
            }
        }
    }

    if (openSysmsgDialog) {
        SysmsgSelectDialog(
            items = sysmsgs.map { it.title to it.key },
            selectedKey = selectedKey,
            onSelect = { key ->
                viewModel.selectSysmsg(key)
                openSysmsgDialog = false
            },
            onDismiss = { openSysmsgDialog = false }
        )
    }

    if (openHistoryDialog) {
        HistoryDialog(
            items = viewModel.history.collectAsStateWithLifecycle().value,
            onSelect = { id, title ->
                viewModel.loadChatById(id, title)
                openHistoryDialog = false
            },
            onDismiss = { openHistoryDialog = false }
        )
    }

    if (openSettingsDialog) {
        SettingsDialog(
            onDismiss = { openSettingsDialog = false },
            onLogout = {
                viewModel.logout()
                openSettingsDialog = false
            },
            currentRate = speechRate,
            onChangeSpeechRate = { viewModel.setSpeechRate(it) },
            currentPitch = speechPitch,
            onChangeSpeechPitch = { viewModel.setSpeechPitch(it) }
        )
    }

    if (openToolsDialog) {
        val items by viewModel.tools.collectAsStateWithLifecycle()
        val loading by viewModel.toolsLoading.collectAsStateWithLifecycle()
        ToolsDialog(
            items = items,
            loading = loading,
            onToggle = { name, enabled -> viewModel.setToolEnabled(name, enabled) },
            onDismiss = { openToolsDialog = false }
        )
    }
}

@Composable
fun MessageList(messages: List<Message>, modifier: Modifier = Modifier, listState: LazyListState, sending: Boolean, onQuoteRequested: (String) -> Unit) {
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

@Composable
fun HistoryDialog(
    items: List<ChatViewModel.ChatSummary>,
    onSelect: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.select_chat), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(items) { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(s.id, s.title) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.title.ifBlank { s.id })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(message: Message, onQuoteRequested: (String) -> Unit) {
    val bubbleColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val shape = if (message.isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!message.isUser) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = stringResource(R.string.assistant),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 6.dp)
            )
        }

        var showMenu by remember { mutableStateOf(false) }
        val clipboard = LocalClipboardManager.current

        Surface(
            color = bubbleColor,
            contentColor = textColor,
            shape = shape,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.75f)
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
                    .size(28.dp)
                    .padding(start = 6.dp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .imePadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.enter_message)) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            trailingIcon = {
                IconButton(onClick = onSendClick, enabled = enabled) {
                    if (!enabled && value.isNotBlank()) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                    }
                }
            }
        )
    }
}

@Composable
fun SysmsgActionSelector(
    items: List<Pair<String, String>>, // (title, key)
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = items.firstOrNull { it.second == selectedKey }?.first ?: "default"
    Box {
        TextButton(onClick = { expanded = true }, content = { Text(selectedTitle) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (title, key) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        onSelect(key)
                    }
                )
            }
        }
    }
}

@Composable
fun SysmsgSelectDialog(
    items: List<Pair<String, String>>, // (title, key)
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(selectedKey) { mutableStateOf(selectedKey) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.system_messages), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(items) { (title, key) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = key }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (selected == key), onClick = { selected = key })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(title)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    TextButton(onClick = { onSelect(selected) }) { Text(stringResource(R.string.select)) }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    currentRate: Float,
    onChangeSpeechRate: (Float) -> Unit,
    currentPitch: Float,
    onChangeSpeechPitch: (Float) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("You can add more settings here.")

                Spacer(modifier = Modifier.height(16.dp))
                // Speech rate
                Text(text = stringResource(R.string.speech_rate) + " x" + String.format(Locale.US, "%.1f", currentRate))
                Slider(
                    value = currentRate,
                    onValueChange = { onChangeSpeechRate(it) },
                    valueRange = 0.5f..2.0f
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Speech pitch
                Text(text = stringResource(R.string.speech_pitch) + " x" + String.format(Locale.US, "%.1f", currentPitch))
                Slider(
                    value = currentPitch,
                    onValueChange = { onChangeSpeechPitch(it) },
                    valueRange = 0.5f..2.0f
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onLogout) { Text(stringResource(R.string.logout)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}

@Composable
fun ToolsDialog(
    items: List<ChatViewModel.ToolItem>,
    loading: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.tools), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (loading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(items) { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.name)
                                    if (t.server.isNotBlank()) {
                                        Text(
                                            t.server,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                Switch(checked = t.enabled, onCheckedChange = { onToggle(t.name, it) })
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun UciBlock(text: String) {
    var copied by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Surface(tonalElevation = 1.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(text, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
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

class MockChatViewModel(application: Application) : ChatViewModel(application)

@Preview(showBackground = true, name = "Chat Screen Preview")
@Composable
fun ChatScreenPreview() {
    OasismobileclientTheme {
        val mockApplication = Application()
        ChatScreen(MockChatViewModel(mockApplication))
    }
}

@Preview(showBackground = true, name = "Login Screen Preview")
@Composable
fun LoginScreenPreview() {
    OasismobileclientTheme {
        LoginScreen(
            onLoginClick = { _, _, _ -> },
            onDiscoverClick = {},
            onRetryLogin = {},
            loginState = LoginState.Idle,
            discoveryState = DiscoveryState.Idle,
            onDismissDialog = {},
            onDismissLoginError = {}
        )
    }
}