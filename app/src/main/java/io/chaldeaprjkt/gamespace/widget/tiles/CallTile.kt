/*
 * Copyright (C) 2022 crDroid Android Project
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

class CallTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private var activeMode = NO_ACTION
        set(value) {
            field = value
            when (value) {
                NO_ACTION -> {
                    appSettings.callMode = 0
                    summary?.text = context.getString(R.string.call_mode_no_action)
                }
                AUTO_ANSWER -> {
                    appSettings.callMode = 1
                    summary?.text = context.getString(R.string.call_mode_auto_answer)
                }
                AUTO_REJECT -> {
                    appSettings.callMode = 2
                    summary?.text = context.getString(R.string.call_mode_auto_reject)
                }
            }
            isSelected = value != NO_ACTION
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.call_mode_title)
        activeMode = appSettings.callMode
        icon?.setImageResource(R.drawable.ic_action_call)
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        activeMode = if (activeMode == AUTO_REJECT) NO_ACTION else activeMode + 1
    }

    companion object {
        private const val NO_ACTION = 0
        private const val AUTO_ANSWER = 1
        private const val AUTO_REJECT = 2
    }
}
