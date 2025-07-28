/*
 * Copyright (C) 2021 Chaldeaprjkt
 *               2022 crDroid Android Project
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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import androidx.core.view.*
import com.android.systemui.screenrecord.IRecordingCallback
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.settings.SettingsActivity
import io.chaldeaprjkt.gamespace.utils.*
import io.chaldeaprjkt.gamespace.widget.MenuSwitcher
import io.chaldeaprjkt.gamespace.widget.PanelView
import javax.inject.Inject

@AndroidEntryPoint(Service::class)
class GameBarService : Hilt_GameBarService() {

    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var screenUtils: ScreenUtils
    @Inject lateinit var danmakuService: DanmakuService

    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val inflater by lazy { LayoutInflater.from(this) }

    private var halfWidth = 0
    private var safeHeight = 0
    private var safeArea = 0

    private val barLayoutParam = WindowManager.LayoutParams(
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

    private val panelLayoutParam = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        gravity = Gravity.CENTER_VERTICAL
    }

    private lateinit var rootBarView: View
    private lateinit var barView: LinearLayout
    private lateinit var menuSwitcher: MenuSwitcher
    private lateinit var rootPanelView: LinearLayout
    private lateinit var panelView: PanelView

    private val binder = GameBarBinder()
    private val firstPaint = Runnable { initActions() }
    private var shouldClose = false
    private var isGameStarting = false

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
                if (!::rootPanelView.isInitialized) {
                    setupPanelView()
                }
                if (!rootPanelView.isAttachedToWindow) {
                    runCatching { wm.addView(rootPanelView, panelLayoutParam) }
                    rootPanelView.fadeIn()
                }
            } else {
                runCatching {
                    rootPanelView.fadeOut {
                        rootPanelView.visibility = View.INVISIBLE
                        handler.postDelayed({
                            runCatching { wm.removeView(rootPanelView) }
                        }, 50)
                    }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        val frame = FrameLayout(this)
        rootBarView = inflater.inflate(R.layout.window_util, frame, false)
        barView = rootBarView.findViewById(R.id.container_bar)
        menuSwitcher = rootBarView.findViewById(R.id.action_menu_switcher)
        applyOpacity()
        updateScreenMetrics()
        danmakuService.init()
    }

    private fun updateScreenMetrics() {
        val bounds = wm.maximumWindowMetrics.bounds
        halfWidth = bounds.width() / 2
        safeArea = statusbarHeight + 4.dp
        safeHeight = bounds.height() - safeArea
    }

    private fun applyOpacity() {
        val alphaValue = appSettings.menuOpacity / 100f
        barView.alpha = alphaValue
        menuSwitcher.alpha = alphaValue
    }

    private fun startForegroundService() {
        val channelId = "gamespace_service_channel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Game Space Service", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.gamespace_running))
            .setSmallIcon(R.drawable.ic_gear)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> onGameLeave()
            ACTION_START -> onGameStart()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent) = binder

    inner class GameBarBinder : Binder() {
        fun getService() = this@GameBarService
    }

    override fun onDestroy() {
        onGameLeave()
        danmakuService.destroy()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
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

    fun onGameStart() {
        if (isGameStarting) return
        isGameStarting = true

        handler.post {
            if (!::rootBarView.isInitialized) return@post
            runCatching {
                wm.addView(rootBarView, barLayoutParam)
                rootBarView.isVisible = false
                rootBarView.alpha = 0f
                handler.postDelayed(firstPaint, 500)
                startForegroundService()
            }.onFailure { it.printStackTrace() }
            isGameStarting = false
        }
    }

    fun onGameLeave() {
        shouldClose = true
        handler.removeCallbacksAndMessages(null)

        runCatching { wm.removeViewImmediate(rootPanelView) }
        runCatching { wm.removeViewImmediate(rootBarView) }

        stopForeground(true)
        stopSelf()
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
        menuSwitcherButton()
        panelButton()
        screenshotButton()
        recorderButton()
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
        val targetLeft = if (barExpanded) 48 else 0
        val targetRight = if (barExpanded) 48 else 0

        if (currentParams.leftMargin != targetLeft || currentParams.rightMargin != targetRight) {
            barView.updatePaddingRelative(
                start = if (barExpanded) 8 else 0,
                top = if (barExpanded) 8 else 0,
                end = if (barExpanded) 8 else 0,
                bottom = if (barExpanded) 8 else 0
            )
            currentParams.setMargins(targetLeft, 0, targetRight, 0)
            barView.layoutParams = currentParams
        }
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
        rootPanelView = inflater.inflate(R.layout.window_panel, FrameLayout(this), false) as LinearLayout
        rootPanelView.alpha = 0f
        rootPanelView.visibility = View.INVISIBLE

        panelView = rootPanelView.findViewById(R.id.panel_view)
        panelView.alpha = appSettings.menuOpacity / 100f

        rootPanelView.setOnClickListener { showPanel = false }

        val barWidth = barView.width + barView.marginStart
        if (barLayoutParam.x < 0) {
            rootPanelView.gravity = Gravity.START
            rootPanelView.setPaddingRelative(barWidth, 16, 16, 16)
        } else {
            rootPanelView.gravity = Gravity.END
            rootPanelView.setPaddingRelative(16, 16, barWidth, 16)
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

    private fun menuSwitcherButton() {
        menuSwitcher.setOnClickListener { barExpanded = !barExpanded }

        menuSwitcher.registerDraggableTouchListener(
            initPoint = { Point(barLayoutParam.x, barLayoutParam.y) },
            listener = { x, y ->
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
    }

    private fun panelButton() {
        val actionPanel = rootBarView.findViewById<ImageButton>(R.id.action_panel)
        actionPanel.alpha = appSettings.menuOpacity / 100f
        actionPanel.setOnClickListener { showPanel = !showPanel }
        actionPanel.setOnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }
    }

    private fun screenshotButton() {
        rootBarView.findViewById<ImageButton>(R.id.action_screenshot).apply {
            alpha = appSettings.menuOpacity / 100f
            setOnClickListener { takeShot() }
        }
    }

    private fun recorderButton() {
        val actionRecorder = rootBarView.findViewById<ImageButton>(R.id.action_record)
        actionRecorder.alpha = appSettings.menuOpacity / 100f
        val recorder = screenUtils.recorder ?: run {
            actionRecorder.isVisible = false
            return
        }

        recorder.addRecordingCallback(object : IRecordingCallback.Stub() {
            override fun onRecordingStart() {
                handler.post { actionRecorder.isSelected = true }
            }

            override fun onRecordingEnd() {
                handler.post { actionRecorder.isSelected = false }
            }
        })

        actionRecorder.setOnClickListener {
            if (recorder.isStarting) return@setOnClickListener
            if (!recorder.isRecording) recorder.startRecording() else recorder.stopRecording()
            barExpanded = false
        }
    }

    fun View.fadeIn(duration: Long = 300L) {
        animate().cancel()
        if (!isVisible || alpha < 1f) {
            alpha = 0f
            isVisible = true
            animate().alpha(1f).setDuration(duration).start()
        }
    }

    fun View.fadeOut(duration: Long = 300L, endAction: () -> Unit = {}) {
        animate().cancel()
        if (isVisible && alpha > 0f) {
            animate()
                .alpha(0f)
                .setDuration(duration)
                .withEndAction {
                    if (isAttachedToWindow) isVisible = false
                    endAction()
                }
                .start()
        } else {
            endAction()
        }
    }

    companion object {
        const val TAG = "GameBar"
        const val ACTION_START = "GameBar.ACTION_START"
        const val ACTION_STOP = "GameBar.ACTION_STOP"
    }
}
