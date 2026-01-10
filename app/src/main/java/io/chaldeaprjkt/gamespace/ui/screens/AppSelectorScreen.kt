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
package io.chaldeaprjkt.gamespace.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.ui.viewmodel.AppSelectorViewModel
import io.chaldeaprjkt.gamespace.utils.rememberDrawablePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
    viewModel: AppSelectorViewModel,
    onBack: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val apps by viewModel.apps.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val active by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    viewModel.filterApps(it)
                },
                onSearch = {},
                active = false,
                onActiveChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.app_search_title)) }
            ) {}

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(apps) { app ->
                    ListItem(
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(app.packageName) },
                        leadingContent = {
                            Image(
                                painter = rememberDrawablePainter(app.icon),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                        },
                        modifier = Modifier.clickable {
                            onAppSelected(app.packageName)
                        }
                    )
                }
            }
        }
    }
}
