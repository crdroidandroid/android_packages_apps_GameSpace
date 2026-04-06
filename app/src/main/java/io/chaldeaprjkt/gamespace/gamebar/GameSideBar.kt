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

package io.chaldeaprjkt.gamespace.gamebar

import android.annotation.SuppressLint
import android.app.ActivityTaskManager
import android.content.*
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.view.*
import android.window.TaskFpsCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import com.android.axion.compose.lifecycle.repeatWhenAttached
import com.android.axion.platform.AxPlatformClient
import io.chaldeaprjkt.gamespace.BuildFlags
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.gamebar.brightness.*
import io.chaldeaprjkt.gamespace.gamebar.fps.*
import io.chaldeaprjkt.gamespace.gamebar.mapper.MapperController
import io.chaldeaprjkt.gamespace.gamebar.tiles.*
import io.chaldeaprjkt.gamespace.utils.*
import java.math.RoundingMode
import java.text.DecimalFormat

class GameSidebar(
    private val context: Context,
    private val wm: WindowManager,
    private val handler: Handler,
    private val appSettings: AppSettings,
    private val screenUtils: ScreenUtils,
    private val danmakuService: DanmakuService,
    private val brightnessInteractor: BrightnessInteractor,
    private val fpsInteractor: FpsInteractor,
    private val gameModeUtils: GameModeUtils,
    private val settings: SystemSettings,
    private val tileRepository: TileRepository,
    private val platform: AxPlatformClient,
    private val mapperController: MapperController,
) {
    private val gameBarLayoutParam = createGameBarLayoutParam()
    private val panelLayoutParam = createPanelLayoutParam()

    private var halfWidth = 0
    private var safeHeight = 0
    private var safeArea = 0
    private var shouldClose = false
    private var panelShowing = false

    private lateinit var gameBarView: ComposeView
    private var panelView: View? = null

    private val firstPaint = Runnable { initActions() }

    private var circleOnLeft = false
    private val dockedOnLeftState = mutableStateOf(false)

    private val noOpBackDispatcherOwner = object : OnBackPressedDispatcherOwner {
        override val onBackPressedDispatcher = OnBackPressedDispatcher()
        private val reg = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle get() = reg
    }

    private val panelDismissing = mutableStateOf(false)

    private val showFpsState = mutableStateOf(false)
    private val fpsTextState = mutableStateOf("")
    private val isLockedState = mutableStateOf(false)
    private val isIdleState = mutableStateOf(true)
    private val collapseRequestState = mutableIntStateOf(0)
    private var pillExpanded = false
    private val pillExpandedState = mutableStateOf(false)
    private val barTopState = mutableIntStateOf(0)
    private val taskManager by lazy { ActivityTaskManager.getService() }

    private val taskFpsCallback = object : TaskFpsCallback() {
        override fun onFpsReported(fps: Float) {
            if (::gameBarView.isInitialized && gameBarView.isAttachedToWindow) {
                val formatted = DecimalFormat("#").apply {
                    roundingMode = RoundingMode.HALF_EVEN
                }.format(fps)
                handler.post { fpsTextState.value = formatted }
            }
        }
    }

    private val recordingListener = object : AxPlatformClient.Listener() {
        override fun onStateChanged(key: String, state: Bundle) {}
    }

    fun onCreate() {
        gameBarView = ComposeView(context).apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        CompositionLocalProvider(
                            LocalOnBackPressedDispatcherOwner provides noOpBackDispatcherOwner,
                        ) {
                            MaterialExpressiveTheme(
                            colorScheme = dynamicDarkColorScheme(context),
                            motionScheme = MotionScheme.expressive(),
                        ) {
                            GameBarView(
                                showFps = showFpsState.value,
                                fpsText = fpsTextState.value,
                                isLocked = isLockedState.value,
                                isIdle = isIdleState.value,
                                idleAlpha = (appSettings.iconIdleAlpha / 100f).coerceAtLeast(0.05f),
                                dockedOnLeft = dockedOnLeftState.value,
                                mapperEnabled = BuildFlags.MAPPER_ENABLED,
                                onShowPanel = { handler.post { showPanel() } },
                                onMapControls = { handler.post { enterMapperEdit() } },
                                onToggleLock = {
                                    isLockedState.value = !isLockedState.value
                                    Settings.Secure.putIntForUser(
                                        context.contentResolver,
                                        "ax_gaming_gesture_lock",
                                        if (isLockedState.value) 1 else 0,
                                        UserHandle.USER_CURRENT
                                    )
                                    scheduleIdle()
                                },
                                onToggleFps = {
                                    showFpsState.value = !showFpsState.value
                                    appSettings.showFps = showFpsState.value
                                    updateFpsTracking()
                                    scheduleIdle()
                                },
                                pillExpanded = pillExpandedState.value,
                                barTopPx = barTopState.intValue,
                                onExpanded = { handler.post { setPillExpanded(true) } },
                                onCollapsed = {
                                    handler.post { setPillExpanded(false) }
                                    scheduleIdle()
                                },
                                collapseRequestKey = collapseRequestState.intValue,
                                onDragStart = {
                                    val loc = IntArray(2)
                                    gameBarView.getLocationOnScreen(loc)
                                    gameBarLayoutParam.gravity = Gravity.TOP or Gravity.START
                                    gameBarLayoutParam.x = loc[0]
                                    gameBarLayoutParam.y = loc[1]
                                    runCatching { wm.updateViewLayout(gameBarView, gameBarLayoutParam) }
                                    Pair(loc[0], loc[1])
                                },
                                onDragUpdate = { x, y ->
                                    gameBarLayoutParam.x = x
                                    gameBarLayoutParam.y = y
                                    runCatching { wm.updateViewLayout(gameBarView, gameBarLayoutParam) }
                                },
                                onDragEnd = { x, y ->
                                    circleOnLeft = x < halfWidth
                                    dockedOnLeftState.value = circleOnLeft
                                    appSettings.x = if (circleOnLeft) -1 else 1
                                    appSettings.y = y
                                    dockGameBar()
                                    runCatching { wm.updateViewLayout(gameBarView, gameBarLayoutParam) }
                                    scheduleIdle()
                                },
                            )
                        }
                        }
                    }
                }
            }
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                v.setSystemGestureExclusionRects(
                    listOf(Rect(0, 0, v.width, v.height))
                )
            }
        }

        updateScreenMetrics()
        danmakuService.init()
    }

    fun onGameStart(packageName: String) {
        if (BuildFlags.MAPPER_ENABLED) {
            mapperController.onGameStart(packageName)
        }
        platform.addListener(recordingListener)
        handler.post {
            if (!::gameBarView.isInitialized) return@post
            runCatching {
                dockGameBar()
                wm.addView(gameBarView, gameBarLayoutParam)
                gameBarView.visibility = View.INVISIBLE
                gameBarView.alpha = 0f
                handler.postDelayed(firstPaint, 500)
            }
        }
    }

    fun onGameLeave() {
        mapperController.onGameLeave()
        platform.removeListener(recordingListener)
        stopFpsTracking()
        shouldClose = true
        isLockedState.value = false
        Settings.Secure.putIntForUser(
            context.contentResolver,
            "ax_gaming_gesture_lock",
            0,
            UserHandle.USER_CURRENT
        )
        handler.removeCallbacksAndMessages(null)
        forceRemovePanel()
        runCatching { wm.removeViewImmediate(gameBarView) }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        mapperController.onConfigurationChanged(newConfig)
        updateScreenMetrics()
        forceRemovePanel()
        if (gameBarView.visibility != View.VISIBLE) {
            handler.removeCallbacks(firstPaint)
            handler.postDelayed({ firstPaint.run() }, 100)
        } else {
            dockGameBar()
            runCatching { wm.updateViewLayout(gameBarView, gameBarLayoutParam) }
        }
        danmakuService.updateConfiguration(newConfig)
    }

    private fun showPanel() {
        if (panelShowing) return
        panelShowing = true
        panelDismissing.value = false
        handler.removeCallbacks(idleRunnable)
        isIdleState.value = false

        tileRepository.refreshPlatformStates()
        fpsInteractor.start()
        brightnessInteractor.start()

        val pv = createPanelView()
        panelView = pv

        try {
            Process.setThreadGroupAndCpuset(Process.myPid(), Process.THREAD_GROUP_TOP_APP)
            Process.setProcessGroup(Process.myPid(), Process.THREAD_GROUP_TOP_APP)
            wm.addView(pv, panelLayoutParam)
            gameBarView.visibility = View.GONE
        } catch (_: Exception) {
            brightnessInteractor.dispose()
            fpsInteractor.dispose()
            panelShowing = false
        }
    }

    private fun requestDismissPanel() {
        panelDismissing.value = true
    }

    private fun removePanelAndRestoreCircle() {
        if (!panelShowing) return
        panelShowing = false

        brightnessInteractor.dispose()
        fpsInteractor.dispose()

        panelView?.let { pv ->
            runCatching { wm.removeViewImmediate(pv) }
            panelView = null
        }
        runCatching {
            Process.setThreadGroupAndCpuset(Process.myPid(), 9)
            Process.setProcessGroup(Process.myPid(), 9)
        }

        if (!shouldClose) {
            gameBarView.visibility = View.VISIBLE
            scheduleIdle()
        }
    }

    private fun forceRemovePanel() {
        if (!panelShowing) return
        panelShowing = false
        panelDismissing.value = true

        brightnessInteractor.dispose()
        fpsInteractor.dispose()

        panelView?.let { pv ->
            runCatching { wm.removeViewImmediate(pv) }
            panelView = null
        }
        if (::gameBarView.isInitialized) gameBarView.visibility = View.VISIBLE
    }

    private fun createPanelView(): ComposeView {
        return ComposeView(context).apply {
            repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        MaterialExpressiveTheme(
                            colorScheme = dynamicDarkColorScheme(context),
                            motionScheme = MotionScheme.expressive(),
                        ) {
                            PanelContent()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelContent() {
        val apps = remember { getQuickStartApps(context) }
        val dismissing by panelDismissing

        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }

        val show = entered && !dismissing
        val slideSign = if (circleOnLeft) -1f else 1f

        val slideOffset by animateFloatAsState(
            targetValue = if (show) 0f else slideSign * 400f,
            animationSpec = tween(
                durationMillis = if (show) 350 else 250,
                easing = if (show) FastOutSlowInEasing else FastOutLinearInEasing,
            ),
            label = "panel_slide",
            finishedListener = {
                if (dismissing) handler.post { removePanelAndRestoreCircle() }
            },
        )

        val panelAlpha by animateFloatAsState(
            targetValue = if (show) 1f else 0f,
            animationSpec = tween(if (show) 280 else 180),
            label = "panel_alpha",
        )

        val panelScale by animateFloatAsState(
            targetValue = if (show) 1f else 0.88f,
            animationSpec = tween(
                durationMillis = if (show) 350 else 220,
                easing = if (show) FastOutSlowInEasing else FastOutLinearInEasing,
            ),
            label = "panel_scale",
        )

        val density = LocalDensity.current
        val barTopPx = gameBarLayoutParam.y
        val barTopDp = with(density) { barTopPx.toDp() }
        val navBottomDp = with(density) {
            context.resources.getDimensionPixelSize(
                com.android.internal.R.dimen.navigation_bar_height
            ).toDp()
        }
        val screenHeightDp = with(density) { safeHeight.toDp() }
        val spaceBelow = (screenHeightDp - barTopDp - navBottomDp - 16.dp).coerceAtLeast(0.dp)
        val spaceAbove = (barTopDp - navBottomDp - 16.dp).coerceAtLeast(0.dp)
        val bottomAligned = spaceBelow < 240.dp && spaceAbove > spaceBelow
        val panelMaxHeight = (if (bottomAligned) spaceAbove else spaceBelow).coerceIn(240.dp, 520.dp)
        val alignment = when {
            bottomAligned && circleOnLeft -> Alignment.BottomStart
            bottomAligned && !circleOnLeft -> Alignment.BottomEnd
            !bottomAligned && circleOnLeft -> Alignment.TopStart
            else -> Alignment.TopEnd
        }
        val transformOriginX = if (circleOnLeft) 0f else 1f
        val transformOriginY = if (bottomAligned) 1f else 0f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (!dismissing) requestDismissPanel() },
            contentAlignment = alignment,
        ) {
            val edgePadding = if (bottomAligned) navBottomDp + 8.dp else barTopDp
            Box(
                modifier = Modifier
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = if (!bottomAligned) edgePadding else 0.dp,
                        bottom = if (bottomAligned) edgePadding else 0.dp,
                    )
                    .graphicsLayer {
                        translationX = slideOffset
                        alpha = panelAlpha
                        scaleX = panelScale
                        scaleY = panelScale
                        transformOrigin = TransformOrigin(transformOriginX, transformOriginY)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
            ) {
                GamePanelCard(
                    interactor = brightnessInteractor,
                    fpsInteractor = fpsInteractor,
                    apps = apps,
                    gameModeUtils = gameModeUtils,
                    systemSettings = settings,
                    tileRepository = tileRepository,
                    maxHeight = panelMaxHeight,
                )
            }
        }
    }

    private fun setPillExpanded(expanded: Boolean) {
        if (pillExpanded == expanded) return
        pillExpanded = expanded
        pillExpandedState.value = expanded
        if (expanded) {
            barTopState.intValue = gameBarLayoutParam.y
            gameBarLayoutParam.width = WindowManager.LayoutParams.MATCH_PARENT
            gameBarLayoutParam.height = WindowManager.LayoutParams.MATCH_PARENT
            gameBarLayoutParam.flags = gameBarLayoutParam.flags and
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        } else {
            gameBarLayoutParam.width = WindowManager.LayoutParams.WRAP_CONTENT
            gameBarLayoutParam.height = WindowManager.LayoutParams.WRAP_CONTENT
            gameBarLayoutParam.flags = gameBarLayoutParam.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            dockGameBar()
        }
        runCatching { wm.updateViewLayout(gameBarView, gameBarLayoutParam) }
    }

    private fun collapseVerticalPill() {
        if (!pillExpanded) return
        collapseRequestState.intValue = collapseRequestState.intValue + 1
    }

    private fun updateFpsTracking() {
        if (showFpsState.value) {
            taskManager?.focusedRootTaskInfo?.taskId?.let {
                wm.registerTaskFpsCallback(it, Runnable::run, taskFpsCallback)
            }
        } else {
            stopFpsTracking()
        }
    }

    private fun stopFpsTracking() {
        runCatching { wm.unregisterTaskFpsCallback(taskFpsCallback) }
    }

    private fun updateScreenMetrics() {
        val bounds = wm.maximumWindowMetrics.bounds
        halfWidth = bounds.width() / 2
        safeArea = context.statusbarHeight + (4 * context.resources.displayMetrics.density).toInt()
        safeHeight = bounds.height() - safeArea
    }

    private fun initActions() {
        if (shouldClose) return
        gameBarView.visibility = View.VISIBLE
        gameBarView.animate().alpha(1f).setDuration(300).start()
        circleOnLeft = appSettings.x < 0
        dockedOnLeftState.value = circleOnLeft
        gameBarLayoutParam.y = appSettings.y
        dockGameBar()
        runCatching { wm.updateViewLayout(gameBarView, gameBarLayoutParam) }
        showFpsState.value = appSettings.showFps
        updateFpsTracking()
        scheduleIdle()
    }

    private fun dockGameBar() {
        gameBarLayoutParam.gravity = Gravity.TOP or (if (circleOnLeft) Gravity.START else Gravity.END)
        gameBarLayoutParam.x = 0
        gameBarLayoutParam.y = gameBarLayoutParam.y.coerceIn(safeArea, safeHeight)
    }

    private val idleRunnable = Runnable {
        isIdleState.value = true
    }

    private fun enterMapperEdit() {
        forceRemovePanel()
        gameBarView.animate().alpha(0f).setDuration(150).withEndAction {
            gameBarView.visibility = View.GONE
        }.start()
        mapperController.onEditStarted = null
        mapperController.onEditFinished = {
            handler.post {
                gameBarView.visibility = View.VISIBLE
                gameBarView.animate().alpha(1f).setDuration(200).start()
                scheduleIdle()
            }
        }
        mapperController.enterEditMode()
    }

    private fun scheduleIdle() {
        handler.removeCallbacks(idleRunnable)
        isIdleState.value = false
        handler.postDelayed(idleRunnable, IDLE_TIMEOUT_MS)
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
                    appList.add(AppInfo(name = appName, icon = icon, packageName = pkg))
                } catch (_: Exception) {}
            }
        }
        return appList
    }

    private fun createGameBarLayoutParam() = WindowManager.LayoutParams(
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
        gravity = Gravity.TOP or Gravity.END
    }

    private fun createPanelLayoutParam() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        preferMinimalPostProcessing = true
    }

    companion object {
        private const val IDLE_TIMEOUT_MS = 3000L
    }
}
