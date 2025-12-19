package com.example.oasis_mobile_client

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.oasis_mobile_client.ui.chat.ChatScreen
import com.example.oasis_mobile_client.ui.login.LoginScreen
import com.example.oasis_mobile_client.ui.theme.OasismobileclientTheme

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
