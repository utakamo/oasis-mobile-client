package com.example.oasis_mobile_client.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.oasis_mobile_client.R

@Composable
fun SysmsgActionSelector(
    items: List<Pair<String, String>>, // (title, key)
    selectedKey: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = items.firstOrNull { it.second == selectedKey }?.first ?: "default"
    Box {
        TextButton(onClick = { expanded = true }, content = { Text(selectedTitle) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (title, key) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        onSelect(key)
                    }
                )
            }
        }
    }
}

@Composable
fun SysmsgSelectDialog(
    items: List<Pair<String, String>>, // (title, key)
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(selectedKey) { mutableStateOf(selectedKey) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.system_messages), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(items) { (title, key) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = key }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (selected == key), onClick = { selected = key })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(title)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    TextButton(onClick = { onSelect(selected) }) { Text(stringResource(R.string.select)) }
                }
            }
        }
    }
}
