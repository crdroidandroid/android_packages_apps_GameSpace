/*
 * Copyright (C) 2025 AxionOS Project
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

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import com.android.internal.app.IGameSpaceCallback
import com.android.internal.app.IGameSpaceService
import kotlinx.coroutines.*

class GameSpaceService : Service() {

    private val TAG = "GameSpaceService"

    private val retryDelayMs = 2000L
    @Volatile private var serviceRegistered = false
    private var gameSpaceService: IGameSpaceService? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val callback = object : IGameSpaceCallback.Stub() {
        override fun shouldSuppressFullScreenIntent(suppress: Boolean) {}

        override fun onGameStart(packageName: String) {
            SessionService.start(applicationContext, packageName)
        }

        override fun onGameLeave() {
            serviceScope.launch {
                SessionService.stop(applicationContext)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created, registering GameSpace callback...")
        serviceScope.launch { tryRegister() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "game_start" -> {
                intent.getStringExtra("package_name")?.let {
                    SessionService.start(applicationContext, it)
                }
            }
            "game_stop" -> {
                SessionService.stop(applicationContext)
            }
        }
        return START_STICKY
    }

    private suspend fun tryRegister() {
        val service = withContext(Dispatchers.IO) {
            IGameSpaceService.Stub.asInterface(ServiceManager.getService("game_space"))
        }
        gameSpaceService = service

        if (service != null) {
            try {
                service.registerCallback(callback)
                serviceRegistered = true
                Log.i(TAG, "GameSpaceCallback registered successfully.")
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to register callback", e)
                serviceRegistered = false
                scheduleRetry()
            }
        } else {
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (serviceRegistered) return
        serviceScope.launch {
            delay(retryDelayMs)
            tryRegister()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            unregisterCallback()
        }
        serviceScope.cancel()
    }

    private suspend fun unregisterCallback() {
        if (serviceRegistered && gameSpaceService != null) {
            try {
                gameSpaceService?.unregisterCallback(callback)
                Log.i(TAG, "GameSpaceCallback unregistered.")
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to unregister callback", e)
            }
        }
        serviceRegistered = false
        gameSpaceService = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
