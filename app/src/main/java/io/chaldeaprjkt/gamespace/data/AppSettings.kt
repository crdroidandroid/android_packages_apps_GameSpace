/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2023 risingOS Android Project
 *               2022 crDroid Android Project
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
package io.chaldeaprjkt.gamespace.data

import android.app.Service
import android.content.Context
import android.provider.Settings
import android.view.WindowManager
import androidx.preference.PreferenceManager
import io.chaldeaprjkt.gamespace.utils.dp
import io.chaldeaprjkt.gamespace.utils.statusbarHeight
import javax.inject.Inject

class AppSettings @Inject constructor(private val context: Context) {

    private val db by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    private val wm by lazy { context.getSystemService(Service.WINDOW_SERVICE) as WindowManager }

    var x
        get() = db.getInt("offset_x", wm.maximumWindowMetrics.bounds.width() / 2)
        set(point) = db.edit().putInt("offset_x", point).apply()

    var y
        get() = db.getInt("offset_y", context.statusbarHeight + 8.dp)
        set(point) = db.edit().putInt("offset_y", point).apply()

    var showFps
        get() = db.getBoolean("show_fps", false)
        set(point) = db.edit().putBoolean("show_fps", point).apply()

    var noAutoBrightness
        get() = db.getBoolean(KEY_AUTO_BRIGHTNESS_DISABLE, true)
        set(it) = db.edit().putBoolean(KEY_AUTO_BRIGHTNESS_DISABLE, it).apply()

    var noThreeScreenshot
        get() = db.getBoolean(KEY_3SCREENSHOT_DISABLE, false)
        set(it) = db.edit().putBoolean(KEY_3SCREENSHOT_DISABLE, it).apply()

    var danmakuNotification
        get() = db.getBoolean(KEY_DANMAKU_NOTIFICATION_MODE, true)
        set(value) = db.edit().putBoolean(KEY_DANMAKU_NOTIFICATION_MODE, value).apply()

    var callsMode: Int
        get() = db.getString(KEY_CALLS_MODE, "0")?.toIntOrNull() ?: 0
        set(value) = db.edit().putString(KEY_CALLS_MODE, value.toString()).apply()

    var ringerMode: Int
        get() = db.getString(KEY_RINGER_MODE, "3")?.toIntOrNull() ?: 3
        set(value) = db.edit().putString(KEY_RINGER_MODE, value.toString()).apply()

    var menuOpacity: Int
        get() = db.getInt(KEY_MENU_OPACITY, 100)
        set(value) = db.edit().putInt(KEY_MENU_OPACITY, value).apply()

    var tileOrder: List<String>
        get() = db.getString(KEY_TILE_ORDER, null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        set(value) = db.edit().putString(KEY_TILE_ORDER, value.joinToString(",")).apply()

    var brightnessEnabled: Boolean
        get() = db.getBoolean(KEY_BRIGHTNESS_ENABLED, true)
        set(value) = db.edit().putBoolean(KEY_BRIGHTNESS_ENABLED, value).apply()

    var fpsGraphEnabled: Boolean
        get() = db.getBoolean(KEY_FPS_GRAPH_ENABLED, true)
        set(value) = db.edit().putBoolean(KEY_FPS_GRAPH_ENABLED, value).apply()
        
    var quickStartApps: String
        get() = db.getString(KEY_QUICK_START_APPS, "") ?: ""
        set(value) = db.edit().putString(KEY_QUICK_START_APPS, value).apply()

    var callOverlayEnabled
        get() = db.getBoolean(KEY_CALL_OVERLAY_ENABLED, true)
        set(point) = db.edit().putBoolean(KEY_CALL_OVERLAY_ENABLED, point).apply()

    var iconIdleAlpha: Int
        get() = db.getInt(KEY_ICON_IDLE_ALPHA, 25)
        set(value) = db.edit().putInt(KEY_ICON_IDLE_ALPHA, value).apply()

    var autoDnd: Boolean
        get() = db.getBoolean(KEY_AUTO_DND, false)
        set(value) = db.edit().putBoolean(KEY_AUTO_DND, value).apply()

    companion object {
        const val KEY_AUTO_BRIGHTNESS_DISABLE = "gamespace_auto_brightness_disabled"
        const val KEY_3SCREENSHOT_DISABLE = "gamespace_tfgesture_disabled"
        const val KEY_STAY_AWAKE = "gamespace_stay_awake"
        const val KEY_DANMAKU_NOTIFICATION_MODE = "gamespace_danmaku_notification_mode"
        const val KEY_CALLS_MODE = "gamespace_calls_mode"
        const val KEY_RINGER_MODE = "gamespace_ringer_mode"
        const val KEY_LOCK_GESTURE = "gamespace_lock_gesture"
        const val KEY_MENU_OPACITY = "gamespace_menu_opacity"
        const val KEY_TILE_ORDER = "tile_order"
        const val KEY_BRIGHTNESS_ENABLED = "brightness_enabled"
        const val KEY_FPS_GRAPH_ENABLED = "fps_graph_enabled"
        const val KEY_QUICK_START_APPS = "quick_start_apps"
        const val KEY_CALL_OVERLAY_ENABLED = "call_overlay_enabled"
        const val KEY_ICON_IDLE_ALPHA = "gamespace_icon_idle_alpha"
        const val KEY_AUTO_DND = "gamespace_auto_dnd"
    }
}
