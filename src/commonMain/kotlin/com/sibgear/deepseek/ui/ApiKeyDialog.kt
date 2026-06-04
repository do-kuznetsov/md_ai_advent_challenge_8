package com.sibgear.deepseek.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ApiKeyDialog(
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
    onConfirmed: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("API key:")
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChanged,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("sk-...") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmed,
                enabled = apiKey.isNotBlank(),
            ) {
                Text("OK")
            }
        },
    )
}
