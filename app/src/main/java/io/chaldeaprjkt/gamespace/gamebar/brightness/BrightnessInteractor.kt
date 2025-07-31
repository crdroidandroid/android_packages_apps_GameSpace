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
package io.chaldeaprjkt.gamespace.gamebar.brightness

import android.hardware.display.BrightnessInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrightnessInteractor @Inject constructor(
    private val repository: BrightnessRepository
) {

    val brightnessInfo: StateFlow<BrightnessInfo?> = repository.brightnessInfo
    val isAuto: StateFlow<Boolean?> = repository.isAuto

    private val _userHasInteracted = MutableStateFlow(false)
    val userHasInteracted: StateFlow<Boolean> = _userHasInteracted

    fun onUserInteracted() {
        _userHasInteracted.value = true
    }

    fun setBrightness(percent: Float) {
        if (_userHasInteracted.value) {
            repository.setBrightness(percent)
        }
    }

    fun toggleAutoMode() {
        repository.isAuto.value?.let { current ->
            repository.setAutoMode(!current)
        }
    }

    fun refresh() = repository.refresh()
    
    fun start() = repository.start()
    
    fun dispose() = repository.dispose()
}
