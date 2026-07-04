package com.sibgear.deepseek.chat.ui.internal.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import com.sibgear.deepseek.chat.ui.external.model.ChatViewState
import com.sibgear.rag.domain.model.ChunkingStrategyType

@Composable
internal fun RagSettingsPanel(
    state: ChatViewState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.isRagEnabled,
                onCheckedChange = { onEvent(ChatEvent.RagEnabledChanged(it)) },
            )

            ApiSettingsLabel(
                text = "RAG",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RagStrategyButton(
                text = "fixed",
                isSelected = state.ragStrategy == ChunkingStrategyType.Fixed,
                isEnabled = state.isRagEnabled,
                onClick = { onEvent(ChatEvent.RagStrategySelected(ChunkingStrategyType.Fixed)) },
                modifier = Modifier.weight(1f),
            )
            RagStrategyButton(
                text = "structure",
                isSelected = state.ragStrategy == ChunkingStrategyType.Structure,
                isEnabled = state.isRagEnabled,
                onClick = { onEvent(ChatEvent.RagStrategySelected(ChunkingStrategyType.Structure)) },
                modifier = Modifier.weight(1f),
            )
        }

        ShortcutOutlinedTextField(
            value = state.ragIndexDirectory,
            onValueChange = { onEvent(ChatEvent.RagIndexDirectoryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = state.isRagEnabled,
        )

        RagToggleRow(
            text = "rewrite",
            checked = state.isRagQueryRewriteEnabled,
            enabled = state.isRagEnabled,
            onCheckedChange = { onEvent(ChatEvent.RagQueryRewriteEnabledChanged(it)) },
        )

        RagToggleRow(
            text = "filter",
            checked = state.isRagFilteringEnabled,
            enabled = state.isRagEnabled,
            onCheckedChange = { onEvent(ChatEvent.RagFilteringEnabledChanged(it)) },
        )

        RagToggleRow(
            text = "rerank",
            checked = state.isRagRerankingEnabled,
            enabled = state.isRagEnabled,
            onCheckedChange = { onEvent(ChatEvent.RagRerankingEnabledChanged(it)) },
        )

        ShortcutOutlinedTextField(
            value = state.ragRerankerModelDirectory,
            onValueChange = { onEvent(ChatEvent.RagRerankerModelDirectoryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = state.isRagEnabled && state.isRagRerankingEnabled,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RagInputField(
                label = "before",
                value = state.ragTopKBeforeFilterInput,
                onValueChange = { onEvent(ChatEvent.RagTopKBeforeFilterChanged(it)) },
                enabled = state.isRagEnabled && state.isRagFilteringEnabled,
                modifier = Modifier.weight(1f),
            )
            RagInputField(
                label = "after",
                value = state.ragTopKAfterFilterInput,
                onValueChange = { onEvent(ChatEvent.RagTopKAfterFilterChanged(it)) },
                enabled = state.isRagEnabled,
                modifier = Modifier.weight(1f),
            )
            RagInputField(
                label = "threshold",
                value = state.ragSimilarityThresholdInput,
                onValueChange = { onEvent(ChatEvent.RagSimilarityThresholdChanged(it)) },
                enabled = state.isRagEnabled && state.isRagFilteringEnabled,
                modifier = Modifier.weight(1f),
            )
        }

        state.ragStatus?.let { status ->
            ApiSettingsLabel(
                text = status,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RagToggleRow(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )

        ApiSettingsLabel(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RagInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ApiSettingsLabel(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ShortcutOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
        )
    }
}

@Composable
private fun RagStrategyButton(
    text: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isSelected) {
        Button(
            onClick = onClick,
            enabled = isEnabled,
            modifier = modifier,
        ) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = isEnabled,
            modifier = modifier,
        ) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
