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
import android.os.UserHandle
import android.provider.Settings
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import javax.inject.Inject

import lineageos.providers.LineageSettings

class SystemSettings @Inject constructor(
    context: Context,
    private val gameModeUtils: GameModeUtils
) {

    private val resolver = context.contentResolver

    var headsup
        get() = Settings.Global.getInt(
            resolver, Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED, 1) == 1
        set(it) {
            Settings.Global.putInt(
                resolver, Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED,
                it.toInt()
            )
        }

    var autoBrightness
        get() =
            Settings.System.getIntForUser(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                UserHandle.USER_CURRENT
            ) ==
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        set(auto) {
            Settings.System.putIntForUser(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                UserHandle.USER_CURRENT
            )
        }

    var bypassChargeEnabled
        get() =
            Settings.System.getIntForUser(
                resolver, "bypass_charge_enabled", 0,
                UserHandle.USER_CURRENT
            ) == 1
        set(value) {
            Settings.System.putIntForUser(
                resolver, "bypass_charge_enabled",
                if (value) 1 else 0, UserHandle.USER_CURRENT
            )
        }

    var stayAwake
        get() = Settings.System.getIntForUser(
            resolver, "gamespace_stay_awake", 0,
            UserHandle.USER_CURRENT
        ) != 0
        set(value) {
            Settings.System.putIntForUser(
                resolver, "gamespace_stay_awake",
                if (value) 1 else 0, UserHandle.USER_CURRENT
            )
        }

    var threeScreenshot
        get() = Settings.Secure.getIntForUser(
            resolver, "nothing_three_finger_screenshot", 0,
            UserHandle.USER_CURRENT
        )
        set(value) {
            Settings.Secure.putIntForUser(
                resolver, "nothing_three_finger_screenshot",
                value, UserHandle.USER_CURRENT
            )
        }

    var userGames
        get() =
            Settings.System.getStringForUser(
                resolver, "gamespace_game_list",
                UserHandle.USER_CURRENT
            )
                ?.split(";")
                ?.toList()?.filter { it.isNotEmpty() }
                ?.map { UserGame.fromSettings(it) } ?: emptyList()
        set(games) {
            Settings.System.putStringForUser(
                resolver,
                "gamespace_game_list",
                if (games.isEmpty()) "" else
                    games.joinToString(";") { it.toString() },
                UserHandle.USER_CURRENT
            )
            gameModeUtils.setupBatteryMode(games.isNotEmpty())
        }

    private fun Boolean.toInt() = if (this) 1 else 0
}
