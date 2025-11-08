/*
 * Copyright (C) 2025 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.gamebar

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.systemui.screenrecord.IRecordingCallback
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.gamebar.brightness.BrightnessInteractor
import io.chaldeaprjkt.gamespace.gamebar.fps.FpsInteractor
import io.chaldeaprjkt.gamespace.gamebar.tiles.TileRepository
import io.chaldeaprjkt.gamespace.settings.SettingsActivity
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import io.chaldeaprjkt.gamespace.utils.ScreenUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class GameBarViewModel(
    val appSettings: AppSettings,
    val brightnessInteractor: BrightnessInteractor,
    val fpsInteractor: FpsInteractor,
    val gameModeUtils: GameModeUtils,
    val systemSettings: SystemSettings,
    val tileRepository: TileRepository,
    private val screenUtils: ScreenUtils
) : ViewModel() {

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val recordingCallback = object : IRecordingCallback.Stub() {
        override fun onRecordingStart() {
            _isRecording.value = true
        }

        override fun onRecordingEnd() {
            _isRecording.value = false
        }
    }

    val menuOpacity = appSettings.menuOpacity / 100f
    
    val showFps = appSettings
        .collectFpsSetting()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), appSettings.showFps)

    val currentFps = fpsInteractor.fpsHistory
        .map { it.lastOrNull() ?: 0f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    private val _expanded = MutableStateFlow(false)
    val expanded = _expanded.asStateFlow()

    private val _showPanel = MutableStateFlow(false)
    val showPanel = _showPanel.asStateFlow()
    
    private val _panelProcessing = MutableStateFlow(false)
    val panelProcessing = _panelProcessing.asStateFlow()

    init {
        screenUtils.addRecordingCallback(recordingCallback)
    }

    fun setExpanded(value: Boolean) {
        _expanded.value = value
        if (!value) _showPanel.value = false
    }

    fun togglePanel() {
        _showPanel.value = !_showPanel.value
    }

    fun setPanelProcessing(value: Boolean) {
        _panelProcessing.value = value
    }

    fun setShowPanel(value: Boolean) {
        _showPanel.value = value
    }

    fun takeScreenshot() {
        runCatching { screenUtils.takeScreenshot { } }
    }

    fun toggleRecording() {
        val recorder = screenUtils.recorder ?: return
        if (!recorder.isStarting) {
            if (!recorder.isRecording) recorder.startRecording()
            else recorder.stopRecording()
        }
        setExpanded(false)
    }

    fun onPanelToggle() {
        togglePanel()
    }

    fun openSettings(context: Context) {
        context.startActivity(
            Intent(context, SettingsActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }
    
    fun dismiss() {
        setExpanded(false)
        setShowPanel(false)
    }
    
    override fun onCleared() {
        super.onCleared()
        screenUtils.removeRecordingCallback(recordingCallback)
    }
}
