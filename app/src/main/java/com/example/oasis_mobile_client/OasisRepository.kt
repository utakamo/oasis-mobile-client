package com.example.oasis_mobile_client

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class OasisRepository(private val context: Context) {

    private val apiService get() = RetrofitClient.instance
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val INITIAL_SESSION_ID = "00000000000000000000000000000000"
        private const val UBUS_OBJECT_SESSION = "session"
        private const val UBUS_METHOD_LOGIN = "login"
        private const val UBUS_OBJECT_OASIS = "oasis"
        private const val UBUS_METHOD_BASE_INFO = "base_info"
        private const val UBUS_METHOD_SELECT_AI_SERVICE = "select_ai_service"
        private const val UBUS_OBJECT_OASIS_CHAT = "oasis.chat"
        private const val UBUS_METHOD_SEND = "send"
        private const val UBUS_METHOD_LIST = "list"
        private const val UBUS_METHOD_LOAD = "load"
        private const val UBUS_OBJECT_OASIS_TOOL_LIST = "oasis.tool.edge"
        private const val UBUS_OBJECT_OASIS_TOOL_MANAGER = "oasis.tool.manager"
        private const val UBUS_METHOD_TOOL_LIST = "tool_list"
        private const val UBUS_METHOD_FUNCTION_CALLING = "function_calling"
        private const val UBUS_METHOD_SET_TOOL_ENABLED = "set_tool_enabled"
        private const val UBUS_METHOD_SET_TOOL_DISABLED = "set_tool_disabled"
        private const val UBUS_OBJECT_OASIS_SERVICE = "oasis.service"
        private const val UBUS_METHOD_OPERATE = "operate"
        private val NSD_SERVICE_TYPES = listOf(
            "_oasis._tcp.",
            "_oasis-jsonrpc._tcp.",
            "_ubus._tcp.",
            "_http._tcp."
        )
        private const val DISCOVERY_TIMEOUT_MS = 5000L
    }

    @SuppressLint("UnsafeOptInUsageError")
    @kotlinx.serialization.Serializable
    data class DiscoveredDevice(val name: String, val ip: String, val port: Int)

    @Suppress("DEPRECATION")
    suspend fun discoverDevices(): List<DiscoveredDevice> = suspendCancellableCoroutine { continuation ->
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifi.createMulticastLock("oasis-mdns-lock").apply {
            setReferenceCounted(true)
            runCatching { acquire() }
        }
        val discoveredDevices = ConcurrentHashMap<String, DiscoveredDevice>()
        val stopped = AtomicBoolean(false)
        var discoveryListener: NsdManager.DiscoveryListener? = null

        fun stopDiscovery() {
            runCatching { discoveryListener?.let { nsdManager.stopServiceDiscovery(it) } }
        }

        fun finishAndResume() {
            if (stopped.compareAndSet(false, true)) {
                stopDiscovery()
                runCatching { if (multicastLock.isHeld) multicastLock.release() }

                if (!continuation.isActive) return

                // If possible, complement device names using JmDNS
                val initial = discoveredDevices.values.toList()
                val enhanced = runCatching {
                    val type = "_oasis._tcp.local."
                    var jmdns: JmDNS? = null
                    try {
                        jmdns = JmDNS.create()
                        val infos: Array<ServiceInfo> = runCatching { jmdns.list(type, 1500) }.getOrNull() ?: emptyArray()
                        initial.map { d ->
                            val match = infos.firstOrNull { info ->
                                val addrs = (info.inet4Addresses?.mapNotNull { it.hostAddress } ?: emptyList()) +
                                        (info.inet6Addresses?.mapNotNull { it.hostAddress } ?: emptyList())
                                addrs.contains(d.ip)
                            }
                            if (match != null && !match.server.isNullOrBlank()) d.copy(name = match.server.trimEnd('.')) else d
                        }
                    } finally {
                        runCatching { jmdns?.close() }
                    }
                }.getOrElse { initial }
                continuation.resume(enhanced)
            }
        }

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host
                val displayName = host?.hostName?.takeIf { it.isNotBlank() } ?: serviceInfo.serviceName
                val port = serviceInfo.port
                val ipCandidate = host?.hostAddress
                val ipv4 = try {
                    val hn = host?.hostName
                    if (!hn.isNullOrBlank()) {
                        InetAddress.getAllByName(hn).firstOrNull { it.hostAddress?.contains('.') == true }?.hostAddress
                    } else null
                } catch (_: Throwable) { null }
                val ipAddress = ipv4 ?: ipCandidate
                if (!displayName.isNullOrBlank() && !ipAddress.isNullOrBlank() && port > 0) {
                    discoveredDevices[displayName] = DiscoveredDevice(displayName, ipAddress, port)
                }
            }
        }

        var currentTypeIndex = 0

        fun startNextTypeOrFinish() {
            if (!continuation.isActive) return
            if (currentTypeIndex >= NSD_SERVICE_TYPES.size) {
                finishAndResume()
                return
            }
            val type = NSD_SERVICE_TYPES[currentTypeIndex++]
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    // Attempt to resolve all services without filtering
                    nsdManager.resolveService(service, resolveListener)
                }
                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    stopDiscovery()
                    startNextTypeOrFinish()
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    stopDiscovery()
                    startNextTypeOrFinish()
                }
            }
            nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

            // Time slice per service type
            CoroutineScope(continuation.context).launch {
                val slice = (DISCOVERY_TIMEOUT_MS / NSD_SERVICE_TYPES.size)
                delay(slice)
                stopDiscovery()
                if (currentTypeIndex >= NSD_SERVICE_TYPES.size) {
                    finishAndResume()
                } else {
                    startNextTypeOrFinish()
                }
            }
        }

        // Overall timeout as a safeguard
        val overallTimeout = CoroutineScope(continuation.context).launch {
            delay(DISCOVERY_TIMEOUT_MS)
            if (continuation.isActive) finishAndResume()
        }

        continuation.invokeOnCancellation {
            stopDiscovery()
            runCatching { if (multicastLock.isHeld) multicastLock.release() }
            overallTimeout.cancel()
        }

        startNextTypeOrFinish()
    }

    @SuppressLint("UnsafeOptInUsageError")
    @kotlinx.serialization.Serializable
    data class ToolInfo(
        val name: String,
        val server: String,
        val enabled: Boolean,
        val properties: List<String> = emptyList(),
        val required: List<String> = emptyList()
    )

    suspend fun getToolList(sessionId: String): List<ToolInfo> {
        val requestParams = buildJsonArray {
            add(sessionId)
            add(UBUS_OBJECT_OASIS_TOOL_LIST)
            add(UBUS_METHOD_TOOL_LIST)
            add(buildJsonObject { })
        }
        val request = JsonRpcRequest(method = JsonRpcRequest.METHOD_CALL, params = requestParams)
        return makeRpcCall(request) { result ->
            val obj = result[1].jsonObject
            val list = mutableListOf<ToolInfo>()
            obj["tools"]?.jsonObject?.values?.forEach { e ->
                val o = e.jsonObject
                val name = o["name"]?.jsonPrimitive?.content ?: return@forEach
                val server = o["server"]?.jsonPrimitive?.content ?: ""
                val enableStr = o["enable"]?.jsonPrimitive?.content ?: "0"
                val enabled = (enableStr == "1" || enableStr.equals("true", ignoreCase = true))
                
                val properties = o["property"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val required = o["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

                list.add(ToolInfo(name, server, enabled, properties, required))
            }
            list
        }
    }

    suspend fun executeFunctionCalling(sessionId: String, toolName: String, param: String): String {
        val requestParams = buildJsonArray {
            add(sessionId)
            add(UBUS_OBJECT_OASIS_TOOL_LIST) // Same object as tool_list
            add(UBUS_METHOD_FUNCTION_CALLING)
            add(buildJsonObject {
                put("tool", toolName)
                put("param", param)
            })
        }
        val request = JsonRpcRequest(method = JsonRpcRequest.METHOD_CALL, params = requestParams)
        // Returns the raw JSON result as a string
        return makeRpcCall(request) { result ->
            result[1].toString()
        }
    }

    suspend fun operateService(sessionId: String, serviceName: String, cmd: String): String {
        val requestParams = buildJsonArray {
            add(sessionId)
            add(UBUS_OBJECT_OASIS_SERVICE)
            add(UBUS_METHOD_OPERATE)
            add(buildJsonObject {
                put("cmd", cmd)
                put("service", serviceName)
            })
        }
        val request = JsonRpcRequest(method = JsonRpcRequest.METHOD_CALL, params = requestParams)
        // Returns the raw JSON result as a string (or empty if void)
        return makeRpcCall(request) { result ->
            result.getOrNull(1)?.toString() ?: ""
        }
    }

    suspend fun setToolEnable(sessionId: String, name: String, enabled: Boolean) {
        val method = if (enabled) UBUS_METHOD_SET_TOOL_ENABLED else UBUS_METHOD_SET_TOOL_DISABLED
        val requestParams = buildJsonArray {
            add(sessionId)
            add(UBUS_OBJECT_OASIS_TOOL_MANAGER)
            add(method)
            add(buildJsonObject {
                put("tool", name)
            })
        }
        val request = JsonRpcRequest(method = JsonRpcRequest.METHOD_CALL, params = requestParams)
        makeRpcCall(request) { _ -> Unit }
    }

    private suspend fun <T> makeRpcCall(request: JsonRpcRequest, parse: (JsonArray) -> T): T {
        val response = apiService.call(request)
        if (response.error != null) {
            throw Exception("RPC Error ${response.error.code}: ${response.error.message}")
        }
        val result = response.result ?: throw Exception("Missing result in RPC response")
        // ubus returns [return_code, payload]. 0 is success.
        val returnCode = result[0].jsonPrimitive.content.toIntOrNull() ?: -1
        if (returnCode != 0) {
            throw Exception("ubus call error: code=$returnCode")
        }
        return parse(result)
    }

    suspend fun login(params: LoginParams): String {
        val requestParams = buildJsonArray {
            add(INITIAL_SESSION_ID)
            add(UBUS_OBJECT_SESSION)
            add(UBUS_METHOD_LOGIN)
            add(buildJsonObject {
                put("username", params.username)
                put("password", params.password)
            })
        }
        val request = JsonRpcRequest(method = JsonRpcRequest.METHOD_CALL, params = requestParams)
        return makeRpcCall(request) { result ->
            val sessionObject = result[1].jsonObject
            sessionObject["ubus_rpc_session"]!!.jsonPrimitive.content
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @kotlinx.serialization.Serializable
    data class BaseInfoSysmsg(val key: String, val title: String)
    @SuppressLint("UnsafeOptInUsageError")
    @kotlinx.serialization.Serializable
    data class AiService(val identifier: String, val name: String, val model: String? = null)
    @SuppressLint("UnsafeOptInUsageError")
    @kotlinx.serialization.Serializable
    data class BaseInfo(val sysmsg: List<BaseInfoSysmsg>, val services: List<AiService>)

    suspend fun getBaseInfo(sessionId: String): BaseInfo {
        val requestParams = buildJsonArray {
            add(sessionId)
            add(UBUS_OBJECT_OASIS)
            add(UBUS_METHOD_BASE_INFO)
            add(buildJsonObject { })
        }
        val request = JsonRpcRequest(method = JsonRpcRequest.METHOD_CALL, params = requestParams)
        return makeRpcCall(request) { result ->
            val obj = result[1].jsonObject
            val sysmsgs = mutableListOf<BaseInfoSysmsg>()
            obj["sysmsg"]?.let { el ->
                el.jsonArray.forEach { e ->
                    val o = e.jsonObject
                    val k = o["key"]?.jsonPrimitive?.content ?: return@forEach
                    val t = o["title"]?.jsonPrimitive?.content ?: k
                    sysmsgs.add(BaseInfoSysmsg(k, t))
                }
            }

            val services = mutableListOf<AiService>()
            obj["service"]?.let { el ->
                el.jsonArray.forEach { e ->
                    val o = e.jsonObject
                    val id = o["identifier"]?.jsonPrimitive?.content ?: return@forEach
                    val name = o["name"]?.jsonPrimitive?.content ?: id
                    val model = o["model"]?.jsonPrimitive?.content
                    services.add(AiService(id, name, model))
                }
            }

            BaseInfo(sysmsg = sysmsgs, services = services)
        }
    }

    suspend fun selectAiService(sessionId: String, id: String, name: String, model: String?) {
        val requestParams = buildJsonArray {
            add(sessionId)
            add(UBUS_OBJECT_OASIS)
            add(UBUS_METHOD_SELECT_AI_SERVICE)
            add(buildJsonObject {
                put("id", id)
                put("name", name)
                put("model", model ?: "")
            })
        }
        val request = JsonRpcRequest(method = JsonRpcRequest.METHOD_CALL, params = requestParams)
        makeRpcCall(request) { _ -> Unit }
    }

    suspend fun sendMessage(sessionId: String, chatId: String, message: String, sysmsgKey: String): SendResult {
        val sendParams = SendParams(id = chatId, sysmsgKey = sysmsgKey, message = message)
        val requestParams = buildJsonArray {
            add(sessionId)
            add(UBUS_OBJECT_OASIS_CHAT)
            add(UBUS_METHOD_SEND)
            add(buildJsonObject {
                put("id", sendParams.id)
                put("sysmsg_key", sendParams.sysmsgKey)
                put("message", sendParams.message)
            })
        }
        val request = JsonRpcRequest(method = JsonRpcRequest.METHOD_CALL, params = requestParams)
        return makeRpcCall(request) { result ->
            json.decodeFromJsonElement<SendResult>(result[1])
        }
    }

    // ---- chat history ----
    @SuppressLint("UnsafeOptInUsageError")
    @kotlinx.serialization.Serializable
    data class ChatSummary(val id: String, val title: String)

    suspend fun listChats(sessionId: String): List<ChatSummary> {
        val requestParams = buildJsonArray {
            add(sessionId)
            add(UBUS_OBJECT_OASIS_CHAT)
            add(UBUS_METHOD_LIST)
            add(buildJsonObject { })
        }
        val request = JsonRpcRequest(method = JsonRpcRequest.METHOD_CALL, params = requestParams)
        return makeRpcCall(request) { result ->
            val obj = when (val payload = result[1]) {
                is kotlinx.serialization.json.JsonPrimitive -> {
                    val raw = payload.content
                    json.parseToJsonElement(raw).jsonObject
                }
                is JsonObject -> payload
                else -> throw Exception("Unexpected payload type for chat list")
            }
            val list = mutableListOf<ChatSummary>()
            obj["item"]?.let { el ->
                el.jsonArray.forEach { e ->
                    val o = e.jsonObject
                    val id = o["id"]?.jsonPrimitive?.content
                    val title = o["title"]?.jsonPrimitive?.content ?: ""
                    if (!id.isNullOrBlank()) list.add(ChatSummary(id, title))
                }
            }
            list
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @kotlinx.serialization.Serializable
    data class ChatMessage(val role: String, val content: String)

    suspend fun loadChat(sessionId: String, chatId: String): List<ChatMessage> {
        val requestParams = buildJsonArray {
            add(sessionId)
            add(UBUS_OBJECT_OASIS_CHAT)
            add(UBUS_METHOD_LOAD)
            add(buildJsonObject { put("id", chatId) })
        }
        val request = JsonRpcRequest(method = JsonRpcRequest.METHOD_CALL, params = requestParams)
        return makeRpcCall(request) { result ->
            val root = when (val payload = result[1]) {
                is kotlinx.serialization.json.JsonPrimitive -> {
                    val raw = payload.content
                    json.parseToJsonElement(raw).jsonObject
                }
                is JsonObject -> payload
                else -> throw Exception("Unexpected payload type for chat load")
            }
            val out = mutableListOf<ChatMessage>()
            root["messages"]?.jsonArray?.forEach { m ->
                val o = m.jsonObject
                val role = o["role"]?.jsonPrimitive?.content ?: ""
                val content = o["content"]?.jsonPrimitive?.content ?: ""
                if (role.isNotBlank()) out.add(ChatMessage(role, content))
            }
            out
        }
    }
}
