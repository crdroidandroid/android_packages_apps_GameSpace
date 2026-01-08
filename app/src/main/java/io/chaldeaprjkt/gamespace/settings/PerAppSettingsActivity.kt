/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2024 crDroid Android Project
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
package io.chaldeaprjkt.gamespace.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.ui.screens.PerAppSettingsScreen
import io.chaldeaprjkt.gamespace.ui.theme.GameSpaceTheme
import io.chaldeaprjkt.gamespace.ui.viewmodel.PerAppSettingsViewModel

@AndroidEntryPoint(ComponentActivity::class)
class PerAppSettingsActivity : Hilt_PerAppSettingsActivity() {

    private val viewModel: PerAppSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: run {
            finish()
            return
        }

        viewModel.loadGame(packageName)

        enableEdgeToEdge()

        setContent {
            GameSpaceTheme {
                PerAppSettingsScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    onUnregister = { pkg ->
                        setResult(Activity.RESULT_OK, Intent().apply {
                            putExtra(PREF_UNREGISTER, pkg)
                        })
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package_name"
        const val PREF_UNREGISTER = "per_app_unregister"
    }
}
