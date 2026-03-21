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

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.annotation.WorkerThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.chaldeaprjkt.gamespace.data.SystemSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

@HiltViewModel
class AppSelectorViewModel @Inject constructor(
    private val app: Application,
    private val settings: SystemSettings,
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppItem>>(emptyList())
    val apps = _apps.asStateFlow()

    private val allApps = mutableListOf<AppItem>()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = app.packageManager
            val flags = PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveFlags = PackageManager.ResolveInfoFlags.of(0)
            val launcherPackages = pm.queryIntentActivities(launchIntent, resolveFlags)
                .map { it.activityInfo.packageName }
                .toSet()

            val installedApps = pm.getInstalledApplications(flags)
                .filter {
                    it.packageName != app.packageName &&
                            launcherPackages.contains(it.packageName) &&
                            !settings.userGames.any { game -> game.packageName == it.packageName }
                }
                .map {
                    AppItem(
                        packageName = it.packageName,
                        label = it.loadLabel(pm).toString(),
                        icon = it.loadIcon(pm)
                    )
                }
                .sortedBy { it.label.lowercase() }

            allApps.clear()
            allApps.addAll(installedApps)
            _apps.value = allApps
        }
    }

    fun filterApps(query: String) {
        if (query.isEmpty()) {
            _apps.value = allApps
        } else {
            _apps.value = allApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
}
