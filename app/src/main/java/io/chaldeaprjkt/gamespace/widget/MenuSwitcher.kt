package io.chaldeaprjkt.gamespace.widget

import android.app.ActivityTaskManager
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.window.TaskFpsCallback
import io.chaldeaprjkt.gamespace.R
import io.chaldeaprjkt.gamespace.utils.di.ServiceViewEntryPoint
import io.chaldeaprjkt.gamespace.utils.dp
import io.chaldeaprjkt.gamespace.utils.entryPointOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.text.DecimalFormat

class MenuSwitcher @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        LayoutInflater.from(context).inflate(R.layout.bar_menu_switcher, this, true)
    }

    private val appSettings by lazy { context.entryPointOf<ServiceViewEntryPoint>().appSettings() }
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private val taskManager by lazy { ActivityTaskManager.getService() }

    private val taskFpsCallback = object : TaskFpsCallback() {
        override fun onFpsReported(fps: Float) {
            if (isAttachedToWindow) {
                onFrameUpdated(fps)
            }
        }
    }

    private val wm: WindowManager
        get() = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

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
        showFps = if (isExpanded) false else appSettings.showFps
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
            taskManager?.focusedRootTaskInfo?.taskId?.let {
                wm.registerTaskFpsCallback(it, Runnable::run, taskFpsCallback)
            }
        } else {
            wm.unregisterTaskFpsCallback(taskFpsCallback)
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
        wm.unregisterTaskFpsCallback(taskFpsCallback)
    }
}
