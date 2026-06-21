package com.sibgear.deepseek.chat.workspace.ui.internal.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatStorageType
import com.sibgear.deepseek.chat.workspace.ui.external.model.ChatTab

@Composable
internal fun ChatTabBar(
    tabs: List<ChatTab>,
    activeTabNumber: Int,
    selectedStorageType: ChatStorageType,
    storageDirectoryLabel: String,
    isStorageMenuExpanded: Boolean,
    isStorageSwitchEnabled: Boolean,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onTabAdded: () -> Unit,
    onStorageMenuExpandedChanged: (Boolean) -> Unit,
    onStorageSelected: (ChatStorageType) -> Unit,
    onProfileClicked: () -> Unit,
    onInvariantsClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            tabs.forEach { tab ->
                ChatTabItem(
                    title = tab.title,
                    isActive = tab.number == activeTabNumber,
                    onSelected = { onTabSelected(tab.number) },
                    onClosed = { onTabClosed(tab.number) },
                )
            }
        }

        AddTabButton(onTabAdded = onTabAdded)

        StorageSelector(
            selectedStorageType = selectedStorageType,
            storageDirectoryLabel = storageDirectoryLabel,
            isStorageMenuExpanded = isStorageMenuExpanded,
            isStorageSwitchEnabled = isStorageSwitchEnabled,
            onStorageMenuExpandedChanged = onStorageMenuExpandedChanged,
            onStorageSelected = onStorageSelected,
            onProfileClicked = onProfileClicked,
            onInvariantsClicked = onInvariantsClicked,
        )
    }
}

@Composable
private fun AddTabButton(
    onTabAdded: () -> Unit,
) {
    Text(
        text = "+",
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
            .clickable(onClick = onTabAdded)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun ChatTabItem(
    title: String,
    isActive: Boolean,
    onSelected: () -> Unit,
    onClosed: () -> Unit,
) {
    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelected)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = "×",
            modifier = Modifier
                .background(Color.Transparent, RoundedCornerShape(4.dp))
                .clickable(onClick = onClosed)
                .padding(horizontal = 4.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StorageSelector(
    selectedStorageType: ChatStorageType,
    storageDirectoryLabel: String,
    isStorageMenuExpanded: Boolean,
    isStorageSwitchEnabled: Boolean,
    onStorageMenuExpandedChanged: (Boolean) -> Unit,
    onStorageSelected: (ChatStorageType) -> Unit,
    onProfileClicked: () -> Unit,
    onInvariantsClicked: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TooltipArea(
            tooltip = {
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = storageDirectoryLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
        ) {
            Text(
                text = storageDirectoryLabel,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onProfileClicked,
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "user profile",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            IconButton(
                onClick = onInvariantsClicked,
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "project invariants",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { onStorageMenuExpandedChanged(true) },
                    enabled = isStorageSwitchEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = selectedStorageType.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                DropdownMenu(
                    expanded = isStorageMenuExpanded,
                    onDismissRequest = { onStorageMenuExpandedChanged(false) },
                ) {
                    ChatStorageType.entries.forEach { storageType ->
                        DropdownMenuItem(
                            text = { Text(storageType.label) },
                            onClick = { onStorageSelected(storageType) },
                        )
                    }
                }
            }
        }
    }
}
