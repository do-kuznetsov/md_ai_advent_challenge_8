package com.sibgear.deepseek.chat.ui.internal.view

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SendButtonLoaderColor = Color(0xFF3B82F6)

@Composable
internal fun SendButtonArea(
    isLoading: Boolean,
    isSendEnabled: Boolean,
    onSendClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onSendClicked,
        enabled = isSendEnabled,
        modifier = modifier.height(56.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.width(24.dp).height(24.dp),
                color = SendButtonLoaderColor,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = "отправить",
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
