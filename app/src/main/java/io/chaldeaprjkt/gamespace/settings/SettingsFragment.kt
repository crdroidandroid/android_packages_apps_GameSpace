/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2025 crDroid Android Project
 *               2023-2024 the risingOS Android Project
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
package io.chaldeaprjkt.gamespace.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemProperties
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint

import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.preferences.AppListPreferences
import io.chaldeaprjkt.gamespace.preferences.appselector.AppSelectorActivity
import io.chaldeaprjkt.gamespace.preferences.QuickStartAppPreference
import io.chaldeaprjkt.gamespace.preferences.QuickStartAppPreferenceDialogFragment

@AndroidEntryPoint(SettingsBasePreferenceFragment::class)
class SettingsFragment : Hilt_SettingsFragment() {

    private var apps: AppListPreferences? = null

    private val selectorResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            apps?.useSelectorResult(it)
        }

    private val perAppResult =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            apps?.usePerAppResult(it)
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isGoogleDevice = Build.MANUFACTURER.equals("Google", ignoreCase = true)
        val bypassSupported = SystemProperties.getBoolean("persist.sys.battery_bypass_supported", false)
        val bypassChargePref = findPreference<Preference>("bypass_charge_enabled")

        if (!isGoogleDevice && !bypassSupported && bypassChargePref != null) {
            val category = findPreference<Preference>("in_game_preferences") as? PreferenceCategory
            category?.removePreference(bypassChargePref)
        }
