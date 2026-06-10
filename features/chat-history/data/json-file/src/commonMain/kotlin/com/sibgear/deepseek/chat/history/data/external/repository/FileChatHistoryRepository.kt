package com.sibgear.deepseek.chat.history.data.external.repository

import com.sibgear.deepseek.chat.history.data.internal.mapper.toChatHistoryFileDto
import com.sibgear.deepseek.chat.history.data.internal.mapper.toHistoryMessages
import com.sibgear.deepseek.chat.history.data.internal.model.ChatHistoryFileDto
import com.sibgear.deepseek.chat.history.domain.model.HistoryMessage
import com.sibgear.deepseek.chat.history.domain.repository.ChatHistoryRepository
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FileChatHistoryRepository(
    private val file: File,
) : ChatHistoryRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }
    private var messages: List<HistoryMessage> = readMessages()

    override suspend fun add(message: HistoryMessage): List<HistoryMessage> =
        replace(messages + message)

    override suspend fun getMessages(): List<HistoryMessage> = messages

    override suspend fun replace(messages: List<HistoryMessage>): List<HistoryMessage> {
        this.messages = messages
        writeMessages(messages)
        return this.messages
    }

    override suspend fun clear() {
        replace(emptyList())
    }

    private fun readMessages(): List<HistoryMessage> {
        if (!file.exists()) {
            return emptyList()
        }

        return runCatching {
            json.decodeFromString<ChatHistoryFileDto>(file.readText()).toHistoryMessages()
        }.getOrElse {
            preserveCorruptFile()
            emptyList()
        }
    }

    private fun writeMessages(messages: List<HistoryMessage>) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        val tempFile = File(parent ?: File("."), "${file.name}.tmp")
        tempFile.writeText(json.encodeToString(messages.toChatHistoryFileDto()))
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
}
