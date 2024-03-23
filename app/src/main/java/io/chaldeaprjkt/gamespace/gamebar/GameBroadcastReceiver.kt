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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.UserHandle


class GameBroadcastReceiver : BroadcastReceiver() {
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            GAME_START -> context.onGameStart(intent)
            GAME_STOP -> context.onGameStop(intent)
        }
    }

    private fun Context.onGameStart(intent: Intent) {
        handler.post { resendBroadcast(intent) }
        val app = intent.getStringExtra(SessionService.EXTRA_PACKAGE_NAME)!!
        SessionService.start(this, app)
    }

    private fun Context.onGameStop(intent: Intent) {
        handler.post { resendBroadcast(intent) }
        SessionService.stop(this)
    }

    private fun Context.resendBroadcast(prevIntent: Intent) {
        val intent = (prevIntent.clone() as Intent).apply {
            setPackage(null)
            component = null
        }
        val flags = PackageManager.ResolveInfoFlags.of(0)
        packageManager.queryBroadcastReceivers(intent, flags)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != packageName }
            .forEach {
                (intent.clone() as Intent).apply {
                    setPackage(it)
                    sendBroadcastAsUser(this, UserHandle.CURRENT,
                        android.Manifest.permission.MANAGE_GAME_MODE)
                }
            }
    }

    companion object {
        const val GAME_START = "io.chaldeaprjkt.gamespace.action.GAME_START"
        const val GAME_STOP = "io.chaldeaprjkt.gamespace.action.GAME_STOP"
    }
}
