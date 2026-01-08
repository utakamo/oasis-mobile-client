package com.example.oasis_mobile_client.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.oasis_mobile_client.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionCallingDialog(
    tools: List<ChatViewModel.ToolItem>,
    onExecute: (String, Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
    result: String?
) {
    var selectedTool by remember { mutableStateOf<ChatViewModel.ToolItem?>(null) }
    var expanded by remember { mutableStateOf(false) }
    // Map of paramName -> value
    val inputValues = remember { mutableStateMapOf<String, String>() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = "Function Calling", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                // Tool Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedTool?.name ?: "Select a tool",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            tools.filter { it.enabled }.forEach { tool ->
                                DropdownMenuItem(
                                    text = { Text(tool.name) },
                                    onClick = {
                                        selectedTool = tool
                                        expanded = false
                                        inputValues.clear()
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dynamic Input Fields
                selectedTool?.let { tool ->
                    if (tool.properties.isNotEmpty()) {
                        Text("Parameters:", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        tool.properties.forEach { propDef ->
                            // Format: name:type:desc
                            val parts = propDef.split(":")
                            if (parts.size >= 3) {
                                val name = parts[0]
                                val type = parts[1]
                                val desc = parts.subList(2, parts.size).joinToString(":") // Join remaining parts
                                val isRequired = tool.required.contains(name)
                                
                                val labelText = "$name ($type)" + if (isRequired) " *" else ""
                                
                                OutlinedTextField(
                                    value = inputValues[name] ?: "",
                                    onValueChange = { inputValues[name] = it },
                                    label = { Text(labelText) },
                                    placeholder = { Text(desc) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    singleLine = true
                                )
                            }
                        }
                    } else {
                        Text("No parameters required.", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Result Area
                if (result != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Result:\n$result",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            selectedTool?.let {
                                onExecute(it.name, inputValues.toMap())
                            }
                        },
                        enabled = selectedTool != null
                    ) {
                        Text("Execute")
                    }
                }
            }
        }
    }
}
