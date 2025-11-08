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
package io.chaldeaprjkt.gamespace.utils

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import  android.util.Log
import android.os.PowerManager
import android.os.SystemProperties
import android.os.UserHandle
import android.provider.Settings
import android.view.WindowManager
import com.android.internal.util.ScreenshotHelper
import com.android.systemui.screenrecord.IRemoteRecording
import com.android.systemui.screenrecord.IRecordingCallback
import javax.inject.Inject
import kotlin.system.exitProcess

/**
 * utilities for interacting with system screenshot and recorder service
 */
class ScreenUtils @Inject constructor(private val context: Context) {

    private var isRecorderBound = false
    private var remoteRecording: IRemoteRecording? = null
    private var wakelock: PowerManager.WakeLock? = null
    private val recorderConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            runCatching {
                remoteRecording = IRemoteRecording.Stub.asInterface(service)
            }.onFailure {
                Log.e("ScreenUtils", "Failed to connect to recorder service", it)
                remoteRecording = null
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remoteRecording = null
        }
    }

    val recorder: IRemoteRecording? get() = remoteRecording
    
    fun addRecordingCallback(callback: IRecordingCallback) {
        remoteRecording?.addRecordingCallback(callback)
    }
    
    fun removeRecordingCallback(callback: IRecordingCallback) {
        remoteRecording?.removeRecordingCallback(callback)
    }

    private var isGestureLocked = false
    
    val resolver get() = context.contentResolver
    
    val smartChargeByUser get() = Settings.System.getIntForUser(
        resolver, "smart_charge_by_user", 0, UserHandle.USER_CURRENT
    ) == 1

    val bypassEnabled get() = Settings.System.getIntForUser(
        resolver, "bypass_charge_enabled", 0, UserHandle.USER_CURRENT
    ) == 1

    val battLevel: Int
        get() {
            val batteryIntent = context.registerReceiver(
                null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            return batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        }

    var smartChargeLvl: Int
        get() = SystemProperties.getInt("persist.sys.smart_charge_level", 100)
        set(value) {
            SystemProperties.set("persist.sys.smart_charge_level", value.toString())
        }

    var bypassActive: Boolean
        get() = SystemProperties.getInt("persist.sys.gs_charge_bypass_active", 0) == 1
        set(value) {
            SystemProperties.set("persist.sys.gs_charge_bypass_active", if (value) "1" else "0")
        }

    fun bind() {
        isRecorderBound = context.bindServiceAsUser(Intent().apply {
            component = ComponentName(
                "com.android.systemui",
                "com.android.systemui.screenrecord.RecordingService"
            )
        }, recorderConnection, Context.BIND_AUTO_CREATE, UserHandle.CURRENT)
        if (!isRecorderBound) {
            exitProcess(1)
        }
        @Suppress("DEPRECATION") // we use it for stay-awake feature
        wakelock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.FULL_WAKE_LOCK, "GameSpace:ScreenUtils")
    }

    fun unbind() {
        runCatching {
            wakelock?.takeIf { it.isHeld }?.release()
        }.onFailure {
            Log.w("ScreenUtils", "Failed to release wakelock: $it")
        }.also {
            wakelock = null
        }

        if (isRecorderBound) {
            runCatching {
                context.unbindService(recorderConnection)
            }.onFailure {
                Log.w("ScreenUtils", "Recorder service not registered or already unbound: $it")
            }.also {
                isRecorderBound = false
            }
        } else {
            isRecorderBound = false
        }

        remoteRecording = null
        lockGesture = false
        bypassCharge = false
    }

    fun takeScreenshot(onComplete: ((Uri?) -> Unit)? = null) {
        val handler = Handler(Looper.getMainLooper())
        ScreenshotHelper(context).takeScreenshot(
            WindowManager.TAKE_SCREENSHOT_FULLSCREEN,
            WindowManager.ScreenshotSource.SCREENSHOT_GLOBAL_ACTIONS, handler
        ) { handler.post { onComplete?.invoke(it) } }
    }

    var stayAwake = false
        get() = wakelock?.isHeld ?: false
        @SuppressLint("WakelockTimeout")
        set(enable) {
            field = enable
            if (enable) {
                wakelock?.takeIf { !it.isHeld }?.acquire()
            } else {
                wakelock?.takeIf { it.isHeld }?.release()
            }
        }

    var lockGesture = false
        get() = isGestureLocked
        set(enable) {
            Settings.Secure.putInt(context.contentResolver,
                    "nt_game_mode_mistouch_prevention", if (enable) 1 else 0)
            field = enable
            isGestureLocked = enable
        }

    var bypassCharge: Boolean
        get() = bypassActive
        set(enable) {
            if (!bypassEnabled) return
            bypassActive = enable
            smartChargeLvl = when {
                enable -> battLevel
                smartChargeByUser -> 80
                else -> 100
            }
        }
}
