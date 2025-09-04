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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FpsInteractor @Inject constructor(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val taskManager = ActivityTaskManager.getService()

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _fpsHistory = MutableStateFlow<List<Float>>(emptyList())
    val fpsHistory: StateFlow<List<Float>> get() = _fpsHistory

    private val _dynamicMaxFps = MutableStateFlow(60f)
    val dynamicMaxFps: StateFlow<Float> get() = _dynamicMaxFps

    val maxRefreshRate: Float by lazy {
        wm.defaultDisplay?.mode?.refreshRate ?: 60f
    }

    private val historySize = 100
    private val buffer = ArrayDeque<Float>(historySize)

    private val fpsFlow = MutableSharedFlow<Float>(extraBufferCapacity = 64)

    private val fpsCallback = object : TaskFpsCallback() {
        override fun onFpsReported(fps: Float) {
            fpsFlow.tryEmit(fps)
        }
    }

    init {
        coroutineScope.launch {
            fpsFlow
                .sample(1000) 
                .map { fps ->
                    val smoothed = (buffer.lastOrNull() ?: fps) * 0.75f + fps * 0.25f
                    if (buffer.size >= historySize) buffer.removeFirst()
                    buffer.addLast(smoothed)
                    val dynamicMax = buffer.maxOrNull()?.coerceAtLeast(15f) ?: 60f
                    smoothed to dynamicMax
                }
                .flowOn(Dispatchers.Default)
                .collect { (smoothed, dynamicMax) ->
                    _fpsHistory.value = buffer.toList()
                    _dynamicMaxFps.value = dynamicMax
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
