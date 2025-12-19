package com.example.oasis_mobile_client.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.oasis_mobile_client.R
import java.util.Locale

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
