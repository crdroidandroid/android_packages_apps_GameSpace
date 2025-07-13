/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2024 crDroid Android Project
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import com.android.internal.app.IGameSpaceCallback
import com.android.internal.app.IGameSpaceService
import io.chaldeaprjkt.gamespace.gamebar.SessionService

class GameBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "BOOT_COMPLETED received. Scheduling GameSpaceService registration.")
            Handler(Looper.getMainLooper()).post {
                GameSpaceRegistry(context.applicationContext).start()
            }
        }
    }

    companion object {
        private const val TAG = "GameBroadcastReceiver"
    }

    private class GameSpaceRegistry(private val context: Context) {
        private val handler = Handler(Looper.getMainLooper())
        private val retryDelayMs = 2000L
        private var serviceRegistered = false

        private val callback = object : IGameSpaceCallback.Stub() {
            override fun shouldSuppressFullScreenIntent(suppress: Boolean) {}

            override fun onGameStart(packageName: String) {
                packageName?.let { SessionService.start(context, it) }
            }

            override fun onGameLeave() {
                SessionService.stop(context)
            }
        }

        fun start() {
            tryRegister()
        }

        private fun tryRegister() {
            val service = IGameSpaceService.Stub.asInterface(
                ServiceManager.getService("game_space")
            )
            if (service != null) {
                try {
                    service.registerCallback(callback)
                    serviceRegistered = true
                    Log.i(TAG, "GameSpaceCallback successfully registered.")
                } catch (e: RemoteException) {
                    Log.e(TAG, "Failed to register GameSpaceCallback", e)
                    serviceRegistered = false
                    scheduleRetry()
                }
            } else {
                scheduleRetry()
            }
        }

        private fun scheduleRetry() {
            if (serviceRegistered) return
            handler.postDelayed({ tryRegister() }, retryDelayMs)
        }
    }
}
