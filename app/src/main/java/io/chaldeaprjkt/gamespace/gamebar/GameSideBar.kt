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
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.gamebar.brightness.BrightnessInteractor
import io.chaldeaprjkt.gamespace.gamebar.fps.FpsInteractor
import io.chaldeaprjkt.gamespace.gamebar.lifecycle.repeatWhenAttached
import io.chaldeaprjkt.gamespace.gamebar.tiles.TileRepository
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import io.chaldeaprjkt.gamespace.utils.ScreenUtils
import io.chaldeaprjkt.gamespace.utils.safeArea
import io.chaldeaprjkt.gamespace.utils.dp
import kotlinx.coroutines.flow.*

class GameSideBar(
    private val context: Context,
    private val windowManager: WindowManager,
    private val handler: Handler,
    private val screenUtils: ScreenUtils,
    private val danmakuService: DanmakuService,
    private val brightnessInteractor: BrightnessInteractor,
    private val fpsInteractor: FpsInteractor,
    private val gameModeUtils: GameModeUtils,
    private val systemSettings: SystemSettings,
    private val tileRepository: TileRepository,
    private val appSettings: AppSettings
) {
    private val overlayFactory = OverlayFactory(
        context = context,
        windowManager = windowManager,
        handler = handler,
        brightnessInteractor = brightnessInteractor,
        fpsInteractor = fpsInteractor,
        gameModeUtils = gameModeUtils,
        appSettings = appSettings,
        systemSettings = systemSettings,
        tileRepository = tileRepository,
        screenUtils = screenUtils,
        danmakuService = danmakuService,
        gameSideBarProvider = { this }
    )

    fun onCreate() {
        overlayFactory.initialize()
    }

    fun onGameStart() {
        overlayFactory.showSidebar()
        brightnessInteractor.start()
        fpsInteractor.start()
    }

    fun onGameLeave() {
        handler.removeCallbacksAndMessages(null)
        overlayFactory.hideAll()
        brightnessInteractor.dispose()
        fpsInteractor.dispose()
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        danmakuService.updateConfiguration(newConfig)
    }

    fun showPanel() {
        overlayFactory.showPanel()
    }

    fun hidePanel() {
        overlayFactory.hidePanel()
    }
}

class OverlayFactory(
    private val context: Context,
    private val windowManager: WindowManager,
    private val handler: Handler,
    private val brightnessInteractor: BrightnessInteractor,
    private val fpsInteractor: FpsInteractor,
    private val gameModeUtils: GameModeUtils,
    private val appSettings: AppSettings,
    private val systemSettings: SystemSettings,
    private val tileRepository: TileRepository,
    private val screenUtils: ScreenUtils,
    private val danmakuService: DanmakuService,
    private val gameSideBarProvider: () -> GameSideBar
) {
    private val overlayManager = OverlayManager(context, windowManager, handler)
    private var sidebarView: ComposeView? = null

    fun initialize() {
        sidebarView = createSidebarOverlay()
    }

    fun showSidebar() {
        handler.post {
            sidebarView?.let { overlayManager.attachSidebar(it) }
        }
    }

    fun showPanel() {
        handler.post {
            val panelView = createPanelOverlay()
            overlayManager.attachPanel(panelView)
        }
    }

    fun hidePanel() {
        handler.post {
            overlayManager.detachPanel()
        }
    }

    fun hideAll() {
        overlayManager.detachAll()
    }

    private val viewModelFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return GameBarViewModel(
                brightnessInteractor = brightnessInteractor,
                fpsInteractor = fpsInteractor,
                gameModeUtils = gameModeUtils,
                systemSettings = systemSettings,
                tileRepository = tileRepository,
                screenUtils = screenUtils,
                appSettings = appSettings
            ) as T
        }
    }

    private fun createSidebarOverlay(): ComposeView {
        val overlayView = ComposeView(context)
        danmakuService.init()

        overlayView.apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnDetachedFromWindow
                    )
                    setContent {
                        val isDark = isSystemInDarkTheme()
                        val colorScheme = if (isDark) {
                            dynamicDarkColorScheme(context)
                        } else {
                            dynamicLightColorScheme(context)
                        }

                        val viewModel: GameBarViewModel = viewModel(factory = viewModelFactory)

                        MaterialTheme(colorScheme = colorScheme) {
                            GameSidebarOverlay(
                                gameSideBar = gameSideBarProvider(),
                                overlayView = overlayView,
                                viewModel = viewModel,
                            )
                        }
                    }
                }
            }
        }

        return overlayView
    }

    private fun createPanelOverlay(): ComposeView {
        val overlayView = ComposeView(context)

        overlayView.apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnDetachedFromWindow
                    )
                    setContent {
                        val isDark = isSystemInDarkTheme()
                        val colorScheme = if (isDark) {
                            dynamicDarkColorScheme(context)
                        } else {
                            dynamicLightColorScheme(context)
                        }

                        val viewModel: GameBarViewModel = viewModel(factory = viewModelFactory)

                        MaterialTheme(colorScheme = colorScheme) {
                            Box(
                                modifier = Modifier.wrapContentSize(unbounded = true)
                            ) {
                                GamePanelOverlay(
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }

        return overlayView
    }
}

class OverlayManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val handler: Handler
) {
    private var sidebarParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var sidebarView: View? = null
    private var panelView: View? = null

    fun attachSidebar(view: View) {
        sidebarView = view
        sidebarParams = createSidebarParams()
        addView(view, sidebarParams!!)
    }

    fun attachPanel(view: View) {
        detachPanel()
        panelView = view
        panelParams = createPanelParams()
        addView(view, panelParams!!)
    }

    fun detachPanel() {
        panelView?.let { view ->
            removeView(view)
            panelView = null
            panelParams = null
        }
    }

    fun detachAll() {
        sidebarView?.let { removeView(it) }
        panelView?.let { removeView(it) }
        sidebarView = null
        panelView = null
        sidebarParams = null
        panelParams = null
    }

    private fun createSidebarParams(): WindowManager.LayoutParams {
        return createBaseParams().apply {
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            x = 1
        }
    }

    private fun createPanelParams(): WindowManager.LayoutParams {
        val sidebarWidth = 64.dp + 12.dp
        return createBaseParams().apply {
            x = sidebarWidth
        }
    }

    private fun createBaseParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            preferMinimalPostProcessing = true
            gravity = Gravity.TOP or Gravity.END
            y = context.safeArea
        }
    }

    private fun addView(view: View, params: WindowManager.LayoutParams) {
        runCatching {
            windowManager.addView(view, params)
        }
    }

    private fun removeView(view: View) {
        runCatching {
            if (view.isAttachedToWindow) {
                windowManager.removeViewImmediate(view)
            }
        }
    }
}
