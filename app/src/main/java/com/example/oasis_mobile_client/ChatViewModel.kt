package com.example.oasis_mobile_client

import android.app.Application
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.core.content.edit
import com.example.oasis_mobile_client.util.ErrorMessageMapper

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

sealed class DiscoveryState {
    object Idle : DiscoveryState()
    object Searching : DiscoveryState()
    data class Success(val devices: List<OasisRepository.DiscoveredDevice>) : DiscoveryState()
    data class Error(val message: String) : DiscoveryState()
}

open class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ChatViewModel"

    val messages = mutableStateListOf<Message>()
    private var inputText = ""

    private val repository = OasisRepository(application)
    private var sessionId: String? = null
    private var chatId: String = ""
    private var lastIpAddress: String? = null
    private var lastUsername: String? = null
    private var lastPassword: String? = null

    // ---- Credentials persistence ----
    private val prefs = run {
        val app = getApplication<Application>()
        val masterKey = MasterKey.Builder(app, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val enc = EncryptedSharedPreferences.create(
            app,
            "oasis_prefs_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        // One-time migration from legacy plain SharedPreferences
        val legacy = app.getSharedPreferences("oasis_prefs", Context.MODE_PRIVATE)
        val hasLegacy = legacy.contains("ip") || legacy.contains("user") || legacy.contains("pass")
        if (hasLegacy) {
            val ip = legacy.getString("ip", null)
            val u = legacy.getString("user", null)
            val p = legacy.getString("pass", null)
            if (!ip.isNullOrBlank() && !u.isNullOrBlank() && !p.isNullOrBlank()) {
                enc.edit {putString("ip", ip).putString("user", u).putString("pass", p) }
            }
            legacy.edit { clear() }
        }
        enc
    }
    private val PREF_KEY_IP = "ip"
    private val PREF_KEY_USER = "user"
    private val PREF_KEY_PASS = "pass"
    private val PREF_KEY_SPEECH_RATE = "speech_rate"
    private val PREF_KEY_SPEECH_PITCH = "speech_pitch"
    private var attemptedAutoLogin = false

    private data class Credentials(val ip: String, val user: String, val pass: String)

    private fun saveCredentials(ip: String, user: String, pass: String) {
        prefs.edit {
            putString(PREF_KEY_IP, ip)
                .putString(PREF_KEY_USER, user)
                .putString(PREF_KEY_PASS, pass)
        }
    }

    private fun loadCredentials(): Credentials? {
        val ip = prefs.getString(PREF_KEY_IP, null)
        val u = prefs.getString(PREF_KEY_USER, null)
        val p = prefs.getString(PREF_KEY_PASS, null)
        return if (!ip.isNullOrBlank() && !u.isNullOrBlank() && !p.isNullOrBlank()) Credentials(ip, u, p) else null
    }

    private fun clearCredentials() {
        prefs.edit {
            remove(PREF_KEY_IP)
                .remove(PREF_KEY_USER)
                .remove(PREF_KEY_PASS)
        }
    }

    fun tryAutoLoginIfNeeded() {
        if (attemptedAutoLogin) return
        attemptedAutoLogin = true
        val creds = loadCredentials() ?: return
        if (loginState.value is LoginState.Loading || loginState.value is LoginState.Success) return
        viewModelScope.launch(Dispatchers.Main) {
            login(creds.ip, creds.user, creds.pass)
        }
    }

    data class Sysmsg(val key: String, val title: String)
    private val _sysmsgList = MutableStateFlow<List<Sysmsg>>(emptyList())
    val sysmsgList = _sysmsgList.asStateFlow()
    private val _selectedSysmsgKey = MutableStateFlow("default")
    val selectedSysmsgKey = _selectedSysmsgKey.asStateFlow()

    data class AiServiceItem(val id: String, val name: String, val model: String?, val label: String)
    private val _aiServices = MutableStateFlow<List<AiServiceItem>>(emptyList())
    val aiServices = _aiServices.asStateFlow()
    private val _selectedServiceId = MutableStateFlow<String?>(null)
    val selectedServiceId = _selectedServiceId.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending = _sending.asStateFlow()

    private val _chatTitle = MutableStateFlow<String?>(null)
    val chatTitle = _chatTitle.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()
    private var lastFailedMessage: String? = null

    data class ChatSummary(val id: String, val title: String)
    private val _history = MutableStateFlow<List<ChatSummary>>(emptyList())
    val history = _history.asStateFlow()

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState = _loginState.asStateFlow()

    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState = _discoveryState.asStateFlow()

    private val _rebootBanner = MutableStateFlow(false)
    val rebootBanner = _rebootBanner.asStateFlow()

    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired = _sessionExpired.asStateFlow()

    private fun handleApiError(e: Throwable) {
        val msg = e.message ?: ""
        if (msg.contains("Access Denied", ignoreCase = true) || 
            msg.contains("Session expired", ignoreCase = true) ||
            msg.contains("code=6", ignoreCase = true)) {
            _sessionExpired.value = true
        } else {
            _lastError.value = ErrorMessageMapper.getMessage(getApplication(), e)
        }
    }

    fun reconnect() {
        _sessionExpired.value = false
        val ip = lastIpAddress
        val user = lastUsername
        val pass = lastPassword
        if (!ip.isNullOrBlank() && !user.isNullOrBlank() && !pass.isNullOrBlank()) {
            login(ip, user, pass)
        } else {
            logout()
        }
    }

    // Text-to-Speech mode
    private val _voiceEnabled = MutableStateFlow(false)
    val voiceEnabled = _voiceEnabled.asStateFlow()

    fun setVoiceEnabled(enabled: Boolean) {
        _voiceEnabled.value = enabled
    }

    fun toggleVoiceEnabled() {
        _voiceEnabled.value = !_voiceEnabled.value
    }

    // Speech rate (0.5f..2.0f)
    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate = _speechRate.asStateFlow()
    private val _speechPitch = MutableStateFlow(1.0f)
    val speechPitch = _speechPitch.asStateFlow()

    init {
        val saved = prefs.getFloat(PREF_KEY_SPEECH_RATE, 1.0f)
        _speechRate.value = saved.coerceIn(0.5f, 2.0f)
        val savedPitch = prefs.getFloat(PREF_KEY_SPEECH_PITCH, 1.0f)
        _speechPitch.value = savedPitch.coerceIn(0.5f, 2.0f)
    }

    fun setSpeechRate(rate: Float) {
        val r = rate.coerceIn(0.5f, 2.0f)
        _speechRate.value = r
        prefs.edit { putFloat(PREF_KEY_SPEECH_RATE, r) }
    }

    fun setSpeechPitch(pitch: Float) {
        val p = pitch.coerceIn(0.5f, 2.0f)
        _speechPitch.value = p
        prefs.edit { putFloat(PREF_KEY_SPEECH_PITCH, p) }
    }

    // ---- Tools ----
    data class ToolItem(
        val name: String,
        val server: String,
        val enabled: Boolean,
        val properties: List<String> = emptyList(),
        val required: List<String> = emptyList()
    )
    private val _tools = MutableStateFlow<List<ToolItem>>(emptyList())
    val tools = _tools.asStateFlow()
    private val _toolsLoading = MutableStateFlow(false)
    val toolsLoading = _toolsLoading.asStateFlow()
    
    private val _functionCallingResult = MutableStateFlow<String?>(null)
    val functionCallingResult = _functionCallingResult.asStateFlow()

    private val _restartServiceTarget = MutableStateFlow<String?>(null)
    val restartServiceTarget = _restartServiceTarget.asStateFlow()

    private val _shutdownConfirmation = MutableStateFlow(false)
    val shutdownConfirmation = _shutdownConfirmation.asStateFlow()

    fun clearFunctionCallingResult() {
        _functionCallingResult.value = null
    }

    fun dismissRestartDialog() {
        _restartServiceTarget.value = null
    }

    fun dismissShutdownDialog() {
        _shutdownConfirmation.value = false
    }

    fun restartService() {
        val target = _restartServiceTarget.value ?: return
        val sid = sessionId ?: return
        viewModelScope.launch {
            runCatching { repository.operateService(sid, target, "restart") }
                .onSuccess {
                    Log.d(TAG, "Service $target restarted successfully")
                    // Optionally show a message to the user?
                    // For now, just dismiss the dialog
                    _restartServiceTarget.value = null
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to restart service $target", e)
                    handleApiError(e)
                    _restartServiceTarget.value = null
                }
        }
    }

    fun shutdownSystem() {
        val sid = sessionId ?: return
        viewModelScope.launch {
            runCatching { repository.confirmSystemAction(sid, "shutdown") }
                .onSuccess {
                    Log.d(TAG, "Shutdown command sent successfully")
                    _shutdownConfirmation.value = false
                }
                .onFailure { e ->
                    Log.e(TAG, "Failed to shutdown system", e)
                    handleApiError(e)
                    _shutdownConfirmation.value = false
                }
        }
    }

    private fun checkToolResultForActions(element: JsonElement?) {
        if (element == null) return
        // Log.d(TAG, "checkToolResultForActions: $element") // Uncomment for verbose logging
        
        fun checkObject(obj: kotlinx.serialization.json.JsonObject) {
            val target = obj["prepare_service_restart"]?.jsonPrimitive?.content
            if (!target.isNullOrBlank()) {
                Log.d(TAG, "Found prepare_service_restart: $target")
                _restartServiceTarget.value = target
            }
            val shutdown = obj["shutdown"]?.jsonPrimitive?.booleanOrNull ?: false
            if (shutdown) {
                Log.d(TAG, "Found shutdown: true")
                _shutdownConfirmation.value = true
            }
            // Also check inside tool_outputs if present
            obj["tool_outputs"]?.jsonArray?.forEach { checkToolResultForActions(it) }
            obj["tools"]?.jsonArray?.forEach { checkToolResultForActions(it) }
        }

        runCatching {
            when (element) {
                is kotlinx.serialization.json.JsonObject -> checkObject(element)
                is kotlinx.serialization.json.JsonArray -> element.forEach { checkToolResultForActions(it) }
                is kotlinx.serialization.json.JsonPrimitive -> {
                    if (element.isString) {
                        // Sometimes the tool output is a stringified JSON inside a string
                        val parsed = try {
                             Json.parseToJsonElement(element.content)
                        } catch (e: Exception) {
                             null
                        }
                        if (parsed != null) {
                            checkToolResultForActions(parsed)
                        }
                    }
                }
            }
        }
    }

    fun executeFunctionCalling(toolName: String, inputParams: Map<String, String>) {
        val sid = sessionId ?: return
        val tool = _tools.value.firstOrNull { it.name == toolName } ?: return
        
        viewModelScope.launch {
            try {
                val paramStrings = inputParams.mapNotNull { (key, value) ->
                    val def = tool.properties.find { it.startsWith("$key:") }
                    if (def != null) {
                        val parts = def.split(":")
                        if (parts.size >= 2) {
                            val type = parts[1]
                            "$key:$type:$value"
                        } else null
                    } else null
                }
                
                val paramString = paramStrings.joinToString(" ")
                
                val resultString = repository.executeFunctionCalling(sid, toolName, paramString)
                Log.d(TAG, "executeFunctionCalling result: $resultString")
                _functionCallingResult.value = resultString

                // Check for actions in the result
                runCatching {
                    val jsonElement = Json.parseToJsonElement(resultString)
                    checkToolResultForActions(jsonElement)
                }
            } catch (e: Exception) {
                Log.e(TAG, "executeFunctionCalling failed", e)
                val msg = ErrorMessageMapper.getMessage(getApplication(), e)
                _functionCallingResult.value = "Error: $msg"
            }
        }
    }

    fun refreshTools() {
        val sid = sessionId ?: return
        viewModelScope.launch {
            _toolsLoading.value = true
            runCatching { repository.getToolList(sid) }
                .onSuccess { list ->
                    _tools.value = list.map { ToolItem(it.name, it.server, it.enabled, it.properties, it.required) }
                }
                .onFailure { e ->
                    handleApiError(e)
                }
            _toolsLoading.value = false
        }
    }

    fun setToolEnabled(name: String, enabled: Boolean) {
        val sid = sessionId ?: return
        // Optimistic update
        _tools.value = _tools.value.map { if (it.name == name) it.copy(enabled = enabled) else it }
        viewModelScope.launch {
            runCatching { repository.setToolEnable(sid, name, enabled) }
                .onSuccess {
                    // Success - state already updated optimistically
                }
                .onFailure { e ->
                    // Roll back on failure
                    _tools.value = _tools.value.map { if (it.name == name) it.copy(enabled = !enabled) else it }
                    handleApiError(e)
                }
        }
    }

    fun discoverOasisDevices() {
        viewModelScope.launch {
            _discoveryState.value = DiscoveryState.Searching
            try {
                val devices = repository.discoverDevices()
                if (devices.isNotEmpty()) {
                    _discoveryState.value = DiscoveryState.Success(devices)
                } else {
                    _discoveryState.value = DiscoveryState.Error(getApplication<Application>().getString(R.string.no_devices_found))
                }
            } catch (e: Exception) {
                val msg = ErrorMessageMapper.getMessage(getApplication(), e)
                Log.e(TAG, "discoverOasisDevices failed", e)
                _discoveryState.value = DiscoveryState.Error(msg)
            }
        }
    }

    fun clearDiscoveryState() {
        _discoveryState.value = DiscoveryState.Idle
    }

    fun clearLoginState() {
        _loginState.value = LoginState.Idle
    }

    fun refreshHistory() {
        val currentSessionId = sessionId ?: return
        viewModelScope.launch {
            runCatching { repository.listChats(currentSessionId) }
                .onSuccess { items ->
                    _history.value = items.map { ChatSummary(it.id, it.title) }
                }
                .onFailure { e ->
                    handleApiError(e)
                }
        }
    }

    fun login(ipAddress: String, username: String, password: String) {
        if (ipAddress.contains("oasis-device-ip", ignoreCase = true)) {
            _loginState.value = LoginState.Error("Please enter a valid IP address.")
            return
        }
        lastIpAddress = ipAddress
        lastUsername = username
        lastPassword = password
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val raw = ipAddress.trim()
                val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
                val baseUrl = if (withScheme.endsWith("/")) withScheme else "$withScheme/"
                RetrofitClient.updateBaseUrl(baseUrl)
                
                val session = repository.login(LoginParams(username, password))
                sessionId = session
                val base = repository.getBaseInfo(session)
                val list = base.sysmsg.map { Sysmsg(it.key, it.title) }
                _sysmsgList.value = list
                val default = list.firstOrNull { it.key == "default" }?.key ?: list.firstOrNull()?.key ?: "default"
                _selectedSysmsgKey.value = default

                // Apply AI services list
                val services = base.services.map {
                    val lbl = if (it.model.isNullOrBlank()) it.name else "${it.name} (${it.model})"
                    AiServiceItem(id = it.identifier, name = it.name, model = it.model, label = lbl)
                }
                _aiServices.value = services
                if (_selectedServiceId.value == null && services.isNotEmpty()) {
                    _selectedServiceId.value = services.first().id
                }

                // Fetch chat history list
                runCatching { repository.listChats(session) }
                    .onSuccess { items ->
                        _history.value = items.map { ChatSummary(it.id, it.title) }
                    }
                // Save credentials on success
                saveCredentials(ipAddress, username, password)
                _loginState.value = LoginState.Success
            } catch (e: Exception) {
                val msg = ErrorMessageMapper.getMessage(getApplication(), e)
                Log.e(TAG, "login failed", e)
                // messages.add(Message(msg, isUser = false)) // Don't add login error to chat history
                _loginState.value = LoginState.Error(msg)
            }
        }
    }

    fun retryLogin() {
        val ip = lastIpAddress
        val user = lastUsername
        val pass = lastPassword
        if (!ip.isNullOrBlank() && !user.isNullOrBlank() && !pass.isNullOrBlank()) {
            login(ip, user, pass)
        } else {
            _loginState.value = LoginState.Idle
        }
    }

    fun selectAiService(identifier: String) {
        val currentSessionId = sessionId ?: return
        _selectedServiceId.value = identifier
        viewModelScope.launch {
            val item = _aiServices.value.firstOrNull { it.id == identifier }
            if (item == null) {
                _lastError.value = "AIサービスの選択に失敗しました: 不明なID"
                return@launch
            }
            runCatching { repository.selectAiService(currentSessionId, item.id, item.name, item.model) }
                .onFailure { e -> handleApiError(e) }
        }
    }

    fun onInputTextChanged(text: String) {
        inputText = text
    }

    fun selectSysmsg(key: String) {
        _selectedSysmsgKey.value = key
    }

    fun sendMessage() {
        val currentSessionId = sessionId ?: return
        if (inputText.isBlank()) return

        val userMessage = Message(inputText, true)
        messages.add(userMessage)
        val capturedInput = inputText
        inputText = ""

        viewModelScope.launch {
            _sending.value = true
            try {
                val result = repository.sendMessage(currentSessionId, chatId, capturedInput, selectedSysmsgKey.value)
                result.id?.let { if (it.isNotBlank()) chatId = it } // Save chat ID for next time (update if present)
                if (!result.title.isNullOrBlank()) { _chatTitle.value = result.title }
                run {
                    var label = OasisJsonParser.parseToolLabel(result.toolInfo)
                    var text = result.content
                    val namesFromText = OasisJsonParser.extractToolNamesFromContentIfJson(text)
                    if (label == null && namesFromText != null) {
                        label = namesFromText
                    }
                    if (namesFromText != null) {
                        text = ""
                    }
                    messages.add(Message(text, false, toolUsed = (label != null), toolLabel = label))
                    // Check for actions triggered by AI tool execution
                    checkToolResultForActions(result.toolInfo)
                }
                OasisJsonParser.formatUciProposal(result.uciParseTbl)?.let { messages.add(Message(it, false)) }
                if (result.reboot == true) { _rebootBanner.value = true }
                refreshHistory()
            } catch (e: Exception) {
                // On session expiration, re-login and resend once
                try {
                    val u = lastUsername
                    val p = lastPassword
                    if (!u.isNullOrBlank() && !p.isNullOrBlank()) {
                        val newSession = repository.login(LoginParams(u, p))
                        sessionId = newSession
                        val retry = repository.sendMessage(newSession, chatId, capturedInput, selectedSysmsgKey.value)
                        retry.id?.let { if (it.isNotBlank()) chatId = it }
                        if (!retry.title.isNullOrBlank()) { _chatTitle.value = retry.title }
                        run {
                            var label = OasisJsonParser.parseToolLabel(retry.toolInfo)
                            var text = retry.content
                            val namesFromText = OasisJsonParser.extractToolNamesFromContentIfJson(text)
                            if (label == null && namesFromText != null) {
                                label = namesFromText
                            }
                            if (namesFromText != null) {
                                text = ""
                            }
                            messages.add(Message(text, false, toolUsed = (label != null), toolLabel = label))
                            checkToolResultForActions(retry.toolInfo)
                        }
                        OasisJsonParser.formatUciProposal(retry.uciParseTbl)?.let { messages.add(Message(it, false)) }
                        if (retry.reboot == true) { _rebootBanner.value = true }
                        refreshHistory()
                        return@launch
                    }
                } catch (e2: Exception) {
                    // fallthrough to error message below
                }
                lastFailedMessage = capturedInput
                val mappedMsg = ErrorMessageMapper.getMessage(getApplication(), e)
                val msg = getApplication<Application>().getString(R.string.send_failed, mappedMsg)
                Log.e(TAG, "sendMessage failed", e)
                messages.add(Message(msg, false))
                handleApiError(e)
            } finally {
                _sending.value = false
            }
        }
    }

    fun consumeError() {
        _lastError.value = null
    }

    fun retryLastFailed() {
        val msg = lastFailedMessage ?: return
        val currentSessionId = sessionId ?: return
        viewModelScope.launch {
            _sending.value = true
            try {
                val result = repository.sendMessage(currentSessionId, chatId, msg, selectedSysmsgKey.value)
                result.id?.let { if (it.isNotBlank()) chatId = it }
                if (!result.title.isNullOrBlank()) { _chatTitle.value = result.title }
                run {
                    var label = OasisJsonParser.parseToolLabel(result.toolInfo)
                    var text = result.content
                    val namesFromText = OasisJsonParser.extractToolNamesFromContentIfJson(text)
                    if (label == null && namesFromText != null) {
                        label = namesFromText
                    }
                    if (namesFromText != null) {
                        text = ""
                    }
                    messages.add(Message(text, false, toolUsed = (label != null), toolLabel = label))
                    checkToolResultForActions(result.toolInfo)
                }
                OasisJsonParser.formatUciProposal(result.uciParseTbl)?.let { messages.add(Message(it, false)) }
                if (result.reboot == true) { _rebootBanner.value = true }
                lastFailedMessage = null
                refreshHistory()
            } catch (e: Exception) {
                Log.e(TAG, "retryLastFailed failed", e)
                handleApiError(e)
            } finally {
                _sending.value = false
            }
        }
    }

    fun loadChatById(id: String, title: String?) {
        val currentSessionId = sessionId ?: return
        viewModelScope.launch {
            try {
                val msgs = repository.loadChat(currentSessionId, id)
                messages.clear()
                msgs.forEach { m ->
                    val isUser = m.role == "user"
                    if (m.role == "user" || m.role == "assistant") {
                        messages.add(Message(m.content, isUser))
                    }
                }
                chatId = id
                if (!title.isNullOrBlank()) _chatTitle.value = title
            } catch (e: Exception) {
                Log.e(TAG, "loadChatById failed", e)
                handleApiError(e)
            }
        }
    }

    fun dismissRebootBanner() {
        _rebootBanner.value = false
    }

    fun startNewChat() {
        messages.clear()
        chatId = ""
        _chatTitle.value = null
        lastFailedMessage = null
    }

    fun logout() {
        sessionId = null
        lastUsername = null
        lastPassword = null

        messages.clear()
        chatId = ""
        _chatTitle.value = null
        lastFailedMessage = null

        _history.value = emptyList()
        _aiServices.value = emptyList()
        _selectedServiceId.value = null
        _sysmsgList.value = emptyList()
        _selectedSysmsgKey.value = "default"
        _sending.value = false
        _rebootBanner.value = false
        _lastError.value = null
        _voiceEnabled.value = false

        clearCredentials()

        // Navigate back to the login screen
        _loginState.value = LoginState.Idle
    }
}
