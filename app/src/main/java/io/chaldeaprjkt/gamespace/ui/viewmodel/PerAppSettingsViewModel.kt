/*
 * Copyright (C) 2025 AxionOS
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
package io.chaldeaprjkt.gamespace.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.*
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import javax.inject.Inject

@HiltViewModel
class PerAppSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemSettings: SystemSettings,
    private val gameModeUtils: GameModeUtils
) : ViewModel() {

    var packageName by mutableStateOf("")
        private set

    var gameLabel by mutableStateOf("")
        private set

    var gameIcon by mutableStateOf<Drawable?>(null)
        private set

    var preferredMode by mutableIntStateOf(1)
        private set

    var angleDriverChoice by mutableStateOf(GameModeUtils.DRIVER_CHOICE_DEFAULT)
        private set

    var angleFeatureAvailable by mutableStateOf(false)
        private set

    val gameModeOptions = listOf(
        1 to "Standard",
        2 to "Performance",
        3 to "Battery"
    )

    fun loadGame(pkg: String) {
        if (pkg.isEmpty()) return
        packageName = pkg

        try {
            val pm = context.packageManager
            val flags = PackageManager.ApplicationInfoFlags.of(0)
            val appInfo = pm.getApplicationInfo(pkg, flags)
            gameLabel = appInfo.loadLabel(pm).toString()
            gameIcon = appInfo.loadIcon(pm)
        } catch (e: PackageManager.NameNotFoundException) {
            return
        }

        val userGame = systemSettings.userGames.firstOrNull { it.packageName == pkg }
        preferredMode = userGame?.mode ?: 1

        val hasAngle = gameModeUtils.findAnglePackage()?.isEnabled == true
        val hasVulkan = gameModeUtils.isVulkanSupported()
        angleFeatureAvailable = hasAngle && hasVulkan
        angleDriverChoice = gameModeUtils.getAngleDriverChoice(pkg)
    }

    fun updatePreferredMode(mode: Int) {
        preferredMode = mode
        gameModeUtils.setGameModeFor(packageName, systemSettings, mode)
    }

    val angleDriverOptions = listOf(
        GameModeUtils.DRIVER_CHOICE_DEFAULT to "Default",
        GameModeUtils.DRIVER_CHOICE_ANGLE to "ANGLE",
        GameModeUtils.DRIVER_CHOICE_NATIVE to "Native"
    )

    fun updateAngleDriverChoice(choice: String) {
        angleDriverChoice = choice
        gameModeUtils.setAngleDriverChoice(packageName, choice)
    }

    fun unregisterGame() {
        gameModeUtils.setAngleDriverChoice(packageName, GameModeUtils.DRIVER_CHOICE_DEFAULT)
        val games = systemSettings.userGames.toMutableList()
        games.removeIf { it.packageName == packageName }
        systemSettings.userGames = games
    }
}
