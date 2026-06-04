package com.sibgear.deepseek.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.domain.ChatMessage
import com.sibgear.deepseek.domain.ChatRole

private val UserMessageColor = Color(0xFFDDF7DF)
private val AssistantMessageColor = Color(0xFFEDE1FF)

@Composable
fun ChatArea(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(messages.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            messages.forEach { message ->
                ChatBubble(message = message)
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(8.dp),
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val horizontalArrangement = when (message.role) {
        ChatRole.User -> Arrangement.Start
        ChatRole.Assistant -> Arrangement.End
    }
    val backgroundColor = when (message.role) {
        ChatRole.User -> UserMessageColor
        ChatRole.Assistant -> AssistantMessageColor
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
    ) {
        SelectionContainer {
            Text(
                text = message.content,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .widthIn(min = 48.dp)
                    .background(backgroundColor, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                color = Color(0xFF202124),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
