package com.checkin.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkin.assistant.data.AppPrefs
import com.checkin.assistant.data.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 设置首页只需要展示状态，接口配置的读写已移到 [ApiConfigViewModel] */
@HiltViewModel
class SettingsViewModel @Inject constructor(prefs: PrefsRepository) : ViewModel() {

    val appPrefs: StateFlow<AppPrefs> =
        prefs.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPrefs())
}
