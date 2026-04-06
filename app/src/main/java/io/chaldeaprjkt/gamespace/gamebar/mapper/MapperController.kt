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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.chaldeaprjkt.gamespace.gamebar.mapper

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Handler
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.axion.compose.lifecycle.repeatWhenAttached
import com.google.gson.Gson

data class KeyCaptureEvent(val pointId: Int, val keyCode: Int)

class MapperController(
    private val context: Context,
    private val wm: WindowManager,
    private val handler: Handler,
    private val gson: Gson,
) {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val store = TapMappingStore(context, gson)
    private val injector = KeyTapInjector(context, wm)
    private var keyMonitor: KeyInputMonitor? = null

    private var overlayView: ComposeView? = null
    private var currentPackage: String? = null
    private var editing = false
    private var capturingPointId = -1

    val keyCaptureEvent = mutableStateOf<KeyCaptureEvent?>(null)
    val awaitingKeyState = mutableStateOf(false)

    var onEditStarted: (() -> Unit)? = null
    var onEditFinished: (() -> Unit)? = null

    fun onGameStart(packageName: String) {
        currentPackage = packageName
        val mappings = store.load(packageName)
        if (mappings.isNotEmpty()) {
            startPlayMode(mappings)
        }
    }

    fun onGameLeave() {
        stopPlayMode()
        dismissEdit()
        currentPackage = null
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        injector.updateScreenSize()
        if (editing) {
            dismissEdit()
        }
    }

    fun enterEditMode() {
        val pkg = currentPackage ?: return
        if (editing) return
        editing = true
        stopPlayMode()
        onEditStarted?.invoke()

        val mappings = store.load(pkg)
        keyCaptureEvent.value = null
        awaitingKeyState.value = false

        overlayView = ComposeView(context).apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        MaterialExpressiveTheme(
                            colorScheme = dynamicDarkColorScheme(context),
                            motionScheme = MotionScheme.expressive(),
                        ) {
                            TapPointOverlay(
                                mappings = mappings,
                                keyCaptureEvent = keyCaptureEvent.value,
                                awaitingKey = awaitingKeyState.value,
                                onSave = { result -> handler.post { saveAndExitEdit(result) } },
                                onCancel = { handler.post { dismissEdit() } },
                                onRequestKeyCapture = { pointId ->
                                    capturingPointId = pointId
                                    awaitingKeyState.value = true
                                    keyCaptureEvent.value = null
                                    setOverlayFocusable(true)
                                },
                                onKeyCaptureConsumed = {
                                    keyCaptureEvent.value = null
                                    awaitingKeyState.value = false
                                },
                            )
                        }
                    }
                }
            }

            setOnKeyListener { _, keyCode, event ->
                if (capturingPointId != -1 && event.action == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        capturingPointId = -1
                        awaitingKeyState.value = false
                        setOverlayFocusable(false)
                        return@setOnKeyListener true
                    }
                    keyCaptureEvent.value = KeyCaptureEvent(capturingPointId, keyCode)
                    capturingPointId = -1
                    setOverlayFocusable(false)
                    true
                } else {
                    false
                }
            }
        }

        try {
            wm.addView(overlayView, createEditLayoutParams())
        } catch (e: Exception) {
            editing = false
            onEditFinished?.invoke()
        }
    }

    val isEditing: Boolean get() = editing

    private fun startPlayMode(mappings: List<TapMapping>) {
        injector.start(mappings)
        keyMonitor?.stop()
        keyMonitor = KeyInputMonitor(inputManager) { event ->
            injector.onKeyEvent(event)
        }
        keyMonitor?.start()
    }

    private fun stopPlayMode() {
        keyMonitor?.stop()
        keyMonitor = null
        injector.stop()
    }

    private fun saveAndExitEdit(mappings: List<TapMapping>) {
        val pkg = currentPackage ?: return
        store.save(pkg, mappings)
        dismissEdit()
        if (mappings.isNotEmpty()) {
            startPlayMode(mappings)
        }
    }

    private fun dismissEdit() {
        if (!editing) return
        editing = false
        capturingPointId = -1
        keyCaptureEvent.value = null
        awaitingKeyState.value = false
        overlayView?.let { view ->
            runCatching { wm.removeViewImmediate(view) }
        }
        overlayView = null
        onEditFinished?.invoke()
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        val view = overlayView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun createEditLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        preferMinimalPostProcessing = true
    }
}
