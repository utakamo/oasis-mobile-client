package com.example.oasis_mobile_client

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

import androidx.compose.material3.TextButton

@Composable
fun LoginScreen(
    onLoginClick: (String, String, String) -> Unit,
    onDiscoverClick: () -> Unit,
    onRetryLogin: () -> Unit,
    loginState: LoginState,
    discoveryState: DiscoveryState,
    onDismissDialog: () -> Unit,
    onDismissLoginError: () -> Unit
) {
    var ipAddress by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useHttps by remember { mutableStateOf(false) }

    val isLoginEnabled = ipAddress.isNotBlank() && userId.isNotBlank() && password.isNotBlank()

    when (discoveryState) {
        is DiscoveryState.Success -> {
            DeviceDiscoveryDialog(
                devices = discoveryState.devices,
                onDeviceSelected = { selectedIp ->
                    ipAddress = selectedIp
                    onDismissDialog()
                },
                onDismiss = onDismissDialog
            )
        }
        is DiscoveryState.Error -> { /* Do not show a dialog (only a red hint under the button) */ }
        else -> {}
    }

    if (loginState is LoginState.Error) {
        ErrorDialog(
            message = loginState.message,
            onDismiss = onDismissLoginError,
            onRetry = onRetryLogin
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.align(Alignment.Start),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.https))
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = useHttps, onCheckedChange = { useHttps = it })
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text(stringResource(R.string.ip_address)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onDiscoverClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = discoveryState !is DiscoveryState.Searching
        ) {
            if (discoveryState is DiscoveryState.Searching) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text(stringResource(R.string.discover_devices))
            }
        }
        if (discoveryState is DiscoveryState.Error) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.discovery_hint_manual_ip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = userId,
            onValueChange = { userId = it },
            label = { Text(stringResource(R.string.user_id)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val raw = ipAddress
                val ipWithScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    raw
                } else {
                    (if (useHttps) "https://" else "http://") + raw
                }
                onLoginClick(ipWithScheme, userId, password)
            },
            enabled = isLoginEnabled && loginState !is LoginState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loginState is LoginState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text(stringResource(R.string.login))
            }
        }
    }
}

@Composable
fun DeviceDiscoveryDialog(
    devices: List<OasisRepository.DiscoveredDevice>,
    onDeviceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.discovered_devices), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn {
                    items(devices) { d ->
                        val line = "${d.name} - ${d.ip} [${d.port}]"
                        Text(line, modifier = Modifier.clickable { onDeviceSelected(d.ip) })
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit, onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.error)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reconfigure))
            }
        }
    )
}


