@file:OptIn(ExperimentalAnimationApi::class)
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
package io.chaldeaprjkt.gamespace.gamebar

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.*
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import com.android.settingslib.display.BrightnessUtils.*
import androidx.core.graphics.drawable.*
import androidx.compose.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.*
import androidx.compose.ui.hapticfeedback.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.*
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.gamebar.brightness.*
import io.chaldeaprjkt.gamespace.gamebar.fps.*
import io.chaldeaprjkt.gamespace.gamebar.tiles.*
import io.chaldeaprjkt.gamespace.settings.SettingsActivity
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

import com.android.internal.util.NTAppLockerHelper

@Composable
fun GamePanelCard(
    interactor: BrightnessInteractor,
    fpsInteractor: FpsInteractor,
    apps: List<AppInfo>,
    gameModeUtils: GameModeUtils,
    systemSettings: SystemSettings,
    tileRepository: TileRepository
) {
    val panelWidth = 300.dp

    val initialMode = remember {
        fromSystemGameMode(gameModeUtils.activeGame?.mode ?: GameManager.GAME_MODE_STANDARD)
    }
    var selectedMode by remember { mutableStateOf(initialMode) }
    var headerExpanded by remember { mutableStateOf(false) }

    val time = rememberCurrentTime()

    Card(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .width(panelWidth)
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        GamePanelContent(
            apps = apps,
            headerExpanded = headerExpanded,
            onToggleExpand = { headerExpanded = !headerExpanded },
            selectedMode = selectedMode,
            onModeChange = { mode ->
                selectedMode = mode
                gameModeUtils.setActiveGameMode(systemSettings, mode.toSystemGameMode())
            },
            interactor = interactor,
            fpsInteractor = fpsInteractor,
            time = time,
            tileRepository = tileRepository
        )
    }
}

@Composable
fun GamePanelContent(
    apps: List<AppInfo>,
    headerExpanded: Boolean,
    onToggleExpand: () -> Unit,
    selectedMode: GameMode,
    onModeChange: (GameMode) -> Unit,
    interactor: BrightnessInteractor,
    fpsInteractor: FpsInteractor,
    time: String,
    tileRepository: TileRepository
) {
    var isEditing by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = if (isEditing) 4.dp else 12.dp, start = 12.dp, bottom = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isEditing) {
            HeaderInfoBar(
                modifier = Modifier.fillMaxWidth(),
                headerExpanded = headerExpanded,
                time = time,
                currentMode = selectedMode,
                onModeChange = onModeChange,
                fpsInteractor = fpsInteractor,
                onToggleExpand = onToggleExpand,
                onEditClick = { isEditing = true },
                tileRepository = tileRepository
            )

            if (apps.isEmpty() == false) {
                QuickStartAppSidebar(apps = apps)
            }

            PanelContent(
                interactor = interactor,
                tileRepository = tileRepository,
                currentMode = selectedMode,
                onEditClick = { isEditing = true },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            TileEditPanel(
                tileRepository = tileRepository,
                onClose = { isEditing = false }
            )
        }
    }
}

@Composable
fun PanelContent(
    interactor: BrightnessInteractor,
    tileRepository: TileRepository,
    currentMode: GameMode,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tiles by remember { derivedStateOf { tileRepository.tiles } }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(start = 4.dp, end = 4.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (tileRepository.isBrightnessVisible.value) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(start = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrightnessSlider(interactor = interactor)
            }
        }

        val visibleTiles = if (expanded) tiles else tiles.take(4)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            visibleTiles.chunked(2).forEach { rowTiles ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowTiles.forEach { tile ->
                        TileButton(
                            tile = tile,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(100f))
                        )
                    }
                    if (rowTiles.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            if (tiles.size > 4) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(24.dp)
                        .clickable { expanded = !expanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(if (expanded) 180f else 0f)
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderInfoBar(
    modifier: Modifier = Modifier,
    headerExpanded: Boolean = true,
    time: String,
    currentMode: GameMode,
    onModeChange: (GameMode) -> Unit,
    fpsInteractor: FpsInteractor,
    onToggleExpand: () -> Unit,
    onEditClick: () -> Unit,
    tileRepository: TileRepository
) {
    val batteryInfo = rememberBatteryInfo()
    val batteryLevel = batteryInfo.level
    val temp = batteryInfo.temperatureC.toInt()

    val modeColor = when (currentMode) {
        GameMode.Performance -> Color(0xFFD32F2F)
        GameMode.PowerSave -> Color(0xFF388E3C)
        GameMode.Balanced -> Color(0xFFEF6C00)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(
                top = if (headerExpanded) 12.dp else 8.dp,
                bottom = if (headerExpanded) 12.dp else 8.dp,
                start = 12.dp,
                end = 4.dp
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TopRowHeader(
                time = time,
                headerExpanded = headerExpanded,
                onToggleExpand = onToggleExpand,
                onEditClick = onEditClick
            )
            InfoRow(
                batteryInfo = batteryInfo,
                currentMode = currentMode,
                modeColor = modeColor
            )
        }

        AnimatedVisibility(
            visible = headerExpanded,
            enter = fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = fadeOut(animationSpec = tween(durationMillis = 0))
        ) {
            Column {
                if (tileRepository.isFpsGraphVisible.value) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FpsGraph(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        interactor = fpsInteractor
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                GameModeSelector(
                    selectedMode = currentMode,
                    onModeSelected = onModeChange
                )
            }
        }
    }
}

@Composable
private fun TopRowHeader(
    time: String,
    headerExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEditClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onEditClick,
            modifier = Modifier.size(18.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit Tiles"
            )
        }

        IconButton(
            onClick = onToggleExpand,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (headerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (headerExpanded) "Collapse" else "Expand"
            )
        }
    }
}

@Composable
private fun InfoRow(
    batteryInfo: BatteryInfo, 
    modeColor: Color,
    currentMode: GameMode) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val temp = batteryInfo.temperatureC.toInt()
        BatteryIndicator(batteryLevel = batteryInfo.level)
        Spacer(modifier = Modifier.width(4.dp))
        InfoItem(icon = Icons.Default.DeviceThermostat, value = "$temp")
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = currentMode.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = modeColor
        )
    }
}

