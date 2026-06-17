package com.sibgear.deepseek.assistant.memory.domain.service

import com.sibgear.deepseek.assistant.memory.domain.model.UserProfile

interface AssistantProfileService {
    suspend fun updateProfile(
        currentProfile: UserProfile,
        interviewAnswers: List<String>,
        modelId: String,
    ): UserProfile
}
