package com.example.oasis_mobile_client.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.oasis_mobile_client.DiscoveryState
import com.example.oasis_mobile_client.LoginState
import com.example.oasis_mobile_client.OasisRepository
import com.example.oasis_mobile_client.R

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
        is DiscoveryState.Error -> { /* Do not show a dialog */ }
        else -> {}
    }

    if (loginState is LoginState.Error) {
        ErrorDialog(
            message = loginState.message,
            onDismiss = onDismissLoginError,
            onRetry = onRetryLogin
        )
    }

    val textFieldColors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Router,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.oasis_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))

            // IP Address & Discovery
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    placeholder = { Text(stringResource(R.string.ip_address)) },
                    leadingIcon = { Icon(Icons.Default.Lan, null) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = textFieldColors,
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onDiscoverClick,
                    enabled = discoveryState !is DiscoveryState.Searching,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    if (discoveryState is DiscoveryState.Searching) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.discover_devices))
                    }
                }
            }
            
            if (discoveryState is DiscoveryState.Error) {
                Text(
                    text = stringResource(R.string.discovery_hint_manual_ip),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Credentials
            TextField(
                value = userId,
                onValueChange = { userId = it },
                placeholder = { Text(stringResource(R.string.user_id)) },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = textFieldColors,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text(stringResource(R.string.password)) },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = textFieldColors,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.Start)) {
                Switch(checked = useHttps, onCheckedChange = { useHttps = it })
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = stringResource(R.string.https), style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(32.dp))

            ElevatedButton(
                onClick = {
                    val raw = ipAddress.trim()
                    val ipWithScheme = when {
                        raw.startsWith("http://") || raw.startsWith("https://") -> raw
                        useHttps -> "https://$raw"
                        else -> "http://$raw"
                    }
                    onLoginClick(ipWithScheme, userId, password)
                },
                enabled = isLoginEnabled && loginState !is LoginState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (loginState is LoginState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.login), fontWeight = FontWeight.Bold)
                }
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
                        Text(line, modifier = Modifier.clickable { onDeviceSelected(d.ip) }.padding(vertical = 8.dp))
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