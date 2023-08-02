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
package io.chaldeaprjkt.gamespace.data

import android.content.Context
import android.media.AudioManager
import com.google.gson.Gson
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import javax.inject.Inject

private const val gameSwitchNode = "/proc/touchpanel/game_switch_enable"
class GameSession @Inject constructor(
    private val context: Context,
    private val appSettings: AppSettings,
    private val systemSettings: SystemSettings,
    private val gson: Gson,
) {

    private val db by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private var state
        get() = db.getString(KEY_SAVED_SESSION, "")
            .takeIf { !it.isNullOrEmpty() }
            ?.let {
                try {
                    gson.fromJson(it, SessionState::class.java)
                } catch (e: RuntimeException) {
                    null
                }
            }
        set(value) = db.edit()
            .putString(KEY_SAVED_SESSION, value?.let {
                try {
                    gson.toJson(value)
                } catch (e: RuntimeException) {
                    ""
                }
            } ?: "")
            .apply()

    fun register(sessionName: String) {
        if (state?.packageName != sessionName) unregister()

        state = SessionState(
            packageName = sessionName,
            autoBrightness = systemSettings.autoBrightness,
            threeScreenshot = systemSettings.threeScreenshot,
            headsUp = systemSettings.headsUp,
            reTicker = systemSettings.reTicker,
            ringerMode = audioManager.ringerModeInternal,
            adbEnabled = systemSettings.adbEnabled,
        )
        if (appSettings.noAutoBrightness) {
            systemSettings.autoBrightness = false
        }
        if (appSettings.noThreeScreenshot) {
            systemSettings.threeScreenshot = false
        }
        if (appSettings.noAdbEnabled) {
            systemSettings.adbEnabled = false
        }
        if (appSettings.notificationMode == 0 || appSettings.notificationMode == 3) {
            systemSettings.headsUp = false
            systemSettings.reTicker = false
        } else if (appSettings.notificationMode == 1) {
            systemSettings.headsUp = true
            systemSettings.reTicker = false
        } else {
            systemSettings.headsUp = true
            systemSettings.reTicker = true
        }
        if (appSettings.ringerMode != 3) {
            audioManager.ringerModeInternal = appSettings.ringerMode
        }
        if (GameModeUtils(context).isFileWritable(gameSwitchNode)) {
            GameModeUtils(context).writeValue(gameSwitchNode, "1")
        }
    }

    fun unregister() {
        val orig = state?.copy() ?: return
        if (appSettings.noAutoBrightness) {
            orig.autoBrightness?.let { systemSettings.autoBrightness = it }
        }
        if (appSettings.noThreeScreenshot) {
            orig.threeScreenshot?.let { systemSettings.threeScreenshot = it }
        }
        if (appSettings.noAdbEnabled) {
            orig.adbEnabled?.let { systemSettings.adbEnabled = it }
        }
         if (GameModeUtils(context).isFileWritable(gameSwitchNode)) {
            GameModeUtils(context).writeValue(gameSwitchNode, "0")
        }
        orig.headsUp?.let { systemSettings.headsUp = it }
        orig.reTicker?.let { systemSettings.reTicker = it }
        if (appSettings.ringerMode != 3) {
            audioManager.ringerModeInternal = orig.ringerMode
        }
        state = null
    }

    fun finalize() {
        unregister()
    }

    companion object {
        const val PREFS_NAME = "persisted_session"
        const val KEY_SAVED_SESSION = "session"
    }
}
