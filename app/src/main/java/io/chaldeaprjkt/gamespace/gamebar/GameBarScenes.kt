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

package io.chaldeaprjkt.gamespace.gamebar

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.InterruptionHandler
import com.android.compose.animation.scene.InterruptionResult
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitions
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.animation.scene.transitions

object GameBarScenes {
    val Pill = SceneKey("gamebar_pill_idle")
    val VerticalPill = SceneKey("gamebar_pill")
}

object GameBarElements {
    val PillContent = ElementKey("gamebar_pill_content")
    val VerticalPillContent = ElementKey("gamebar_vertical_pill_content")
}

val GameBarTransitions: SceneTransitions = transitions {
    interruptionHandler = object : InterruptionHandler {
        override fun onInterruption(
            interrupted: TransitionState.Transition.ChangeScene,
            newTargetScene: SceneKey,
        ): InterruptionResult = InterruptionResult(
            animateFrom = interrupted.currentScene,
            chain = false,
        )
    }

    from(GameBarScenes.Pill, to = GameBarScenes.VerticalPill) {
        spec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        fractionRange(end = 0.25f) { fade(GameBarElements.PillContent) }
        fractionRange(start = 0.15f) { fade(GameBarElements.VerticalPillContent) }
    }

    from(GameBarScenes.VerticalPill, to = GameBarScenes.Pill) {
        spec = tween(durationMillis = 150, easing = FastOutLinearInEasing)
        fractionRange(end = 0.4f) { fade(GameBarElements.VerticalPillContent) }
        fractionRange(start = 0.3f) { fade(GameBarElements.PillContent) }
    }
}
