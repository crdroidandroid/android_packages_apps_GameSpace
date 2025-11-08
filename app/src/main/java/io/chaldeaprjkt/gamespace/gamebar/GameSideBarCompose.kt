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

import android.content.Context
import android.content.res.Configuration
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import io.chaldeaprjkt.gamespace.data.SystemSettings
import io.chaldeaprjkt.gamespace.gamebar.tiles.TileRepository
import io.chaldeaprjkt.gamespace.utils.GameModeUtils
import io.chaldeaprjkt.gamespace.utils.getQuickStartApps
import io.chaldeaprjkt.gamespace.utils.ScreenUtils
import io.chaldeaprjkt.gamespace.utils.statusbarHeight
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun GameSidebarOverlay(
    gameSideBar: GameSideBar,
    overlayView: ComposeView,
    viewModel: GameBarViewModel
) {
    val showPanel by viewModel.showPanel.collectAsState()
    val expanded by viewModel.expanded.collectAsState()
    val isProcessingPanel by viewModel.panelProcessing.collectAsState()

    LaunchedEffect(showPanel) {
        if (showPanel) {
            viewModel.setPanelProcessing(true)
            gameSideBar.showPanel()
            delay(300)
            viewModel.setPanelProcessing(false)
        } else {
            viewModel.setPanelProcessing(true)
            gameSideBar.hidePanel()
            delay(300)
            viewModel.setPanelProcessing(false)
        }
    }

    LaunchedEffect(expanded) {
        if (!expanded) {
            viewModel.setShowPanel(false)
        }
    }
    
    DisposableEffect(overlayView) {
        val listener = View.OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                viewModel.dismiss()
                true
            } else false
        }
        overlayView.setOnTouchListener(listener)
        onDispose { overlayView.setOnTouchListener(null) }
    }

    Box(
        modifier = Modifier
            .wrapContentSize(
                unbounded = true, 
                align = Alignment.CenterEnd 
            )
    ) {
        when {
            expanded -> ExpandedBar(viewModel)
            else -> CollapsedBar(viewModel)
        }
    }
}


@Composable
fun GamePanelOverlay(
    viewModel: GameBarViewModel
) {
    val context = LocalContext.current
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current

    val screenHeightPx = with(density) { cfg.screenHeightDp.dp.toPx() }
    val statusBarHeight = context.statusbarHeight

    val portrait = cfg.orientation == Configuration.ORIENTATION_PORTRAIT

    val gamePanelHeightDp = with(density) {
        val height = if (portrait)
            screenHeightPx * 0.4f
        else
            screenHeightPx - statusBarHeight
        height.toDp()
    }

    val apps = remember { context.getQuickStartApps(viewModel.appSettings) }

    val offsetY = if (!portrait) statusBarHeight else 0
    val offsetYDp = with(density) { offsetY.toDp() }

    Box(
        modifier = Modifier
            .offset(y = offsetYDp)
            .wrapContentWidth()
            .height(gamePanelHeightDp)
    ) {
        GamePanelCard(
            interactor = viewModel.brightnessInteractor,
            fpsInteractor = viewModel.fpsInteractor,
            apps = apps,
            gameModeUtils = viewModel.gameModeUtils,
            systemSettings = viewModel.systemSettings,
            tileRepository = viewModel.tileRepository
        )
    }
}

@Composable
private fun ExpandedBar(
    viewModel: GameBarViewModel
) {
    val opacity = viewModel.menuOpacity
    val showPanel by viewModel.showPanel.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isProcessingPanel by viewModel.panelProcessing.collectAsState()

    val transition = updateTransition(true, label = "bar_transition")
    val scale by transition.animateFloat(
        label = "scale_anim",
        transitionSpec = { tween(200, easing = FastOutSlowInEasing) }
    ) { 1f }

    val context = LocalContext.current

    val targetIcon = when {
        showPanel -> Icons.Outlined.KeyboardArrowRight
        else -> Icons.Outlined.ExpandMore
    }
    
    val targetRecordIcon = if (isRecording) Icons.Outlined.Stop 
        else Icons.Outlined.FiberManualRecord

    val buttons = listOf(
        Triple(Icons.Outlined.Close, "Close") {
            viewModel.dismiss()
        },
        Triple(targetIcon, "Toggle panel") {
            viewModel.togglePanel()
        },
        Triple(Icons.Outlined.Tune, "Settings") {
            viewModel.openSettings(context)
        },
        Triple(Icons.Outlined.CameraAlt, "Screenshot") {
            viewModel.takeScreenshot()
        },
        Triple(
            targetRecordIcon, "Record screen"
        ) {
            viewModel.toggleRecording()
        }
    )

    Surface(
        modifier = Modifier
            .width(64.dp)
            .padding(end = 8.dp)
            .wrapContentHeight()
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
            }
            .clip(RoundedCornerShape(32.dp)),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = opacity),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .wrapContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            buttons.forEachIndexed { index, (icon, desc, onClick) ->
                val tint = when (index) {
                    buttons.lastIndex -> if (isRecording)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onPrimary
                }
                
                val enabled = when(index) {
                    0, 1 -> !isProcessingPanel
                    else -> true
                }

                BarButton(
                    onClick = onClick,
                    enabled = enabled,
                    icon = icon,
                    contentDescription = desc,
                    tint = tint
                )
            }
        }
    }
}

@Composable
fun BarButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: ImageVector,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onPrimary,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            contentColor = tint,
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = tint,
            disabledContainerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

@Composable
private fun CollapsedBar(
    viewModel: GameBarViewModel
) {
    val opacity = viewModel.menuOpacity
    val currentFps by viewModel.currentFps.collectAsState()
    val showFps by viewModel.showFps.collectAsState()

    val size = 36.dp
    val shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)

    Surface(
        modifier = Modifier
            .size(width = size, height = size)
            .shadow(2.dp, shape)
            .clickable { 
                viewModel.setExpanded(true)
            },
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = opacity),
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (showFps) {
                Text(
                    text = currentFps.roundToInt().toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Game Icon",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
