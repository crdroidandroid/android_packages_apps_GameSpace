/*
 * Copyright (C) 2024 crDroid Android Project
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
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.utils.di.ServiceViewEntryPoint
import io.chaldeaprjkt.gamespace.utils.dp
import io.chaldeaprjkt.gamespace.utils.entryPointOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.math.RoundingMode
import java.text.DecimalFormat

class MenuSwitcher @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var fpsInfoNode: RandomAccessFile? = null
    private var fpsReadJob: Job? = null
    private var fpsReadInterval = 1000L

    init {
        val nodePath = resources.getString(R.string.config_fpsInfoSysNode)
        val file = File(nodePath)
        if (file.exists() && file.canRead()) {
            try {
                fpsInfoNode = RandomAccessFile(nodePath, "r")
            } catch (e: IOException) {
                Log.e(TAG, "Error while opening file: $nodePath", e)
            }
        } else {
            Log.e(TAG, "$nodePath does not exist or is not readable")
        }
        fpsReadInterval = resources.getInteger(R.integer.config_fpsReadInterval).toLong()
        LayoutInflater.from(context).inflate(R.layout.bar_menu_switcher, this, true)
    }

    private val appSettings by lazy { context.entryPointOf<ServiceViewEntryPoint>().appSettings() }
    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val handler = Handler(Looper.getMainLooper())

    private fun startReading() {
        if (fpsReadJob != null) return
        fpsReadJob = scope.launch {
            do {
                val fps = measureFps()
                handler.post {
                    onFrameUpdated(fps)
                }
                delay(fpsReadInterval)
            } while (isActive)
        }
    }

    private fun stopReading() {
        if (fpsReadJob == null) return
        fpsReadJob?.cancel()
        fpsReadJob = null
    }

    private fun measureFps(): Float {
        fpsInfoNode!!.seek(0L)
        val measuredFps: String
        try {
            measuredFps = fpsInfoNode!!.readLine()
        } catch (e: IOException) {
            Log.e(TAG, "IOException while reading from FPS node, ${e.message}")
            return -1.0f
        }
        try {
            val fps: Float = measuredFps.trim().let {
                if (it.contains(": ")) it.split("\\s+".toRegex())[1] else it
            }.toFloat()
            return fps
        } catch (e: NumberFormatException) {
            Log.e(TAG, "NumberFormatException occurred while parsing FPS info, ${e.message}")
        }
        return -1.0f
    }

    private val content: TextView?
        get() = findViewById(R.id.menu_content)

    var showFps = false
        set(value) {
            setMenuIcon(null)
            field = value
        }

    var isDragged = false
        set(value) {
            if (value && !showFps) setMenuIcon(R.drawable.ic_drag)
            field = value
        }

    fun updateIconState(isExpanded: Boolean, location: Int) {
        showFps = if (isExpanded) false else fpsInfoNode != null && appSettings.showFps
        when {
            isExpanded -> R.drawable.ic_close
            location > 0 -> R.drawable.ic_arrow_right
            else -> R.drawable.ic_arrow_left
        }.let { setMenuIcon(it) }
        updateFrameRateBinding()
    }

    private fun onFrameUpdated(newValue: Float) = scope.launch {
        DecimalFormat("#").apply {
            roundingMode = RoundingMode.HALF_EVEN
            content?.text = this.format(newValue)
        }
    }

    private fun updateFrameRateBinding() {
        if (showFps) {
            startReading()
        } else {
            stopReading()
        }
    }

    private fun setMenuIcon(icon: Int?) {
        when (icon) {
            R.drawable.ic_close, R.drawable.ic_drag -> layoutParams.width = 36.dp
            else -> layoutParams.width = LayoutParams.WRAP_CONTENT
        }
        val ic = icon?.takeIf { !showFps }?.let { resources.getDrawable(it, context.theme) }
        content?.textScaleX = if (showFps) 1f else 0f
        content?.setCompoundDrawablesRelativeWithIntrinsicBounds(null, ic, null, null)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopReading()
    }

    companion object {
        private const val TAG = "MenuSwitcher"
    }
}
