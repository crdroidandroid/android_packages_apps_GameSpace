/*
 * Copyright (C) 2026 crDroid Android Project
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
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt

// The topmost window's refresh rate preference wins in WindowManager, so an
// invisible overlay above the game overrides whatever the game requests.
class RefreshRateLock(
    private val context: Context,
    private val wm: WindowManager,
) {

    private var view: View? = null

    fun apply(rate: Int) {
        release()
        if (rate <= 0) return

        val mode = findMode(context, rate) ?: run {
            Log.w(TAG, "No display mode found for $rate Hz")
            return
        }

        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = TAG
            preferredDisplayModeId = mode.modeId
            preferredMinDisplayRefreshRate = mode.refreshRate
            preferredMaxDisplayRefreshRate = mode.refreshRate
        }

        val v = View(context)
        runCatching { wm.addView(v, lp) }
            .onSuccess { view = v }
            .onFailure { Log.e(TAG, "Failed to add refresh rate lock window", it) }
    }

    fun release() {
        view?.let { v -> runCatching { wm.removeViewImmediate(v) } }
        view = null
    }

    companion object {
        private const val TAG = "GameSpaceRefreshRateLock"

        private fun defaultDisplay(context: Context): Display? =
            context.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)

        fun availableRates(context: Context): List<Int> {
            val display = defaultDisplay(context) ?: return emptyList()
            val current = display.mode
            return display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                            it.physicalHeight == current.physicalHeight
                }
                .map { it.refreshRate.roundToInt() }
                .distinct()
                .sorted()
        }

        private fun findMode(context: Context, rate: Int): Display.Mode? {
            val display = defaultDisplay(context) ?: return null
            val current = display.mode
            return display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                            it.physicalHeight == current.physicalHeight
                }
                .minByOrNull { abs(it.refreshRate - rate) }
                ?.takeIf { abs(it.refreshRate - rate) < 1f }
        }
    }
}
