package com.sibgear.deepseek.chat.history.data.external.storage

import com.sibgear.deepseek.chat.history.data.external.repository.FileChatHistoryRepository
import com.sibgear.deepseek.chat.history.data.internal.mapper.ChatHistoryData
import com.sibgear.deepseek.chat.history.data.internal.mapper.toChatDataByChatId
import com.sibgear.deepseek.chat.history.data.internal.mapper.toChatHistoriesDataFileDto
import com.sibgear.deepseek.chat.history.data.internal.mapper.toHistoryMessages
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
        readChatDataByChatId().keys.sorted()

    fun createRepository(chatId: Int): FileChatHistoryRepository =
        FileChatHistoryRepository(
            file = file,
            chatId = chatId,
        )

    fun deleteChat(chatId: Int) {
        val dataByChatId = readChatDataByChatId().toMutableMap()
        dataByChatId.remove(chatId)
        writeChatDataByChatId(dataByChatId)
    }

    private fun readChatDataByChatId(): Map<Int, ChatHistoryData> =
        if (!file.exists()) {
            emptyMap()
        } else {
            runCatching {
                json.decodeFromString<ChatHistoryFileDto>(file.readText()).toChatDataByChatId()
            }.recoverCatching {
                mapOf(
                    LegacySingleChatId to ChatHistoryData(
                        messages = json.decodeFromString<LegacyChatHistoryFileDto>(file.readText())
                            .toHistoryMessages(),
                    ),
                )
            }.getOrDefault(emptyMap())
        }

    private fun writeChatDataByChatId(dataByChatId: Map<Int, ChatHistoryData>) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        val tempFile = File(parent ?: File("."), "${file.name}.tmp")
        tempFile.writeText(json.encodeToString(dataByChatId.toChatHistoriesDataFileDto()))
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
