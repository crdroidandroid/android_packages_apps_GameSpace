/*
 * Copyright (C) 2021 Chaldeaprjkt
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
package io.chaldeaprjkt.gamespace.preferences.appselector

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.preferences.AppListPreferences
import io.chaldeaprjkt.gamespace.ui.screens.AppSelectorScreen
import io.chaldeaprjkt.gamespace.ui.theme.GameSpaceTheme
import io.chaldeaprjkt.gamespace.ui.viewmodel.AppSelectorViewModel

@AndroidEntryPoint(ComponentActivity::class)
class AppSelectorActivity : Hilt_AppSelectorActivity() {

    private val viewModel by viewModels<AppSelectorViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameSpaceTheme {
                AppSelectorScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onAppSelected = { packageName ->
                        setResult(Activity.RESULT_OK, Intent().apply {
                            putExtra(AppListPreferences.EXTRA_APP, packageName)
                        })
                        finish()
                    }
                )
            }
        }
    }
}
