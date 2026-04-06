/*
 * Copyright (C) 2025-2026 AxionOS
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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.chaldeaprjkt.gamespace.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.ui.components.AddGameTile
import io.chaldeaprjkt.gamespace.ui.components.FeaturedGameCard
import io.chaldeaprjkt.gamespace.ui.components.GameTile
import io.chaldeaprjkt.gamespace.ui.components.HubBackground
import io.chaldeaprjkt.gamespace.ui.components.gameModeLabel
import io.chaldeaprjkt.gamespace.ui.viewmodel.RegisteredGame
import io.chaldeaprjkt.gamespace.ui.viewmodel.SettingsViewModel
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

private const val PANEL_LIBRARY = 0
private const val PANEL_SETTINGS = 1
private const val PANEL_GAME_DETAILS = 2
private const val PANEL_ADD_GAME = 3

@Composable
fun GameHubScreen(
    viewModel: SettingsViewModel,
) {
    val games = viewModel.registeredGames
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    val selectedGame = games.firstOrNull { it.packageName == selectedPackage } ?: games.firstOrNull()
    var activePanel by remember { mutableIntStateOf(PANEL_LIBRARY) }

    var hubVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        hubVisible = true
    }

    val hubAlpha by animateFloatAsState(
        targetValue = if (hubVisible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "hub_alpha",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        HubBackground(
            dimLeft = activePanel == PANEL_LIBRARY,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .graphicsLayer { alpha = hubAlpha }
        ) {
            HubTopBar(
                onSettings = { activePanel = PANEL_SETTINGS },
            )

            if (games.isEmpty() && activePanel == PANEL_LIBRARY) {
                EmptyHubContent(onAddGame = { activePanel = PANEL_ADD_GAME })
            }
            if (games.isNotEmpty() || activePanel != PANEL_LIBRARY) {
                val panelEnterFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                val panelExitFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

                AnimatedContent(
                    targetState = activePanel,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val forward = targetState > initialState
                        if (forward) {
                            (slideInHorizontally { it / 4 } + fadeIn(panelEnterFade))
                                .togetherWith(slideOutHorizontally { -it / 4 } + fadeOut(panelExitFade))
                        } else {
                            (slideInHorizontally { -it / 4 } + fadeIn(panelEnterFade))
                                .togetherWith(slideOutHorizontally { it / 4 } + fadeOut(panelExitFade))
                        }
                    },
                    label = "panel",
                ) { panel ->
                    when (panel) {
                        PANEL_LIBRARY -> GameLibraryPanel(
                            games = games,
                            selectedGame = selectedGame,
                            onSelectGame = { selectedPackage = it.packageName },
                            onViewDetails = { activePanel = PANEL_GAME_DETAILS },
                            onAddGame = { activePanel = PANEL_ADD_GAME },
                            onRemoveGame = { pkg ->
                                viewModel.unregisterGame(pkg)
                                selectedPackage = null
                            },
                        )
                        PANEL_SETTINGS -> GlobalSettingsPanel(
                            viewModel = viewModel,
                            onBack = { activePanel = PANEL_LIBRARY },
                        )
                        PANEL_GAME_DETAILS -> selectedGame?.let { game ->
                            GameDetailsPanel(
                                game = game,
                                viewModel = viewModel,
                                onBack = { activePanel = PANEL_LIBRARY },
                                onRemoveGame = {
                                    viewModel.unregisterGame(game.packageName)
                                    selectedPackage = null
                                    activePanel = PANEL_LIBRARY
                                },
                            )
                        }
                        PANEL_ADD_GAME -> AddGamePanel(
                            viewModel = viewModel,
                            onBack = { activePanel = PANEL_LIBRARY },
                            onGameAdded = { pkg ->
                                viewModel.registerGame(pkg)
                                selectedPackage = pkg
                                activePanel = PANEL_LIBRARY
                            },
                        )
                }
            }
        }
    }
}
}

@Composable
private fun HubTopBar(
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    var timeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val format = DateFormat.getTimeFormat(context)
            timeText = format.format(Date())
            val now = System.currentTimeMillis()
            delay(60_000L - (now % 60_000L))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.SportsEsports,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.4f),
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.width(16.dp))

        HubIconButton(onClick = onSettings) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.tile_settings),
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun GameLibraryPanel(
    games: List<RegisteredGame>,
    selectedGame: RegisteredGame?,
    onSelectGame: (RegisteredGame) -> Unit,
    onViewDetails: () -> Unit,
    onAddGame: () -> Unit,
    onRemoveGame: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 4.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                AddGameTile(
                    onClick = onAddGame,
                    modifier = Modifier.height(140.dp).width(140.dp),
                )
            }
            items(games, key = { it.packageName }) { game ->
                val isSelected = game.packageName == selectedGame?.packageName

                GameTile(
                    game = game,
                    isSelected = isSelected,
                    onClick = { onSelectGame(game) },
                    onLongClick = { onViewDetails() },
                    modifier = Modifier
                        .height(if (isSelected) 170.dp else 140.dp)
                        .width(if (isSelected) 170.dp else 140.dp)
                        .animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        selectedGame?.let { game ->
            val context = LocalContext.current
            GameInfoBar(
                game = game,
                onViewDetails = onViewDetails,
                onLaunchGame = {
                    context.packageManager.getLaunchIntentForPackage(game.packageName)?.let { intent ->
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                },
                onRemoveGame = { onRemoveGame(game.packageName) },
            )
        }
    }
}

@Composable
private fun GameInfoBar(
    game: RegisteredGame,
    onViewDetails: () -> Unit,
    onLaunchGame: () -> Unit,
    onRemoveGame: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val enterEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val enterSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val exitEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    AnimatedContent(
        targetState = game,
        transitionSpec = {
            (fadeIn(enterEffects) + scaleIn(
                initialScale = 0.97f,
                animationSpec = enterSpatial,
            )).togetherWith(fadeOut(exitEffects)).using(null)
        },
        contentKey = { it.packageName },
        label = "game_info",
    ) { currentGame ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            Text(
                text = currentGame.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = gameModeLabel(currentGame.mode),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HubButton(
                    text = stringResource(R.string.launch_game),
                    onClick = onLaunchGame,
                )

                Spacer(modifier = Modifier.width(8.dp))

                HubButton(
                    text = stringResource(R.string.view_details),
                    onClick = onViewDetails,
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    HubIconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove_game)) },
                            onClick = {
                                showMenu = false
                                onRemoveGame()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameDetailsPanel(
    game: RegisteredGame,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onRemoveGame: () -> Unit,
) {
    var selectedMode by remember(game.packageName) { mutableStateOf(game.mode) }
    var angleChoice by remember(game.packageName) {
        mutableStateOf(viewModel.getAngleDriverChoice(game.packageName))
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FeaturedGameCard(
            game = game,
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                HubIconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = game.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HubSectionLabel(stringResource(R.string.game_mode))

                HubSelector(
                    options = viewModel.gameModeOptions,
                    selectedKey = selectedMode,
                    onSelect = { mode ->
                        selectedMode = mode
                        viewModel.updateGameMode(game.packageName, mode)
                    },
                )

                if (viewModel.angleFeatureAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HubSectionLabel(stringResource(R.string.graphics))

                    HubSelector(
                        options = viewModel.angleDriverOptions.map { it.first to it.second },
                        selectedKey = angleChoice,
                        onSelect = { choice ->
                            angleChoice = choice
                            viewModel.updateAngleDriverChoice(game.packageName, choice)
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HubButton(
                    text = stringResource(R.string.remove_game),
                    onClick = onRemoveGame,
                    color = Color(0xFFCC4444),
                )
            }
        }
    }
}

@Composable
private fun GlobalSettingsPanel(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showQuickStart by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<QuickStartApp>>(emptyList()) }
    val selectedPkgs = remember {
        mutableStateListOf<String>().apply {
            addAll(viewModel.quickStartApps.split(",").filter { it.isNotBlank() })
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                .mapNotNull { ri ->
                    val ai = ri.activityInfo ?: return@mapNotNull null
                    try {
                        val info = pm.getApplicationInfo(ai.packageName, PackageManager.ApplicationInfoFlags.of(0))
                        QuickStartApp(
                            packageName = ai.packageName,
                            label = pm.getApplicationLabel(info).toString(),
                            icon = pm.getApplicationIcon(info),
                        )
                    } catch (_: Exception) { null }
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            installedApps = apps
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp),
            ) {
                HubIconButton(
                    onClick = {
                        if (showQuickStart) {
                            viewModel.updateQuickStartApps(selectedPkgs.joinToString(","))
                            showQuickStart = false
                        } else {
                            onBack()
                        }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (showQuickStart) stringResource(R.string.quick_start_apps_title)
                           else stringResource(R.string.tile_settings),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }

            if (showQuickStart) {
                QuickStartAppsList(
                    apps = installedApps,
                    selectedPkgs = selectedPkgs,
                    onToggle = { pkg, checked ->
                        if (checked) selectedPkgs.add(pkg) else selectedPkgs.remove(pkg)
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HubToggle(
                        label = stringResource(R.string.auto_brightness_disabled_title),
                        checked = viewModel.noAutoBrightness,
                        onCheckedChange = { viewModel.updateNoAutoBrightness(it) },
                    )
                    HubToggle(
                        label = stringResource(R.string.stay_awake_title),
                        checked = viewModel.stayAwake,
                        onCheckedChange = { viewModel.updateStayAwake(it) },
                    )
                    HubToggle(
                        label = stringResource(R.string.danmaku_notification_mode_title),
                        checked = viewModel.danmakuNotification,
                        onCheckedChange = { viewModel.updateDanmakuNotification(it) },
                    )
                    HubToggle(
                        label = stringResource(R.string.auto_dnd_title),
                        checked = viewModel.autoDnd,
                        onCheckedChange = { viewModel.updateAutoDnd(it) },
                    )
                    HubToggle(
                        label = stringResource(R.string.call_overlay_enabled_title),
                        checked = viewModel.callOverlayEnabled,
                        onCheckedChange = { viewModel.updateCallOverlay(it) },
                    )
                    HubToggle(
                        label = stringResource(R.string.three_screenshot_disabled_title),
                        checked = viewModel.noThreeScreenshot,
                        onCheckedChange = { viewModel.updateNoThreeScreenshot(it) },
                    )
                    if (viewModel.isBypassSupported) {
                        HubToggle(
                            label = stringResource(R.string.bypass_charge_enabled_title),
                            checked = viewModel.bypassChargeEnabled,
                            onCheckedChange = { viewModel.updateBypassChargeEnabled(it) },
                        )
                    }

                    HubSlider(
                        label = stringResource(R.string.icon_idle_alpha_title),
                        value = viewModel.iconIdleAlpha,
                        onValueChange = { viewModel.updateIconIdleAlpha(it) },
                        valueRange = 5f..100f,
                        valueLabel = "${viewModel.iconIdleAlpha.toInt()}%",
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    HubButton(
                        text = stringResource(R.string.quick_start_apps_title),
                        onClick = { showQuickStart = true },
                    )
                }
            }
        }

        GameSpaceBanner(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp),
        )
    }
}

@Composable
private fun HubToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor = if (checked) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)
    val thumbColor = if (checked) Color.White else Color.White.copy(alpha = 0.3f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .drawBehind {
                    drawRoundRect(
                        color = trackColor,
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                    val thumbX = if (checked) size.width - 14.dp.toPx() else 14.dp.toPx()
                    drawCircle(
                        color = thumbColor,
                        radius = 8.dp.toPx(),
                        center = Offset(thumbX, size.height / 2f),
                    )
                }
                .clickable { onCheckedChange(!checked) },
        )
    }
}

@Composable
private fun HubButton(
    text: String,
    onClick: () -> Unit,
    color: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun HubSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    valueLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.6f),
                inactiveTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}

@Composable
private fun HubSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.4f),
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun <T> HubSelector(
    options: List<Pair<T, String>>,
    selectedKey: T,
    onSelect: (T) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (key, label) ->
            val isSelected = key == selectedKey
            val bg = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
            val border = if (isSelected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(10.dp))
                    .clickable { onSelect(key) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun HubIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun AddGamePanel(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onGameAdded: (String) -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AddableApp>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    val registeredPkgs = remember(viewModel.registeredGames) {
        viewModel.registeredGames.map { it.packageName }.toSet()
    }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val bitmapCache = mutableMapOf<String, androidx.compose.ui.graphics.ImageBitmap?>()
        val loaded = pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                if (ai.packageName == context.packageName) return@mapNotNull null
                try {
                    val info = pm.getApplicationInfo(ai.packageName, PackageManager.ApplicationInfoFlags.of(0))
                    val icon = info.loadIcon(pm)
                    val bmp = icon?.toBitmap(48, 48)?.asImageBitmap()
                    AddableApp(
                        packageName = ai.packageName,
                        label = pm.getApplicationLabel(info).toString(),
                        iconBitmap = bmp,
                    )
                } catch (_: Exception) { null }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
        apps = loaded
    }

    val filteredApps = remember(apps, searchQuery, registeredPkgs) {
        apps.filter { app ->
            app.packageName !in registeredPkgs &&
            (searchQuery.isEmpty() ||
             app.label.contains(searchQuery, ignoreCase = true) ||
             app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            HubIconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.add_game),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.9f),
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_apps),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.3f),
                        )
                    }
                    innerTextField()
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onGameAdded(app.packageName) }
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .animateItem(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (app.iconBitmap != null) {
                        Image(
                            bitmap = app.iconBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.3f),
                            maxLines = 1,
                        )
                    }

                    HubButton(
                        text = stringResource(R.string.add),
                        onClick = { onGameAdded(app.packageName) },
                    )
                }
            }
        }
    }
}

private data class AddableApp(
    val packageName: String,
    val label: String,
    val iconBitmap: ImageBitmap?,
)

@Composable
private fun GameSpaceBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
    }

    val appIcon = remember {
        try {
            context.packageManager.getApplicationIcon(context.packageName)
                ?.toBitmap(96, 96)?.asImageBitmap()
        } catch (_: Exception) { null }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            appIcon?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
            )

            if (appVersion.isNotEmpty()) {
                Text(
                    text = appVersion,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "AxionOS",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.3f),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AboutAction(
                    icon = Icons.Outlined.Code,
                    label = stringResource(R.string.about_github),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                )
                AboutAction(
                    icon = Icons.Outlined.Translate,
                    label = stringResource(R.string.about_translate),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TRANSLATE_URL))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                )
                AboutAction(
                    icon = Icons.Outlined.VolunteerActivism,
                    label = stringResource(R.string.about_donate),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                )
            }
        }
    }
}

private const val GITHUB_URL = "https://github.com/AxionAOSP/android_packages_apps_GameSpace"
private const val TRANSLATE_URL = "https://crowdin.com/project/axionos"
private const val DONATE_URL = "https://buymeacoffee.com/rmp22"

@Composable
private fun AboutAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun EmptyHubContent(onAddGame: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "empty_alpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.SportsEsports,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(80.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_library),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(24.dp))
        HubButton(
            text = stringResource(R.string.add_first_game),
            onClick = onAddGame,
        )
    }
}

private data class QuickStartApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

@Composable
private fun QuickStartAppsList(
    apps: List<QuickStartApp>,
    selectedPkgs: List<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(apps, key = { it.packageName }) { app ->
            val isSelected = selectedPkgs.contains(app.packageName)
            val iconBitmap = remember(app.icon) {
                app.icon?.toBitmap(48, 48)?.asImageBitmap()
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(app.packageName, !isSelected) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )

                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .drawBehind {
                            val trackColor = if (isSelected) Color.White.copy(alpha = 0.35f)
                                             else Color.White.copy(alpha = 0.08f)
                            drawRoundRect(
                                color = trackColor,
                                cornerRadius = CornerRadius(12.dp.toPx()),
                            )
                            val thumbX = if (isSelected) size.width - 14.dp.toPx() else 14.dp.toPx()
                            val thumbColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f)
                            drawCircle(
                                color = thumbColor,
                                radius = 8.dp.toPx(),
                                center = Offset(thumbX, size.height / 2f),
                            )
                        },
                )
            }
        }
    }
}
