package com.sibgear.deepseek.chat.history.data.external.repository

import com.sibgear.deepseek.chat.history.data.internal.mapper.toHistoryMessages
import com.sibgear.deepseek.chat.history.data.internal.mapper.toHistoryMessagesByChatId
import com.sibgear.deepseek.chat.history.data.internal.mapper.toChatHistoriesFileDto
import com.sibgear.deepseek.chat.history.data.internal.model.ChatHistoryFileDto
import com.sibgear.deepseek.chat.history.data.internal.model.LegacyChatHistoryFileDto
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.repository.ChatHistoryRepository
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileChatHistoryRepository(
    private val file: File,
    private val chatId: Int = 1,
) : ChatHistoryRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }

    override suspend fun add(message: HistoryMessage): List<HistoryMessage> =
        replace(getMessages() + message)

    override suspend fun getMessages(): List<HistoryMessage> =
        readMessagesByChatId()[chatId].orEmpty()

    override suspend fun replace(messages: List<HistoryMessage>): List<HistoryMessage> {
        val messagesByChatId = readMessagesByChatId().toMutableMap()
        messagesByChatId[chatId] = messages
        writeMessagesByChatId(messagesByChatId)
        return messages
    }

    override suspend fun clear() {
        val messagesByChatId = readMessagesByChatId().toMutableMap()
        messagesByChatId.remove(chatId)
        writeMessagesByChatId(messagesByChatId)
    }

    private fun readMessagesByChatId(): Map<Int, List<HistoryMessage>> {
        if (!file.exists()) {
            return emptyMap()
        }

        return runCatching {
            json.decodeFromString<ChatHistoryFileDto>(file.readText()).toHistoryMessagesByChatId()
        }.recoverCatching {
            mapOf(
                LegacySingleChatId to json.decodeFromString<LegacyChatHistoryFileDto>(file.readText())
                    .toHistoryMessages(),
            )
        }.getOrElse {
            preserveCorruptFile()
            emptyMap()
        }
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

    private fun preserveCorruptFile() {
        runCatching {
            val corruptFile = File(file.parentFile, "${file.name}.corrupt")
            file.copyTo(corruptFile, overwrite = true)
        }
    }

    private companion object {
        const val LegacySingleChatId = 1
    }
}
