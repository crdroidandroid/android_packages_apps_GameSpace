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

package io.chaldeaprjkt.gamespace.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import io.chaldeaprjkt.gamespace.R

@Composable
fun HubBackground(
    dimLeft: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val leftScrimAlpha by animateFloatAsState(
        targetValue = if (dimLeft) 1f else 0.3f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "scrim_alpha",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.hub_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val scrimColor = Color(0xFF040810)
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to scrimColor.copy(alpha = 0.95f * leftScrimAlpha),
                        0.4f to scrimColor.copy(alpha = 0.7f * leftScrimAlpha),
                        0.7f to scrimColor.copy(alpha = 0.25f * leftScrimAlpha),
                        1f to Color.Transparent,
                    ),
                    center = Offset(0f, size.height * 0.5f),
                    radius = size.width * 0.7f,
                ),
            )
        }
    }
}
