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
package io.chaldeaprjkt.gamespace.gamebar.fps

import android.app.ActivityTaskManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.WindowManager
import android.window.TaskFpsCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.*
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FpsInteractor @Inject constructor(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val taskManager = ActivityTaskManager.getService()
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _fpsHistory = MutableStateFlow<List<Float>>(emptyList())
    val fpsHistory: StateFlow<List<Float>> get() = _fpsHistory

    private val _dynamicMaxFps = MutableStateFlow(15f)
    val dynamicMaxFps: StateFlow<Float> get() = _dynamicMaxFps

    val maxRefreshRate: Float by lazy {
        wm.defaultDisplay?.mode?.refreshRate ?: 60f
    }

    private val fpsCallback = object : TaskFpsCallback() {
        override fun onFpsReported(fps: Float) {
            coroutineScope.launch {
                val previous = _fpsHistory.value
                val alpha = 0.25f
                val last = previous.lastOrNull() ?: fps
                val currentFps = last + alpha * (fps - last)
                val updatedHistory = (previous + currentFps).takeLast(60)
                val dynamicMax = updatedHistory
                    .sortedDescending()
                    .take((updatedHistory.size * 0.1f).coerceAtLeast(3f).toInt())
                    .average()
                    .toFloat()
                    .coerceAtLeast(0f)
                withContext(Dispatchers.Main) {
                    _fpsHistory.value = updatedHistory
                    _dynamicMaxFps.value = dynamicMax
                }
            }
        }
    }

    fun start() {
        val taskId = taskManager?.focusedRootTaskInfo?.taskId ?: return
        wm.registerTaskFpsCallback(taskId, Runnable::run, fpsCallback)
    }

    fun dispose() {
        wm.unregisterTaskFpsCallback(fpsCallback)
    }
}
