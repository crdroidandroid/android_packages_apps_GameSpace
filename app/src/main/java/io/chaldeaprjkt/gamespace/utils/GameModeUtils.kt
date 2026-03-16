/*
 * Copyright (C) 2021 Chaldeaprjkt
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

import android.app.GameManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.IDeviceIdleController
import android.os.RemoteException
import android.os.ServiceManager
import android.provider.Settings
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.GameConfig
import io.chaldeaprjkt.gamespace.data.GameConfig.Companion.asConfig
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.data.UserGame
import javax.inject.Inject

class GameModeUtils @Inject constructor(private val context: Context) {

    private var manager: GameManager? = null
    var activeGame: UserGame? = null

    fun bind(manager: GameManager) {
        this.manager = manager
    }

    fun unbind() {
        manager = null
    }

    fun setIntervention(packageName: String, modeData: List<GameConfig>? = null) {
        // Separate key and value by ;; to identify them from
        // com.android.server.app.GameManagerService for the device_config property.
        // Example: com.libremobileos.game;;mode=2,downscaleFactor=0.7:mode=3,downscaleFactor=0.8
        val configValue = "${packageName};;${modeData?.asConfig()}"
        Settings.Secure.putString(
                context.contentResolver,
                "game_overlay",
                configValue
        )
    }

    fun setActiveGameMode(systemSettings: SystemSettings, mode: Int) {
        val packageName = activeGame?.packageName ?: return
        manager?.setGameMode(packageName, mode)
        activeGame = setGameModeFor(packageName, systemSettings, mode)
    }

    fun setGameModeFor(packageName: String, systemSettings: SystemSettings, mode: Int): UserGame {
        val data = UserGame(packageName, mode)
        systemSettings.userGames = systemSettings.userGames
            .filter { x -> x.packageName != packageName }
            .toMutableList()
            .apply { add(data) }

        return data
    }

    fun setupBatteryMode(enable: Boolean) {
        val svc = IDeviceIdleController.Stub.asInterface(
            ServiceManager.getService(Context.DEVICE_IDLE_CONTROLLER)
        )
        try {
            val isListed = svc?.isPowerSaveWhitelistApp(context.packageName) ?: false
            if (enable && !isListed) {
                svc?.addPowerSaveWhitelistApp(context.packageName)
            } else if (!enable && isListed) {
                svc?.removePowerSaveWhitelistApp(context.packageName)
            }
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }


    fun findAnglePackage(): ActivityInfo? {
        val intent = Intent(ACTION_ANGLE_FOR_ANDROID)
        val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_SYSTEM_ONLY.toLong())
        val info = context.packageManager.queryIntentActivities(intent, flags)
        return info.firstOrNull()?.activityInfo
    }

    fun getAngleDriverChoice(packageName: String): String {
        val resolver = context.contentResolver
        val pkgsStr = Settings.Global.getString(resolver, DRIVER_SELECTION_PACKAGES)
            ?: return DRIVER_CHOICE_DEFAULT
        val valsStr = Settings.Global.getString(resolver, DRIVER_SELECTION_VALUES)
            ?: return DRIVER_CHOICE_DEFAULT
        val pkgs = pkgsStr.split(",")
        val vals = valsStr.split(",")
        val index = pkgs.indexOf(packageName)
        if (index < 0 || index >= vals.size) return DRIVER_CHOICE_DEFAULT
        return vals[index]
    }

    fun setAngleDriverChoice(packageName: String, choice: String) {
        val resolver = context.contentResolver
        val pkgsStr = Settings.Global.getString(resolver, DRIVER_SELECTION_PACKAGES) ?: ""
        val valsStr = Settings.Global.getString(resolver, DRIVER_SELECTION_VALUES) ?: ""
        val pkgs = if (pkgsStr.isEmpty()) mutableListOf()
            else pkgsStr.split(",").toMutableList()
        val vals = if (valsStr.isEmpty()) mutableListOf()
            else valsStr.split(",").toMutableList()

        val index = pkgs.indexOf(packageName)
        if (choice == DRIVER_CHOICE_DEFAULT) {
            if (index >= 0) {
                pkgs.removeAt(index)
                vals.removeAt(index)
            }
        } else {
            if (index >= 0) {
                vals[index] = choice
            } else {
                pkgs.add(packageName)
                vals.add(choice)
            }
        }

        Settings.Global.putString(
            resolver, DRIVER_SELECTION_PACKAGES, pkgs.joinToString(",")
        )
        Settings.Global.putString(
            resolver, DRIVER_SELECTION_VALUES, vals.joinToString(",")
        )
    }

    fun isVulkanSupported(): Boolean =
        context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_0
        )

    companion object {
        const val defaultPreferredMode = GameManager.GAME_MODE_STANDARD
        const val ACTION_ANGLE_FOR_ANDROID = "android.app.action.ANGLE_FOR_ANDROID"
        private const val DRIVER_SELECTION_PACKAGES = "angle_gl_driver_selection_pkgs"
        private const val DRIVER_SELECTION_VALUES = "angle_gl_driver_selection_values"
        const val DRIVER_CHOICE_DEFAULT = "default"
        const val DRIVER_CHOICE_ANGLE = "angle"
        const val DRIVER_CHOICE_NATIVE = "native"
        private const val VULKAN_1_0 = 0x00400000

        fun Context.describeGameMode(mode: Int) =
            resources.getStringArray(R.array.game_mode_names)[mode] ?: "Unsupported"
    }
}
