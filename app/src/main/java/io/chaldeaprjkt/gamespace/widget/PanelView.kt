/*
 * Copyright (C) 2021 Chaldeaprjkt
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
package io.chaldeaprjkt.gamespace.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.utils.di.ServiceViewEntryPoint
import io.chaldeaprjkt.gamespace.utils.entryPointOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PanelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val appSettings by lazy { context.entryPointOf<ServiceViewEntryPoint>().appSettings() }

    private var uiScope: CoroutineScope? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.panel_view, this, true)
        isClickable = true
        isFocusable = true
    }

    fun animatePanelView() {
        uiScope?.launch {
            val params = layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = appSettings.y
            layoutParams = params
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(300L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        uiScope = CoroutineScope(Dispatchers.Main + Job())
        animatePanelView()
        batteryTemperature()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        uiScope?.cancel()
        uiScope = null
    }

    private fun batteryTemperature() {
        val intent: Intent =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))!!
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10
        val degree = "\u2103"
        val batteryTemp: TextView = requireViewById(R.id.batteryTemp)
        batteryTemp.text = "$temp$degree"
    }
}
