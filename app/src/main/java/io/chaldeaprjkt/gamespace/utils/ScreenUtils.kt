/*
 * Copyright (C) 2021 Chaldeaprjkt
 *               2022 crDroid Android Project
 *               2025 AxionOS
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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.os.UserHandle
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

    private var recordingCallback: IRecordingCallback? = null
    private var remoteRecording: IRemoteRecording? = null
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
    
    private val handler = Handler(Looper.getMainLooper())

    val recorder: IRemoteRecording? get() = remoteRecording

    fun addRecordingCallback(callback: IRecordingCallback) {
        remoteRecording?.let { 
            it.addRecordingCallback(callback)
            recordingCallback = callback
        }
    }
    
    fun removeRecordingCallback() {
        recordingCallback?.let {
            remoteRecording?.removeRecordingCallback(it)
        }
    }

    fun bind() {
        val started = context.bindServiceAsUser(Intent().apply {
            component = ComponentName(
                "com.android.systemui",
                "com.android.systemui.screenrecord.RecordingService"
            )
        }, recorderConnection, Context.BIND_AUTO_CREATE, UserHandle.CURRENT)
        if (!started) {
            exitProcess(1)
        }
    }

    fun unbind() {
        runCatching {
            removeRecordingCallback()
            context.unbindService(recorderConnection)
            remoteRecording = null
        }.onFailure {
            Log.w("ScreenUtils", "Recorder service not registered or already unbound: $it")
        }
    }
    
    fun takeScreenshot(onComplete: ((Uri?) -> Unit)? = null) {
        ScreenshotHelper(context).takeScreenshot(
            WindowManager.TAKE_SCREENSHOT_FULLSCREEN,
            WindowManager.ScreenshotSource.SCREENSHOT_GLOBAL_ACTIONS, handler
        ) { handler.post { onComplete?.invoke(it) } }
    }
}
