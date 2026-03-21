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
import android.os.Process
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import com.android.internal.app.IGameSpaceCallback
import com.android.internal.app.IGameSpaceService

class GameSpaceService : Service() {

    private val TAG = "GameSpaceService"
    
    private var gameSpaceService: IGameSpaceService? = null

    private val callback = object : IGameSpaceCallback.Stub() {
        override fun onGameStart(packageName: String) {
            Log.d(TAG, "Game started: $packageName")
            SessionService.start(applicationContext, packageName)
            Process.setThreadAffinity(Process.myPid(), 2)
        }

        override fun onGameLeave() {
            Log.d(TAG, "Game left")
            SessionService.stop(applicationContext)
            Process.setThreadAffinity(Process.myPid(), 1)
            Process.setThreadGroupAndCpuset(Process.myPid(), 9)
            Process.setProcessGroup(Process.myPid(), 9)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created, registering callback...")
        registerCallback()
        Process.setThreadAffinity(Process.myPid(), 1)
        Process.setThreadGroupAndCpuset(Process.myPid(), 9)
        Process.setProcessGroup(Process.myPid(), 9)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "game_start" -> {
                intent.getStringExtra("package_name")?.let {
                    SessionService.start(applicationContext, it)
                }
            }
            "game_stop" -> SessionService.stop(applicationContext)
        }
        return START_STICKY
    }

    private fun registerCallback() {
        gameSpaceService = IGameSpaceService.Stub.asInterface(
            ServiceManager.getService("game_space")
        )

        if (gameSpaceService != null) {
            try {
                gameSpaceService?.registerCallback(callback)
                Log.i(TAG, "Callback registered successfully")
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to register callback", e)
            }
        } else {
            Log.e(TAG, "game_space service not available")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterCallback()
    }

    private fun unregisterCallback() {
        if (gameSpaceService != null) {
            try {
                gameSpaceService?.unregisterCallback(callback)
                Log.i(TAG, "Callback unregistered")
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to unregister callback", e)
            }
            gameSpaceService = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
