package com.sibgear.deepseek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ChatTabBar(
    tabs: List<ChatTab>,
    activeTabNumber: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onTabAdded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            ChatTabItem(
                number = tab.number,
                isActive = tab.number == activeTabNumber,
                onSelected = { onTabSelected(tab.number) },
                onClosed = { onTabClosed(tab.number) },
            )
        }

        Text(
            text = "+",
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                .clickable(onClick = onTabAdded)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ChatTabItem(
    number: Int,
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
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelected)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString(),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
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
