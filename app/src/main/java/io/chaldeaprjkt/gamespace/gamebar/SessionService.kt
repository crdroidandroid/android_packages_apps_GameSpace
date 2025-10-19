/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2024 crDroid Android Project
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
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.os.UserHandle
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.data.AppSettings
import io.chaldeaprjkt.gamespace.data.GameSession
import io.chaldeaprjkt.gamespace.data.SystemSettings
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

    private var isRunning = false
    private var tarketPkgName = ""

    private lateinit var gameManager: GameManager
    private lateinit var gameBar: GameBarService
    private var isBarConnected = false
    private var commandIntent: Intent? = null
    private var gameBarConnection: ServiceConnection? = null

    @SuppressLint("WrongConstant")
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        runCatching {
            screenUtils.bind()
        }.onFailure {
            Log.e(TAG, "Error binding ScreenUtils", it)
        }
        gameManager = getSystemService(Context.GAME_SERVICE) as GameManager
        gameModeUtils.bind(gameManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            Log.w(TAG, "Service is not properly initialized. Stopping.")
            stopServiceCleanup()
            return START_NOT_STICKY
        }

        intent?.let { commandIntent = it }
        super.onStartCommand(intent, flags, startId)

        if (intent == null && flags == 0 && startId > 1) {
            stopServiceCleanup()
            return tryStartFromDeath()
        }

        when (intent?.action) {
            START -> startGameBar()
            STOP -> {
                stopServiceCleanup()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun startGameBar() {
        if (isBarConnected) {
            Log.i(TAG, "GameBar is already connected.")
            return
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                isBarConnected = true
                gameBar = (service as GameBarService.GameBarBinder).getService()
                onGameBarReady()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isBarConnected = false
                gameBarConnection = null
                stopServiceCleanup()
            }
        }

        gameBarConnection = connection
        val success = bindServiceAsUser(Intent(this, GameBarService::class.java), connection, Context.BIND_AUTO_CREATE, UserHandle.CURRENT)
        if (!success) {
            Log.e(TAG, "Failed to bind GameBarService")
            gameBarConnection = null
            stopServiceCleanup()
        }
    }

    private fun onGameBarReady() {
        if (!isBarConnected) {
            Log.w(TAG, "GameBar is not connected. Retrying connection.")
            startGameBar()
            return
        }

        val intent = commandIntent ?: run {
            Log.e(TAG, "Command Intent is uninitialized. Stopping service.")
            stopServiceCleanup()
            return
        }

        val app = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            Log.e(TAG, "App package name missing in intent. Stopping service.")
            stopServiceCleanup()
            return
        }

        tarketPkgName = app
        session.unregister()
        session.register(app)
        applyGameModeConfig(app)

        gameBar.onGameStart()
        screenUtils.stayAwake = appSettings.stayAwake
        screenUtils.lockGesture = appSettings.lockGesture
        screenUtils.bypassCharge = true
        callListener.init()
    }

    private fun tryStartFromDeath(): Int {
        if (isBarConnected) return START_STICKY

        if (tarketPkgName.isBlank()) {
            Log.w(TAG, "Missing target package name. Cannot recover. Stopping.")
            stopServiceCleanup()
            return START_NOT_STICKY
        }

        commandIntent = Intent(START).putExtra(EXTRA_PACKAGE_NAME, tarketPkgName)
        startGameBar()
        return START_STICKY
    }

    private fun applyGameModeConfig(app: String) {
        val preferred = settings.userGames.firstOrNull { it.packageName == app }?.mode
            ?: GameModeUtils.defaultPreferredMode

        gameModeUtils.activeGame = settings.userGames.firstOrNull { it.packageName == app }

        val availableModes = gameManager.getAvailableGameModes(app)
        if (availableModes.contains(preferred)) {
            gameManager.setGameMode(app, preferred)
        }
    }

    private fun stopServiceCleanup() {
        unbindGameBar()
        session.unregister()
        gameModeUtils.unbind()
        screenUtils.unbind()
        callListener.destroy()
        tarketPkgName = ""
        isRunning = false
        stopSelf()
    }

    private fun unbindGameBar() {
        if (isBarConnected && gameBarConnection != null) {
            runCatching {
                unbindService(gameBarConnection!!)
            }.onFailure {
                Log.w(TAG, "GameBar already unbound or not bound", it)
            }.also {
                gameBarConnection = null
                isBarConnected = false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServiceCleanup()
        super.onDestroy()
    }

    companion object {
        const val TAG = "SessionService"
        const val START = "game_start"
        const val STOP = "game_stop"
        const val EXTRA_PACKAGE_NAME = "package_name"

        fun start(context: Context, app: String) = Intent(context, SessionService::class.java)
            .apply {
                action = START
                putExtra(EXTRA_PACKAGE_NAME, app)
            }
            .takeIf { !context.isServiceRunning(SessionService::class.java) }
            ?.run { context.startServiceAsUser(this, UserHandle.CURRENT) }

        fun stop(context: Context) = Intent(context, SessionService::class.java)
            .apply { action = STOP }
            .takeIf { context.isServiceRunning(SessionService::class.java) }
            ?.run { context.stopServiceAsUser(this, UserHandle.CURRENT) }
    }
}
