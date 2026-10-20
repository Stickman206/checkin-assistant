package com.checkin.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkin.assistant.data.AppPrefs
import com.checkin.assistant.data.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 只负责一件事：在首帧之前判断「是不是第一次用」，好决定从哪个页面开始。
 *
 * 初始值是 null，表示 DataStore 还没读出来——此时不要渲染任何页面，
 * 否则会先闪一下首页再跳到引导页。
 */
@HiltViewModel
class AppViewModel @Inject constructor(prefs: PrefsRepository) : ViewModel() {

    val prefs: StateFlow<AppPrefs?> = prefs.prefs
        .map<AppPrefs, AppPrefs?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
