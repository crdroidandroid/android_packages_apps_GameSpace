package io.chaldeaprjkt.gamespace.utils

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.graphics.drawable.toBitmap

@Composable
fun rememberDrawablePainter(drawable: Drawable?): Painter {
    val bitmapState = remember(drawable) { mutableStateOf<Painter?>(null) }
    LaunchedEffect(drawable) {
        bitmapState.value = drawable?.toBitmap()?.asImageBitmap()?.let { BitmapPainter(it) }
    }
    return bitmapState.value ?: ColorPainter(Color.Transparent)
}
