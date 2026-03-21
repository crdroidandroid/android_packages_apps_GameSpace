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
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Process
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.window.TaskFpsCallback
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.*
import com.android.axion.compose.lifecycle.repeatWhenAttached
import com.android.axion.platform.AxPlatformClient
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.gamebar.brightness.*
import io.chaldeaprjkt.gamespace.gamebar.fps.*
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
    private val platform: AxPlatformClient
) {
    private val circleLayoutParam = createCircleLayoutParam()
    private val panelLayoutParam = createPanelLayoutParam()

    private var halfWidth = 0
    private var safeHeight = 0
    private var safeArea = 0
    private var shouldClose = false
    private var panelShowing = false

    private lateinit var circleView: FrameLayout
    private var circleIcon: ImageView? = null
    private var circleFpsText: TextView? = null
    private var panelView: View? = null

    private val firstPaint = Runnable { initActions() }

    private var circleOnLeft = false
    private var showFps = false
    private var circleIdleState = true

    private val panelDismissing = mutableStateOf(false)

    private val taskManager by lazy { ActivityTaskManager.getService() }

    private val taskFpsCallback = object : TaskFpsCallback() {
        override fun onFpsReported(fps: Float) {
            if (::circleView.isInitialized && circleView.isAttachedToWindow) {
                val formatted = DecimalFormat("#").apply {
                    roundingMode = RoundingMode.HALF_EVEN
                }.format(fps)
                handler.post {
                    circleFpsText?.text = formatted
                    updateCircleContent()
                }
            }
        }
    }

    private val recordingListener = object : AxPlatformClient.Listener() {
        override fun onStateChanged(key: String, state: Bundle) {}
    }

    @SuppressLint("ClickableViewAccessibility")
    fun onCreate() {
        val sizePx = circleSizePx
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x99000000.toInt())
        }

        circleIcon = ImageView(context).apply {
            setImageResource(R.drawable.materialsymbols_ic_sports_esports_rounded_filled)
            setColorFilter(0xFFFFFFFF.toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = (sizePx * 0.16f).toInt()
            setPadding(pad, pad, pad, pad)
        }

        circleFpsText = TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        circleView = FrameLayout(context).apply {
            background = bg
            layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
            addView(circleIcon, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(circleFpsText, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            ))

            setOnClickListener {
                circleIdleState = false
                updateCircleAlpha()
                showPanel()
            }

            setOnTouchListener(CircleDragTouchListener())
        }

        updateScreenMetrics()
        danmakuService.init()
    }

    fun onGameStart() {
        platform.addListener(recordingListener)
        handler.post {
            if (!::circleView.isInitialized) return@post
            runCatching {
                dockCircle()
                wm.addView(circleView, circleLayoutParam)
                circleView.visibility = View.INVISIBLE
                circleView.alpha = 0f
                handler.postDelayed(firstPaint, 500)
            }
        }
    }

    fun onGameLeave() {
        platform.removeListener(recordingListener)
        stopFpsTracking()
        shouldClose = true
        handler.removeCallbacksAndMessages(null)
        forceRemovePanel()
        runCatching { wm.removeViewImmediate(circleView) }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        updateScreenMetrics()
        forceRemovePanel()
        if (circleView.visibility != View.VISIBLE) {
            handler.removeCallbacks(firstPaint)
            handler.postDelayed({ firstPaint.run() }, 100)
        } else {
            dockCircle()
            runCatching { wm.updateViewLayout(circleView, circleLayoutParam) }
        }
        danmakuService.updateConfiguration(newConfig)
    }

    private fun showPanel() {
        if (panelShowing) return
        panelShowing = true
        panelDismissing.value = false

        tileRepository.refreshPlatformStates()
        fpsInteractor.start()
        brightnessInteractor.start()

        runCatching { wm.removeViewImmediate(circleView) }

        val pv = createPanelView()
        panelView = pv

        try {
            Process.setThreadGroupAndCpuset(Process.myPid(), Process.THREAD_GROUP_TOP_APP)
            Process.setProcessGroup(Process.myPid(), Process.THREAD_GROUP_TOP_APP)
            wm.addView(pv, panelLayoutParam)
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

        if (!shouldClose && ::circleView.isInitialized) {
            runCatching {
                if (!circleView.isAttachedToWindow) {
                    wm.addView(circleView, circleLayoutParam)
                }
            }
            circleView.alpha = 0f
            circleView.animate().alpha(0.7f).setDuration(200).start()
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

        val scrimAlpha by animateFloatAsState(
            targetValue = if (show) 0.45f else 0f,
            animationSpec = tween(if (show) 300 else 200),
            label = "scrim_alpha",
        )

        val panelAlpha by animateFloatAsState(
            targetValue = if (show) 1f else 0f,
            animationSpec = tween(if (show) 250 else 150),
            label = "panel_alpha",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { if (!dismissing) requestDismissPanel() },
            contentAlignment = if (circleOnLeft) Alignment.TopStart
                               else Alignment.TopEnd,
        ) {
            val density = LocalDensity.current.density
            val topOffsetDp = (circleLayoutParam.y / density).dp

            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = topOffsetDp, end = 12.dp)
                    .graphicsLayer {
                        translationX = slideOffset
                        alpha = panelAlpha
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
                )
            }
        }
    }

    private fun updateFpsTracking() {
        if (showFps) {
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

    private fun updateCircleContent() {
        if (showFps) {
            circleIcon?.visibility = View.GONE
            circleFpsText?.visibility = View.VISIBLE
        } else {
            circleIcon?.visibility = View.VISIBLE
            circleFpsText?.visibility = View.GONE
        }
    }

    private fun updateCircleAlpha() {
        val target = if (circleIdleState) 0.25f else 0.7f
        circleView.animate().cancel()
        circleView.animate().alpha(target).setDuration(400).start()
    }

    private val circleSizePx get() = (CIRCLE_SIZE_DP * context.resources.displayMetrics.density).toInt()

    private fun createCircleLayoutParam() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        width = circleSizePx
        height = circleSizePx
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

    private fun updateScreenMetrics() {
        val bounds = wm.maximumWindowMetrics.bounds
        halfWidth = bounds.width() / 2
        safeArea = context.statusbarHeight + (4 * context.resources.displayMetrics.density).toInt()
        safeHeight = bounds.height() - safeArea
    }

    private fun initActions() {
        if (shouldClose) return
        circleView.visibility = View.VISIBLE
        circleView.animate().alpha(0.7f).setDuration(300).start()
        circleOnLeft = appSettings.x < 0
        circleLayoutParam.y = appSettings.y
        dockCircle()
        runCatching { wm.updateViewLayout(circleView, circleLayoutParam) }
        showFps = appSettings.showFps
        updateCircleContent()
        updateFpsTracking()
        scheduleIdle()
    }

    private fun dockCircle() {
        circleLayoutParam.gravity = Gravity.TOP or (if (circleOnLeft) Gravity.START else Gravity.END)
        circleLayoutParam.x = 0
        circleLayoutParam.y = circleLayoutParam.y.coerceIn(safeArea, safeHeight)
    }

    private val idleRunnable = Runnable {
        circleIdleState = true
        updateCircleAlpha()
    }

    private fun scheduleIdle() {
        handler.removeCallbacks(idleRunnable)
        circleIdleState = false
        updateCircleAlpha()
        handler.postDelayed(idleRunnable, IDLE_TIMEOUT_MS)
    }

    private fun View.fadeIn(duration: Long = 300L) {
        animate().cancel()
        if (visibility != View.VISIBLE || alpha < 1f) {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(duration).start()
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
                    appList.add(AppInfo(name = appName, icon = icon, packageName = pkg))
                } catch (_: Exception) {}
            }
        }
        return appList
    }

    @SuppressLint("ClickableViewAccessibility")
    private inner class CircleDragTouchListener : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var startParamX = 0
        private var startParamY = 0
        private var isDragging = false
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isDragging = false
                    circleIdleState = false
                    updateCircleAlpha()
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (!isDragging && (dx * dx + dy * dy) > touchSlop * touchSlop) {
                        isDragging = true
                        val loc = IntArray(2)
                        circleView.getLocationOnScreen(loc)
                        circleLayoutParam.gravity = Gravity.TOP or Gravity.START
                        circleLayoutParam.x = loc[0]
                        circleLayoutParam.y = loc[1]
                        startParamX = loc[0]
                        startParamY = loc[1]
                        runCatching { wm.updateViewLayout(circleView, circleLayoutParam) }
                    }
                    if (isDragging) {
                        circleLayoutParam.x = startParamX + dx.toInt()
                        circleLayoutParam.y = startParamY + dy.toInt()
                        runCatching { wm.updateViewLayout(circleView, circleLayoutParam) }
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        circleOnLeft = circleLayoutParam.x < 0
                        appSettings.x = if (circleOnLeft) -1 else 1
                        appSettings.y = circleLayoutParam.y
                        dockCircle()
                        runCatching { wm.updateViewLayout(circleView, circleLayoutParam) }
                        scheduleIdle()
                        return true
                    }
                    scheduleIdle()
                    return false
                }
            }
            return false
        }
    }

    companion object {
        private const val CIRCLE_SIZE_DP = 36
        private const val IDLE_TIMEOUT_MS = 3000L
    }
}
