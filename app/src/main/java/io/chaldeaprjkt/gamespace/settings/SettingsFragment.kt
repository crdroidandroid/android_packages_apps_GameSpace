/*
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
package io.chaldeaprjkt.gamespace.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference

import com.android.settingslib.widget.SettingsBasePreferenceFragment

import dagger.hilt.android.AndroidEntryPoint

import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.preferences.AppListPreferences
import io.chaldeaprjkt.gamespace.preferences.QuickStartAppPreference
import io.chaldeaprjkt.gamespace.preferences.QuickStartAppPreferenceDialogFragment
import io.chaldeaprjkt.gamespace.preferences.appselector.AppSelectorActivity

@AndroidEntryPoint(SettingsBasePreferenceFragment::class)
class SettingsFragment : Hilt_SettingsFragment(),
    QuickStartAppPreferenceDialogFragment.QuickStartAppListener {

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

        apps = findPreference("gamespace_game_list")
        apps?.onRegisteredAppClick { pkg ->
            perAppResult.launch(
                Intent(context, PerAppSettingsActivity::class.java).apply {
                    putExtra(PerAppSettingsActivity.EXTRA_PACKAGE, pkg)
                }
            )
        }

        findPreference<Preference>(AppListPreferences.KEY_ADD_GAME)
            ?.setOnPreferenceClickListener {
                selectorResult.launch(Intent(context, AppSelectorActivity::class.java))
                true
            }
    }

    override fun onResume() {
        super.onResume()
        apps?.updateAppList()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is QuickStartAppPreference) {
            val dialogFragment =
                QuickStartAppPreferenceDialogFragment.newInstance(preference.key)
            dialogFragment.setListener(this)
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(parentFragmentManager, "QuickStartAppPreferenceDialogFragment")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun getSavedQuickStartApps(): String {
        val prefs = preferenceManager.sharedPreferences ?: return ""
        return prefs.getString(io.chaldeaprjkt.gamespace.data.AppSettings.KEY_QUICK_START_APPS, "") ?: ""
    }

    override fun saveQuickStartApps(apps: String) { /* no-op */ }
}
