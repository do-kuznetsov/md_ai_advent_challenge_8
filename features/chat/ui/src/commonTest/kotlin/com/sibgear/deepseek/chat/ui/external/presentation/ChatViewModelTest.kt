package com.sibgear.deepseek.chat.ui.external.presentation

import com.sibgear.deepseek.chat.domain.interactor.ChatInteractor
import com.sibgear.deepseek.chat.domain.model.ChatMessage
import com.sibgear.deepseek.chat.domain.model.ChatRole
import com.sibgear.deepseek.chat.domain.repository.RoutingAiRepository
import com.sibgear.deepseek.chat.ui.external.model.ChatEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatViewModelTest {
    @Test
    fun systemPromptCanChangeBeforeFirstMessage() {
        val viewModel = chatViewModel()

        viewModel.onEvent(ChatEvent.SystemPromptChanged("system"))

        assertEquals("system", viewModel.state.systemPrompt)
    }

    @Test
    fun systemPromptCannotChangeAfterFirstMessage() {
        val viewModel = chatViewModel()

        viewModel.onEvent(ChatEvent.SystemPromptChanged("system"))
        viewModel.appendLocalMessage(ChatMessage(role = ChatRole.User, content = "hello"))
        viewModel.onEvent(ChatEvent.SystemPromptChanged("changed"))

        assertEquals("system", viewModel.state.systemPrompt)
    }

    private fun chatViewModel(): ChatViewModel =
        ChatViewModel(
            interactor = ChatInteractor(
                repository = RoutingAiRepository(
                    chatRepositories = emptyMap(),
                    modelRepositories = emptyMap(),
                ),
                dispatcher = Dispatchers.Unconfined,
            ),
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
        )
}
