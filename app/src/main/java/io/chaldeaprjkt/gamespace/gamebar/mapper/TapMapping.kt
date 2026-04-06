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

import android.view.KeyEvent

data class TapMapping(
    val id: Int,
    val keyCode: Int,
    val xPercent: Float,
    val yPercent: Float,
    val label: String = keyCodeToLabel(keyCode)
) {
    companion object {
        private val GAMEPAD_LABELS = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to "A",
            KeyEvent.KEYCODE_BUTTON_B to "B",
            KeyEvent.KEYCODE_BUTTON_C to "C",
            KeyEvent.KEYCODE_BUTTON_X to "X",
            KeyEvent.KEYCODE_BUTTON_Y to "Y",
            KeyEvent.KEYCODE_BUTTON_Z to "Z",
            KeyEvent.KEYCODE_BUTTON_L1 to "L1",
            KeyEvent.KEYCODE_BUTTON_R1 to "R1",
            KeyEvent.KEYCODE_BUTTON_L2 to "L2",
            KeyEvent.KEYCODE_BUTTON_R2 to "R2",
            KeyEvent.KEYCODE_BUTTON_THUMBL to "LS",
            KeyEvent.KEYCODE_BUTTON_THUMBR to "RS",
            KeyEvent.KEYCODE_BUTTON_START to "START",
            KeyEvent.KEYCODE_BUTTON_SELECT to "SEL",
            KeyEvent.KEYCODE_BUTTON_MODE to "MODE",
            KeyEvent.KEYCODE_DPAD_UP to "UP",
            KeyEvent.KEYCODE_DPAD_DOWN to "DOWN",
            KeyEvent.KEYCODE_DPAD_LEFT to "LEFT",
            KeyEvent.KEYCODE_DPAD_RIGHT to "RIGHT",
        )

        fun keyCodeToLabel(keyCode: Int): String =
            GAMEPAD_LABELS[keyCode] ?: KeyEvent.keyCodeToString(keyCode)
                .removePrefix("KEYCODE_")
                .take(6)
    }
}
