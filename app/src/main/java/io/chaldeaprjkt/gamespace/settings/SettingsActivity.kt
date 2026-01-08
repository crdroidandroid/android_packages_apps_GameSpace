/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2024 crDroid Android Project
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.preferences.AppListPreferences
import io.chaldeaprjkt.gamespace.preferences.appselector.AppSelectorActivity
import io.chaldeaprjkt.gamespace.ui.screens.SettingsScreen
import io.chaldeaprjkt.gamespace.ui.theme.GameSpaceTheme
import io.chaldeaprjkt.gamespace.ui.viewmodel.SettingsViewModel

@AndroidEntryPoint(ComponentActivity::class)
class SettingsActivity : Hilt_SettingsActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    private val selectorResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getStringExtra(AppListPreferences.EXTRA_APP)?.let { packageName ->
            viewModel.registerGame(packageName)
        }
    }

    private val perAppResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getStringExtra(PerAppSettingsActivity.PREF_UNREGISTER)?.let { packageName ->
            viewModel.unregisterGame(packageName)
        }
        viewModel.loadRegisteredGames()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GameSpaceTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onAddGameClick = {
                        selectorResult.launch(Intent(this, AppSelectorActivity::class.java))
                    },
                    onGameClick = { packageName ->
                        perAppResult.launch(
                            Intent(this, PerAppSettingsActivity::class.java).apply {
                                putExtra(PerAppSettingsActivity.EXTRA_PACKAGE, packageName)
                            }
                        )
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRegisteredGames()
    }
}
