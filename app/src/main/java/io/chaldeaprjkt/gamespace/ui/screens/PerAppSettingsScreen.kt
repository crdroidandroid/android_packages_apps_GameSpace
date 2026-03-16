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
@file:OptIn(ExperimentalMaterial3Api::class)

package io.chaldeaprjkt.gamespace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.ui.components.*
import io.chaldeaprjkt.gamespace.ui.viewmodel.PerAppSettingsViewModel

@Composable
fun PerAppSettingsScreen(
    viewModel: PerAppSettingsViewModel,
    onBackClick: () -> Unit,
    onUnregister: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showUnregisterDialog by remember { mutableStateOf(false) }

    if (showUnregisterDialog) {
        AlertDialog(
            onDismissRequest = { showUnregisterDialog = false },
            title = { Text(stringResource(R.string.per_app_title)) },
            text = {
                Text(
                    stringResource(R.string.per_app_unregister, viewModel.gameLabel)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.unregisterGame()
                        onUnregister(viewModel.packageName)
                    }
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnregisterDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.per_app_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GameHeader(
                label = viewModel.gameLabel,
                icon = viewModel.gameIcon?.let {
                    BitmapPainter(it.toBitmap(96, 96).asImageBitmap())
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(title = stringResource(R.string.game_mode)) {
                SettingsDropdown(
                    title = stringResource(R.string.per_app_mode_title),
                    selectedValue = viewModel.preferredMode.toString(),
                    options = viewModel.gameModeOptions.map { it.first.toString() to it.second },
                    onValueChange = { viewModel.updatePreferredMode(it.toIntOrNull() ?: 1) },
                    icon = painterResource(R.drawable.materialsymbols_ic_speed_rounded_filled)
                )
            }

            if (viewModel.angleFeatureAvailable) {
                SettingsSection(title = stringResource(R.string.graphics)) {
                    SettingsDropdown(
                        title = stringResource(R.string.per_app_angle_title),
                        selectedValue = viewModel.angleDriverChoice,
                        options = viewModel.angleDriverOptions,
                        onValueChange = { viewModel.updateAngleDriverChoice(it) },
                        icon = painterResource(R.drawable.materialsymbols_ic_view_in_ar_rounded_filled)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsClickable(
                title = stringResource(R.string.remove_from_library),
                summary = stringResource(R.string.per_app_unregister, viewModel.gameLabel),
                onClick = { showUnregisterDialog = true },
                icon = painterResource(R.drawable.materialsymbols_ic_delete_rounded_filled),
            )

            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
private fun GameHeader(
    label: String,
    icon: BitmapPainter?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(56.dp),
                        tint = Color.Unspecified
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.materialsymbols_ic_sports_esports_rounded_filled),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.registered_game),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
