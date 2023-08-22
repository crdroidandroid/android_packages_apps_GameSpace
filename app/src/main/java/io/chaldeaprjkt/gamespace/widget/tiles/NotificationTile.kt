/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022 Nameless-AOSP
 * Copyright (C) 2023 the risingOS Android Project
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
package io.chaldeaprjkt.gamespace.widget.tiles

import android.content.Context
import android.util.AttributeSet
import android.view.View
import io.chaldeaprjkt.gamespace.R

class NotificationTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private var activeMode = true
        set(value) {
            field = value
            appSettings.danmakuNotification = value
            summary?.text = if (value) {
                systemSettings.headsup = false
                context.getString(R.string.notification_danmaku)
            } else {
                systemSettings.headsup = true
                context.getString(R.string.state_default)
            }
            isSelected = value
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.notification_mode_title)
        activeMode = appSettings.danmakuNotification
        icon?.setImageResource(R.drawable.ic_action_heads_up)
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        activeMode = !activeMode
    }
}
