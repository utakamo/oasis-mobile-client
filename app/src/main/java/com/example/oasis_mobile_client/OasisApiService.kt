package com.example.oasis_mobile_client

import android.annotation.SuppressLint
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
// region API Service
interface OasisApiService {
    @POST("/ubus")
    suspend fun call(@Body request: JsonRpcRequest): JsonRpcResponseContainer
}
// endregion

// region Data Models
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int = 1,
    val method: String,
    val params: JsonArray
) {
    companion object {
        const val METHOD_CALL = "call"
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class JsonRpcResponseContainer(
    val jsonrpc: String = "2.0",
    val id: Int?,
    val result: JsonArray? = null,
    val error: JsonRpcError? = null
)

// --- Parameter data classes ---
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class LoginParams(
    val username: String,
    val password: String
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SendParams(
    val id: String,
    @SerialName("sysmsg_key") val sysmsgKey: String,
    val message: String
)

// --- Result data classes ---
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SendResult(
    val id: String? = null,
    val title: String? = null,
    val content: String,
    @SerialName("uci_parse_tbl") val uciParseTbl: JsonElement? = null,
    val reboot: Boolean? = null,
    @SerialName("tool_info") val toolInfo: JsonElement? = null
)
// endregion

// region Retrofit Client
object RetrofitClient {
    private var BASE_URL = "http://oasis-device-ip/"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
    }

    private val retryInterceptor = Interceptor { chain ->
        var attempt = 0
        var lastException: IOException? = null
        while (attempt < 2) {
            try {
                return@Interceptor chain.proceed(chain.request())
            } catch (e: IOException) {
                lastException = e
                attempt++
                if (attempt < 2) { // Only sleep if we are going to retry
                    try { Thread.sleep(300) } catch (_: InterruptedException) { }
                }
            }
        }
        throw lastException ?: IOException("network error")
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(retryInterceptor)
        .addInterceptor(logging)
        .build()

    @Volatile
    private var retrofitInstance: OasisApiService? = null

    val instance: OasisApiService
        get() = retrofitInstance ?: synchronized(this) {
            retrofitInstance ?: buildRetrofit().also { retrofitInstance = it }
        }

    fun updateBaseUrl(newUrl: String) {
        BASE_URL = newUrl
        retrofitInstance = buildRetrofit()
    }

    private fun buildRetrofit(): OasisApiService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OasisApiService::class.java)
    }
}
// endregion
