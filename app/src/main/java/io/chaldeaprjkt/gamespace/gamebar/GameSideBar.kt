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

import android.content.*
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Handler
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp as composeDp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.*
import androidx.lifecycle.*
import com.android.systemui.screenrecord.IRecordingCallback
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.gamebar.fps.*
import io.chaldeaprjkt.gamespace.settings.SettingsActivity
import io.chaldeaprjkt.gamespace.widget.MenuSwitcher
import io.chaldeaprjkt.gamespace.gamebar.brightness.*
import io.chaldeaprjkt.gamespace.gamebar.lifecycle.repeatWhenAttached
import io.chaldeaprjkt.gamespace.gamebar.tiles.*
import io.chaldeaprjkt.gamespace.utils.*

class GameSidebar(
    private val context: Context,
    private val wm: WindowManager,
    private val handler: Handler,
    private val inflater: LayoutInflater,
    private val appSettings: AppSettings,
    private val screenUtils: ScreenUtils,
    private val danmakuService: DanmakuService,
    private val brightnessInteractor: BrightnessInteractor,
    private val fpsInteractor: FpsInteractor,
    private val gameModeUtils: GameModeUtils,
    private val settings: SystemSettings,
    private val tileRepository: TileRepository
) {
    private val barLayoutParam = createBarLayoutParam()
    private val panelLayoutParam = createPanelLayoutParam()

    private var halfWidth = 0
    private var safeHeight = 0
    private var safeArea = 0
    private var shouldClose = false

    private lateinit var rootBarView: View
    private lateinit var barView: LinearLayout
    private lateinit var menuSwitcher: MenuSwitcher
    private lateinit var panelView: View

    private val firstPaint = Runnable { initActions() }

    private var barExpanded = false
        set(value) {
            field = value
            menuSwitcher.updateIconState(value, barLayoutParam.x)
            barView.children.forEach { if (it.id != R.id.action_menu_switcher) it.isVisible = value }
            updateBackground()
            updateContainerGaps()
        }

    private var showPanel = false
        set(value) {
            field = value
            if (value) {
                setupPanelView()
                fpsInteractor.start()
                brightnessInteractor.start()
                addViewSafely(panelView, panelLayoutParam)
                panelView.fadeIn()
            } else {
                panelView.fadeOut {
                    brightnessInteractor.dispose()
                    fpsInteractor.dispose()
                    panelView.visibility = View.INVISIBLE
                    handler.postDelayed({ runCatching { removeViewSafely(panelView) } }, 50)
                }
            }
        }


    fun onCreate() {
        val frame = FrameLayout(context)
        rootBarView = inflater.inflate(R.layout.window_util, frame, false)
        barView = rootBarView.findViewById(R.id.container_bar)
        menuSwitcher = rootBarView.findViewById(R.id.action_menu_switcher)
        applyOpacity()
        updateScreenMetrics()
        danmakuService.init()
    }

    fun onGameStart() {
        handler.post {
            if (!::rootBarView.isInitialized) return@post
            runCatching {
                wm.addView(rootBarView, barLayoutParam)
                rootBarView.isVisible = false
                rootBarView.alpha = 0f
                handler.postDelayed(firstPaint, 500)
            }
        }
    }

    fun onGameLeave() {
        shouldClose = true
        handler.removeCallbacksAndMessages(null)
        runCatching { wm.removeViewImmediate(panelView) }
        runCatching { wm.removeViewImmediate(rootBarView) }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        updateScreenMetrics()
        if (!rootBarView.isVisible) {
            handler.removeCallbacks(firstPaint)
            handler.postDelayed({
                firstPaint.run()
                dockCollapsedMenu()
            }, 100)
        } else {
            dockCollapsedMenu()
        }
        danmakuService.updateConfiguration(newConfig)
    }

    private fun createBarLayoutParam() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        preferMinimalPostProcessing = true
        gravity = Gravity.TOP
    }

    private fun createPanelLayoutParam() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        preferMinimalPostProcessing = true
        gravity = Gravity.TOP
    }

    private fun updateScreenMetrics() {
        val bounds = wm.maximumWindowMetrics.bounds
        halfWidth = bounds.width() / 2
        safeArea = context.statusbarHeight + 4.dp
        safeHeight = bounds.height() - safeArea
    }

    private fun applyOpacity() {
        val alphaValue = appSettings.menuOpacity / 100f
        barView.alpha = alphaValue
        menuSwitcher.alpha = alphaValue
    }

    private fun updateLayout(update: WindowManager.LayoutParams.() -> Unit = {}) {
        barLayoutParam.update()
        runCatching { wm.updateViewLayout(rootBarView, barLayoutParam) }
    }

    private fun initActions() {
        if (shouldClose) return
        rootBarView.fadeIn()
        barExpanded = false
        barLayoutParam.x = appSettings.x
        barLayoutParam.y = appSettings.y
        dockCollapsedMenu()
        setupButtons()
    }

    private fun updateBackground() {
        val barDragged = !barExpanded && barView.translationX == 0f
        val collapsedAtStart = !barDragged && barLayoutParam.x < 0
        val collapsedAtEnd = !barDragged && barLayoutParam.x > 0
        barView.setBackgroundResource(
            when {
                barExpanded -> R.drawable.bar_expanded
                collapsedAtStart -> R.drawable.bar_collapsed_start
                collapsedAtEnd -> R.drawable.bar_collapsed_end
                else -> R.drawable.bar_dragged
            }
        )
    }

    private fun updateContainerGaps() {
        val currentParams = barView.layoutParams as ViewGroup.MarginLayoutParams
        val margin = if (barExpanded) 48 else 0
        barView.updatePaddingRelative(
            start = if (barExpanded) 8 else 0,
            top = if (barExpanded) 8 else 0,
            end = if (barExpanded) 8 else 0,
            bottom = if (barExpanded) 8 else 0
        )
        currentParams.setMargins(margin, 0, margin, 0)
        barView.layoutParams = currentParams
    }

    private fun dockCollapsedMenu() {
        if (barLayoutParam.x < 0) {
            barView.translationX = -22f
            barLayoutParam.x = -halfWidth
        } else {
            barView.translationX = 22f
            barLayoutParam.x = halfWidth
        }
        barLayoutParam.y = barLayoutParam.y.coerceIn(safeArea, safeHeight)
        updateBackground()
        updateContainerGaps()
        menuSwitcher.showFps = if (barExpanded) false else appSettings.showFps
        menuSwitcher.updateIconState(barExpanded, barLayoutParam.x)
        runCatching { wm.updateViewLayout(rootBarView, barLayoutParam) }
    }

    private fun setupPanelView() {
        panelView = ComposeView(context).apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                    )
                    setContent {
                        val isDark = isSystemInDarkTheme()
                        val colorScheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                        val panelOnLeft = barLayoutParam.x < halfWidth
                        val apps = remember { getQuickStartApps(context) }
                        MaterialTheme(colorScheme = colorScheme) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { showPanel = false }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = if (panelOnLeft) 60.composeDp else (-60).composeDp)
                                        .align(if (panelOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                                ) {
                                    GamePanelCard(
                                        interactor = brightnessInteractor,
                                        fpsInteractor = fpsInteractor,
                                        apps = apps,
                                        gameModeUtils = gameModeUtils,
                                        systemSettings = settings,
                                        tileRepository = tileRepository
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        panelLayoutParam.x = barLayoutParam.x
        panelLayoutParam.y = appSettings.y
    }

    private fun setupButtons() {
        menuSwitcher.setOnClickListener { 
            barExpanded = !barExpanded 
            if (!barExpanded) {
                showPanel = false
            }
        }

        menuSwitcher.registerDraggableTouchListener(
            initPoint = { Point(barLayoutParam.x, barLayoutParam.y) },
            listener = { x: Int, y: Int ->
                if (!menuSwitcher.isDragged) {
                    menuSwitcher.isDragged = true
                    barView.translationX = 0f
                }
                updateLayout {
                    this.x = x
                    this.y = y
                }
                updateBackground()
            },
            onComplete = {
                menuSwitcher.isDragged = false
                dockCollapsedMenu()
                updateBackground()
                appSettings.x = barLayoutParam.x
                appSettings.y = barLayoutParam.y
            }
        )

        rootBarView.findViewById<ImageButton>(R.id.action_panel).apply {
            alpha = appSettings.menuOpacity / 100f
            setOnClickListener { showPanel = !showPanel }
            setOnLongClickListener {
                context.startActivity(Intent(context, SettingsActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        }

        rootBarView.findViewById<ImageButton>(R.id.action_screenshot).apply {
            alpha = appSettings.menuOpacity / 100f
            setOnClickListener { takeShot() }
        }

        rootBarView.findViewById<ImageButton>(R.id.action_record).apply {
            alpha = appSettings.menuOpacity / 100f
            val recorder = screenUtils.recorder ?: run {
                isVisible = false
                return
            }

            recorder.addRecordingCallback(object : IRecordingCallback.Stub() {
                override fun onRecordingStart() { handler.post { isSelected = true } }
                override fun onRecordingEnd() { handler.post { isSelected = false } }
            })

            setOnClickListener {
                if (!recorder.isStarting) {
                    if (!recorder.isRecording) recorder.startRecording() else recorder.stopRecording()
                    barExpanded = false
                }
            }
        }
    }

    private fun takeShot() {
        val afterShot = {
            barExpanded = false
            handler.postDelayed({ updateLayout { alpha = 1f } }, 100)
        }
        updateLayout { alpha = 0f }
        handler.postDelayed({
            runCatching {
                screenUtils.takeScreenshot { afterShot() }
            }.onFailure {
                it.printStackTrace()
                afterShot()
            }
        }, 250)
    }

    private fun View.fadeIn(duration: Long = 300L) {
        animate().cancel()
        if (!isVisible || alpha < 1f) {
            alpha = 0f
            isVisible = true
            animate().alpha(1f).setDuration(duration).start()
        }
    }

    private fun View.fadeOut(duration: Long = 300L, endAction: () -> Unit = {}) {
        animate().cancel()
        if (isVisible && alpha > 0f) {
            animate()
                .alpha(0f)
                .setDuration(duration)
                .withEndAction {
                    if (isAttachedToWindow) isVisible = false
                    endAction()
                }.start()
        } else {
            endAction()
        }
    }
    
    private fun addViewSafely(view: View, lp: WindowManager.LayoutParams) {
        try {
            wm.addView(view, lp)
        } catch (e: Exception) {
            brightnessInteractor.dispose()
            fpsInteractor.dispose()
        }
    }

    private fun removeViewSafely(view: View) {
        try {
            if (view.isAttachedToWindow) {
                wm.removeViewImmediate(view)
            }
        } catch (e: Exception) {
            brightnessInteractor.dispose()
            fpsInteractor.dispose()
        }
    }
    
    private fun getQuickStartApps(context: Context): List<AppInfo> {
        val appList = mutableListOf<AppInfo>()
        val packageManager = context.packageManager
        if (appSettings.quickStartApps.isNullOrEmpty() == false) {
            val packages = appSettings.quickStartApps.split(",")
            for (pkg in packages) {
                try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    appList.add(
                        AppInfo(
                            name = appName,
                            icon = icon,
                            packageName = pkg
                        )
                    )
                } catch (_: Exception) {}
            }
        }
        return appList
    }
}
