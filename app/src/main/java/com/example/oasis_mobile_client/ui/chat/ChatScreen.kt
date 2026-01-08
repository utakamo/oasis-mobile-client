package com.example.oasis_mobile_client.ui.chat

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.oasis_mobile_client.ChatViewModel
import com.example.oasis_mobile_client.MarkdownParser
import com.example.oasis_mobile_client.R
import com.example.oasis_mobile_client.SpeechRecognizerManager
import com.example.oasis_mobile_client.TextToSpeechManager
import com.example.oasis_mobile_client.ui.components.AiServiceSelector
import com.example.oasis_mobile_client.ui.components.AudioVisualizer
import com.example.oasis_mobile_client.ui.components.MessageInput
import com.example.oasis_mobile_client.ui.dialogs.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    val sessionExpired by viewModel.sessionExpired.collectAsStateWithLifecycle()
    val restartServiceTarget by viewModel.restartServiceTarget.collectAsStateWithLifecycle()
    val shutdownConfirmation by viewModel.shutdownConfirmation.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val atBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            val isUserAtBottom = listState.firstVisibleItemIndex < 2
            val isAiResponse = !viewModel.messages.last().isUser
            
            if (isUserAtBottom || isAiResponse) {
                listState.animateScrollToItem(0)
            }
        }
    }

    // Initialize/release TextToSpeech and speak messages
    val context = LocalContext.current
    val ttsManager = remember { TextToSpeechManager(context) }
    val sttManager = remember { SpeechRecognizerManager(context) }
    val isListening by sttManager.isListening.collectAsStateWithLifecycle()

    LaunchedEffect(speechRate) {
        ttsManager.setSpeechRate(speechRate)
    }

    LaunchedEffect(speechPitch) {
        ttsManager.setPitch(speechPitch)
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
            sttManager.destroy()
        }
    }

    // Callback when TTS finishes speaking
    LaunchedEffect(Unit) {
        ttsManager.onSpeakDone = {
            if (viewModel.voiceEnabled.value) {
                // Must run on Main Thread
                coroutineScope.launch(Dispatchers.Main) {
                    sttManager.startListening(
                        onResult = { text ->
                            viewModel.onInputTextChanged(text)
                            viewModel.sendMessage()
                        },
                        onError = { /* Handle error or just stop listening */ }
                    )
                }
            }
        }
    }

    var lastSpokenIndex by remember { mutableStateOf(0) }

    LaunchedEffect(voiceEnabled) {
        // Avoid bulk reading of recent history when toggling voice mode
        lastSpokenIndex = viewModel.messages.size
        
        if (voiceEnabled) {
             // If enabled, start listening immediately (if not speaking)
             sttManager.startListening(
                onResult = { text ->
                    viewModel.onInputTextChanged(text)
                    viewModel.sendMessage()
                },
                onError = {}
             )
        } else {
            sttManager.stopListening()
            ttsManager.stop()
        }
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
    var openFunctionCallingDialog by remember { mutableStateOf(false) }

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
    
    LaunchedEffect(openFunctionCallingDialog) {
        if (openFunctionCallingDialog) {
            viewModel.refreshTools() // Ensure we have the latest tool definitions
            viewModel.clearFunctionCallingResult()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(text = title ?: stringResource(R.string.oasis_title), style = MaterialTheme.typography.titleLarge) },
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
                        text = { Text("Function Calling") },
                        onClick = {
                            openMenu = false
                            openFunctionCallingDialog = true
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
                    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
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
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Background visualizer when voice mode is enabled
                if (voiceEnabled) {
                    AudioVisualizer(isListening = isListening, rmsDbFlow = sttManager.rmsDb)
                }

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

    if (openFunctionCallingDialog) {
        val items by viewModel.tools.collectAsStateWithLifecycle()
        val result by viewModel.functionCallingResult.collectAsStateWithLifecycle()
        
        FunctionCallingDialog(
            tools = items,
            onExecute = { name, params -> viewModel.executeFunctionCalling(name, params) },
            onDismiss = { openFunctionCallingDialog = false },
            result = result
        )
    }

    restartServiceTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestartDialog() },
            title = { Text(text = "Restart Service") },
            text = { Text(text = "Do you want to restart the service '$target'?") },
            confirmButton = {
                TextButton(onClick = { viewModel.restartService() }) {
                    Text(text = stringResource(R.string.ok)) // Using generic OK string or add "Yes"
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRestartDialog() }) {
                    Text(text = stringResource(R.string.close)) // Using generic Close or add "No"
                }
            }
        )
    }

    if (shutdownConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissShutdownDialog() },
            title = { Text(text = "System Shutdown") },
            text = { Text(text = "Do you want to shutdown the system?") },
            confirmButton = {
                TextButton(onClick = { viewModel.shutdownSystem() }) {
                    Text(text = stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissShutdownDialog() }) {
                    Text(text = stringResource(R.string.close))
                }
            }
        )
    }

    if (sessionExpired) {
        AlertDialog(
            onDismissRequest = { /* Force user to choose */ },
            title = { Text("Session Expired") },
            text = { Text("The connection to the server has expired. Do you want to reconnect?") },
            confirmButton = {
                TextButton(onClick = { viewModel.reconnect() }) {
                    Text("Reconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.logout() }) {
                    Text("Logout")
                }
            }
        )
    }
}