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

package io.chaldeaprjkt.gamespace.gamebar

import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.SwipeDetector
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import io.chaldeaprjkt.gamespace.R

private const val PILL_WIDTH_DP = 36
private const val PILL_BUTTON_SIZE_DP = 40
private const val PILL_IDLE_WIDTH_DP = 4
private const val PILL_IDLE_HEIGHT_DP = 64
private const val PILL_IDLE_TOUCH_WIDTH_DP = 24
private const val SWIPE_THRESHOLD_DP = 20

@Composable
fun GameBarView(
    showFps: Boolean,
    fpsText: String,
    isLocked: Boolean,
    isIdle: Boolean,
    idleAlpha: Float,
    dockedOnLeft: Boolean,
    mapperEnabled: Boolean,
    onShowPanel: () -> Unit,
    onToggleLock: () -> Unit,
    onMapControls: () -> Unit,
    pillExpanded: Boolean,
    barTopPx: Int,
    onToggleFps: () -> Unit,
    onExpanded: () -> Unit,
    onCollapsed: () -> Unit,
    collapseRequestKey: Int,
    onDragStart: () -> Pair<Int, Int>,
    onDragUpdate: (Int, Int) -> Unit,
    onDragEnd: (Int, Int) -> Unit,
) {
    val state = rememberMutableSceneTransitionLayoutState(
        initialScene = GameBarScenes.Pill,
        transitions = GameBarTransitions,
    )
    val scope = rememberCoroutineScope()
    val noSwipe = remember {
        object : SwipeDetector {
            override fun detectSwipe(change: PointerInputChange) = false
        }
    }

    val animatedPillAlpha by animateFloatAsState(
        targetValue = if (isIdle) idleAlpha else 1f,
        label = "pillAlpha",
    )

    LaunchedEffect(collapseRequestKey) {
        if (collapseRequestKey > 0) {
            state.setTargetScene(GameBarScenes.Pill, scope)
            onCollapsed()
        }
    }

    var startWindowX by remember { mutableIntStateOf(0) }
    var startWindowY by remember { mutableIntStateOf(0) }
    var accDragX by remember { mutableFloatStateOf(0f) }
    var accDragY by remember { mutableFloatStateOf(0f) }
    var repositioning by remember { mutableStateOf(false) }

    val pointerModifier = Modifier
        .pointerInput(dockedOnLeft) {
            val swipeThresholdPx = SWIPE_THRESHOLD_DP.dp.toPx()
            detectDragGestures(
                onDragStart = { _ ->
                    val pos = onDragStart()
                    startWindowX = pos.first
                    startWindowY = pos.second
                    accDragX = 0f
                    accDragY = 0f
                    repositioning = false
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    accDragX += dragAmount.x
                    accDragY += dragAmount.y
                    if (!repositioning) {
                        val inward = if (dockedOnLeft) accDragX else -accDragX
                        if (abs(accDragY) > swipeThresholdPx || inward < -swipeThresholdPx) {
                            repositioning = true
                        }
                    }
                    if (repositioning) {
                        onDragUpdate(
                            startWindowX + accDragX.toInt(),
                            startWindowY + accDragY.toInt(),
                        )
                    }
                },
                onDragEnd = {
                    if (repositioning) {
                        onDragEnd(
                            startWindowX + accDragX.toInt(),
                            startWindowY + accDragY.toInt(),
                        )
                    } else {
                        val inward = if (dockedOnLeft) accDragX else -accDragX
                        if (inward > swipeThresholdPx && abs(accDragX) > abs(accDragY)) {
                            state.setTargetScene(GameBarScenes.VerticalPill, scope)
                            onExpanded()
                        } else {
                            onDragEnd(startWindowX, startWindowY)
                        }
                    }
                },
                onDragCancel = {
                    onDragEnd(startWindowX, startWindowY)
                },
            )
        }

    val density = LocalDensity.current
    val barTopDp = with(density) { barTopPx.toDp() }

    val sceneContent = @Composable {
        SceneTransitionLayout(
            state = state,
            swipeDetector = noSwipe,
        ) {
            scene(GameBarScenes.Pill) {
                PillTab(
                    dockedOnLeft = dockedOnLeft,
                    showFps = showFps,
                    fpsText = fpsText,
                    idleAlpha = animatedPillAlpha,
                    pointerModifier = pointerModifier,
                    onTap = {
                        state.setTargetScene(GameBarScenes.VerticalPill, scope)
                        onExpanded()
                    },
                    modifier = Modifier.element(GameBarElements.PillContent),
                )
            }
            scene(GameBarScenes.VerticalPill) {
                VerticalPill(
                    isLocked = isLocked,
                    mapperEnabled = mapperEnabled,
                    dockedOnLeft = dockedOnLeft,
                    showFps = showFps,
                    onToggleFps = onToggleFps,
                    onShowPanel = onShowPanel,
                    onToggleLock = onToggleLock,
                    onMapControls = onMapControls,
                    modifier = Modifier.element(GameBarElements.VerticalPillContent),
                )
            }
        }
    }

    if (pillExpanded) {
        val dismissAlignment = if (dockedOnLeft) Alignment.TopStart else Alignment.TopEnd
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        state.setTargetScene(GameBarScenes.Pill, scope)
                        onCollapsed()
                    },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = barTopDp),
                contentAlignment = dismissAlignment,
            ) {
                sceneContent()
            }
        }
    } else {
        sceneContent()
    }
}

