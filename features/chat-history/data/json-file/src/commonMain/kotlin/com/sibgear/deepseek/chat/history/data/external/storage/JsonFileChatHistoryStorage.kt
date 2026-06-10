package com.sibgear.deepseek.chat.history.data.external.storage

import com.sibgear.deepseek.chat.history.data.external.repository.FileChatHistoryRepository
import com.sibgear.deepseek.chat.history.data.internal.mapper.toChatHistoriesFileDto
import com.sibgear.deepseek.chat.history.data.internal.mapper.toHistoryMessages
import com.sibgear.deepseek.chat.history.data.internal.mapper.toHistoryMessagesByChatId
import com.sibgear.deepseek.chat.history.data.internal.model.ChatHistoryFileDto
import com.sibgear.deepseek.chat.history.data.internal.model.LegacyChatHistoryFileDto
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class JsonFileChatHistoryStorage(
    private val file: File,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }

    fun loadSavedChatIds(): List<Int> =
        readMessagesByChatId().keys.sorted()

    fun createRepository(chatId: Int): FileChatHistoryRepository =
        FileChatHistoryRepository(
            file = file,
            chatId = chatId,
        )

    fun deleteChat(chatId: Int) {
        val messagesByChatId = readMessagesByChatId().toMutableMap()
        messagesByChatId.remove(chatId)
        writeMessagesByChatId(messagesByChatId)
    }

    private fun readMessagesByChatId(): Map<Int, List<HistoryMessage>> =
        if (!file.exists()) {
            emptyMap()
        } else {
            runCatching {
                json.decodeFromString<ChatHistoryFileDto>(file.readText()).toHistoryMessagesByChatId()
            }.recoverCatching {
                mapOf(
                    LegacySingleChatId to json.decodeFromString<LegacyChatHistoryFileDto>(file.readText())
                        .toHistoryMessages(),
                )
            }.getOrDefault(emptyMap())
        }

    private fun writeMessagesByChatId(messagesByChatId: Map<Int, List<HistoryMessage>>) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        val tempFile = File(parent ?: File("."), "${file.name}.tmp")
        tempFile.writeText(json.encodeToString(messagesByChatId.toChatHistoriesFileDto()))
        if (file.exists() && !file.delete()) {
            tempFile.delete()
            error("Cannot replace chat history file: ${file.absolutePath}")
        }
        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }

    private companion object {
        const val LegacySingleChatId = 1
    }
}
