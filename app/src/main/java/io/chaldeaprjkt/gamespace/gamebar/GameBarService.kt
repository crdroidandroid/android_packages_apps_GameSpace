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

import android.app.*
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.*
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.gamebar.brightness.*
import io.chaldeaprjkt.gamespace.gamebar.fps.*
import io.chaldeaprjkt.gamespace.gamebar.tiles.*
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import io.chaldeaprjkt.gamespace.utils.ScreenUtils
import javax.inject.Inject
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.core.app.NotificationCompat

@AndroidEntryPoint(Service::class)
class GameBarService : Hilt_GameBarService() {

    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var screenUtils: ScreenUtils
    @Inject lateinit var danmakuService: DanmakuService
    @Inject lateinit var brightnessInteractor: BrightnessInteractor
    @Inject lateinit var fpsInteractor: FpsInteractor
    @Inject lateinit var gameModeUtils: GameModeUtils
    @Inject lateinit var settings: SystemSettings
    @Inject lateinit var tileRepository: TileRepository

    private lateinit var sidebar: GameSidebar

    private val binder = GameBarBinder()

    override fun onCreate() {
        super.onCreate()
        sidebar = GameSidebar(
            context = this,
            wm = getSystemService(WINDOW_SERVICE) as WindowManager,
            handler = Handler(Looper.getMainLooper()),
            inflater = LayoutInflater.from(this),
            appSettings = appSettings,
            screenUtils = screenUtils,
            danmakuService = danmakuService,
            brightnessInteractor = brightnessInteractor,
            fpsInteractor = fpsInteractor,
            gameModeUtils = gameModeUtils,
            settings = settings,
            tileRepository = tileRepository
        )
        sidebar.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> sidebar.onGameLeave()
            ACTION_START -> {
                sidebar.onGameStart()
                startForegroundService()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "gamespace_service_channel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Game Space Service",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
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

    override fun onBind(intent: Intent) = binder

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        sidebar.onConfigurationChanged(newConfig)
    }

    fun onGameStart() {
        sidebar.onGameStart()
    }

    fun onGameLeave() {
        sidebar.onGameLeave()
    }

    override fun onDestroy() {
        sidebar.onGameLeave()
        danmakuService.destroy()
        super.onDestroy()
    }

    inner class GameBarBinder : Binder() {
        fun getService() = this@GameBarService
    }

    companion object {
        const val TAG = "GameBar"
        const val ACTION_START = "GameBar.ACTION_START"
        const val ACTION_STOP = "GameBar.ACTION_STOP"
    }
}
