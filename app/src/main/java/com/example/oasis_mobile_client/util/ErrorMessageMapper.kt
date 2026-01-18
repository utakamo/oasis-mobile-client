package com.example.oasis_mobile_client.util

import android.content.Context
import com.example.oasis_mobile_client.R
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorMessageMapper {
    fun getMessage(context: Context, t: Throwable?): String {
        val msg = t?.message ?: "Unknown"
        return when {
            t is ConnectException || t is UnknownHostException || msg.contains("Failed to connect", true) || msg.contains("Unable to resolve host", true) -> {
                context.getString(R.string.error_network_connection)
            }
            t is SocketTimeoutException || msg.contains("timeout", true) -> {
                context.getString(R.string.error_timeout)
            }
            msg.contains("Access Denied", true) || msg.contains("code=6", true) -> {
                context.getString(R.string.error_access_denied)
            }
            msg.contains("Session expired", true) -> {
                context.getString(R.string.error_auth)
            }
            else -> {
                context.getString(R.string.error_unknown, msg)
            }
        }
    }
}
