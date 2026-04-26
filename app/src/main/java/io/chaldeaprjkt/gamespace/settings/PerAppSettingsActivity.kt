/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2026 crDroid Android Project
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

import android.os.Bundle
import androidx.activity.viewModels
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import dagger.hilt.android.AndroidEntryPoint
import io.chaldeaprjkt.gamespace.ui.viewmodel.PerAppSettingsViewModel

@AndroidEntryPoint(CollapsingToolbarBaseActivity::class)
class PerAppSettingsActivity : Hilt_PerAppSettingsActivity() {

    private val viewModel: PerAppSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: run {
            finish()
            return
        }

        viewModel.loadGame(packageName)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    PerAppSettingsFragment()
                )
                .commit()
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package_name"
        const val PREF_UNREGISTER = "per_app_unregister"
    }
}
