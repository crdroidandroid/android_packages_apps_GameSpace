/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2026 crDroid Android Project
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
package io.chaldeaprjkt.gamespace.preferences

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import androidx.activity.result.ActivityResult
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat

import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.GameConfig
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.data.UserGame
import io.chaldeaprjkt.gamespace.settings.PerAppSettingsActivity
import io.chaldeaprjkt.gamespace.utils.GameModeUtils.Companion.describeGameMode
import io.chaldeaprjkt.gamespace.utils.di.ServiceViewEntryPoint
import io.chaldeaprjkt.gamespace.utils.entryPointOf

class AppListPreferences @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    PreferenceCategory(context, attrs), Preference.OnPreferenceClickListener {

    private val apps = mutableListOf<UserGame>()

    private val systemSettings by lazy {
        context.entryPointOf<ServiceViewEntryPoint>().systemSettings()
    }

    private val gameModeUtils by lazy {
        context.entryPointOf<ServiceViewEntryPoint>().gameModeUtils()
    }

    private lateinit var registeredAppClickAction: (String) -> Unit

    init {
        isOrderingAsAdded = false
    }

    private val makeAddPref by lazy {
        Preference(context).apply {
            title = context.getString(R.string.add)
            key = KEY_ADD_GAME
            setIcon(R.drawable.ic_add)
            isPersistent = false
            onPreferenceClickListener = this@AppListPreferences
        }
    }

    private val autoDetectPref by lazy {
        SwitchPreferenceCompat(context).apply {
            key = KEY_AUTO_GAME_DETECT
            title = context.getString(R.string.auto_game_detect_title)
            summary = context.getString(R.string.auto_game_detect_summary)
            setDefaultValue(true)
            setOnPreferenceChangeListener { _, newValue ->
                    systemSettings.autoGameDetect = newValue as Boolean
                true
            }
        }
    }

    private fun getAppInfo(packageName: String): ApplicationInfo? = try {
        val flags = PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        context.packageManager.getApplicationInfo(packageName, flags)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun isGameCategory(packageName: String): Boolean =
        getAppInfo(packageName)?.category == ApplicationInfo.CATEGORY_GAME

    private fun readDeniedList(): MutableSet<String> {
        val raw = Settings.System.getStringForUser(
            context.contentResolver, KEY_DENIED_LIST,
            UserHandle.USER_CURRENT
        ) ?: return mutableSetOf()
        return raw.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
    }

    private fun writeDeniedList(set: Set<String>) {
        Settings.System.putStringForUser(
            context.contentResolver,
            KEY_DENIED_LIST,
            set.joinToString(";"),
            UserHandle.USER_CURRENT
        )
    }

    fun updateAppList() {
        apps.clear()
        systemSettings.userGames?.let { apps.addAll(it) }

        removeAll()
        addPreference(autoDetectPref)
        addPreference(makeAddPref)

        apps
            .filter { getAppInfo(it.packageName) != null }
            .map { game ->
                val info = getAppInfo(game.packageName)
                Preference(context).apply {
                    key = game.packageName
                    title = info?.loadLabel(context.packageManager)
                    summary = context.describeGameMode(game.mode)
                    icon = info?.loadIcon(context.packageManager)
                    layoutResource = R.layout.library_item
                    isPersistent = false
                    onPreferenceClickListener = this@AppListPreferences
                }
            }
            .sortedBy { it.title.toString().lowercase() }
            .forEach(::addPreference)
    }

    private fun registerApp(packageName: String) {
        // Lift any prior deny so the framework will auto-keep this on reinstall.
        val denied = readDeniedList()
        if (denied.remove(packageName)) writeDeniedList(denied)

        if (apps.none { it.packageName == packageName }) {
            apps.add(UserGame(packageName))
        }
        systemSettings.userGames = apps
        gameModeUtils.setIntervention(packageName, GameConfig.ModeBuilder.build())
        updateAppList()
    }

    private fun unregisterApp(packageName: String) {
        // Persist the user's "no" so the framework's auto-detect skips this package.
        if (isGameCategory(packageName)) {
            val denied = readDeniedList()
            if (denied.add(packageName)) writeDeniedList(denied)
        }

        apps.removeIf { it.packageName == packageName }
        systemSettings.userGames = apps
        gameModeUtils.setIntervention(packageName, null)
        updateAppList()
    }

    override fun onAttached() {
        super.onAttached()
        updateAppList()
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        if (preference != makeAddPref &&
            preference != autoDetectPref &&
            ::registeredAppClickAction.isInitialized
        ) {
            registeredAppClickAction(preference.key)
        }
        return true
    }

    fun onRegisteredAppClick(action: (String) -> Unit) {
        registeredAppClickAction = action
    }

    fun usePerAppResult(result: ActivityResult?) {
        result?.takeIf { it.resultCode == Activity.RESULT_OK }
            ?.data?.getStringExtra(PerAppSettingsActivity.PREF_UNREGISTER)
            ?.let { unregisterApp(it) }
    }

    fun useSelectorResult(result: ActivityResult?) {
        result?.takeIf { it.resultCode == Activity.RESULT_OK }
            ?.data?.getStringExtra(EXTRA_APP)
            ?.let { registerApp(it) }
    }

    companion object {
        const val KEY_ADD_GAME = "add_game"
        const val EXTRA_APP = "selected_app"
        const val KEY_DENIED_LIST = "gamespace_denied_list"
        const val KEY_AUTO_GAME_DETECT = "gamespace_auto_game_detect"
    }
}