@Composable
private fun InfoItem(
    icon: ImageVector?,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(1.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun TileEditPanel(
    tileRepository: TileRepository,
    onClose: () -> Unit
) {
    val tileHeight = 56.dp
    val tileCorner = 12.dp

    val allTiles = remember { tileRepository.allAvailableTiles }
    val selectedTileIds = remember { mutableStateListOf(*tileRepository.tiles.map { it.id }.toTypedArray()) }

    val selectedTiles = selectedTileIds.mapNotNull { id -> allTiles.find { it.id == id } }
    val unselectedTiles = allTiles.filterNot { selectedTileIds.contains(it.id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 4.dp, start = 12.dp, end = 12.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    tileRepository.updateTileSelection(selectedTileIds)
                    onClose()
                }
            ) {
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text("Panel Options", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            SettingToggleRow(
                title = "Brightness Slider",
                checked = tileRepository.isBrightnessVisible.value,
                onCheckedChange = { tileRepository.setBrightnessEnabled(it) }
            )

            SettingToggleRow(
                title = "FPS Graph",
                checked = tileRepository.isFpsGraphVisible.value,
                onCheckedChange = { tileRepository.setFpsGraphEnabled(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        Text("Selected Tiles", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(8.dp))

        TileGroup(
            tiles = selectedTiles,
            onTileClick = { tile ->
                selectedTileIds.remove(tile.id)
            },
            tileHeight = tileHeight,
            tileCorner = tileCorner
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Available Tiles", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        TileGroup(
            tiles = unselectedTiles,
            onTileClick = { tile ->
                selectedTileIds.add(tile.id)
            },
            tileHeight = tileHeight,
            tileCorner = tileCorner
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun TileGroup(
    tiles: List<TileAction>,
    onTileClick: (TileAction) -> Unit,
    tileHeight: Dp,
    tileCorner: Dp
) {
    val transition = updateTransition(targetState = tiles, label = "TileGroupTransition")

    transition.targetState.chunked(2).forEach { tilePair ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(tileHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            tilePair.forEach { tile ->
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TileItem(
                        tile = tile,
                        onClick = { onTileClick(tile) },
                        tileHeight = tileHeight,
                        tileCorner = tileCorner
                    )
                }
            }

            if (tilePair.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun TileItem(
    tile: TileAction,
    onClick: () -> Unit,
    tileHeight: Dp,
    tileCorner: Dp
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(tileCorner))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                tile.icon,
                contentDescription = tile.label,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                modifier = Modifier.basicMarquee(),
                text = tile.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun TileButton(tile: TileAction, modifier: Modifier = Modifier) {
    val isEnabled = tile.isEnabled
    val bgColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fgColor = if (isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { tile.toggle() }
            .padding(horizontal = 12.dp)
    ) {
        Icon(
            imageVector = tile.icon,
            contentDescription = tile.label,
            tint = fgColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = tile.label,
            fontSize = 12.sp,
            color = fgColor,
            maxLines = 1,
            modifier = Modifier.weight(1f).basicMarquee()
        )
    }
}

@Composable
fun BrightnessSlider(interactor: BrightnessInteractor) {
    val brightnessInfo by interactor.brightnessInfo.collectAsState()
    val isAuto by interactor.isAuto.collectAsState()
    val userHasInteracted by interactor.userHasInteracted.collectAsState()

    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var isSyncingFromSystem by remember { mutableStateOf(false) }

    LaunchedEffect(brightnessInfo) {
        brightnessInfo?.let { info ->
            isSyncingFromSystem = true
            val gamma = convertLinearToGammaFloat(
                info.brightness,
                info.brightnessMinimum,
                info.brightnessMaximum
            )
            val percent = getPercentage(
                gamma.toDouble(),
                GAMMA_SPACE_MIN.toFloat(),
                GAMMA_SPACE_MAX.toFloat()
            )
            sliderPosition = percent.toFloat()
            isSyncingFromSystem = false
        }
    }

    Row {
        Slider(
            value = sliderPosition,
            onValueChange = { newValue ->
                sliderPosition = newValue
                if (isSyncingFromSystem) return@Slider
                interactor.onUserInteracted()
                interactor.setBrightness(newValue)
            },
            enabled = true,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = {
                interactor.toggleAutoMode()
            },
            enabled = brightnessInfo != null && isAuto != null,
            modifier = Modifier.padding(start = 8.dp, end = 0.dp)
        ) {
            Icon(
                imageVector = if (isAuto == true) Icons.Default.BrightnessAuto else Icons.Default.BrightnessHigh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun BatteryIndicator(
    batteryLevel: Int,
    modifier: Modifier = Modifier
) {
    val icon = when {
        batteryLevel >= 90 -> Icons.Default.BatteryFull
        batteryLevel >= 70 -> Icons.Default.Battery4Bar
        batteryLevel >= 50 -> Icons.Default.Battery3Bar
        batteryLevel >= 30 -> Icons.Default.Battery2Bar
        batteryLevel >= 10 -> Icons.Default.Battery1Bar
        else -> Icons.Default.Battery0Bar
    }

    val batteryText = "$batteryLevel%"

    InfoItem(
        icon = icon,
        value = batteryText
    )
}

@Composable
fun FpsGraph(
    modifier: Modifier = Modifier,
    interactor: FpsInteractor
) {
    val fpsHistory by interactor.fpsHistory.collectAsState()
    val maxFps by interactor.dynamicMaxFps.collectAsState()

    val currentFps = fpsHistory.lastOrNull() ?: 0f
    val clampedMaxFps = maxFps.takeIf { it > 0f } ?: 60f

    val redThreshold = clampedMaxFps * 0.5f
    val orangeThreshold = clampedMaxFps * 0.75f

    val fpsColor = when {
        currentFps >= clampedMaxFps * 0.9f -> Color(0xFF4CAF50)
        currentFps >= orangeThreshold -> Color(0xFFFFA000)
        else -> Color(0xFFD32F2F)
    }

    Box(
        modifier = modifier
            .height(50.dp)
            .fillMaxWidth()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
        ) {
            if (fpsHistory.size < 2) return@Canvas
            val samples = fpsHistory.takeLast(size.width.toInt())
            val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
            listOf(
                redThreshold to Color(0xFFD32F2F).copy(alpha = 0.25f),
                orangeThreshold to Color(0xFFFFA000).copy(alpha = 0.25f)
            ).forEach { (fps, color) ->
                val y = size.height * (1f - (fps / clampedMaxFps).coerceIn(0f, 1f))
                drawLine(
                    color = color,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            drawLine(
                color = Color(0xFFD32F2F).copy(alpha = 0.5f),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
            val linePath = Path()
            samples.forEachIndexed { i, fps ->
                val x = i * stepX
                val y = size.height * (1f - (fps / clampedMaxFps).coerceIn(0f, 1f))
                if (i == 0) {
                    linePath.moveTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                }
            }
            drawPath(
                path = linePath,
                color = fpsColor.copy(alpha = 0.5f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .zIndex(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${currentFps.toInt()} fps",
                color = fpsColor,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 20.sp)
            )
        }
    }
}

@Composable
fun GameModeSelector(
    selectedMode: GameMode,
    onModeSelected: (GameMode) -> Unit
) {
    val modes = GameMode.values().toList()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var focusedIndex by remember { mutableIntStateOf(modes.indexOf(selectedMode)) }

    LaunchedEffect(Unit) {
        listState.scrollToItem(focusedIndex)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrolling ->
                if (!isScrolling) {
                    val layoutInfo = listState.layoutInfo
                    val center = layoutInfo.viewportEndOffset / 2
                    val centered = layoutInfo.visibleItemsInfo.minByOrNull { item ->
                        kotlin.math.abs((item.offset + item.size / 2) - center)
                    }
                    centered?.let {
                        val index = it.index
                        if (index != focusedIndex) {
                            focusedIndex = index
                            onModeSelected(modes[index])
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
                        }
                    }
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(modes) { index, mode ->
                val isFocused = index == focusedIndex
                val scale by animateFloatAsState(
                    targetValue = if (isFocused) 1.1f else 1f,
                    label = "scaleAnim"
                )
                val alpha by animateFloatAsState(
                    targetValue = if (isFocused) 1f else 0.4f,
                    label = "alphaAnim"
                )

                val glowColor = when (mode) {
                    GameMode.Performance -> Color(0xFFD32F2F).copy(alpha = 0.32f)
                    GameMode.PowerSave -> Color(0xFF388E3C).copy(alpha = 0.18f)
                    GameMode.Balanced -> Color(0xFFEF6C00).copy(alpha = 0.08f)
                }

                val backgroundColor = if (isFocused) glowColor else Color.Transparent
                val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isFocused) 1f else 0.6f)

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .background(
                            color = backgroundColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                            focusedIndex = index
                            onModeSelected(mode)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        .padding(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Icon(
                            imageVector = mode.icon,
                            contentDescription = mode.displayName,
                            tint = textColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = mode.displayName,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickStartAppSidebar(apps: List<AppInfo>) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Box(
        modifier = Modifier.padding(end = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                apps.forEach { app ->
                    QuickStartAppIcon(appInfo = app)
                }
            }
        }
    }
}

@Composable
fun rememberCurrentTime(): String {
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeState = remember { mutableStateOf(timeFormat.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            timeState.value = timeFormat.format(Date())
            delay(60_000L)
        }
    }
    return timeState.value
}

private val drawablePainterCache = mutableMapOf<Int, Painter>()

@Composable
fun rememberDrawablePainter(drawable: Drawable?): Painter {
    return remember(drawable) {
        drawable?.let {
            val key = it.hashCode()
            drawablePainterCache.getOrPut(key) {
                it.toPainter()
            }
        } ?: ColorPainter(Color.Gray)
    }
}

fun Drawable.toPainter(): Painter {
    return BitmapPainter(this.toBitmap().asImageBitmap())
}

@Composable
fun QuickStartAppIcon(
    appInfo: AppInfo,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val painter = rememberDrawablePainter(appInfo.icon)
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable {
                launchAppInFreeformMode(context, appInfo.packageName)
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = appInfo.name,
            modifier = Modifier.size(40.dp)
        )
    }
}

fun launchAppInFreeformMode(context: Context, packageName: String) {
    val packageManager = context.packageManager
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    NTAppLockerHelper.init(context)
    if (NTAppLockerHelper.get().isAppLocked(packageName)) {
        Toast.makeText(
            context,
            context.getString(R.string.app_locked_message),
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    val display = windowManager.defaultDisplay
    val screenSize = Point()
    display.getSize(screenSize)
    val centerX = screenSize.x / 2
    val centerY = screenSize.y / 2
    val width = 500
    val height = 500
    val launchBounds = Rect(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2)
    val activityOptions = ActivityOptions.makeBasic().apply {
        setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FREEFORM)
        setLaunchBounds(launchBounds)
    }
    try {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent, activityOptions.toBundle())
        }
    } catch (e: Exception) {}
}

@Composable
fun rememberBatteryInfo(): BatteryInfo {
    val context = LocalContext.current
    val batteryInfo = remember { mutableStateOf(BatteryInfo()) }

    LaunchedEffect(Unit) {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)

        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val tempCelsius = tempTenths / 10f

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            batteryInfo.value = BatteryInfo(
                level = percentage,
                temperatureC = tempCelsius
            )
        }
    }

    return batteryInfo.value
}

private fun getPercentage(value: Double, min: Float, max: Float): Double {
    return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
}

data class TileItem(val label: String, val icon: ImageVector)

enum class GameMode(val displayName: String, val icon: ImageVector) {
    Balanced("Balanced", Icons.Default.BatteryStd),
    PowerSave("Power Save", Icons.Default.BatterySaver),
    Performance("Performance", Icons.Default.Bolt)
}

fun GameMode.toSystemGameMode(): Int = when (this) {
    GameMode.Balanced -> GameManager.GAME_MODE_STANDARD
    GameMode.PowerSave -> GameManager.GAME_MODE_BATTERY
    GameMode.Performance -> GameManager.GAME_MODE_PERFORMANCE
}

fun fromSystemGameMode(value: Int): GameMode = when (value) {
    GameManager.GAME_MODE_PERFORMANCE -> GameMode.Performance
    GameManager.GAME_MODE_BATTERY -> GameMode.PowerSave
    else -> GameMode.Balanced
}

data class AppInfo(
    val name: String,
    val icon: Drawable,
    val packageName: String
)

data class BatteryInfo(
    val level: Int = -1,
    val temperatureC: Float = 0f
)
