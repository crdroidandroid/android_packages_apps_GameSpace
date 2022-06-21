/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022 Nameless-AOSP
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

    private var activeMode = DANMAKU_MODE
        set(value) {
            field = value
            appSettings.notificationMode = value
            when (value) {
                NO_NOTIFICATION -> {
                    systemSettings.headsUp = false
                    systemSettings.reTicker = false
                    summary?.text = context.getString(R.string.notification_hide)
                }
                HEADS_UP_MODE -> {
                    systemSettings.headsUp = true
                    systemSettings.reTicker = false
                    summary?.text = context.getString(R.string.notification_headsup)
                }
                RETICKER_MODE -> {
                    systemSettings.headsUp = true
                    systemSettings.reTicker = true
                    summary?.text = context.getString(R.string.notification_reticker)
                }
                DANMAKU_MODE -> {
                    systemSettings.headsUp = false
                    systemSettings.reTicker = false
                    summary?.text = context.getString(R.string.notification_danmaku)
                }
            }
            isSelected = value != NO_NOTIFICATION
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.notification_mode_title)
        activeMode = appSettings.notificationMode
        icon?.setImageResource(R.drawable.ic_action_heads_up)
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        activeMode = if (activeMode == DANMAKU_MODE) NO_NOTIFICATION else activeMode + 1
    }

    companion object {
        private const val NO_NOTIFICATION = 0
        private const val HEADS_UP_MODE = 1
        private const val RETICKER_MODE = 2
        private const val DANMAKU_MODE = 3
    }
}