private const val FPS_CIRCLE_SIZE_DP = 28

@Composable
private fun PillTab(
    dockedOnLeft: Boolean,
    showFps: Boolean,
    fpsText: String,
    idleAlpha: Float,
    pointerModifier: Modifier,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (showFps) {
        Box(
            modifier = modifier
                .size(FPS_CIRCLE_SIZE_DP.dp)
                .alpha(idleAlpha)
                .then(pointerModifier)
                .pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) }
                .border(0.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                .clip(CircleShape)
                .background(Color(0xCC1A1A1A)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = fpsText,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    } else {
        Box(
            modifier = modifier
                .width(PILL_IDLE_TOUCH_WIDTH_DP.dp)
                .height(PILL_IDLE_HEIGHT_DP.dp)
                .then(pointerModifier),
            contentAlignment = if (dockedOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            val pillIdleShape = RoundedCornerShape((PILL_IDLE_WIDTH_DP / 2).dp)
            Box(
                modifier = Modifier
                    .width(PILL_IDLE_WIDTH_DP.dp)
                    .height(PILL_IDLE_HEIGHT_DP.dp)
                    .alpha(idleAlpha)
                    .border(0.5.dp, Color.White.copy(alpha = 0.4f), pillIdleShape)
                    .clip(pillIdleShape)
                    .background(Color(0xFFBFBFBF))
            )
        }
    }
}

@Composable
private fun VerticalPill(
    isLocked: Boolean,
    mapperEnabled: Boolean,
    dockedOnLeft: Boolean,
    showFps: Boolean,
    onToggleFps: () -> Unit,
    onShowPanel: () -> Unit,
    onToggleLock: () -> Unit,
    onMapControls: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape((PILL_WIDTH_DP / 2).dp)
    Column(
        modifier = modifier
            .padding(
                start = if (dockedOnLeft) 12.dp else 0.dp,
                end = if (dockedOnLeft) 0.dp else 12.dp,
            )
            .width(PILL_WIDTH_DP.dp)
            .background(color = Color(0xCC1A1A1A), shape = pillShape)
            .clip(pillShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = onToggleFps,
            modifier = Modifier.size(PILL_BUTTON_SIZE_DP.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_fps),
                contentDescription = null,
                tint = if (showFps) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = onShowPanel,
            modifier = Modifier.size(PILL_BUTTON_SIZE_DP.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.materialsymbols_ic_dashboard_rounded_filled),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(PILL_BUTTON_SIZE_DP.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (isLocked) R.drawable.materialsymbols_ic_lock_rounded_filled
                    else R.drawable.materialsymbols_ic_lock_open_rounded_filled
                ),
                contentDescription = null,
                tint = if (isLocked) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        if (mapperEnabled) {
            IconButton(
                onClick = onMapControls,
                modifier = Modifier.size(PILL_BUTTON_SIZE_DP.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.materialsymbols_ic_sports_esports_rounded_filled),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
