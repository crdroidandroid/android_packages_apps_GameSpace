/*
 * Copyright (C) 2025-2026 AxionOS
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
package io.chaldeaprjkt.gamespace.gamebar.mapper

import android.hardware.input.InputManager
import android.os.Looper
import android.view.Display
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.InputMonitor
import android.view.KeyEvent

class KeyInputMonitor(
    private val inputManager: InputManager,
    private val onKeyEvent: (KeyEvent) -> Unit,
) {
    private var monitor: InputMonitor? = null
    private var receiver: InputEventReceiver? = null

    fun start() {
        if (monitor != null) return
        val mon = inputManager.monitorGestureInput(MONITOR_NAME, Display.DEFAULT_DISPLAY)
        monitor = mon

        receiver = object : InputEventReceiver(mon.inputChannel, Looper.getMainLooper()) {
            override fun onInputEvent(event: InputEvent) {
                if (event is KeyEvent) {
                    onKeyEvent(event)
                }
                finishInputEvent(event, false)
            }
        }
    }

    fun stop() {
        receiver?.dispose()
        receiver = null
        monitor?.dispose()
        monitor = null
    }

    companion object {
        private const val MONITOR_NAME = "GameSpaceMapper"
    }
}
