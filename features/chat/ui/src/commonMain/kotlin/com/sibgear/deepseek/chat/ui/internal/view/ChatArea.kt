package com.sibgear.deepseek.chat.ui.internal.view

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.ui.generated.resources.Res
import com.sibgear.deepseek.chat.ui.generated.resources.ic_paperclip
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatMessageFooter
import com.sibgear.deepseek.chat.domain.model.ChatMessageMemoryMetadata
import com.sibgear.deepseek.chat.domain.model.ChatMemoryChangeAction
import com.sibgear.deepseek.chat.domain.model.ChatMemoryLayer
import com.sibgear.deepseek.chat.domain.model.ChatMessageKind
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.ui.internal.mapper.formatMegabytes
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToLong

private val UserMessageColor = Color(0xFFDDF7DF)
private val AssistantMessageColor = Color(0xFFEDE1FF)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatArea(
    messages: List<ChatMessage>,
    pinnedContextMessageIndex: Int?,
    expandedCompressionMessageIndexes: Set<Int>,
    onCompressionSummaryToggled: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            messages.forEachIndexed { index, message ->
                if (message.kind == ChatMessageKind.CompressionSummary) {
                    item(key = "compression-$index") {
                        CompressionSummaryBlock(
                            message = message,
                            isExpanded = index in expandedCompressionMessageIndexes,
                            onToggle = { onCompressionSummaryToggled(index) },
                        )
                    }
                } else if (index == pinnedContextMessageIndex) {
                    stickyHeader(key = "pinned-$index") {
                        SlidingWindowBoundaryHeader()
                    }
                    item(key = "message-$index") {
                        ChatBubble(message = message)
                    }
                } else {
                    item(key = "message-$index") {
                        ChatBubble(message = message)
                    }
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(8.dp),
        )
    }
}

@Composable
private fun SlidingWindowBoundaryHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "контекст начинается здесь",
            color = Color(0xFF5F6368),
            style = MaterialTheme.typography.labelSmall,
        )
        HorizontalDivider(color = Color(0xFF9AA0A6))
    }
}

@Composable
private fun CompressionSummaryBlock(
    message: ChatMessage,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "сжатие выполнено",
            color = Color(0xFF5F6368),
            style = MaterialTheme.typography.labelSmall,
        )
        HorizontalDivider(color = Color(0xFF9AA0A6))
        ChatBubble(
            message = message,
            isCompressionSummary = true,
            isCompressionExpanded = isExpanded,
            onCompressionToggle = onToggle,
        )
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isCompressionSummary: Boolean = false,
    isCompressionExpanded: Boolean = true,
    onCompressionToggle: (() -> Unit)? = null,
) {
    val horizontalArrangement = when (message.role) {
        ChatRole.User -> Arrangement.Start
        ChatRole.Assistant -> Arrangement.End
    }
    val backgroundColor = when (message.role) {
        ChatRole.User -> UserMessageColor
        ChatRole.Assistant -> AssistantMessageColor
    }
    var isMemoryExpanded by remember(message.memory) { mutableStateOf(false) }

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
                    modifier = if (isCompressionSummary && onCompressionToggle != null) {
                        Modifier.clickable(onClick = onCompressionToggle)
                    } else {
                        Modifier
                    },
                    color = if (isCompressionSummary) Color(0xFF5F6368) else Color(0xFF202124),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (isCompressionSummary && !isCompressionExpanded) 1 else Int.MAX_VALUE,
                    overflow = if (isCompressionSummary && !isCompressionExpanded) {
                        TextOverflow.Ellipsis
                    } else {
                        TextOverflow.Clip
                    },
                )

                if (isCompressionSummary && onCompressionToggle != null) {
                    Text(
                        text = if (isCompressionExpanded) "свернуть" else "развернуть",
                        modifier = Modifier.clickable(onClick = onCompressionToggle),
                        color = Color(0xFF5F6368),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

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

                message.memory?.let { memory ->
                    MemoryMetadataBlock(
                        memory = memory,
                        isExpanded = isMemoryExpanded,
                        onToggle = { isMemoryExpanded = !isMemoryExpanded },
                    )
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

@Composable
private fun MemoryMetadataBlock(
    memory: ChatMessageMemoryMetadata,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val summary = memory.summaryText()
    if (summary.isBlank()) {
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = summary,
            modifier = Modifier.clickable(onClick = onToggle),
            color = Color(0xFF5F6368),
            style = MaterialTheme.typography.labelSmall,
        )
        if (isExpanded) {
            memory.error?.let { error ->
                Text(
                    text = error,
                    color = Color(0xFF8A4B00),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            memory.changes.takeIf { it.isNotEmpty() }?.let { changes ->
                Text(
                    text = buildString {
                        appendLine("memory changes:")
                        changes.forEach { change ->
                            appendLine("- ${change.action.displayText}: ${change.layer.displayText}: ${change.fact}")
                        }
                    }.trimEnd(),
                    color = Color(0xFF5F6368),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            memory.injectedItems.takeIf { it.isNotEmpty() }?.let { items ->
                Text(
                    text = buildString {
                        appendLine("memory injected:")
                        items.forEach { item ->
                            appendLine("- ${item.layer.displayText}: ${item.fact}")
                        }
                    }.trimEnd(),
                    color = Color(0xFF5F6368),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun ChatMessageMemoryMetadata.summaryText(): String =
    buildList {
        if (storedLayers.isNotEmpty()) {
            add("memory stored: ${storedLayers.distinct().joinToString { it.displayText }}")
        }
        if (usedLayers.isNotEmpty()) {
            add("memory used: ${usedLayers.distinct().joinToString { it.displayText }}")
        }
        if (error != null && storedLayers.isEmpty() && usedLayers.isEmpty()) {
            add("memory: ignored")
        }
    }.joinToString(separator = " · ")

private val ChatMemoryLayer.displayText: String
    get() = when (this) {
        ChatMemoryLayer.ShortTerm -> "short-term"
        ChatMemoryLayer.WorkingMemory -> "working"
        ChatMemoryLayer.LongTermMemory -> "long-term"
    }

private val ChatMemoryChangeAction.displayText: String
    get() = when (this) {
        ChatMemoryChangeAction.Add -> "add"
        ChatMemoryChangeAction.Update -> "update"
        ChatMemoryChangeAction.Delete -> "delete"
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
