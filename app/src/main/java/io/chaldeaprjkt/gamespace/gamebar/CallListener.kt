/*
 * Copyright (C) 2020 The exTHmUI Open Source Project
 * Copyright (C) 2021 AOSP-Krypton Project
 * Copyright (C) 2022 Nameless-AOSP Project
 * Copyright (C) 2022-2024 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.chaldeaprjkt.gamespace.gamebar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioSystem
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast

import androidx.core.app.ActivityCompat

import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped

import javax.inject.Inject

import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.AppSettings

@ServiceScoped
class CallListener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettings
) {

    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)!!
    private val telecomManager = context.getSystemService(TelecomManager::class.java)!!

    private val callsMode = appSettings.callsMode

    private var previousAudioMode = audioManager.mode

    private val telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            if (callsMode == 0) return

            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> handleIncomingCall()
                TelephonyManager.CALL_STATE_OFFHOOK -> handleOffhookState()
                TelephonyManager.CALL_STATE_IDLE -> handleIdleState()
            }
        }
    }

    fun init() {
        telephonyManager.registerTelephonyCallback(context.mainExecutor, telephonyCallback)
    }

    fun destroy() {
        telephonyManager.unregisterTelephonyCallback(telephonyCallback)
    }

    private fun handleIncomingCall() {
        if (callsMode == 1) {
            telecomManager.acceptRingingCall()
            Toast.makeText(context, context.getString(
                    R.string.in_game_calls_received_number, ""),
                    Toast.LENGTH_SHORT).show()
        } else if (callsMode == 2) {
            telecomManager.endCall()
            Toast.makeText(context, context.getString(
                    R.string.in_game_calls_rejected_number, ""),
                    Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleOffhookState() {
        if (callsMode == 2) return
        if (isHeadsetPluggedIn()) {
            audioManager.isSpeakerphoneOn = false
            AudioSystem.setForceUse(
                AudioSystem.FOR_COMMUNICATION,
                AudioSystem.FORCE_NONE
            )
        } else {
            audioManager.isSpeakerphoneOn = true
            AudioSystem.setForceUse(
                AudioSystem.FOR_COMMUNICATION,
                AudioSystem.FORCE_SPEAKER
            )
        }
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun handleIdleState() {
        if (callsMode == 2) return
        audioManager.mode = previousAudioMode
        AudioSystem.setForceUse(
            AudioSystem.FOR_COMMUNICATION,
            AudioSystem.FORCE_NONE
        )
    }

    private fun isHeadsetPluggedIn(): Boolean {
        val audioDeviceInfoArr: Array<AudioDeviceInfo> =
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)!!
        return audioDeviceInfoArr.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }
}
