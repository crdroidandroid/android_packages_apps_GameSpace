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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)

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
import androidx.collection.LruCache
import androidx.core.graphics.drawable.*
import androidx.compose.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

private val RoundedTileShape = RoundedCornerShape(100f)

@Composable
fun GamePanelCard(
    interactor: BrightnessInteractor,
    fpsInteractor: FpsInteractor,
    apps: List<AppInfo>,
    gameModeUtils: GameModeUtils,
    systemSettings: SystemSettings,
    tileRepository: TileRepository,
    maxHeight: Dp = Dp.Unspecified,
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
            tileRepository = tileRepository,
            maxHeight = maxHeight,
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
    tileRepository: TileRepository,
    maxHeight: Dp = Dp.Unspecified,
) {
    var isEditing by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val resolvedMaxHeight = if (maxHeight != Dp.Unspecified) maxHeight else {
        (LocalConfiguration.current.screenHeightDp - 64).dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = resolvedMaxHeight)
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
    val tiles = tileRepository.tiles
    val tilesPerPage = 8
    val pages by remember {
        derivedStateOf { tiles.chunked(tilesPerPage) }
    }
    val pagerState = rememberPagerState { pages.size }

    Column(
        modifier = modifier.padding(start = 4.dp, end = 4.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            key = { pageIndex -> "page_$pageIndex" }
        ) { pageIndex ->
            val pageTiles = pages[pageIndex]

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top)
            ) {
                pageTiles.chunked(4).forEach { rowTiles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowTiles.forEach { tile ->
                            key(tile.id) {
                                TileButton(
                                    tile = tile,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        repeat(4 - rowTiles.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(pages.size) { index ->
                    val isSelected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
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
            .animateContentSize(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
            )
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
            enter = fadeIn(
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
            ) + expandVertically(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
            ),
            exit = fadeOut(
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()
            ) + shrinkVertically(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
            )
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
    val rotateArrow by animateFloatAsState(
        targetValue = if (headerExpanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec()
    )

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
                painter = painterResource(R.drawable.materialsymbols_ic_edit_rounded_filled),
                contentDescription = stringResource(R.string.cd_edit_tiles)
            )
        }

        IconButton(
            onClick = onToggleExpand,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.materialsymbols_ic_expand_more_rounded_filled),
                contentDescription = if (headerExpanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
                modifier = Modifier.rotate(rotateArrow)
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
        InfoItem(
            icon = painterResource(R.drawable.materialsymbols_ic_device_thermostat_rounded_filled),
            value = "$temp"
        )
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
    icon: Any?,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        if (icon != null) {
            when (icon) {
                is ImageVector -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                is Painter -> Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
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
    val allTiles = remember { tileRepository.allAvailableTiles }
    val dragDropState = remember { TileDragDropState(tileRepository.tiles, columns = 2) }

    val availableList = remember {
        val activeIds = tileRepository.tiles.map { it.id }.toSet()
        allTiles.filter { it.id !in activeIds }
            .map { DragTileData(it.id, it.label, it.icon) }
            .toMutableStateList()
    }

    var activeGridOffset by remember { mutableStateOf(Offset.Zero) }

    val localMaxHeight = (LocalConfiguration.current.screenHeightDp - 64).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = localMaxHeight)
            .padding(top = 4.dp, start = 12.dp, end = 12.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    tileRepository.updateTileSelection(dragDropState.tileIds())
                    onClose()
                }
            ) {
                Text(stringResource(R.string.save))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(stringResource(R.string.panel_options), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            SettingToggleRow(
                title = stringResource(R.string.brightness_slider),
                checked = tileRepository.isBrightnessVisible.value,
                onCheckedChange = { tileRepository.setBrightnessEnabled(it) }
            )

            SettingToggleRow(
                title = stringResource(R.string.fps_graph),
                checked = tileRepository.isFpsGraphVisible.value,
                onCheckedChange = { tileRepository.setFpsGraphEnabled(it) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(R.string.selected_tiles),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    activeGridOffset = coords.positionInRoot()
                }
                .dragAndDropActiveGrid(
                    contentOffset = { activeGridOffset },
                    dragDropState = dragDropState,
                ) { ids ->
                    dragDropState.draggedTile?.let { dragged ->
                        if (dragDropState.dragType == DragType.Add) {
                            availableList.removeAll { it.id == dragged.id }
                        }
                    }
                    tileRepository.updateTileSelection(ids)
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val tiles = dragDropState.tiles
            val rows = tiles.chunked(4)
            rows.forEachIndexed { rowIndex, rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowTiles.forEachIndexed { colIndex, tile ->
                        val index = rowIndex * 4 + colIndex
                        val isBeingDragged = dragDropState.isMoving(tile.id)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .onGloballyPositioned { coords ->
                                    val posInRoot = coords.positionInRoot()
                                    val relativeOffset = posInRoot - activeGridOffset
                                    dragDropState.updateItemPosition(
                                        tile.id,
                                        index,
                                        IntOffset(relativeOffset.x.toInt(), relativeOffset.y.toInt()),
                                        coords.size,
                                    )
                                }
                        ) {
                            if (isBeingDragged) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                        )
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .dragAndDropTileSource(tile, dragDropState, DragType.Move)
                                ) {
                                    EditorGameTile(
                                        label = tile.label,
                                        icon = tile.icon,
                                        isAdded = true,
                                        onAction = {
                                            val removed = dragDropState.removeTile(tile.id)
                                            if (removed != null) {
                                                availableList.add(removed)
                                                tileRepository.updateTileSelection(dragDropState.tileIds())
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    repeat(4 - rowTiles.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            if (tiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.drag_tiles_here),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(R.string.available_tiles),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .dragAndDropAvailableZone(dragDropState) { id ->
                    val removed = dragDropState.removeTile(id)
                    if (removed != null) {
                        availableList.add(removed)
                        tileRepository.updateTileSelection(dragDropState.tileIds())
                    }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val rows = availableList.chunked(4)
            rows.forEach { rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowTiles.forEach { tile ->
                        val isBeingDragged = dragDropState.isMoving(tile.id)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(80.dp)
                                .graphicsLayer { alpha = if (isBeingDragged) 0.3f else 1f }
                                .dragAndDropAvailableTileSource(tile, dragDropState)
                        ) {
                            EditorGameTile(
                                label = tile.label,
                                icon = tile.icon,
                                isAdded = false,
                                onAction = {
                                    availableList.remove(tile)
                                    dragDropState.addTile(tile)
                                    tileRepository.updateTileSelection(dragDropState.tileIds())
                                },
                            )
                        }
                    }
                    repeat(4 - rowTiles.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            if (availableList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.all_tiles_added),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        }
    }
}

@Composable
fun EditorGameTile(
    label: String,
    icon: Int,
    isAdded: Boolean,
    onAction: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceBright)
                    .clickable { onAction() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .clip(CircleShape)
                    .background(
                        if (isAdded) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    .clickable { onAction() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isAdded) Icons.Rounded.Remove else Icons.Rounded.Add,
                    contentDescription = null,
                    tint = if (isAdded) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(70.dp).basicMarquee(),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
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
fun TileButton(tile: TileAction, modifier: Modifier = Modifier) {
    val isEnabled by tile.observeEnabled()
    val bgColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceBright
    val fgColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press_scale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .height(80.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(bgColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { tile.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(tile.icon),
                contentDescription = tile.label,
                tint = fgColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = tile.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(64.dp)
                .basicMarquee(),
        )
    }
}

@Composable
fun BrightnessSlider(interactor: BrightnessInteractor) {
    val brightnessInfo by interactor.brightnessInfo.collectAsState()
    val isAuto by interactor.isAuto.collectAsState()

    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var userIsAdjusting by remember { mutableStateOf(false) }

    val targetSliderPosition = remember(brightnessInfo) {
        brightnessInfo?.let { info ->
            val gamma = convertLinearToGammaFloat(
                info.brightness,
                info.brightnessMinimum,
                info.brightnessMaximum
            )
            getPercentage(
                gamma.toDouble(),
                GAMMA_SPACE_MIN.toFloat(),
                GAMMA_SPACE_MAX.toFloat()
            ).toFloat()
        } ?: 0.5f
    }

    LaunchedEffect(targetSliderPosition) {
        if (!userIsAdjusting) {
            sliderPosition = targetSliderPosition
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { sliderPosition }
            .distinctUntilChanged()
            .collectLatest { value ->
                if (userIsAdjusting) {
                    interactor.onUserInteracted()
                    interactor.setBrightness(value)
                    delay(100)
                }
            }
    }

    Row {
        Slider(
            value = sliderPosition,
            onValueChange = { newValue ->
                userIsAdjusting = true
                sliderPosition = newValue
                interactor.onUserInteracted()
                interactor.setBrightness(newValue)
            },
            onValueChangeFinished = {
                userIsAdjusting = false
            },
            enabled = true,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = { interactor.toggleAutoMode() },
            enabled = brightnessInfo != null,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Icon(
                painter =
                    if (isAuto == true)
                        painterResource(R.drawable.materialsymbols_ic_brightness_auto_rounded_filled)
                    else
                        painterResource(R.drawable.materialsymbols_ic_brightness_7_rounded_filled),
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
        batteryLevel >= 90 -> painterResource(R.drawable.materialsymbols_ic_battery_android_full_rounded_filled)
        batteryLevel >= 70 -> painterResource(R.drawable.materialsymbols_ic_battery_android_4_rounded_filled)
        batteryLevel >= 50 -> painterResource(R.drawable.materialsymbols_ic_battery_android_3_rounded_filled)
        batteryLevel >= 30 -> painterResource(R.drawable.materialsymbols_ic_battery_android_2_rounded_filled)
        batteryLevel >= 10 -> painterResource(R.drawable.materialsymbols_ic_battery_android_1_rounded_filled)
        else -> painterResource(R.drawable.materialsymbols_ic_battery_android_0_rounded_filled)
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
    val fpsHistory by interactor.fpsHistory.collectAsState(initial = emptyList())
    val maxFps by interactor.dynamicMaxFps.collectAsState()
    
    val graphData = remember(fpsHistory, maxFps) {
        if (fpsHistory.isNotEmpty()) {
            val minFps = fpsHistory.minOrNull() ?: 0f
            val avgFps = fpsHistory.average().toFloat()
            val maxFpsSeen = fpsHistory.maxOrNull() ?: 0f
            val clampedMaxFps = maxFps.takeIf { it > 0f } ?: 60f
            val fpsColor = when {
                avgFps >= clampedMaxFps * 0.9f -> Color(0xFF4CAF50)
                avgFps >= clampedMaxFps * 0.75f -> Color(0xFFFFA000)
                else -> Color(0xFFD32F2F)
            }
            Triple(Triple(minFps, avgFps, maxFpsSeen), clampedMaxFps, fpsColor)
        } else {
            Triple(Triple(0f, 0f, 0f), 60f, Color.Gray)
        }
    }
    
    val normalizedPoints = remember(fpsHistory, graphData.second) {
        val clampedMaxFps = graphData.second
        fpsHistory.map { fps -> (fps / clampedMaxFps).coerceIn(0f, 1f) }
    }
    
    Box(
        modifier = modifier
            .height(50.dp)
            .fillMaxWidth()
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (normalizedPoints.isNotEmpty() && normalizedPoints.size > 1) {
                val width = size.width
                val height = size.height
                val stepX = width / (normalizedPoints.size - 1)
                
                val path = Path().apply {
                    moveTo(0f, height)
                    
                    lineTo(0f, height - (normalizedPoints[0] * height))
                    
                    for (i in 1 until normalizedPoints.size) {
                        val currentX = i * stepX
                        val currentY = height - (normalizedPoints[i] * height)
                        
                        if (i == 1) {
                            lineTo(currentX, currentY)
                        } else {
                            val prevX = (i - 1) * stepX
                            val prevY = height - (normalizedPoints[i - 1] * height)
                            val controlX = (prevX + currentX) / 2f
                            val controlY = (prevY + currentY) / 2f
                            
                            quadraticBezierTo(controlX, controlY, currentX, currentY)
                        }
                    }
                    
                    lineTo(width, height)
                    close()
                }
                
                val gradient = Brush.verticalGradient(
                    colors = listOf(
                        graphData.third.copy(alpha = 0.6f),
                        graphData.third.copy(alpha = 0.1f)
                    ),
                    startY = 0f,
                    endY = height
                )
                
                drawPath(
                    path = path,
                    brush = gradient
                )
                
                val linePath = Path().apply {
                    moveTo(0f, height - (normalizedPoints[0] * height))
                    for (i in 1 until normalizedPoints.size) {
                        val currentX = i * stepX
                        val currentY = height - (normalizedPoints[i] * height)
                        if (i == 1) {
                            lineTo(currentX, currentY)
                        } else {
                            val prevX = (i - 1) * stepX
                            val prevY = height - (normalizedPoints[i - 1] * height)
                            val controlX = (prevX + currentX) / 2f
                            val controlY = (prevY + currentY) / 2f
                            quadraticBezierTo(controlX, controlY, currentX, currentY)
                        }
                    }
                }
                
                drawPath(
                    path = linePath,
                    color = graphData.third,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
        
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (minFps, avgFps, maxFpsSeen) = graphData.first
            Text(
                text = stringResource(R.string.fps_stats, minFps.toInt(), avgFps.toInt(), maxFpsSeen.toInt()),
                color = graphData.third,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp)
            )
        }
    }
}

@Composable
fun GameModeSelector(
    selectedMode: GameMode,
    onModeSelected: (GameMode) -> Unit
) {
    val modes = GameMode.values()
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { mode ->
            val isSelected = mode == selectedMode

            val color = when (mode) {
                GameMode.Performance -> Color(0xFFD32F2F)
                GameMode.PowerSave -> Color(0xFF388E3C)
                GameMode.Balanced -> Color(0xFFEF6C00)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = if (isSelected) 0.4f else 0.2f))
                    .clickable {
                        if (!isSelected) {
                            onModeSelected(mode)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = mode.icon,
                    contentDescription = mode.displayName,
                    tint = color.copy(alpha = if (isSelected) 1f else 0.4f),
                    modifier = Modifier.size(24.dp)
                )
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
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeState = remember { mutableStateOf(timeFormat.format(Date())) }

    DisposableEffect(Unit) {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Main.immediate + job)

        scope.launch {
            while (isActive) {
                val now = Date()
                val newTime = timeFormat.format(now)
                if (newTime != timeState.value) {
                    timeState.value = newTime
                }
                val millisUntilNextMinute = 60000L - (now.time % 60000L)
                delay(millisUntilNextMinute)
            }
        }

        onDispose {
            job.cancel()
        }
    }

    return timeState.value
}

private val drawablePainterCache = LruCache<Int, Painter>(100)

@Composable
fun rememberDrawablePainter(drawable: Drawable?): Painter {
    return remember(drawable?.hashCode()) {
        drawable?.let {
            val key = it.hashCode()
            drawablePainterCache.get(key) ?: run {
                val painter = it.toPainter()
                drawablePainterCache.put(key, painter)
                painter
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
    val painter = rememberDrawablePainter(appInfo.icon)
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable {
                launchAppInFreeformMode(appInfo.packageName)
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

fun launchAppInFreeformMode(packageName: String) {
    FreeformLauncher.launch(packageName)
}

@Composable
fun rememberBatteryInfo(): BatteryInfo {
    val context = LocalContext.current
    val batteryInfo = remember { mutableStateOf(BatteryInfo()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
                    
                    val tempTenths = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                    val tempCelsius = tempTenths / 10f
                    
                    batteryInfo.value = BatteryInfo(
                        level = percentage,
                        temperatureC = tempCelsius
                    )
                }
            }
        }
        
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        
        context.registerReceiver(receiver, intentFilter)
        
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)
        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percentage = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val tempCelsius = tempTenths / 10f
            
            batteryInfo.value = BatteryInfo(
                level = percentage,
                temperatureC = tempCelsius
            )
        }
        
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    
    return batteryInfo.value
}

private fun getPercentage(value: Double, min: Float, max: Float): Double {
    return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
}

enum class GameMode(val displayName: String, val icon: ImageVector) {
    Balanced("Balanced", Icons.Filled.SportsEsports),
    PowerSave("Power Save", Icons.Filled.EnergySavingsLeaf),
    Performance("Performance", Icons.Filled.ElectricBolt)
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

