package com.sibgear.deepseek.chat.ui.internal.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sibgear.deepseek.chat.domain.model.ChatBranch
import com.sibgear.deepseek.chat.domain.model.ContextManagementMode
import com.sibgear.deepseek.chat.ui.external.model.ChatViewState

@Composable
internal fun BranchTreePanel(
    state: ChatViewState,
    modifier: Modifier = Modifier,
) {
    if (state.contextManagementMode != ContextManagementMode.Branching) {
        return
    }

    val activePathIds = state.branches.activePathIds(state.activeBranchId)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 144.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = state.branchingStatus ?: "Branches",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.branches.isEmpty()) {
            Text(
                text = "branches: empty",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.branches.asTreeRows().forEach { row ->
                val isInActivePath = row.branch.id in activePathIds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "",
                        modifier = Modifier.width((row.depth * 16).dp),
                    )
                    Text(
                        text = if (isInActivePath) "●" else "○",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = row.branch.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isInActivePath) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun List<ChatBranch>.activePathIds(activeBranchId: Int?): Set<Int> {
    if (activeBranchId == null) {
        return emptySet()
    }

    val byId = associateBy { it.id }
    val result = mutableSetOf<Int>()
    var currentId: Int? = activeBranchId
    while (currentId != null && currentId !in result) {
        val branch = byId[currentId] ?: break
        result += branch.id
        currentId = branch.parentId
    }
    return result
}

private fun List<ChatBranch>.asTreeRows(): List<BranchTreeRow> {
    val childrenByParent = groupBy { it.parentId }
    fun visit(parentId: Int?, depth: Int): List<BranchTreeRow> =
        childrenByParent[parentId]
            .orEmpty()
            .sortedBy { it.id }
            .flatMap { branch ->
                listOf(BranchTreeRow(branch, depth)) + visit(branch.id, depth + 1)
            }

    return visit(parentId = null, depth = 0)
}

private data class BranchTreeRow(
    val branch: ChatBranch,
    val depth: Int,
)
