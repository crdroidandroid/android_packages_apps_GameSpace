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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.ui.viewmodel.RegisteredGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun FeaturedGameCard(
    game: RegisteredGame,
    modifier: Modifier = Modifier,
) {
    val enterEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val enterSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val exitEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    AnimatedContent(
        targetState = game,
        transitionSpec = {
            (fadeIn(enterEffects) + scaleIn(
                initialScale = 0.95f,
                animationSpec = enterSpatial,
            )).togetherWith(
                fadeOut(exitEffects)
            ).using(null)
        },
        contentKey = { it.packageName },
        label = "featured_card",
    ) { currentGame ->
        var dominantColor by remember { mutableStateOf(Color(0xFF2A2D32)) }

        LaunchedEffect(currentGame.icon) {
            val bmp = currentGame.icon?.toBitmap(48, 48)
            if (bmp != null) {
                withContext(Dispatchers.Default) {
                    val palette = Palette.from(bmp).generate()
                    val color = palette.getDarkMutedColor(
                        palette.getMutedColor(0xFF2A2D32.toInt())
                    )
                    dominantColor = Color(color)
                }
            }
        }

        val iconBitmap = remember(currentGame.icon) {
            currentGame.icon?.toBitmap(128, 128)?.asImageBitmap()
        }

        val gradientBrush = Brush.verticalGradient(
            colors = listOf(
                dominantColor.copy(alpha = 0.8f),
                dominantColor.copy(alpha = 0.4f),
                Color(0xFF0D1117).copy(alpha = 0.95f),
            ),
        )

        Box(
            modifier = modifier
                .clip(CardShape)
                .background(gradientBrush)
                .border(1.5.dp, Color.White.copy(alpha = 0.12f), CardShape),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = currentGame.label,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = currentGame.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = gameModeLabel(currentGame.mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
fun gameModeLabel(mode: Int): String = when (mode) {
    1 -> stringResource(R.string.game_mode_standard)
    2 -> stringResource(R.string.game_mode_performance)
    3 -> stringResource(R.string.game_mode_battery)
    else -> stringResource(R.string.game_mode_standard)
}
