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
    data class ToolItem(val name: String, val server: String, val enabled: Boolean)
    private val _tools = MutableStateFlow<List<ToolItem>>(emptyList())
    val tools = _tools.asStateFlow()
    private val _toolsLoading = MutableStateFlow(false)
    val toolsLoading = _toolsLoading.asStateFlow()

    fun refreshTools() {
        val sid = sessionId ?: return
        viewModelScope.launch {
            _toolsLoading.value = true
            runCatching { repository.getToolList(sid) }
                .onSuccess { list ->
                    _tools.value = list.map { ToolItem(it.name, it.server, it.enabled) }
                }
                .onFailure { e ->
                    _lastError.value = "ツール一覧の取得に失敗しました: ${e.message}"
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
                    // Re-fetch latest state on success
                    refreshTools()
                }
                .onFailure { e ->
                    // Roll back on failure
                    _tools.value = _tools.value.map { if (it.name == name) it.copy(enabled = !enabled) else it }
                    _lastError.value = "ツールの更新に失敗しました: ${e.message}"
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
                val msg = getApplication<Application>().getString(R.string.discovery_failed, e.message ?: "")
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
                    _lastError.value = "履歴の取得に失敗しました: ${e.message}"
                }
        }
    }

    fun login(ipAddress: String, username: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val raw = ipAddress.trim()
                val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
                val baseUrl = if (withScheme.endsWith("/")) withScheme else "$withScheme/"
                RetrofitClient.updateBaseUrl(baseUrl)
                lastUsername = username
                lastPassword = password
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
                val msg = getApplication<Application>().getString(R.string.connection_failed, e.message ?: "")
                Log.e(TAG, "login failed", e)
                messages.add(Message(msg, isUser = false))
                _loginState.value = LoginState.Error(msg)
            }
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
                .onFailure { e -> _lastError.value = "AIサービスの切替に失敗しました: ${e.message}" }
        }
    }

    fun onInputTextChanged(text: String) {
        inputText = text
    }

    fun selectSysmsg(key: String) {
        _selectedSysmsgKey.value = key
    }

    private fun formatUciProposal(el: JsonElement?): String? {
        if (el == null) return null
        val obj = el.jsonObject
        val notify = obj["uci_notify"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!notify) return null
        val list = obj["uci_list"]?.jsonObject ?: return "UCI提案があります。"
        fun linesOf(name: String): List<String> {
            val arr = list[name]?.jsonArray ?: return emptyList()
            return arr.mapNotNull { item ->
                val o = item.jsonObject
                o["param"]?.jsonPrimitive?.content
            }
        }
        val parts = mutableListOf<String>()
        val sections = listOf("set","add","delete","add_list","del_list","reorder")
        for (s in sections) {
            val lines = linesOf(s)
            if (lines.isNotEmpty()) {
                parts.add("$s:\n" + lines.joinToString("\n") { "  $it" })
            }
        }
        if (parts.isEmpty()) return "UCI提案があります。"
        return "UCI提案:\n" + parts.joinToString("\n")
    }

    private fun parseToolLabel(el: JsonElement?): String? {
        if (el == null) return null
        return runCatching {
            val obj = when {
                (el is kotlinx.serialization.json.JsonPrimitive) && el.isString ->
                    runCatching { Json.parseToJsonElement(el.content).jsonObject }.getOrNull()
                else -> el.jsonObject
            } ?: return@runCatching null

            if (obj.isEmpty()) return@runCatching null

            obj["name"]?.jsonPrimitive?.content
                ?: obj["tool"]?.jsonPrimitive?.content
                ?: obj["tools"]?.jsonArray?.mapNotNull {
                    runCatching { it.jsonObject["name"]?.jsonPrimitive?.content ?: it.jsonPrimitive.content }.getOrNull()
                }?.filter { it.isNotBlank() }?.joinToString(", ")?.ifBlank { null }
                ?: obj["tool_outputs"]?.jsonArray?.mapNotNull {
                    it.jsonObject["name"]?.jsonPrimitive?.content
                }?.filter { it.isNotBlank() }?.joinToString(", ")?.ifBlank { null }
        }.getOrNull()
    }

    private fun extractToolNamesFromContentIfJson(text: String): String? {
        return runCatching {
            val trimmed = text.trimStart()
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
            val root = Json.parseToJsonElement(text)
            val o = root.jsonObject
            o["tool_outputs"]?.jsonArray?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.content
            }?.filter { it.isNotBlank() }?.joinToString(", ")?.takeIf { it.isNotBlank() }
                ?: o["tools"]?.jsonArray?.mapNotNull {
                    runCatching { it.jsonObject["name"]?.jsonPrimitive?.content ?: it.jsonPrimitive.content }.getOrNull()
                }?.filter { it.isNotBlank() }?.joinToString(", ")?.takeIf { it.isNotBlank() }
        }.getOrNull()
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
                    var label = parseToolLabel(result.toolInfo)
                    var text = result.content
                    val namesFromText = extractToolNamesFromContentIfJson(text)
                    if (label == null && namesFromText != null) {
                        label = namesFromText
                    }
                    if (namesFromText != null) {
                        text = ""
                    }
                    messages.add(Message(text, false, toolUsed = (label != null), toolLabel = label))
                }
                formatUciProposal(result.uciParseTbl)?.let { messages.add(Message(it, false)) }
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
                            var label = parseToolLabel(retry.toolInfo)
                            var text = retry.content
                            val namesFromText = extractToolNamesFromContentIfJson(text)
                            if (label == null && namesFromText != null) {
                                label = namesFromText
                            }
                            if (namesFromText != null) {
                                text = ""
                            }
                            messages.add(Message(text, false, toolUsed = (label != null), toolLabel = label))
                        }
                        formatUciProposal(retry.uciParseTbl)?.let { messages.add(Message(it, false)) }
                        if (retry.reboot == true) { _rebootBanner.value = true }
                        refreshHistory()
                        return@launch
                    }
                } catch (e2: Exception) {
                    // fallthrough to error message below
                }
                lastFailedMessage = capturedInput
                val msg = getApplication<Application>().getString(R.string.send_failed, e.message ?: "")
                Log.e(TAG, "sendMessage failed", e)
                messages.add(Message(msg, false))
                _lastError.value = msg
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
                    var label = parseToolLabel(result.toolInfo)
                    var text = result.content
                    val namesFromText = extractToolNamesFromContentIfJson(text)
                    if (label == null && namesFromText != null) {
                        label = namesFromText
                    }
                    if (namesFromText != null) {
                        text = ""
                    }
                    messages.add(Message(text, false, toolUsed = (label != null), toolLabel = label))
                }
                formatUciProposal(result.uciParseTbl)?.let { messages.add(Message(it, false)) }
                if (result.reboot == true) { _rebootBanner.value = true }
                lastFailedMessage = null
                refreshHistory()
            } catch (e: Exception) {
                Log.e(TAG, "retryLastFailed failed", e)
                _lastError.value = getApplication<Application>().getString(R.string.retry_failed, e.message ?: "")
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
                _lastError.value = getApplication<Application>().getString(R.string.history_load_failed, e.message ?: "")
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
