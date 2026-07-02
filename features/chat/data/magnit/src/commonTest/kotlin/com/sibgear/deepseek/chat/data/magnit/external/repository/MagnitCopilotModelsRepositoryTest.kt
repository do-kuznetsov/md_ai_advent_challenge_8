package com.sibgear.deepseek.chat.data.magnit.external.repository

import com.sibgear.deepseek.chat.data.magnit.external.MagnitCopilotContextLength
import com.sibgear.deepseek.chat.data.magnit.external.MagnitCopilotModelId
import com.sibgear.deepseek.chat.data.magnit.external.MagnitCopilotProviderLabel
import com.sibgear.deepseek.chat.domain.model.AiProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MagnitCopilotModelsRepositoryTest {
    @Test
    fun loadsStaticMagnitCopilotModel() = runTest {
        val models = MagnitCopilotModelsRepository().loadModels()

        assertEquals(1, models.size)
        assertEquals(MagnitCopilotModelId, models.single().id)
        assertEquals(MagnitCopilotProviderLabel, models.single().displayName)
        assertEquals(AiProvider.MagnitCopilot, models.single().provider)
        assertEquals(MagnitCopilotContextLength, models.single().contextLength)
    }
}
