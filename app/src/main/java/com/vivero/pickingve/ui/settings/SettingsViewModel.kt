package com.vivero.pickingve.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivero.pickingve.data.repository.SettingsRepository
import com.vivero.pickingve.data.repository.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<SettingsStore> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.load())

    fun save(
        telegramBotToken: String,
        telegramChatId: String,
        operatorEmail: String
    ) {
        settingsRepository.update {
            it.copy(
                telegramBotToken = telegramBotToken,
                telegramChatId = telegramChatId,
                operatorEmail = operatorEmail
            )
        }
    }
}