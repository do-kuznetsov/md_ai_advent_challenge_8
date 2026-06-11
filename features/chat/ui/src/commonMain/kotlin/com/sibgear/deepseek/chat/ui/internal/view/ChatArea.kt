package com.sibgear.deepseek.chat.ui.internal.view

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.ui.generated.resources.Res
import com.sibgear.deepseek.chat.ui.generated.resources.ic_paperclip
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageFooter
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.ui.internal.mapper.formatMegabytes
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToLong

private val UserMessageColor = Color(0xFFDDF7DF)
private val AssistantMessageColor = Color(0xFFEDE1FF)

@Composable
internal fun ChatArea(
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
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .widthIn(min = 48.dp)
                    .background(backgroundColor, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                message.sourceLabel?.let { sourceLabel ->
                    Text(
                        text = sourceLabel,
                        color = Color(0xFF5F6368),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Text(
                    text = message.content,
                    color = Color(0xFF202124),
                    style = MaterialTheme.typography.bodyMedium,
                )

                message.attachment?.let { attachment ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_paperclip),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFF5F6368),
                        )
                        Text(
                            text = "${attachment.fileName} · ${attachment.sizeBytes.formatMegabytes()}",
                            color = Color(0xFF5F6368),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                message.footer?.let { footer ->
                    Text(
                        text = footer.displayText(),
                        color = Color(0xFF5F6368),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun ChatMessageFooter.displayText(): String =
    buildList {
        add("time: ${responseTimeMs.formatDuration()}")

        totalTokens?.let { totalTokens ->
            val tokenText = if (promptTokens != null && completionTokens != null) {
                "tokens: $totalTokens (in $promptTokens / out $completionTokens)"
            } else {
                "tokens: $totalTokens"
            }
            add(tokenText)
        }

        add("cost: ${cost?.formatUsdCost() ?: "unknown"}")

        if (retryCount > 0) {
            add("retry: $retryCount")
        }
    }.joinToString(separator = " · ")

private fun Long.formatDuration(): String {
    if (this < 1000L) {
        return "${this}ms"
    }

    val hundredths = (this + 5L) / 10L
    val seconds = hundredths / 100L
    val fraction = (hundredths % 100L).toString().padStart(2, '0')
    return "$seconds.${fraction}s"
}

private fun Double.formatUsdCost(): String {
    if (this == 0.0) {
        return "free"
    }

    val scaledCost = (this * CostScale).roundToLong()
    val whole = scaledCost / CostScale
    val fraction = (scaledCost % CostScale).toString().padStart(CostFractionDigits, '0')
    return "\$$whole.$fraction"
}

private const val CostScale = 1_000_000L
private const val CostFractionDigits = 6
