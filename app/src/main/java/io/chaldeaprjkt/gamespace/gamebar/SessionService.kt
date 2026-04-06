/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2024 crDroid Android Project
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

import android.annotation.SuppressLint
import android.app.GameManager
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import android.view.WindowManager
import com.android.axion.platform.AxPlatformClient
import dagger.hilt.android.AndroidEntryPoint
import com.google.gson.Gson
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.GameSession
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.gamebar.brightness.BrightnessInteractor
import io.chaldeaprjkt.gamespace.gamebar.fps.FpsInteractor
import io.chaldeaprjkt.gamespace.gamebar.mapper.MapperController
import io.chaldeaprjkt.gamespace.gamebar.tiles.TileRepository
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import io.chaldeaprjkt.gamespace.utils.ScreenUtils
import io.chaldeaprjkt.gamespace.utils.isServiceRunning
import javax.inject.Inject

@AndroidEntryPoint(Service::class)
class SessionService : Hilt_SessionService() {
    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var settings: SystemSettings
    @Inject lateinit var session: GameSession
    @Inject lateinit var screenUtils: ScreenUtils
    @Inject lateinit var gameModeUtils: GameModeUtils
    @Inject lateinit var callListener: CallListener
    @Inject lateinit var danmakuService: DanmakuService
    @Inject lateinit var brightnessInteractor: BrightnessInteractor
    @Inject lateinit var fpsInteractor: FpsInteractor
    @Inject lateinit var tileRepository: TileRepository
    @Inject lateinit var gson: Gson

    private var currentPackage: String? = null
    private lateinit var gameManager: GameManager
    private lateinit var sidebar: GameSidebar
    private lateinit var mapperController: MapperController
    private lateinit var platform: AxPlatformClient

    private var dndEnabledByUs = false
    private var previousDndFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    @SuppressLint("WrongConstant")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SessionService created")

        platform = AxPlatformClient.getInstance()
        platform.init(this)

        gameManager = getSystemService(Context.GAME_SERVICE) as GameManager
        gameModeUtils.bind(gameManager)

        tileRepository.init(platform)

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val mainHandler = Handler(Looper.getMainLooper())

        mapperController = MapperController(
            context = this,
            wm = windowManager,
            handler = mainHandler,
            gson = gson,
        )

        sidebar = GameSidebar(
            context = this,
            wm = windowManager,
            handler = mainHandler,
            appSettings = appSettings,
            screenUtils = screenUtils,
            danmakuService = danmakuService,
            brightnessInteractor = brightnessInteractor,
            fpsInteractor = fpsInteractor,
            gameModeUtils = gameModeUtils,
            settings = settings,
            tileRepository = tileRepository,
            platform = platform,
            mapperController = mapperController,
        )
        sidebar.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (packageName != null) {
                    startGameSession(packageName)
                } else {
                    Log.e(TAG, "No package name provided, stopping")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopGameSession()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        sidebar.onConfigurationChanged(newConfig)
    }

    private fun startGameSession(packageName: String) {
        if (currentPackage == packageName) {
            Log.d(TAG, "Session already active for $packageName")
            return
        }
        
        if (currentPackage != null) {
            stopGameSession()
        }
        
        Log.i(TAG, "Starting game session for $packageName")
        currentPackage = packageName
        
        session.unregister()
        session.register(packageName)
        
        applyGameModeConfig(packageName)
        
        applyAutoDnd()

        sidebar.onGameStart(packageName)

        callListener.init()
    }

    private fun stopGameSession() {
        Log.i(TAG, "Stopping game session")

        sidebar.onGameLeave()
        session.unregister()
        callListener.destroy()
        restoreAutoDnd()

        currentPackage = null
    }

    private fun applyAutoDnd() {
        if (!appSettings.autoDnd) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val currentFilter = nm.currentInterruptionFilter
        if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
            currentFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            previousDndFilter = currentFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            dndEnabledByUs = true
        }
    }

    private fun restoreAutoDnd() {
        if (!dndEnabledByUs) return
        dndEnabledByUs = false
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.setInterruptionFilter(previousDndFilter)
    }

    private fun applyGameModeConfig(app: String) {
        val userGame = settings.userGames.firstOrNull { it.packageName == app }
        val preferred = userGame?.mode ?: GameModeUtils.defaultPreferredMode
        
        gameModeUtils.activeGame = userGame
        
        val availableModes = gameManager.getAvailableGameModes(app)
        if (availableModes.contains(preferred)) {
            gameManager.setGameMode(app, preferred)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "SessionService destroyed")
        stopGameSession()
        tileRepository.dispose()
        gameModeUtils.unbind()
        danmakuService.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "SessionService"
        const val ACTION_START = "game_start"
        const val ACTION_STOP = "game_stop"
        const val EXTRA_PACKAGE_NAME = "package_name"

        fun start(context: Context, app: String) {
            if (!context.isServiceRunning(SessionService::class.java)) {
                Intent(context, SessionService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_PACKAGE_NAME, app)
                }.let {
                    context.startServiceAsUser(it, UserHandle.CURRENT)
                }
            }
        }

        fun stop(context: Context) {
            if (context.isServiceRunning(SessionService::class.java)) {
                Intent(context, SessionService::class.java).apply {
                    action = ACTION_STOP
                }.let {
                    context.startServiceAsUser(it, UserHandle.CURRENT)
                }
            }
        }
    }
}
