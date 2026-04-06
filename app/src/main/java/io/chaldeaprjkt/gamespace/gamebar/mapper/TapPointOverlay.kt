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

package io.chaldeaprjkt.gamespace.gamebar.mapper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.chaldeaprjkt.gamespace.R
import kotlin.math.roundToInt

@Composable
fun TapPointOverlay(
    mappings: List<TapMapping>,
    keyCaptureEvent: KeyCaptureEvent?,
    awaitingKey: Boolean,
    onSave: (List<TapMapping>) -> Unit,
    onCancel: () -> Unit,
    onRequestKeyCapture: (pointId: Int) -> Unit,
    onKeyCaptureConsumed: () -> Unit,
) {
    val points = remember { mutableStateListOf<EditablePoint>() }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var nextId by remember { mutableIntStateOf((mappings.maxOfOrNull { it.id } ?: -1) + 1) }
    var selectedPointId by remember { mutableIntStateOf(-1) }
    val density = LocalDensity.current.density

    if (points.isEmpty() && mappings.isNotEmpty() && containerSize != IntSize.Zero) {
        mappings.forEach { m ->
            points.add(
                EditablePoint(
                    id = m.id,
                    keyCode = m.keyCode,
                    label = m.label,
                    xPx = m.xPercent * containerSize.width,
                    yPx = m.yPercent * containerSize.height,
                )
            )
        }
        nextId = (mappings.maxOfOrNull { it.id } ?: -1) + 1
    }

    LaunchedEffect(keyCaptureEvent) {
        val evt = keyCaptureEvent ?: return@LaunchedEffect
        val idx = points.indexOfFirst { it.id == evt.pointId }
        if (idx != -1) {
            points[idx] = points[idx].copy(
                keyCode = evt.keyCode,
                label = TapMapping.keyCodeToLabel(evt.keyCode),
            )
        }
        onKeyCaptureConsumed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        points.forEachIndexed { index, point ->
            val isSelected = point.id == selectedPointId
            val pointScale by animateFloatAsState(
                targetValue = if (isSelected) 1.15f else 1f,
                animationSpec = tween(150),
                label = "point_scale"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (point.xPx - POINT_RADIUS_PX * density).roundToInt(),
                            (point.yPx - POINT_RADIUS_PX * density).roundToInt()
                        )
                    }
                    .size(POINT_SIZE_DP.dp)
                    .scale(pointScale)
                    .pointerInput(point.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val updated = points[index].copy(
                                xPx = (points[index].xPx + dragAmount.x)
                                    .coerceIn(0f, containerSize.width.toFloat()),
                                yPx = (points[index].yPx + dragAmount.y)
                                    .coerceIn(0f, containerSize.height.toFloat()),
                            )
                            points[index] = updated
                        }
                    }
                    .pointerInput(point.id) {
                        detectTapGestures(
                            onTap = {
                                selectedPointId = point.id
                                onRequestKeyCapture(point.id)
                            },
                            onLongPress = {
                                points.removeAt(index)
                                if (selectedPointId == point.id) selectedPointId = -1
                            }
                        )
                    }
                    .background(
                        color = if (isSelected) Color(0xCC4FC3F7)
                        else Color(0xCC212121),
                        shape = CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = if (isSelected) Color(0xFF4FC3F7)
                        else Color(0xFFBDBDBD),
                        shape = CircleShape
                    )
            ) {
                Text(
                    text = if (point.keyCode == 0) "?" else point.label,
                    color = Color.White,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }

        AnimatedVisibility(
            visible = awaitingKey,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(
                        color = Color(0xDD000000),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 32.dp, vertical = 20.dp)
            ) {
                Text(
                    text = stringResource(R.string.mapper_press_button),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .background(
                    color = Color(0xCC1A1A1A),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = {
                    val cx = containerSize.width / 2f
                    val cy = containerSize.height / 2f
                    val id = nextId++
                    points.add(EditablePoint(id = id, xPx = cx, yPx = cy))
                    selectedPointId = id
                    onRequestKeyCapture(id)
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.mapper_add_point))
            }

            if (selectedPointId != -1) {
                FilledIconButton(
                    onClick = {
                        points.removeAll { it.id == selectedPointId }
                        selectedPointId = -1
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.mapper_delete_point))
                }
            }

            FilledIconButton(
                onClick = onCancel,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }

            FilledIconButton(
                onClick = {
                    if (containerSize.width > 0 && containerSize.height > 0) {
                        val result = points.map { p ->
                            TapMapping(
                                id = p.id,
                                keyCode = p.keyCode,
                                xPercent = p.xPx / containerSize.width,
                                yPercent = p.yPx / containerSize.height,
                                label = p.label,
                            )
                        }
                        onSave(result)
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
            }
        }
    }
}

data class EditablePoint(
    val id: Int,
    val keyCode: Int = 0,
    val label: String = "?",
    val xPx: Float = 0f,
    val yPx: Float = 0f,
)

private const val POINT_SIZE_DP = 48
private const val POINT_RADIUS_PX = POINT_SIZE_DP / 2f
