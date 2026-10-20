package com.checkin.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkin.assistant.data.AppPrefs
import com.checkin.assistant.data.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ApiConfigViewModel @Inject constructor(private val prefs: PrefsRepository) : ViewModel() {

    val appPrefs: StateFlow<AppPrefs> =
        prefs.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPrefs())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun save(
        signUrl: String,
        signBody: String,
        successKeyword: String,
        settingsUrl: String
    ) {
        viewModelScope.launch {
            prefs.saveApiConfig(signUrl, signBody, successKeyword, settingsUrl)
            _message.value = "接口配置已保存"
        }
    }

    fun reset() {
        viewModelScope.launch {
            prefs.resetApiConfig()
            _message.value = "已恢复默认配置"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
