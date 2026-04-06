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

import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager

class KeyTapInjector(context: Context, private val wm: WindowManager) {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private var mappings = emptyList<TapMapping>()
    private var keyCodeMap = emptyMap<Int, TapMapping>()
    private val activeDownTimes = mutableMapOf<Int, Long>()
    private var screenWidth = 0
    private var screenHeight = 0
    private var active = false

    fun start(mappingList: List<TapMapping>) {
        mappings = mappingList
        keyCodeMap = mappingList.associateBy { it.keyCode }
        updateScreenSize()
        active = mappingList.isNotEmpty()
    }

    fun stop() {
        active = false
        releaseAll()
        mappings = emptyList()
        keyCodeMap = emptyMap()
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!active) return false
        val mapping = keyCodeMap[event.keyCode] ?: return false

        val x = mapping.xPercent * screenWidth
        val y = mapping.yPercent * screenHeight

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    val downTime = SystemClock.uptimeMillis()
                    activeDownTimes[mapping.keyCode] = downTime
                    injectTouch(MotionEvent.ACTION_DOWN, x, y, downTime, downTime)
                }
            }
            KeyEvent.ACTION_UP -> {
                val downTime = activeDownTimes.remove(mapping.keyCode) ?: SystemClock.uptimeMillis()
                injectTouch(MotionEvent.ACTION_UP, x, y, downTime, SystemClock.uptimeMillis())
            }
        }
        return true
    }

    fun updateScreenSize() {
        val bounds = wm.maximumWindowMetrics.bounds
        screenWidth = bounds.width()
        screenHeight = bounds.height()
    }

    private fun releaseAll() {
        val now = SystemClock.uptimeMillis()
        for ((keyCode, downTime) in activeDownTimes) {
            val mapping = keyCodeMap[keyCode] ?: continue
            injectTouch(
                MotionEvent.ACTION_UP,
                mapping.xPercent * screenWidth,
                mapping.yPercent * screenHeight,
                downTime,
                now
            )
        }
        activeDownTimes.clear()
    }

    private fun injectTouch(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long) {
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1.0f
            size = 1.0f
        }
        val event = MotionEvent.obtain(
            downTime, eventTime, action, 1,
            arrayOf(props), arrayOf(coords),
            0, 0, 1.0f, 1.0f,
            -1, 0, InputDevice.SOURCE_TOUCHSCREEN, 0, 0
        )
        try {
            inputManager.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
        } finally {
            event.recycle()
        }
    }
}
