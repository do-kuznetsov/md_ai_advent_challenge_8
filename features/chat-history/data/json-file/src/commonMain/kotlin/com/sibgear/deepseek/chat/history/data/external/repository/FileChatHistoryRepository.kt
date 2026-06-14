package com.sibgear.deepseek.chat.history.data.external.repository

import com.sibgear.deepseek.chat.history.data.internal.mapper.ChatHistoryData
import com.sibgear.deepseek.chat.history.data.internal.mapper.toChatDataByChatId
import com.sibgear.deepseek.chat.history.data.internal.mapper.toChatHistoriesDataFileDto
import com.sibgear.deepseek.chat.history.data.internal.mapper.toHistoryMessages
import com.sibgear.deepseek.chat.history.data.internal.model.ChatHistoryFileDto
import com.sibgear.deepseek.chat.history.data.internal.model.LegacyChatHistoryFileDto
import com.sibgear.deepseek.chat.history.domain.model.HistoryBranch
import com.sibgear.deepseek.chat.history.domain.model.HistoryFact
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
        readChatDataByChatId()[chatId]?.messages.orEmpty()

    override suspend fun replace(messages: List<HistoryMessage>): List<HistoryMessage> {
        val dataByChatId = readChatDataByChatId().toMutableMap()
        val currentData = dataByChatId[chatId] ?: ChatHistoryData()
        dataByChatId[chatId] = currentData.copy(messages = messages)
        writeChatDataByChatId(dataByChatId)
        return messages
    }

    override suspend fun getFacts(): List<HistoryFact> =
        readChatDataByChatId()[chatId]?.facts.orEmpty()

    override suspend fun replaceFacts(facts: List<HistoryFact>): List<HistoryFact> {
        val dataByChatId = readChatDataByChatId().toMutableMap()
        val currentData = dataByChatId[chatId] ?: ChatHistoryData()
        dataByChatId[chatId] = currentData.copy(facts = facts)
        writeChatDataByChatId(dataByChatId)
        return facts
    }

    override suspend fun getBranches(): List<HistoryBranch> =
        readChatDataByChatId()[chatId]?.branches.orEmpty()

    override suspend fun replaceBranches(branches: List<HistoryBranch>): List<HistoryBranch> {
        val dataByChatId = readChatDataByChatId().toMutableMap()
        val currentData = dataByChatId[chatId] ?: ChatHistoryData()
        dataByChatId[chatId] = currentData.copy(branches = branches)
        writeChatDataByChatId(dataByChatId)
        return branches
    }

    override suspend fun clear() {
        val dataByChatId = readChatDataByChatId().toMutableMap()
        dataByChatId.remove(chatId)
        writeChatDataByChatId(dataByChatId)
    }

    private fun readChatDataByChatId(): Map<Int, ChatHistoryData> {
        if (!file.exists()) {
            return emptyMap()
        }

        return runCatching {
            json.decodeFromString<ChatHistoryFileDto>(file.readText()).toChatDataByChatId()
        }.recoverCatching {
            mapOf(
                LegacySingleChatId to ChatHistoryData(
                    messages = json.decodeFromString<LegacyChatHistoryFileDto>(file.readText())
                        .toHistoryMessages(),
                ),
            )
        }.getOrElse {
            preserveCorruptFile()
            emptyMap()
        }
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
