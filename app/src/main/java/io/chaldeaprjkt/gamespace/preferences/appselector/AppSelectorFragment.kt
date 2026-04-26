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
package io.chaldeaprjkt.gamespace.preferences.appselector

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.View

import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference

import com.android.settingslib.widget.SettingsBasePreferenceFragment

import dagger.hilt.android.AndroidEntryPoint

import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.preferences.AppListPreferences

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import javax.inject.Inject

@AndroidEntryPoint(SettingsBasePreferenceFragment::class)
class AppSelectorFragment : Hilt_AppSelectorFragment() {

    @Inject
    lateinit var systemSettings: SystemSettings

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadApps()
    }

    private fun loadApps() {
        val ctx = requireContext()
        val pm = ctx.packageManager
        val ownPkg = ctx.packageName
        val registered = systemSettings.userGames.map { it.packageName }.toSet()

        viewLifecycleOwner.lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                    .asSequence()
                    .mapNotNull { ri: ResolveInfo ->
                        val info = ri.activityInfo ?: return@mapNotNull null
                        if (info.packageName == ownPkg) return@mapNotNull null
                        if (info.packageName in registered) return@mapNotNull null
                        try {
                            val ai = pm.getApplicationInfo(
                                info.packageName,
                                PackageManager.ApplicationInfoFlags.of(0)
                            )
                            AppEntry(
                                packageName = info.packageName,
                                label = pm.getApplicationLabel(ai).toString(),
                                icon = ai.loadIcon(pm)
                            )
                        } catch (_: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }

            val screen = preferenceScreen ?: return@launch
            screen.removeAll()
            apps.forEach { app ->
                val pref = Preference(requireContext()).apply {
                    key = app.packageName
                    title = app.label
                    summary = app.packageName
                    icon = app.icon
                    isPersistent = false
                    setOnPreferenceClickListener {
                        requireActivity().setResult(
                            Activity.RESULT_OK,
                            Intent().apply {
                                putExtra(AppListPreferences.EXTRA_APP, app.packageName)
                            }
                        )
                        requireActivity().finish()
                        true
                    }
                }
                screen.addPreference(pref)
            }
        }
    }

    private data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?
    )
}
